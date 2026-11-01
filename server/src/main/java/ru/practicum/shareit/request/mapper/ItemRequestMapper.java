package ru.practicum.shareit.request.mapper;

import org.springframework.stereotype.Component;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.request.ItemRequest;
import ru.practicum.shareit.request.dto.ItemAnswerDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.NewRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ItemRequestMapper {
    public ItemRequest mapToItemRequest(NewRequest newRequest) {
        ItemRequest request = new ItemRequest();
        request.setDescription(newRequest.getDescription());
        request.setCreated(LocalDateTime.now(ZoneId.systemDefault()));
        return request;
    }

    public ItemRequestDto mapToItemRequestDto(ItemRequest request) {
        ItemRequestDto dto = new ItemRequestDto();
        dto.setId(request.getId());
        dto.setDescription(request.getDescription());
        dto.setCreated(request.getCreated());
        return dto;
    }

    public ItemRequestDto mapToItemRequestDto(ItemRequest request, List<Item> items) {
        ItemRequestDto dto = this.mapToItemRequestDto(request);
        dto.setItems(items.stream().map(this::mapToItemAnswerDto).toList());
        return dto;
    }

    public List<ItemRequestDto> mapToItemRequestDtos(List<ItemRequest> requests, List<Item> items) {
        Map<Long, List<Item>> itemsByRequest = items.stream().collect(Collectors.groupingBy(item -> item.getRequest().getId()));

        return requests.stream()
            .map(request -> this.mapToItemRequestDto(request, itemsByRequest.getOrDefault(request.getId(), List.of())))
            .toList();
    }

    public ItemAnswerDto mapToItemAnswerDto(Item item) {
        ItemAnswerDto dto = new ItemAnswerDto();
        dto.setId(item.getId());
        dto.setName(item.getName());
        dto.setOwnerId(item.getOwner().getId());
        return dto;
    }
}
