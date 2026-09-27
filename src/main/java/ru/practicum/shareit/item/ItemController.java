package ru.practicum.shareit.item;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.item.dto.*;
import ru.practicum.shareit.item.service.ItemService;

import java.util.List;

@RestController
@RequestMapping("/items")
@Slf4j
public class ItemController {

    private final ItemService itemService;

    ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    public List<ItemDto> find(@RequestHeader("X-Sharer-User-Id") Long userId) {
        log.info("Получен запрос на получение вещей пользователя с id={}", userId);
        return this.itemService.getItemsByUserId(userId);
    }

    @GetMapping("/{itemId}")
    public ItemDto findById(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long itemId) {
        log.info("Получен запрос на получение вещи с id={}", itemId);
        return this.itemService.getItemById(itemId, userId);
    }

    @PostMapping
    public ItemDto createItem(@RequestHeader("X-Sharer-User-Id") Long userId, @Valid @RequestBody NewItemRequest newItemRequest) {
        log.info("Получен запрос на создание вещи={} для пользователя с id={}", newItemRequest, userId);
        return this.itemService.createItem(userId, newItemRequest);
    }

    @PostMapping("/{itemId}/comment")
    public CommentDto createComment(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long itemId, @Valid @RequestBody NewCommentRequest commentRequest) {
        log.info("Получен запрос на создание комментария={} от пользователя с id={}", commentRequest, userId);
        return this.itemService.createComment(userId, itemId, commentRequest);
    }

    @PatchMapping("/{itemId}")
    public ItemDto update(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long itemId, @RequestBody UpdateItemRequest updateItemRequest) {
        log.info("Получен запрос на обновление вещи={} для пользователя с id={}", updateItemRequest, userId);
        return this.itemService.updateItem(userId, itemId, updateItemRequest);
    }

    @GetMapping("/search")
    public List<ItemDto> search(@RequestParam String text) {
        log.info("Получен запрос на поиск с текстом={}", text);
        return this.itemService.search(text);
    }
}
