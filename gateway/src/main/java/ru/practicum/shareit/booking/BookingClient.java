package ru.practicum.shareit.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.practicum.shareit.booking.dto.BookingState;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.client.BaseClient;

import java.util.Map;

@Service
public class BookingClient extends BaseClient {
    public BookingClient(@Value("${shareit-server.url}") String serverUrl, RestTemplateBuilder builder) {
        super(serverUrl + "/bookings", builder);
    }

    public ResponseEntity<Object> createBooking(Long userId, NewBookingRequest request) {
        return post("", userId, request);
    }

    public ResponseEntity<Object> updateBooking(Long bookingId, Long userId, Boolean approved) {
        return patch("/" + bookingId + "?approved={approved}", userId, Map.of("approved", approved), null);
    }

    public ResponseEntity<Object> findBooking(Long bookingId, Long userId) {
        return get("/" + bookingId, userId);
    }

    public ResponseEntity<Object> findBookingOwner(Long userId, BookingState state) {
        return get("/owner?state={state}", userId, Map.of("state", state.name()));
    }

    public ResponseEntity<Object> findByBooker(Long userId, BookingState state) {
        return get("?state={state}", userId, Map.of("state", state.name()));
    }
}
