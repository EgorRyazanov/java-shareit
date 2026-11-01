package ru.practicum.shareit.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.shareit.exception.ConflictException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.user.dto.NewUserRequest;
import ru.practicum.shareit.user.dto.UpdateUserRequest;
import ru.practicum.shareit.user.dto.UserDto;
import ru.practicum.shareit.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockBean
    private UserService service;

    @Test
    void createBindsBodyAndReturnsCreatedUser() throws Exception {
        when(service.createUser(any())).thenReturn(user());

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anna\",\"email\":\"anna@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.name").value("Anna"))
                .andExpect(jsonPath("$.email").value("anna@example.com"));

        ArgumentCaptor<NewUserRequest> request = ArgumentCaptor.forClass(NewUserRequest.class);
        verify(service).createUser(request.capture());
        assertThat(request.getValue().getName()).isEqualTo("Anna");
        assertThat(request.getValue().getEmail()).isEqualTo("anna@example.com");
    }

    @Test
    void getReturnsRequestedUser() throws Exception {
        when(service.getUserById(17L)).thenReturn(user());

        mvc.perform(get("/users/17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.name").value("Anna"))
                .andExpect(jsonPath("$.email").value("anna@example.com"));

        verify(service).getUserById(17L);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "{\"name\":\"Olga\"}|Olga|NULL",
        "{\"email\":\"olga@example.com\"}|NULL|olga@example.com",
        "{\"name\":\"Olga\",\"email\":\"olga@example.com\"}|Olga|olga@example.com",
        "{}|NULL|NULL",
        "{\"name\":\"\",\"email\":\"\"}|''|''"
    }, delimiter = '|', nullValues = "NULL")
    void patchBindsPartialRequestAndReturnsServiceResult(String body, String name, String email) throws Exception {
        when(service.updateUser(eq(17L), any())).thenReturn(user());

        mvc.perform(patch("/users/17").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.name").value("Anna"))
                .andExpect(jsonPath("$.email").value("anna@example.com"));

        ArgumentCaptor<UpdateUserRequest> request = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(service).updateUser(eq(17L), request.capture());
        assertThat(request.getValue().getName()).isEqualTo(name);
        assertThat(request.getValue().getEmail()).isEqualTo(email);
    }

    @Test
    void deleteDelegatesAndReturnsEmptyResponse() throws Exception {
        mvc.perform(delete("/users/17"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(service).deleteUser(17L);
    }

    @Test
    void duplicateEmailOnCreateReturnsConflict() throws Exception {
        when(service.createUser(any())).thenThrow(new ConflictException());

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anna\",\"email\":\"anna@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void duplicateEmailOnUpdateReturnsConflict() throws Exception {
        when(service.updateUser(eq(17L), any())).thenThrow(new ConflictException());

        mvc.perform(patch("/users/17").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void getUnknownUserReturnsNotFound() throws Exception {
        when(service.getUserById(99L)).thenThrow(new NotFoundException());

        mvc.perform(get("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void patchUnknownUserReturnsNotFound() throws Exception {
        when(service.updateUser(eq(99L), any())).thenThrow(new NotFoundException());

        mvc.perform(patch("/users/99").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void unexpectedFailureReturnsGenericErrorWithoutInternalDetails() throws Exception {
        when(service.getUserById(17L)).thenThrow(new IllegalStateException("database password: secret"));

        mvc.perform(get("/users/17"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal server error"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void malformedJsonReturnsBadRequestBeforeCallingService() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request"));

        verifyNoInteractions(service);
    }

    @Test
    void nonNumericIdReturnsBadRequestBeforeCallingService() throws Exception {
        mvc.perform(get("/users/not-a-number"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private UserDto user() {
        UserDto user = new UserDto();
        user.setId(17L);
        user.setName("Anna");
        user.setEmail("anna@example.com");
        return user;
    }
}
