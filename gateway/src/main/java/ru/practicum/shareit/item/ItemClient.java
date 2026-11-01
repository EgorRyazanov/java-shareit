package ru.practicum.shareit.item;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.client.BaseClient;
import ru.practicum.shareit.item.dto.NewCommentRequest;
import ru.practicum.shareit.item.dto.NewItemRequest;
import ru.practicum.shareit.item.dto.UpdateItemRequest;

import java.util.Map;

@Service
public class ItemClient extends BaseClient {
    public ItemClient(@Value("${shareit-server.url}") String serverUrl, RestTemplateBuilder builder) {
        super(serverUrl + "/items", builder);
    }

    public ResponseEntity<Object> getItemsByUserId(Long userId) {
        return get("", userId);
    }

    public ResponseEntity<Object> getItemById(Long itemId, Long userId) {
        return get("/" + itemId, userId);
    }

    public ResponseEntity<Object> createItem(Long userId, NewItemRequest request) {
        return post("", userId, request);
    }

    public ResponseEntity<Object> updateItem(Long userId, Long itemId, UpdateItemRequest request) {
        return patch("/" + itemId, userId, request);
    }

    public ResponseEntity<Object> createComment(Long userId, Long itemId, NewCommentRequest request) {
        return post("/" + itemId + "/comment", userId, request);
    }

    public ResponseEntity<Object> search(String text) {
        return get("/search?text={text}", null, Map.of("text", text));
    }
}
