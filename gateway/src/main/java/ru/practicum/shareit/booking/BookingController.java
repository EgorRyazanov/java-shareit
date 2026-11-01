package ru.practicum.shareit.booking;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.booking.dto.BookingState;

@RestController
@RequestMapping(path = "/bookings")
@Slf4j
public class BookingController {
    private final BookingClient bookingClient;

    BookingController(BookingClient bookingClient) {
        this.bookingClient = bookingClient;
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestHeader("X-Sharer-User-Id") Long userId, @Valid @RequestBody NewBookingRequest newBookingRequest) {
        log.info("Получен запрос на создание бронирования={} для пользователя с id={}", newBookingRequest, userId);
        return this.bookingClient.createBooking(userId, newBookingRequest);
    }

    @PatchMapping("/{bookingId}")
    public ResponseEntity<Object> update(@RequestHeader("X-Sharer-User-Id") Long userId, @RequestParam Boolean approved, @PathVariable Long bookingId) {
        log.info("Получен запрос на изменение бронирования={} для пользователя с id={}", bookingId, userId);
        return this.bookingClient.updateBooking(bookingId, userId, approved);
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<Object> find(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long bookingId) {
        log.info("Получен запрос на получение бронирования={} для пользователя с id={}", bookingId, userId);
        return this.bookingClient.findBooking(bookingId, userId);
    }

    @GetMapping("/owner")
    public ResponseEntity<Object> findByItemOwner(@RequestHeader("X-Sharer-User-Id") Long userId, @RequestParam(defaultValue = "ALL") BookingState state) {
        return this.bookingClient.findBookingOwner(userId, state);
    }

    @GetMapping
    public ResponseEntity<Object> findByBooker(@RequestHeader("X-Sharer-User-Id") Long userId, @RequestParam(defaultValue = "ALL") BookingState state) {
        return this.bookingClient.findByBooker(userId, state);
    }
}
