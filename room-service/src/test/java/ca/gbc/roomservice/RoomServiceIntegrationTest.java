package ca.gbc.roomservice;

import ca.gbc.roomservice.dto.RoomRequest;
import ca.gbc.roomservice.model.Room;
import ca.gbc.roomservice.model.RoomFeature;
import ca.gbc.roomservice.model.RoomType;
import ca.gbc.roomservice.repository.RoomRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoomServiceIntegrationTest {

    // Same image tag as docker-compose.yml
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.4"))
            .withDatabaseName("room_service")
            .withUsername("room_service_user")
            .withPassword("password");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RoomRepository roomRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearSeedData() {
        // V2 seed rooms are removed so every test starts from an empty, known state
        roomRepository.deleteAll();
    }

    // ---------- helpers ----------

    private Room save(String code, String building, RoomType type, int capacity, RoomFeature... features) {
        return roomRepository.save(Room.builder()
                .code(code).name("Room " + code).building(building)
                .capacity(capacity).type(type).accessible(false)
                .features(new HashSet<>(Set.of(features)))
                .build());
    }

    private RoomRequest req(String code, int capacity, RoomType type, Set<RoomFeature> features) {
        return new RoomRequest(code, "Name " + code, "SW", capacity, type, true, features);
    }

    private ResultActions postRoom(Object body) throws Exception {
        return mockMvc.perform(post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body)));
    }

    private ResultActions putRoom(long id, Object body) throws Exception {
        return mockMvc.perform(put("/api/rooms/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body)));
    }

    // ---------- Flyway / persistence ----------

    @Test
    void flywayCreatedSchema_roomFeatureIsItsOwnTable() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(2);
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name IN ('room','room_feature')",
                Integer.class);
        assertThat(tables).isEqualTo(2);
    }

    // ---------- POST ----------

    @Test
    void create_returns201_andStoresFeaturesInSeparateTable() throws Exception {
        postRoom(req("T-1", 10, RoomType.LAB, Set.of(RoomFeature.PROJECTOR, RoomFeature.COMPUTERS)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("T-1"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.features", hasSize(2)));

        assertThat(roomRepository.count()).isEqualTo(1);
        Integer featureRows = jdbc.queryForObject("SELECT count(*) FROM room_feature", Integer.class);
        assertThat(featureRows).isEqualTo(2);
    }

    @Test
    void create_duplicateCode_returns409_andNothingExtraSaved() throws Exception {
        save("DUP-1", "SW", RoomType.STUDY_ROOM, 4);

        postRoom(req("DUP-1", 8, RoomType.LAB, Set.of())).andExpect(status().isConflict());

        assertThat(roomRepository.count()).isEqualTo(1);
    }

    @Test
    void create_invalidInput_returns400_andNothingSaved() throws Exception {
        postRoom(req("T-2", 0, RoomType.LAB, Set.of())).andExpect(status().isBadRequest());   // capacity 0
        postRoom(new RoomRequest("T-3", "", "SW", 5, RoomType.LAB, false, Set.of()))           // blank name
                .andExpect(status().isBadRequest());
        postRoom(req("THIS-CODE-IS-WAY-TOO-LONG-FOR-20", 5, RoomType.LAB, Set.of()))            // code > 20
                .andExpect(status().isBadRequest());

        assertThat(roomRepository.count()).isZero();
    }

    @Test
    void create_unknownTypeInBody_returns400() throws Exception {
        postRoom("{\"code\":\"T-4\",\"name\":\"n\",\"building\":\"SW\",\"capacity\":5,\"type\":\"SPACESHIP\"}")
                .andExpect(status().isBadRequest());
        assertThat(roomRepository.count()).isZero();
    }

    // ---------- GET ----------

    @Test
    void getAll_returnsEveryRoom() throws Exception {
        save("A-1", "SW", RoomType.LAB, 10);
        save("A-2", "NE", RoomType.STUDY_ROOM, 4);

        mockMvc.perform(get("/api/rooms")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getAll_filters_aloneAndCombined() throws Exception {
        save("F-1", "SW", RoomType.LAB, 30);
        save("F-2", "SW", RoomType.STUDY_ROOM, 4);
        save("F-3", "NE", RoomType.LAB, 12);
        save("F-4", "NE", RoomType.LECTURE_HALL, 150);

        mockMvc.perform(get("/api/rooms").param("building", "SW")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/rooms").param("type", "LAB")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/rooms").param("minCapacity", "30")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/rooms").param("building", "SW").param("type", "LAB"))
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].code").value("F-1"));
        mockMvc.perform(get("/api/rooms").param("building", "NE").param("type", "LAB").param("minCapacity", "20"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAll_unknownTypeFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/rooms").param("type", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200_andUnknownIdReturns404() throws Exception {
        Room r = save("G-1", "SW", RoomType.LAB, 10, RoomFeature.WHITEBOARD);

        mockMvc.perform(get("/api/rooms/" + r.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("G-1"))
                .andExpect(jsonPath("$.features[0]").value("WHITEBOARD"));
        mockMvc.perform(get("/api/rooms/999999")).andExpect(status().isNotFound());
    }

    // ---------- PUT ----------

    @Test
    void update_returns204_andChangeIsVisible() throws Exception {
        Room r = save("U-1", "SW", RoomType.LAB, 10, RoomFeature.WHITEBOARD);

        putRoom(r.getId(), req("U-1", 25, RoomType.MEETING_ROOM, Set.of(RoomFeature.PROJECTOR)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/" + r.getId()))
                .andExpect(jsonPath("$.capacity").value(25))
                .andExpect(jsonPath("$.type").value("MEETING_ROOM"))
                .andExpect(jsonPath("$.features", hasSize(1)))
                .andExpect(jsonPath("$.features[0]").value("PROJECTOR"));
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        putRoom(999999, req("U-X", 5, RoomType.LAB, Set.of())).andExpect(status().isNotFound());
    }

    @Test
    void update_duplicateCode_returns409_andRoomUnchanged() throws Exception {
        save("U-A", "SW", RoomType.LAB, 10);
        Room b = save("U-B", "SW", RoomType.LAB, 10);

        putRoom(b.getId(), req("U-A", 10, RoomType.LAB, Set.of())).andExpect(status().isConflict());

        assertThat(roomRepository.findById(b.getId()).orElseThrow().getCode()).isEqualTo("U-B");
    }

    @Test
    void update_invalid_returns400() throws Exception {
        Room r = save("U-2", "SW", RoomType.LAB, 10);
        putRoom(r.getId(), req("U-2", 0, RoomType.LAB, Set.of())).andExpect(status().isBadRequest());
        assertThat(roomRepository.findById(r.getId()).orElseThrow().getCapacity()).isEqualTo(10);
    }

    // ---------- DELETE ----------

    @Test
    void delete_returns204_roomAndFeaturesGone() throws Exception {
        Room r = save("D-1", "SW", RoomType.LAB, 10, RoomFeature.PROJECTOR, RoomFeature.WHITEBOARD);

        mockMvc.perform(delete("/api/rooms/" + r.getId())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/rooms/" + r.getId())).andExpect(status().isNotFound());
        assertThat(roomRepository.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM room_feature", Integer.class)).isZero();
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/rooms/999999")).andExpect(status().isNotFound());
    }
}
