package ru.practicum.shareit.request;

import org.junit.jupiter.api.Test;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.request.dto.ItemAnswerDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.mapper.ItemRequestMapper;
import ru.practicum.shareit.user.model.User;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ItemRequestMapperTest {
    private final ItemRequestMapper mapper = new ItemRequestMapper();

    @Test
    void groupsAnswersByRequestAndPreservesRequestOrder() {
        ItemRequest older = request(10L, "Нужна дрель");
        ItemRequest withoutAnswers = request(20L, "Нужна пила");
        ItemRequest newer = request(30L, "Нужен молоток");
        List<Item> items = List.of(
            item(102L, "Вторая дрель", 7L, older),
            item(103L, "Молоток", 8L, newer),
            item(101L, "Первая дрель", 9L, older)
        );

        List<ItemRequestDto> result = mapper.mapToItemRequestDtos(List.of(newer, withoutAnswers, older), items);

        assertThat(result).extracting(ItemRequestDto::getId).containsExactly(30L, 20L, 10L);
        assertThat(result.getFirst().getDescription()).isEqualTo("Нужен молоток");
        assertThat(result.getFirst().getCreated()).isEqualTo(LocalDateTime.of(2026, 11, 1, 12, 0));
        assertThat(result.getFirst().getItems())
            .extracting(ItemAnswerDto::getId, ItemAnswerDto::getName, ItemAnswerDto::getOwnerId)
            .containsExactly(tuple(103L, "Молоток", 8L));
        assertThat(result.get(1).getItems()).isEmpty();
        assertThat(result.get(2).getItems())
            .extracting(ItemAnswerDto::getId, ItemAnswerDto::getName, ItemAnswerDto::getOwnerId)
            .containsExactlyInAnyOrder(tuple(102L, "Вторая дрель", 7L), tuple(101L, "Первая дрель", 9L));
    }

    @Test
    void returnsEmptyListWhenThereAreNoRequests() {
        assertThat(mapper.mapToItemRequestDtos(List.of(), List.of())).isEmpty();
    }

    @Test
    void returnsRequestWithEmptyAnswersWhenThereAreNoItems() {
        List<ItemRequestDto> result = mapper.mapToItemRequestDtos(List.of(request(10L, "Нужна дрель")), List.of());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(10L);
        assertThat(result.getFirst().getItems()).isEmpty();
    }

    private ItemRequest request(Long id, String description) {
        ItemRequest request = new ItemRequest();
        request.setId(id);
        request.setDescription(description);
        request.setCreated(LocalDateTime.of(2026, 11, 1, 12, 0));
        return request;
    }

    private Item item(Long id, String name, Long ownerId, ItemRequest request) {
        User owner = new User();
        owner.setId(ownerId);
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setOwner(owner);
        item.setRequest(request);
        return item;
    }
}
