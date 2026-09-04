package ru.practicum.shareit.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.dto.NewUserRequest;
import ru.practicum.shareit.user.dto.UpdateUserRequest;
import ru.practicum.shareit.user.mapper.UserMapper;
import ru.practicum.shareit.user.storage.UserStorage;
import ru.practicum.shareit.user.dto.UserDto;

import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {
    private final UserStorage userStorage;
    private final UserMapper userMapper;

    public UserDto getUserById(Long id) {
        log.trace("Получение пользователя по id={}", id);
        return userStorage.getUserById(id)
            .map(this.userMapper::mapToUserDto)
            .orElseThrow(NotFoundException::new);
    }

    public UserDto createUser(NewUserRequest newUser) {
        log.trace("Создание пользователя {}", newUser);

        if (this.userStorage.containsUserWithEmail(newUser.getEmail())) {
            throw new ConflictException();
        }

        return this.userMapper.mapToUserDto(this.userStorage.createUser(this.userMapper.mapToUser(newUser)));
    }

    public void deleteUser(Long id) {
        log.trace("Удаление пользователя c id={}", id);

        this.userStorage.deleteUser(id);
    }

    public UserDto updateUser(Long id, UpdateUserRequest user) {
        log.trace("Обновление пользователя c id={}, обновленные поля={}", id, user);
        User userToUpdate = this.userStorage.getUserById(id).orElseThrow(NotFoundException::new);

        if (this.userStorage.containsUserWithEmail(user.getEmail()) && !Objects.equals(userToUpdate.getEmail(), user.getEmail())) {
            throw new ConflictException();
        }

        if (user.getName() != null && !user.getName().isEmpty()) {
            userToUpdate.setName(user.getName());
        }

        if (user.getEmail() != null &&  !user.getEmail().isEmpty()) {
            userToUpdate.setEmail(user.getEmail());
        }

        return this.userMapper.mapToUserDto(this.userStorage.updateUser(userToUpdate));
    }
}
