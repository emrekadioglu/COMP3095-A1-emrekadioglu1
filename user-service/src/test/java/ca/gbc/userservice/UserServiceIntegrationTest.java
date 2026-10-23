package ca.gbc.userservice;

import ca.gbc.userservice.dto.UserRequest;
import ca.gbc.userservice.model.User;
import ca.gbc.userservice.model.UserRole;
import ca.gbc.userservice.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserServiceIntegrationTest {

    // Same image tag as docker-compose.yml
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.4"))
            .withDatabaseName("user_service")
            .withUsername("user_service_user")
            .withPassword("password");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearSeedData() {
        // V2 seed users are removed so every test starts from an empty, known state
        userRepository.deleteAll();
    }

    // ---------- helpers ----------

    private User save(String email, UserRole role) {
        return userRepository.save(User.builder()
                .email(email).firstName("First").lastName("Last").role(role).build());
    }

    private UserRequest req(String email, UserRole role) {
        return new UserRequest(email, "Alex", "Smith", role, "IT");
    }

    private ResultActions postUser(Object body) throws Exception {
        return mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body)));
    }

    private ResultActions putUser(long id, Object body) throws Exception {
        return mockMvc.perform(put("/api/users/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : objectMapper.writeValueAsString(body)));
    }

    // ---------- Flyway ----------

    @Test
    void flywayCreatedAppUserTable() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'app_user'", Integer.class);
        assertThat(tables).isEqualTo(1);
    }

    // ---------- POST ----------

    @Test
    void create_returns201_emailStoredLowerCase() throws Exception {
        postUser(req("Alex@GBC.ca", UserRole.STUDENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value("alex@gbc.ca"))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findAll().get(0).getEmail()).isEqualTo("alex@gbc.ca");
    }

    @Test
    void create_duplicateEmail_returns409_regardlessOfCase_andNothingSaved() throws Exception {
        save("alex@gbc.ca", UserRole.STUDENT);

        postUser(req("alex@gbc.ca", UserRole.STAFF)).andExpect(status().isConflict());
        postUser(req("ALEX@GBC.CA", UserRole.STAFF)).andExpect(status().isConflict());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void create_invalidEmailOrMissingField_returns400_andNothingSaved() throws Exception {
        postUser(req("not-an-email", UserRole.STUDENT)).andExpect(status().isBadRequest());
        postUser(new UserRequest("a@gbc.ca", "", "Smith", UserRole.STUDENT, null)).andExpect(status().isBadRequest());
        postUser(new UserRequest("a@gbc.ca", "Alex", "Smith", null, null)).andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void create_unknownRoleInBody_returns400() throws Exception {
        postUser("{\"email\":\"a@gbc.ca\",\"firstName\":\"A\",\"lastName\":\"B\",\"role\":\"WIZARD\"}")
                .andExpect(status().isBadRequest());
        assertThat(userRepository.count()).isZero();
    }

    // ---------- GET ----------

    @Test
    void getAll_returnsEveryUser() throws Exception {
        save("a@gbc.ca", UserRole.STUDENT);
        save("b@gbc.ca", UserRole.STAFF);
        mockMvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void getAll_filterByRole() throws Exception {
        save("a@gbc.ca", UserRole.STUDENT);
        save("b@gbc.ca", UserRole.STUDENT);
        save("c@gbc.ca", UserRole.FACULTY);

        mockMvc.perform(get("/api/users").param("role", "STUDENT")).andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get("/api/users").param("role", "FACULTY")).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/users").param("role", "STAFF")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void getAll_unknownRoleFilter_returns400() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "WIZARD")).andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns200_andUnknownIdReturns404() throws Exception {
        User u = save("g@gbc.ca", UserRole.STAFF);
        mockMvc.perform(get("/api/users/" + u.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("g@gbc.ca"));
        mockMvc.perform(get("/api/users/999999")).andExpect(status().isNotFound());
    }

    // ---------- PUT ----------

    @Test
    void update_returns204_andChangeIsVisible() throws Exception {
        User u = save("u@gbc.ca", UserRole.STUDENT);

        putUser(u.getId(), new UserRequest("New@GBC.ca", "Newfirst", "Newlast", UserRole.FACULTY, "Math"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + u.getId()))
                .andExpect(jsonPath("$.email").value("new@gbc.ca"))
                .andExpect(jsonPath("$.firstName").value("Newfirst"))
                .andExpect(jsonPath("$.role").value("FACULTY"));
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        putUser(999999, req("x@gbc.ca", UserRole.STUDENT)).andExpect(status().isNotFound());
    }

    @Test
    void update_duplicateEmail_returns409_andUserUnchanged() throws Exception {
        save("a@gbc.ca", UserRole.STUDENT);
        User b = save("b@gbc.ca", UserRole.STUDENT);

        putUser(b.getId(), req("A@gbc.ca", UserRole.STUDENT)).andExpect(status().isConflict());

        assertThat(userRepository.findById(b.getId()).orElseThrow().getEmail()).isEqualTo("b@gbc.ca");
    }

    @Test
    void update_invalid_returns400() throws Exception {
        User u = save("v@gbc.ca", UserRole.STUDENT);
        putUser(u.getId(), req("bad-email", UserRole.STUDENT)).andExpect(status().isBadRequest());
        assertThat(userRepository.findById(u.getId()).orElseThrow().getEmail()).isEqualTo("v@gbc.ca");
    }

    // ---------- DELETE ----------

    @Test
    void delete_returns204_andUserIsGone() throws Exception {
        User u = save("d@gbc.ca", UserRole.STAFF);

        mockMvc.perform(delete("/api/users/" + u.getId())).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + u.getId())).andExpect(status().isNotFound());
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void delete_unknownId_returns404() throws Exception {
        mockMvc.perform(delete("/api/users/999999")).andExpect(status().isNotFound());
    }
}
