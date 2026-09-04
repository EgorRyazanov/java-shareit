package ru.practicum.shareit.user.storage;

import ru.practicum.shareit.user.model.User;

import java.util.Optional;

public interface UserStorage {
    Optional<User> getUserById(Long id);

    User createUser(User user);

    void deleteUser(Long id);

    User updateUser(User updatedUser);

    Boolean containsUserWithEmail(String email);
}
