package ru.practicum.shareit.user.storage;

import org.springframework.stereotype.Component;
import ru.practicum.shareit.user.model.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class UserStorageImpl implements UserStorage {
    private final Map<Long, User> users = new HashMap<>();

    @Override
    public Optional<User> getUserById(Long id) {
        User user = users.get(id);
        if (user == null) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

    @Override
    public User createUser(User user) {
        user.setId(getNextId());
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public void deleteUser(Long id) {
        users.remove(id);
    }

    @Override
    public User updateUser(User updatedUser) {
        users.put(updatedUser.getId(), updatedUser);
        return updatedUser;
    }

    @Override
    public Boolean containsUserWithEmail(String email) {
        return users.values().stream()
            .anyMatch(user -> user.getEmail().equals(email));
    }

    private Long getNextId() {
        long currentMaxId = users.keySet()
            .stream()
            .max(Long::compare)
            .orElse(0L);
        return ++currentMaxId;
    }
}
