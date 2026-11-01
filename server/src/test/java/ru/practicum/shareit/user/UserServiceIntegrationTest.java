package ru.practicum.shareit.user;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.user.dto.NewUserRequest;
import ru.practicum.shareit.user.dto.UpdateUserRequest;
import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;
import ru.practicum.shareit.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserServiceIntegrationTest {
    @Autowired
    private UserService service;
    @Autowired
    private UserRepository repository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void createAndGetReturnDatabaseGeneratedIdentityAndPersistedFields() {
        UserDto created = create("Anna", "anna@example.com");
        flushAndClear();

        assertThat(created.getId()).isPositive();
        assertThat(service.getUserById(created.getId())).usingRecursiveComparison().isEqualTo(created);
        User stored = repository.findById(created.getId()).orElseThrow();
        assertThat(stored.getName()).isEqualTo("Anna");
        assertThat(stored.getEmail()).isEqualTo("anna@example.com");
    }

    @Test
    void createRejectsDuplicateEmailWithoutAddingUser() {
        UserDto original = create("Anna", "anna@example.com");
        long count = repository.count();
        NewUserRequest request = new NewUserRequest();
        request.setName("Other");
        request.setEmail("anna@example.com");

        assertThatThrownBy(() -> service.createUser(request)).isInstanceOf(ConflictException.class);

        assertThat(repository.count()).isEqualTo(count);
        assertThat(repository.findById(original.getId()).orElseThrow().getName()).isEqualTo("Anna");
    }

    @Test
    void getUnknownUserThrowsNotFound() {
        assertThatThrownBy(() -> service.getUserById(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
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
    void updatePersistsOnlyNonEmptyFields(String name, String email, String expectedName, String expectedEmail) {
        UserDto created = create("Anna", "anna@example.com");

        UserDto updated = service.updateUser(created.getId(), update(name, email));
        flushAndClear();

        User stored = repository.findById(created.getId()).orElseThrow();
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getName()).isEqualTo(expectedName);
        assertThat(updated.getEmail()).isEqualTo(expectedEmail);
        assertThat(stored.getId()).isEqualTo(created.getId());
        assertThat(stored.getName()).isEqualTo(expectedName);
        assertThat(stored.getEmail()).isEqualTo(expectedEmail);
    }

    @Test
    void updateAcceptsUsersOwnEmail() {
        UserDto created = create("Anna", "anna@example.com");

        UserDto updated = service.updateUser(created.getId(), update("Olga", created.getEmail()));
        flushAndClear();

        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(service.getUserById(created.getId()).getName()).isEqualTo("Olga");
        assertThat(service.getUserById(created.getId()).getEmail()).isEqualTo(created.getEmail());
    }

    @Test
    void updateRejectsAnotherUsersEmailAndLeavesStoredRecordUnchanged() {
        UserDto original = create("Anna", "anna@example.com");
        UserDto other = create("Boris", "boris@example.com");
        flushAndClear();
        Long userId = original.getId();
        UpdateUserRequest request = update("Changed", other.getEmail());

        assertThatThrownBy(() -> service.updateUser(userId, request))
                .isInstanceOf(ConflictException.class);

        // The service marks this test transaction for rollback after a conflict; clear without flushing it.
        entityManager.clear();
        assertThat(service.getUserById(original.getId())).usingRecursiveComparison().isEqualTo(original);
        assertThat(service.getUserById(other.getId())).usingRecursiveComparison().isEqualTo(other);
    }

    @Test
    void updateUnknownUserThrowsNotFound() {
        UpdateUserRequest request = update("Olga", "olga@example.com");

        assertThatThrownBy(() -> service.updateUser(Long.MAX_VALUE, request))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesPersistedUserAndPreservesOtherUsers() {
        UserDto deleted = create("Anna", "anna@example.com");
        UserDto retained = create("Boris", "boris@example.com");

        service.deleteUser(deleted.getId());
        flushAndClear();

        assertThat(repository.existsById(deleted.getId())).isFalse();
        assertThat(service.getUserById(retained.getId())).usingRecursiveComparison().isEqualTo(retained);
        Long deletedId = deleted.getId();
        assertThatThrownBy(() -> service.getUserById(deletedId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteUnknownUserIsIdempotent() {
        UserDto retained = create("Anna", "anna@example.com");

        assertThatCode(() -> service.deleteUser(Long.MAX_VALUE)).doesNotThrowAnyException();
        flushAndClear();

        assertThat(service.getUserById(retained.getId())).usingRecursiveComparison().isEqualTo(retained);
    }

    private UserDto create(String name, String email) {
        NewUserRequest request = new NewUserRequest();
        request.setName(name);
        request.setEmail(email);
        return service.createUser(request);
    }

    private UpdateUserRequest update(String name, String email) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName(name);
        request.setEmail(email);
        return request;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
