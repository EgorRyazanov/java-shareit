package ru.practicum.shareit.item.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.NewItemRequest;
import ru.practicum.shareit.item.dto.UpdateItemRequest;
import ru.practicum.shareit.item.mapper.ItemMapper;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.storage.ItemStorage;
import ru.practicum.shareit.user.service.UserService;

import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {
    private final ItemStorage itemStorage;
    private final UserService userService;
    private final ItemMapper itemMapper;

    @Override
    public List<ItemDto> getItemsByUserId(Long id) {
        log.trace("Получение всех вещей пользователя с id={}", id);

        return this.itemStorage.getItemsByUserId(id).stream().map(this.itemMapper::mapToItemDto).toList();
    }

    @Override
    public List<ItemDto> search(String text) {
        if (text.isBlank()) {
            return List.of();
        }

        return this.itemStorage.search(text).stream().map(this.itemMapper::mapToItemDto).toList();
    }

    @Override
    public ItemDto createItem(Long userId, NewItemRequest itemDto) {
        this.userService.getUserById(userId);

        log.trace("Создание вещи: {}", itemDto);

        Item item = this.itemMapper.mapToItem(itemDto);
        item.setCreatedUserId(userId);

        return this.itemMapper.mapToItemDto(this.itemStorage.createItem(userId, item));
    }

    @Override
    public ItemDto getItemById(Long id) {
        log.trace("Получение вещи по id={}", id);
        return itemStorage.getItemById(id)
            .map(this.itemMapper::mapToItemDto)
            .orElseThrow(NotFoundException::new);
    }

    @Override
    public ItemDto updateItem(Long userId, Long itemId, UpdateItemRequest item) {
        this.userService.getUserById(userId);
        Item itemToUpdate = this.itemStorage.getItemById(itemId).orElseThrow(NotFoundException::new);

        if (!Objects.equals(itemToUpdate.getCreatedUserId(), userId)) {
            throw new NotFoundException();
        }

        if (item.getName() != null) {
            itemToUpdate.setName(item.getName());
        }

        if (item.getDescription() != null) {
            itemToUpdate.setDescription(item.getDescription());
        }

        if (item.getAvailable() != null) {
            itemToUpdate.setAvailable(item.getAvailable());
        }

        return this.itemMapper.mapToItemDto(itemStorage.updateItem(itemToUpdate));
    }
}
