package ru.practicum.shareit.booking;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.booking.model.BookingState;
import ru.practicum.shareit.booking.service.BookingService;

import java.util.List;

@RestController
@RequestMapping(path = "/bookings")
@Slf4j
public class BookingController {
    private final BookingService bookingService;

    BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public BookingDto create(@RequestHeader("X-Sharer-User-Id") Long userId, @Valid @RequestBody NewBookingRequest newBookingRequest) {
        log.info("Получен запрос на создание бронирования={} для пользователя с id={}", newBookingRequest, userId);
        return this.bookingService.createBooking(userId, newBookingRequest);
    }

    @PatchMapping("/{bookingId}")
    public BookingDto update(@RequestHeader("X-Sharer-User-Id") Long userId, @RequestParam Boolean approved, @PathVariable Long bookingId) {
        log.info("Получен запрос на изменение бронирования={} для пользователя с id={}", bookingId, userId);
        return this.bookingService.updateBooking(bookingId, userId, approved);
    }

    @GetMapping("/{bookingId}")
    public BookingDto find(@RequestHeader("X-Sharer-User-Id") Long userId, @PathVariable Long bookingId) {
        log.info("Получен запрос на получение бронирования={} для пользователя с id={}", bookingId, userId);
        return this.bookingService.findBooking(bookingId, userId);
    }

    @GetMapping("/owner")
    public List<BookingDto> findByItemOwner(@RequestHeader("X-Sharer-User-Id") Long userId, @RequestParam(defaultValue = "ALL") BookingState state) {
        return this.bookingService.findBookingOwner(userId, state);
    }

    @GetMapping
    public List<BookingDto> findByBooker(@RequestHeader("X-Sharer-User-Id") Long userId, @RequestParam(defaultValue = "ALL") BookingState state) {
        return this.bookingService.findByBooker(userId, state);
    }
}
