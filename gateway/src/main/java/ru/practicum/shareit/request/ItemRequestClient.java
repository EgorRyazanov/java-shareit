package ru.practicum.shareit.request;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.client.BaseClient;
import ru.practicum.shareit.request.dto.NewRequest;

@Service
public class ItemRequestClient extends BaseClient {
    public ItemRequestClient(@Value("${shareit-server.url}") String serverUrl, RestTemplateBuilder builder) {
        super(serverUrl + "/requests", builder);
    }

    public ResponseEntity<Object> createRequest(Long userId, NewRequest request) {
        return post("", userId, request);
    }

    public ResponseEntity<Object> getRequests(Long userId) {
        return get("", userId);
    }

    public ResponseEntity<Object> getOtherRequests(Long userId) {
        return get("/all", userId);
    }

    public ResponseEntity<Object> getRequestById(Long requestId, Long userId) {
        return get("/" + requestId, userId);
    }
}
