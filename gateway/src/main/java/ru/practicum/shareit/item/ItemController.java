package ru.practicum.shareit.item;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.item.dto.*;


@RestController
@RequestMapping("/items")
@Slf4j
public class ItemController {

    private final ItemClient itemClient;

    ItemController(ItemClient itemClient) {
        this.itemClient = itemClient;
    }

    @GetMapping
    public ResponseEntity<Object> find(@RequestHeader("X-Sharer-User-Id") Long userId) {
        log.info("Получен запрос на получение вещей пользователя с id={}", userId);
        return this.itemClient.getItemsByUserId(userId);
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<Object> findById(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long itemId) {
        log.info("Получен запрос на получение вещи с id={}", itemId);
        return this.itemClient.getItemById(itemId, userId);
    }

    @PostMapping
    public ResponseEntity<Object> createItem(@RequestHeader("X-Sharer-User-Id") Long userId, @Valid @RequestBody NewItemRequest newItemRequest) {
        log.info("Получен запрос на создание вещи={} для пользователя с id={}", newItemRequest, userId);
        return this.itemClient.createItem(userId, newItemRequest);
    }

    @PostMapping("/{itemId}/comment")
    public ResponseEntity<Object> createComment(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long itemId, @Valid @RequestBody NewCommentRequest commentRequest) {
        log.info("Получен запрос на создание комментария={} от пользователя с id={}", commentRequest, userId);
        return this.itemClient.createComment(userId, itemId, commentRequest);
    }

    @PatchMapping("/{itemId}")
    public ResponseEntity<Object> update(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long itemId, @RequestBody UpdateItemRequest updateItemRequest) {
        log.info("Получен запрос на обновление вещи={} для пользователя с id={}", updateItemRequest, userId);
        return this.itemClient.updateItem(userId, itemId, updateItemRequest);
    }

    @GetMapping("/search")
    public ResponseEntity<Object> search(@RequestParam String text) {
        log.info("Получен запрос на поиск с текстом={}", text);
        return this.itemClient.search(text);
    }
}
