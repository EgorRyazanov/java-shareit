package ru.practicum.shareit.item;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.request.ItemRequest;
import ru.practicum.shareit.request.repository.ItemRequestRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ItemRequestLinkIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRequestRepository itemRequestRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long ownerId;
    private Long requestId;

    @BeforeEach
    void setUp() {
        User requestor = new User();
        requestor.setName("Автор запроса");
        requestor.setEmail("requestor@example.com");
        requestor = userRepository.save(requestor);

        User owner = new User();
        owner.setName("Владелец вещи");
        owner.setEmail("owner@example.com");
        ownerId = userRepository.save(owner).getId();

        ItemRequest request = new ItemRequest();
        request.setDescription("Нужна дрель");
        request.setRequestor(requestor);
        request.setCreated(LocalDateTime.now());
        requestId = itemRequestRepository.save(request).getId();
    }

    @Test
    void linksItemToAnotherUsersRequestAndKeepsItemOwner() throws Exception {
        long itemId = createItem("{\"name\":\"Дрель\",\"description\":\"Ударная\",\"available\":true,\"requestId\":" + requestId + "}");

        Map<String, Object> saved = jdbcTemplate.queryForMap("SELECT request_id, owner_id FROM items WHERE id = ?", itemId);
        assertThat(((Number) saved.get("request_id")).longValue()).isEqualTo(requestId);
        assertThat(((Number) saved.get("owner_id")).longValue()).isEqualTo(ownerId);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"name\":\"Дрель\",\"description\":\"Ударная\",\"available\":true}",
        "{\"name\":\"Дрель\",\"description\":\"Ударная\",\"available\":true,\"requestId\":null}"
    })
    void createsItemWithoutRequest(String body) throws Exception {
        long itemId = createItem(body);

        assertThat(jdbcTemplate.queryForObject("SELECT request_id FROM items WHERE id = ?", Long.class, itemId)).isNull();
    }

    @Test
    void rejectsUnknownRequestWithoutSavingItem() throws Exception {
        mockMvc.perform(post("/items")
                .header("X-Sharer-User-Id", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Дрель\",\"description\":\"Ударная\",\"available\":true,\"requestId\":9223372036854775807}"))
            .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items", Long.class)).isZero();
    }

    @Test
    void rejectsUnknownOwnerWithoutSavingItem() throws Exception {
        mockMvc.perform(post("/items")
                .header("X-Sharer-User-Id", Long.MAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Дрель\",\"description\":\"Ударная\",\"available\":true,\"requestId\":" + requestId + "}"))
            .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items", Long.class)).isZero();
    }

    @Test
    void allowsMultipleItemsForOneRequest() throws Exception {
        String body = "{\"name\":\"Дрель\",\"description\":\"Ударная\",\"available\":true,\"requestId\":" + requestId + "}";
        long firstId = createItem(body);
        long secondId = createItem(body);

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM items WHERE request_id = ?", Long.class, requestId))
            .isEqualTo(2L);
    }

    @Test
    void preservesRequestWhenUpdatingItem() throws Exception {
        long itemId = createItem("{\"name\":\"Дрель\",\"description\":\"Ударная\",\"available\":true,\"requestId\":" + requestId + "}");

        mockMvc.perform(patch("/items/{itemId}", itemId)
                .header("X-Sharer-User-Id", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Новая дрель\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Новая дрель"));

        assertThat(jdbcTemplate.queryForObject("SELECT request_id FROM items WHERE id = ?", Long.class, itemId))
            .isEqualTo(requestId);
    }


    private long createItem(String body) throws Exception {
        byte[] response = mockMvc.perform(post("/items")
                .header("X-Sharer-User-Id", ownerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Дрель"))
            .andReturn().getResponse().getContentAsByteArray();
        return objectMapper.readTree(response).get("id").asLong();
    }
}
