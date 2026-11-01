package ru.practicum.shareit.booking.service;

import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.booking.model.BookingState;

import java.util.List;

public interface BookingService {
    BookingDto createBooking(Long userId, NewBookingRequest newBookingRequest);

    BookingDto updateBooking(Long bookingId, Long userId, Boolean approved);

    BookingDto findBooking(Long bookingId, Long userId);

    List<BookingDto> findBookingOwner(Long userId, BookingState state);

    List<BookingDto> findByBooker(Long userId, BookingState state);
}
