package ca.gbc.bookingservice;

import ca.gbc.bookingservice.dto.BookingRequest;
import ca.gbc.bookingservice.model.Booking;
import ca.gbc.bookingservice.model.BookingStatus;
import ca.gbc.bookingservice.repository.BookingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BookingServiceIntegrationTest {

    // Same image tag as docker-compose.yml
    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer(DockerImageName.parse("mongo:7.0.14"));

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BookingRepository bookingRepository;

    // A fixed whole-hour anchor well in the future: slot(10) = 10:00, slot(11) = 11:00 ...
    private final Instant base = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

    @BeforeEach
    void clean() {
        bookingRepository.deleteAll();
    }

    // ---------- helpers ----------

    private Instant slot(int hour) {
        return base.plus(hour, ChronoUnit.HOURS);
    }

    private BookingRequest req(long room, long user, int fromHour, int toHour) {
        return new BookingRequest(room, user, slot(fromHour), slot(toHour), "Study group", 4);
    }

    private ResultActions postBooking(Object body) throws Exception {
        return mockMvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body)));
    }

    private ResultActions putBooking(String id, Object body) throws Exception {
        return mockMvc.perform(put("/api/bookings/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body)));
    }

    /** Creates through the API, asserts 201 and returns the new id. */
    private String create(long room, long user, int fromHour, int toHour) throws Exception {
        String json = postBooking(req(room, user, fromHour, toHour))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private Booking stored(String id) {
        return bookingRepository.findById(id).orElseThrow();
    }

    // ---------- POST ----------

    @Test
    void create_returns201_confirmed_createdAtSet_timesRoundTrip() throws Exception {
        BookingRequest r = req(1, 1, 10, 11);

        String json = postBooking(r)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();

        // Instants read back exactly as sent (API and Mongo)
        var node = objectMapper.readTree(json);
        assertThat(Instant.parse(node.get("startTime").asText())).isEqualTo(r.startTime());
        assertThat(Instant.parse(node.get("endTime").asText())).isEqualTo(r.endTime());
        Booking b = stored(node.get("id").asText());
        assertThat(b.getStartTime()).isEqualTo(r.startTime());
        assertThat(b.getEndTime()).isEqualTo(r.endTime());
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.getCreatedAt()).isNotNull();
    }

    @Test
    void create_clientCannotSetStatus() throws Exception {
        String body = "{\"roomId\":1,\"userId\":1,\"startTime\":\"" + slot(10) + "\",\"endTime\":\"" + slot(11)
                + "\",\"purpose\":\"x\",\"attendees\":2,\"status\":\"CANCELLED\"}";
        postBooking(body).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void create_endNotAfterStart_returns400_nothingSaved() throws Exception {
        postBooking(req(1, 1, 11, 10)).andExpect(status().isBadRequest());   // end before start
        postBooking(req(1, 1, 10, 10)).andExpect(status().isBadRequest());   // end equals start
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void create_startInPast_returns400_nothingSaved() throws Exception {
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        postBooking(new BookingRequest(1L, 1L, past, past.plus(1, ChronoUnit.HOURS), "x", 2))
                .andExpect(status().isBadRequest());
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void create_missingFieldsOrBadAttendees_returns400_nothingSaved() throws Exception {
        postBooking("{\"userId\":1}").andExpect(status().isBadRequest());
        postBooking(new BookingRequest(1L, 1L, slot(10), slot(11), "", 4)).andExpect(status().isBadRequest());
        postBooking(new BookingRequest(1L, 1L, slot(10), slot(11), "x", 0)).andExpect(status().isBadRequest());
        assertThat(bookingRepository.count()).isZero();
    }

    // ---------- overlap rules ----------

    @Test
    void overlap_sameRoom_returns409_andNothingSaved() throws Exception {
        create(1, 1, 10, 12);

        postBooking(req(1, 2, 11, 13)).andExpect(status().isConflict());   // starts inside
        postBooking(req(1, 2, 9, 11)).andExpect(status().isConflict());    // ends inside
        postBooking(req(1, 2, 10, 12)).andExpect(status().isConflict());   // identical
        postBooking(req(1, 2, 9, 13)).andExpect(status().isConflict());    // contains it
        postBooking(req(1, 2, 10, 11)).andExpect(status().isConflict());   // contained by it

        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void overlap_differentRoom_isAllowed() throws Exception {
        create(1, 1, 10, 12);
        create(2, 1, 10, 12);
        assertThat(bookingRepository.count()).isEqualTo(2);
    }

    @Test
    void backToBack_bothSucceed() throws Exception {
        create(1, 1, 10, 11);
        create(1, 2, 11, 12);   // starts exactly when the first ends
        create(1, 3, 9, 10);    // ends exactly when the first starts
        assertThat(bookingRepository.count()).isEqualTo(3);
    }

    @Test
    void cancelThenRebook_freedSlotCanBeBookedAgain() throws Exception {
        String first = create(1, 1, 10, 11);
        postBooking(req(1, 2, 10, 11)).andExpect(status().isConflict());

        mockMvc.perform(patch("/api/bookings/" + first + "/cancel")).andExpect(status().isOk());

        create(1, 2, 10, 11);
        assertThat(bookingRepository.count()).isEqualTo(2);   // cancelled one is kept
    }

    // ---------- GET ----------

    @Test
    void getAll_andFilters_aloneAndCombined() throws Exception {
        create(1, 1, 10, 11);
        create(1, 2, 11, 12);
        create(2, 1, 10, 11);

        mockMvc.perform(get("/api/bookings")).andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(get("/api/bookings").param("roomId", "1")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/bookings").param("userId", "1")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/bookings").param("roomId", "1").param("userId", "1"))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/bookings").param("roomId", "99")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getById_returns200_andUnknownIdReturns404() throws Exception {
        String id = create(1, 1, 10, 11);
        mockMvc.perform(get("/api/bookings/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.roomId").value(1));
        mockMvc.perform(get("/api/bookings/000000000000000000000000")).andExpect(status().isNotFound());
    }

    // ---------- PUT ----------

    @Test
    void update_returns204_andChangeIsVisible() throws Exception {
        String id = create(1, 1, 10, 11);

        putBooking(id, new BookingRequest(2L, 5L, slot(14), slot(16), "Updated", 8))
                .andExpect(status().isNoContent());

        Booking b = stored(id);
        assertThat(b.getRoomId()).isEqualTo(2L);
        assertThat(b.getUserId()).isEqualTo(5L);
        assertThat(b.getStartTime()).isEqualTo(slot(14));
        assertThat(b.getPurpose()).isEqualTo("Updated");
        assertThat(b.getAttendees()).isEqualTo(8);
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void update_doesNotConflictWithItself() throws Exception {
        String id = create(1, 1, 10, 12);
        // same room, overlapping its own old time, only the purpose/time shifts
        putBooking(id, new BookingRequest(1L, 1L, slot(10), slot(13), "Longer", 4)).andExpect(status().isNoContent());
        assertThat(stored(id).getEndTime()).isEqualTo(slot(13));
    }

    @Test
    void update_overlappingAnotherBooking_returns409_andUnchanged() throws Exception {
        create(1, 1, 10, 11);
        String second = create(1, 2, 12, 13);

        putBooking(second, req(1, 2, 10, 11)).andExpect(status().isConflict());

        assertThat(stored(second).getStartTime()).isEqualTo(slot(12));
    }

    @Test
    void update_cancelledBooking_returns409() throws Exception {
        String id = create(1, 1, 10, 11);
        mockMvc.perform(patch("/api/bookings/" + id + "/cancel")).andExpect(status().isOk());

        putBooking(id, req(1, 1, 14, 15)).andExpect(status().isConflict());

        assertThat(stored(id).getStartTime()).isEqualTo(slot(10));
    }

    @Test
    void update_unknownId_returns404_andInvalidReturns400() throws Exception {
        putBooking("000000000000000000000000", req(1, 1, 10, 11)).andExpect(status().isNotFound());

        String id = create(1, 1, 10, 11);
        putBooking(id, req(1, 1, 12, 11)).andExpect(status().isBadRequest());
        assertThat(stored(id).getEndTime()).isEqualTo(slot(11));
    }

    // ---------- PATCH cancel ----------

    @Test
    void cancel_returns200_cancelled_andStaysInCollection() throws Exception {
        String id = create(1, 1, 10, 11);

        mockMvc.perform(patch("/api/bookings/" + id + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(bookingRepository.count()).isEqualTo(1);
        assertThat(stored(id).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancel_again_returns409_andUnknownReturns404() throws Exception {
        String id = create(1, 1, 10, 11);
        mockMvc.perform(patch("/api/bookings/" + id + "/cancel")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/bookings/" + id + "/cancel")).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/bookings/000000000000000000000000/cancel")).andExpect(status().isNotFound());
    }

    // ---------- DELETE ----------

    @Test
    void delete_returns204_andBookingIsGone() throws Exception {
        String id = create(1, 1, 10, 11);

        mockMvc.perform(delete("/api/bookings/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bookings/" + id)).andExpect(status().isNotFound());
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/bookings/000000000000000000000000")).andExpect(status().isNotFound());
    }
}
