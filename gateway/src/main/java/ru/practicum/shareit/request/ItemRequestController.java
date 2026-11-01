package ru.practicum.shareit.request;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.request.dto.NewRequest;


@RestController
@RequestMapping(path = "/requests")
@RequiredArgsConstructor
@Slf4j
public class ItemRequestController {
    private final ItemRequestClient itemRequestClient;

    @PostMapping
    public ResponseEntity<Object> createRequest(@RequestHeader("X-Sharer-User-Id") Long userId,
                                       @Valid @RequestBody NewRequest newRequest) {
        log.info("Получен запрос на создание запроса вещи={} для пользователя с id={}", newRequest, userId);
        return this.itemRequestClient.createRequest(userId, newRequest);
    }

    @GetMapping
    public ResponseEntity<Object> getRequests(@RequestHeader("X-Sharer-User-Id") Long userId) {
        log.info("Получен запрос на получение запросов вещей пользователя с id={}", userId);
        return this.itemRequestClient.getRequests(userId);
    }

    @GetMapping("/all")
    public ResponseEntity<Object> getOtherRequests(@RequestHeader("X-Sharer-User-Id") Long userId) {
        log.info("Получен запрос на получение запросов вещей других пользователей для пользователя с id={}", userId);
        return this.itemRequestClient.getOtherRequests(userId);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<Object> getRequestById(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long requestId) {
        log.info("Получен запрос на получение запроса вещи с id={} пользователем с id={}", requestId, userId);
        return this.itemRequestClient.getRequestById(requestId, userId);
    }
}
