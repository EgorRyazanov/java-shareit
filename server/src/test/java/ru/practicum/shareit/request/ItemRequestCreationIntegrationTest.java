package ru.practicum.shareit.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ItemRequestCreationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setName("Автор запроса");
        user.setEmail("requestor@example.com");
        userId = userRepository.save(user).getId();
    }

    @Test
    void createsRequestWithGeneratedIdTimeAndAuthorFromHeader() throws Exception {
        LocalDateTime before = LocalDateTime.now();
        byte[] response = mockMvc.perform(post("/requests")
                .header("X-Sharer-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Нужна дрель\",\"id\":-1,"
                    + "\"created\":\"2000-01-01T00:00:00\",\"requestor\":{\"id\":-1}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Нужна дрель"))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.created").isString())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items").isEmpty())
            .andReturn().getResponse().getContentAsByteArray();

        JsonNode dto = objectMapper.readTree(response);
        assertThat(dto.get("id").asLong()).isPositive();
        assertThat(LocalDateTime.parse(dto.get("created").asText())).isBetween(before, LocalDateTime.now());

        Map<String, Object> saved = jdbcTemplate.queryForMap(
            "SELECT description, requestor_id, created FROM item_requests WHERE id = ?", dto.get("id").asLong());
        assertThat(saved.get("description")).isEqualTo("Нужна дрель");
        assertThat(((Number) saved.get("requestor_id")).longValue()).isEqualTo(userId);
        assertThat(((java.sql.Timestamp) saved.get("created")).toLocalDateTime())
            .isBetween(before, LocalDateTime.now());
    }


    @Test
    void rejectsUnknownUserWithoutSavingRequest() throws Exception {
        mockMvc.perform(post("/requests")
                .header("X-Sharer-User-Id", -1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Нужна дрель\"}"))
            .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item_requests", Long.class)).isZero();
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/requests")
                .header("X-Sharer-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingUserHeader() throws Exception {
        mockMvc.perform(post("/requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Нужна дрель\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsLongDescriptionWithoutTruncation() throws Exception {
        String description = "Длинное описание ".repeat(100);
        byte[] response = mockMvc.perform(post("/requests")
                .header("X-Sharer-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("description", description))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        long id = objectMapper.readTree(response).get("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT description FROM item_requests WHERE id = ?", String.class, id))
            .isEqualTo(description);
    }
}
