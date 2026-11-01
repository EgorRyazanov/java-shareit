package ru.practicum.shareit.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.booking.model.BookingState;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.booking.service.BookingService;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.user.dto.UserDto;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
class BookingControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private BookingService service;

    @Test
    void forwardsCreationRequestAndSerializesDatesItemAndBooker() throws Exception {
        NewBookingRequest request = request();
        when(service.createBooking(2L, request)).thenReturn(booking());

        mockMvc.perform(post("/bookings")
                .header("X-Sharer-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.status").value("WAITING"))
            .andExpect(jsonPath("$.start").value("2030-01-10T12:00:00"))
            .andExpect(jsonPath("$.end").value("2030-01-11T12:00:00"))
            .andExpect(jsonPath("$.item.id").value(10))
            .andExpect(jsonPath("$.item.name").value("Drill"))
            .andExpect(jsonPath("$.booker.id").value(2))
            .andExpect(jsonPath("$.booker.email").value("booker@example.com"));
        verify(service).createBooking(2L, request);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forwardsOwnerDecisionAndReturnsResult(boolean approved) throws Exception {
        BookingDto result = booking();
        result.setStatus(approved ? BookingStatus.APPROVED : BookingStatus.REJECTED);
        when(service.updateBooking(20L, 1L, approved)).thenReturn(result);

        mockMvc.perform(patch("/bookings/20").header("X-Sharer-User-Id", 1L)
                .param("approved", String.valueOf(approved)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.status").value(result.getStatus().name()));
        verify(service).updateBooking(20L, 1L, approved);
    }

    @Test
    void forwardsReadToService() throws Exception {
        when(service.findBooking(20L, 2L)).thenReturn(booking());

        mockMvc.perform(get("/bookings/20").header("X-Sharer-User-Id", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(20))
            .andExpect(jsonPath("$.item.id").value(10))
            .andExpect(jsonPath("$.booker.id").value(2));
        verify(service).findBooking(20L, 2L);
    }

    @Test
    void defaultsBothListsToAll() throws Exception {
        when(service.findByBooker(2L, BookingState.ALL)).thenReturn(List.of(booking()));
        when(service.findBookingOwner(1L, BookingState.ALL)).thenReturn(List.of(booking()));

        mockMvc.perform(get("/bookings").header("X-Sharer-User-Id", 2L))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(20));
        mockMvc.perform(get("/bookings/owner").header("X-Sharer-User-Id", 1L))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(20));
        verify(service).findByBooker(2L, BookingState.ALL);
        verify(service).findBookingOwner(1L, BookingState.ALL);
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void bindsEveryStateForBookerAndOwnerLists(BookingState state) throws Exception {
        when(service.findByBooker(2L, state)).thenReturn(List.of(booking()));
        when(service.findBookingOwner(1L, state)).thenReturn(List.of(booking()));

        mockMvc.perform(get("/bookings").header("X-Sharer-User-Id", 2L).param("state", state.name()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(20));
        mockMvc.perform(get("/bookings/owner").header("X-Sharer-User-Id", 1L).param("state", state.name()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(20));
        verify(service).findByBooker(2L, state);
        verify(service).findBookingOwner(1L, state);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings", "/bookings/owner", "/bookings/20"})
    void rejectsMissingReadHeader(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingWriteHeaders() throws Exception {
        mockMvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/bookings/20").param("approved", "true"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings", "/bookings/owner"})
    void rejectsUnknownStateBeforeCallingService(String path) throws Exception {
        mockMvc.perform(get(path).header("X-Sharer-User-Id", 2L).param("state", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Unknown state: UNKNOWN"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedJsonAndInvalidBindingBeforeCallingService() throws Exception {
        mockMvc.perform(post("/bookings").header("X-Sharer-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"itemId\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request"));
        mockMvc.perform(get("/bookings/invalid").header("X-Sharer-User-Id", 2L))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/bookings/20").header("X-Sharer-User-Id", "invalid"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/bookings/20").header("X-Sharer-User-Id", 1L).param("approved", "invalid"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/bookings/20").header("X-Sharer-User-Id", 1L))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void mapsMissingOrHiddenBookingTo404() throws Exception {
        when(service.findBooking(20L, 2L)).thenThrow(new NotFoundException());
        mockMvc.perform(get("/bookings/20").header("X-Sharer-User-Id", 2L))
            .andExpect(status().isNotFound());
    }

    @Test
    void mapsNonOwnerDecisionTo403() throws Exception {
        when(service.updateBooking(20L, 2L, true)).thenThrow(new ForbiddenException("You can't update booking"));
        mockMvc.perform(patch("/bookings/20").header("X-Sharer-User-Id", 2L).param("approved", "true"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("You can't update booking"));
    }

    @Test
    void mapsRepeatedDecisionTo400() throws Exception {
        when(service.updateBooking(20L, 1L, false))
            .thenThrow(new BadRequestException("Booking status must be WAITING"));
        mockMvc.perform(patch("/bookings/20").header("X-Sharer-User-Id", 1L).param("approved", "false"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Booking status must be WAITING"));
    }

    @Test
    void mapsUnavailableItemTo400() throws Exception {
        when(service.createBooking(eq(2L), any(NewBookingRequest.class)))
            .thenThrow(new BadRequestException("Item is not available"));
        mockMvc.perform(post("/bookings").header("X-Sharer-User-Id", 2L)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Item is not available"));
    }

    private NewBookingRequest request() {
        NewBookingRequest request = new NewBookingRequest();
        request.setItemId(10L);
        request.setStart(LocalDateTime.of(2030, 1, 10, 12, 0));
        request.setEnd(request.getStart().plusDays(1));
        return request;
    }

    private BookingDto booking() {
        ItemDto item = new ItemDto();
        item.setId(10L);
        item.setName("Drill");
        UserDto booker = new UserDto();
        booker.setId(2L);
        booker.setEmail("booker@example.com");
        BookingDto booking = new BookingDto();
        booking.setId(20L);
        booking.setItem(item);
        booking.setBooker(booker);
        booking.setStatus(BookingStatus.WAITING);
        booking.setStart(request().getStart());
        booking.setEnd(request().getEnd());
        return booking;
    }
}
