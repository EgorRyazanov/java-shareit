package ru.practicum.shareit.user;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.user.dto.NewUserRequest;
import ru.practicum.shareit.user.dto.UpdateUserRequest;
import ru.practicum.shareit.user.service.UserService;
import ru.practicum.shareit.user.dto.UserDto;

@RestController
@RequestMapping(path = "/users")
@Slf4j
public class UserController {
    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public UserDto findById(@PathVariable("userId") Long id) {
        log.info("Получен запрос на получение пользователя");
        return this.userService.getUserById(id);
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody NewUserRequest user) {
        log.info("Получен запрос на создание пользователя");
        UserDto createdUser = this.userService.createUser(user);
        log.info("Запрос на создание пользователя завершился успешно");
        return createdUser;
    }

    @DeleteMapping("/{userId}")
    public void delete(@PathVariable("userId") Long id) {
        log.info("Получен запрос на удаление из друзей");
        this.userService.deleteUser(id);
    }

    @PatchMapping("/{userId}")
    public UserDto update(@PathVariable("userId") Long id, @Valid @RequestBody UpdateUserRequest user) {
        log.info("Получен запрос на обновление пользователя");
        UserDto updatedUser = this.userService.updateUser(id, user);
        log.info("Запрос на обновление пользователя завершился успешно");
        return updatedUser;
    }
}
