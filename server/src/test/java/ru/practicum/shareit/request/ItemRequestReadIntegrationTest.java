package ru.practicum.shareit.request;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.request.repository.ItemRequestRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Transactional
class ItemRequestReadIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRequestRepository itemRequestRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private EntityManager entityManager;

    private User requestor;
    private User owner;
    private User viewer;
    private final LocalDateTime created = LocalDateTime.of(2026, 11, 1, 12, 0);

    @BeforeEach
    void setUp() {
        requestor = createUser("Автор", "requestor@example.com");
        owner = createUser("Владелец", "owner@example.com");
        viewer = createUser("Читатель", "viewer@example.com");
    }

    @Test
    void returnsOnlyOwnRequestsNewestFirstWithMatchingAnswers() throws Exception {
        ItemRequest older = createRequest(requestor, "Нужна дрель", created.minusDays(1));
        ItemRequest newer = createRequest(requestor, "Нужен молоток", created);
        ItemRequest foreign = createRequest(owner, "Чужой запрос", created.plusDays(1));
        Item drill = createItem(owner, older, "Дрель", true);
        Item secondDrill = createItem(viewer, older, "Вторая дрель", false);
        createItem(owner, null, "Без запроса", true);
        createItem(owner, foreign, "Чужой ответ", true);

        mockMvc.perform(get("/requests").header("X-Sharer-User-Id", requestor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(newer.getId()))
            .andExpect(jsonPath("$[0].description").value("Нужен молоток"))
            .andExpect(jsonPath("$[0].created").value("2026-11-01T12:00:00"))
            .andExpect(jsonPath("$[0].items").isArray())
            .andExpect(jsonPath("$[0].items").isEmpty())
            .andExpect(jsonPath("$[1].id").value(older.getId()))
            .andExpect(jsonPath("$[1].items.length()").value(2))
            .andExpect(jsonPath("$[1].items[*].id", containsInAnyOrder(drill.getId().intValue(), secondDrill.getId().intValue())))
            .andExpect(jsonPath("$[1].items[*].name", containsInAnyOrder("Дрель", "Вторая дрель")))
            .andExpect(jsonPath("$[1].items[*].ownerId", containsInAnyOrder(owner.getId().intValue(), viewer.getId().intValue())));
    }

    @Test
    void returnsOnlyOtherUsersRequestsNewestFirst() throws Exception {
        createRequest(requestor, "Свой запрос", created.plusDays(1));
        ItemRequest older = createRequest(owner, "Чужой старый", created.minusDays(1));
        ItemRequest newer = createRequest(viewer, "Чужой новый", created);
        Item answer = createItem(owner, newer, "Молоток", true);

        mockMvc.perform(get("/requests/all").header("X-Sharer-User-Id", requestor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(newer.getId()))
            .andExpect(jsonPath("$[0].items[0].id").value(answer.getId()))
            .andExpect(jsonPath("$[0].items[0].ownerId").value(owner.getId()))
            .andExpect(jsonPath("$[1].id").value(older.getId()))
            .andExpect(jsonPath("$[1].items").isArray())
            .andExpect(jsonPath("$[1].items").isEmpty());
    }

    @Test
    void allowsUnrelatedUserToReadRequestWithAnswerDetails() throws Exception {
        ItemRequest request = createRequest(requestor, "Нужна дрель", created);
        Item answer = createItem(owner, request, "Дрель", true);

        mockMvc.perform(get("/requests/{requestId}", request.getId()).header("X-Sharer-User-Id", viewer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(request.getId()))
            .andExpect(jsonPath("$.description").value("Нужна дрель"))
            .andExpect(jsonPath("$.created").value("2026-11-01T12:00:00"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(answer.getId()))
            .andExpect(jsonPath("$.items[0].name").value("Дрель"))
            .andExpect(jsonPath("$.items[0].ownerId").value(owner.getId()))
            .andExpect(jsonPath("$.items[0].description").doesNotExist());
    }

    @Test
    void returnsEmptyAnswersForSingleRequest() throws Exception {
        ItemRequest request = createRequest(requestor, "Без ответов", created);

        mockMvc.perform(get("/requests/{requestId}", request.getId()).header("X-Sharer-User-Id", requestor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/requests", "/requests/all"})
    void returnsEmptyListWhenThereAreNoRequests(String path) throws Exception {
        mockMvc.perform(get(path).header("X-Sharer-User-Id", viewer.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsEmptyOtherRequestsWhenOnlyOwnRequestsExist() throws Exception {
        createRequest(requestor, "Только свой", created);

        mockMvc.perform(get("/requests/all").header("X-Sharer-User-Id", requestor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returnsNotFoundForMissingRequest() throws Exception {
        mockMvc.perform(get("/requests/{requestId}", Long.MAX_VALUE).header("X-Sharer-User-Id", viewer.getId()))
            .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/requests", "/requests/all", "/requests/{requestId}"})
    void returnsNotFoundForUnknownUser(String path) throws Exception {
        ItemRequest request = createRequest(requestor, "Нужна дрель", created);

        mockMvc.perform(get(path, request.getId()).header("X-Sharer-User-Id", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/requests", "/requests/all", "/requests/{requestId}"})
    void rejectsMissingUserHeader(String path) throws Exception {
        ItemRequest request = createRequest(requestor, "Нужна дрель", created);

        mockMvc.perform(get(path, request.getId()))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/requests", "/requests/all"})
    void ordersRequestsWithSameCreationTimeByDescendingId(String path) throws Exception {
        User author = path.endsWith("/all") ? owner : requestor;
        ItemRequest first = createRequest(author, "Первый", created);
        ItemRequest second = createRequest(author, "Второй", created);

        mockMvc.perform(get(path).header("X-Sharer-User-Id", requestor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(second.getId()))
            .andExpect(jsonPath("$[1].id").value(first.getId()));
    }

    @Test
    void loadsAnswersForSeveralRequestsWithoutQueryPerRequest() throws Exception {
        for (int i = 0; i < 5; i++) {
            ItemRequest request = createRequest(requestor, "Запрос " + i, created.plusHours(i));
            createItem(owner, request, "Ответ " + i, true);
        }
        Long userId = requestor.getId();
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        mockMvc.perform(get("/requests").header("X-Sharer-User-Id", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].items.length()").value(1))
            .andExpect(jsonPath("$[4].items[0].ownerId").value(owner.getId()));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3L);
    }

    private User createUser(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        return userRepository.save(user);
    }

    private ItemRequest createRequest(User author, String description, LocalDateTime date) {
        ItemRequest request = new ItemRequest();
        request.setRequestor(author);
        request.setDescription(description);
        request.setCreated(date);
        return itemRequestRepository.save(request);
    }

    private Item createItem(User itemOwner, ItemRequest request, String name, boolean available) {
        Item item = new Item();
        item.setOwner(itemOwner);
        item.setRequest(request);
        item.setName(name);
        item.setDescription("Описание вещи");
        item.setAvailable(available);
        return itemRepository.save(item);
    }
}
