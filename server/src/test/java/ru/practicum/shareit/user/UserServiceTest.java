package ru.practicum.shareit.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.user.dto.NewUserRequest;
import ru.practicum.shareit.user.dto.UpdateUserRequest;
import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.user.mapper.UserMapper;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;
import ru.practicum.shareit.user.service.UserService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository repository;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, new UserMapper());
    }

    @Test
    void createReturnsSavedUserWithGeneratedId() {
        NewUserRequest request = new NewUserRequest();
        request.setName("Anna");
        request.setEmail("anna@example.com");
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertThat(user.getId()).isNull();
            assertThat(user.getName()).isEqualTo("Anna");
            assertThat(user.getEmail()).isEqualTo("anna@example.com");
            user.setId(17L);
            return user;
        });

        UserDto result = service.createUser(request);

        assertUser(result, 17L, "Anna", "anna@example.com");
        verify(repository).existsByEmail("anna@example.com");
    }

    @Test
    void createRejectsDuplicateEmailWithoutSaving() {
        NewUserRequest request = new NewUserRequest();
        request.setName("Another Anna");
        request.setEmail("anna@example.com");
        when(repository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(request)).isInstanceOf(ConflictException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void getReturnsStoredUser() {
        when(repository.findById(17L)).thenReturn(Optional.of(user()));

        assertUser(service.getUserById(17L), 17L, "Anna", "anna@example.com");
    }

    @Test
    void getUnknownUserThrowsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserById(99L)).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "Olga,NULL,Olga,anna@example.com",
        "NULL,olga@example.com,Anna,olga@example.com",
        "Olga,olga@example.com,Olga,olga@example.com",
        "NULL,NULL,Anna,anna@example.com",
        "'',NULL,Anna,anna@example.com",
        "NULL,'',Anna,anna@example.com",
        "'','',Anna,anna@example.com"
    }, nullValues = "NULL")
    void updateOnlyReplacesNonEmptyFields(String name, String email, String expectedName, String expectedEmail) {
        User stored = user();
        UpdateUserRequest request = update(name, email);
        when(repository.findById(17L)).thenReturn(Optional.of(stored));
        when(repository.save(stored)).thenReturn(stored);

        UserDto result = service.updateUser(17L, request);

        assertUser(result, 17L, expectedName, expectedEmail);
        assertThat(stored.getId()).isEqualTo(17L);
        assertThat(stored.getName()).isEqualTo(expectedName);
        assertThat(stored.getEmail()).isEqualTo(expectedEmail);
        verify(repository).save(stored);
    }

    @Test
    void updateAcceptsUsersOwnEmail() {
        User stored = user();
        when(repository.findById(17L)).thenReturn(Optional.of(stored));
        when(repository.existsByEmail("anna@example.com")).thenReturn(true);
        when(repository.save(stored)).thenReturn(stored);

        assertUser(service.updateUser(17L, update("Olga", "anna@example.com")),
                17L, "Olga", "anna@example.com");
    }

    @Test
    void updateRejectsAnotherUsersEmailBeforeChangingAnyField() {
        User stored = user();
        when(repository.findById(17L)).thenReturn(Optional.of(stored));
        when(repository.existsByEmail("other@example.com")).thenReturn(true);
        UpdateUserRequest request = update("Changed", "other@example.com");

        assertThatThrownBy(() -> service.updateUser(17L, request))
                .isInstanceOf(ConflictException.class);

        assertThat(stored.getId()).isEqualTo(17L);
        assertThat(stored.getName()).isEqualTo("Anna");
        assertThat(stored.getEmail()).isEqualTo("anna@example.com");
        verify(repository, never()).save(any());
    }

    @Test
    void updateUnknownUserThrowsNotFoundWithoutSaving() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        UpdateUserRequest request = update("Olga", "olga@example.com");

        assertThatThrownBy(() -> service.updateUser(99L, request))
                .isInstanceOf(NotFoundException.class);

        verify(repository, never()).existsByEmail(any());
        verify(repository, never()).save(any());
    }

    @Test
    void deleteDelegatesToRepository() {
        service.deleteUser(17L);

        verify(repository).deleteById(17L);
    }

    private User user() {
        User user = new User();
        user.setId(17L);
        user.setName("Anna");
        user.setEmail("anna@example.com");
        return user;
    }

    private UpdateUserRequest update(String name, String email) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName(name);
        request.setEmail(email);
        return request;
    }

    private void assertUser(UserDto user, Long id, String name, String email) {
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getEmail()).isEqualTo(email);
    }
}
