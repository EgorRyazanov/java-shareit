package ru.practicum.shareit.request;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.request.dto.ItemAnswerDto;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.NewRequest;
import ru.practicum.shareit.request.service.ItemRequestService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemRequestController.class)
class ItemRequestControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ItemRequestService itemRequestService;

    @Test
    void forwardsUserAndDescriptionAndReturnsCreatedRequest() throws Exception {
        ItemRequestDto dto = new ItemRequestDto();
        dto.setId(42L);
        dto.setDescription("Нужна дрель");
        dto.setCreated(LocalDateTime.of(2026, 11, 1, 12, 0));
        when(itemRequestService.createRequest(eq(7L), argThat(request -> "Нужна дрель".equals(request.getDescription()))))
            .thenReturn(dto);

        mockMvc.perform(post("/requests")
                .header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Нужна дрель\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.description").value("Нужна дрель"))
            .andExpect(jsonPath("$.created").value("2026-11-01T12:00:00"));
    }


    @Test
    void returnsNotFoundWhenUserDoesNotExist() throws Exception {
        when(itemRequestService.createRequest(eq(7L), any(NewRequest.class))).thenThrow(new NotFoundException());

        mockMvc.perform(post("/requests")
                .header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Нужна дрель\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returnsOwnRequestsFromService() throws Exception {
        when(itemRequestService.getRequests(7L)).thenReturn(List.of(requestWithAnswer()));

        mockMvc.perform(get("/requests").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(42))
            .andExpect(jsonPath("$[0].items[0].id").value(11))
            .andExpect(jsonPath("$[0].items[0].name").value("Дрель"))
            .andExpect(jsonPath("$[0].items[0].ownerId").value(3));
    }

    @Test
    void returnsOtherRequestsFromService() throws Exception {
        when(itemRequestService.getOtherRequests(7L)).thenReturn(List.of(requestWithAnswer()));

        mockMvc.perform(get("/requests/all").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(42));
    }

    @Test
    void returnsSingleRequestFromService() throws Exception {
        when(itemRequestService.getRequestById(42L, 7L)).thenReturn(requestWithAnswer());

        mockMvc.perform(get("/requests/42").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.description").value("Нужна дрель"))
            .andExpect(jsonPath("$.created").value("2026-11-01T12:00:00"))
            .andExpect(jsonPath("$.items[0].id").value(11))
            .andExpect(jsonPath("$.items[0].name").value("Дрель"))
            .andExpect(jsonPath("$.items[0].ownerId").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/requests", "/requests/all", "/requests/42"})
    void rejectsGetWithoutUserHeaderBeforeCallingService(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(itemRequestService);
    }

    @Test
    void returnsNotFoundForMissingRequest() throws Exception {
        when(itemRequestService.getRequestById(42L, 7L)).thenThrow(new NotFoundException());

        mockMvc.perform(get("/requests/42").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isNotFound());
    }

    private ItemRequestDto requestWithAnswer() {
        ItemAnswerDto answer = new ItemAnswerDto();
        answer.setId(11L);
        answer.setName("Дрель");
        answer.setOwnerId(3L);

        ItemRequestDto dto = new ItemRequestDto();
        dto.setId(42L);
        dto.setDescription("Нужна дрель");
        dto.setCreated(LocalDateTime.of(2026, 11, 1, 12, 0));
        dto.setItems(List.of(answer));
        return dto;
    }
}
