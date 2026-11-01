package ru.practicum.shareit.item.service;

import ru.practicum.shareit.item.dto.*;

import java.util.List;

public interface ItemService {
    List<ItemDto> getItemsByUserId(Long id);

    List<ItemDto> search(String text);

    CommentDto createComment(Long userId, Long itemId, NewCommentRequest comment);

    ItemDto createItem(Long userId, NewItemRequest itemDto);

    ItemDto getItemById(Long id, Long userId);

    ItemDto updateItem(Long userId, Long itemId, UpdateItemRequest itemDto);
}
