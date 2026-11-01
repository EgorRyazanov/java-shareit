package ru.practicum.shareit.user;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.user.dto.NewUserRequest;
import ru.practicum.shareit.user.dto.UpdateUserRequest;

@RestController
@RequestMapping(path = "/users")
@Slf4j
public class UserController {
    private final UserClient userClient;

    UserController(UserClient userClient) {
        this.userClient = userClient;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Object> findById(@PathVariable("userId") Long id) {
        log.info("Получен запрос на получение пользователя");
        return this.userClient.getUserById(id);
    }

    @PostMapping
    public ResponseEntity<Object> create(@Valid @RequestBody NewUserRequest user) {
        log.info("Получен запрос на создание пользователя");
        return this.userClient.createUser(user);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Object> delete(@PathVariable("userId") Long id) {
        log.info("Получен запрос на удаление из друзей");
        return this.userClient.deleteUser(id);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<Object> update(@PathVariable("userId") Long id, @Valid @RequestBody UpdateUserRequest user) {
        log.info("Получен запрос на обновление пользователя");
        return this.userClient.updateUser(id, user);
    }
}
