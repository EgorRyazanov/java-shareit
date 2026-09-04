package ru.practicum.shareit.item.service;

import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.NewItemRequest;
import ru.practicum.shareit.item.dto.UpdateItemRequest;

import java.util.List;

public interface ItemService {
    List<ItemDto> getItemsByUserId(Long id);

    List<ItemDto> search(String text);

    ItemDto createItem(Long userId, NewItemRequest itemDto);

    ItemDto getItemById(Long id);

    ItemDto updateItem(Long userId, Long itemId, UpdateItemRequest itemDto);
}
