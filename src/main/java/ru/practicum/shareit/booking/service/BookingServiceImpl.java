package ru.practicum.shareit.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.booking.mapper.BookingMapper;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.model.BookingState;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {
    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public BookingDto createBooking(Long userId, NewBookingRequest newBookingRequest) {
        User user = this.userRepository.findById(userId).orElseThrow(NotFoundException::new);
        Item item = this.itemRepository.findById(newBookingRequest.getItemId()).orElseThrow(NotFoundException::new);
        Booking booking = bookingMapper.mapToBooking(newBookingRequest);

        if (!item.getAvailable()) {
            throw new BadRequestException("Item is not available");
        }

        if (item.getOwner().getId().equals(user.getId())) {
            throw new BadRequestException("You can't book your own item");
        }

        if (booking.getStart().isAfter(booking.getEnd()) || booking.getStart().isEqual(booking.getEnd())) {
            throw new BadRequestException("End date must be after start date");
        }

        if (!booking.getStart().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Start date must be in the future");
        }

        booking.setItem(item);
        booking.setBooker(user);
        booking.setStatus(BookingStatus.WAITING);

        return this.bookingMapper.mapToBookingDto(this.bookingRepository.save(booking));
    }

    @Override
    @Transactional
    public BookingDto updateBooking(Long bookingId, Long userId, Boolean approved) {
        Booking booking = this.bookingRepository.findById(bookingId).orElseThrow(NotFoundException::new);

        if (!booking.getItem().getOwner().getId().equals(userId)) {
            throw new ForbiddenException("You can't update booking");
        }

        if (!booking.getStatus().equals(BookingStatus.WAITING)) {
            throw new BadRequestException("Booking status must be WAITING");
        }

        if (approved) {
            booking.setStatus(BookingStatus.APPROVED);
        } else {
            booking.setStatus(BookingStatus.REJECTED);
        }

        return this.bookingMapper.mapToBookingDto(this.bookingRepository.save(booking));
    }

    @Override
    public BookingDto findBooking(Long bookingId, Long userId) {
        User user = this.userRepository.findById(userId).orElseThrow(NotFoundException::new);
        Booking booking = this.bookingRepository.findById(bookingId).orElseThrow(NotFoundException::new);

        if (booking.getItem().getOwner().getId().equals(user.getId()) || booking.getBooker().getId().equals(user.getId())) {
            return this.bookingMapper.mapToBookingDto(booking);
        }

        throw new NotFoundException();
    }

    @Override
    public List<BookingDto> findBookingOwner(Long userId, BookingState state) {
        this.userRepository.findById(userId).orElseThrow(NotFoundException::new);

        LocalDateTime now = LocalDateTime.now();

        List<Booking> bookings = switch (state) {
            case ALL ->
                this.bookingRepository.findAllByItem_Owner_IdOrderByStartDesc(userId);
            case PAST ->
                this.bookingRepository.findAllByItem_Owner_IdAndEndBeforeOrderByStartDesc(userId, now);
            case CURRENT ->
                this.bookingRepository.findAllByItem_Owner_IdAndStartBeforeAndEndAfterOrderByStartDesc(userId, now, now);
            case FUTURE ->
                this.bookingRepository.findAllByItem_Owner_IdAndStartAfterOrderByStartDesc(userId, now);
            case WAITING ->
                this.bookingRepository.findAllByItem_Owner_IdAndStatusOrderByStartDesc(userId, BookingStatus.WAITING);
            case REJECTED ->
                this.bookingRepository.findAllByItem_Owner_IdAndStatusOrderByStartDesc(userId, BookingStatus.REJECTED);
        };


        return bookings.stream().map(this.bookingMapper::mapToBookingDto).toList();
    }

    @Override
    public List<BookingDto> findByBooker(Long userId, BookingState state) {
        this.userRepository.findById(userId).orElseThrow(NotFoundException::new);

        LocalDateTime now = LocalDateTime.now();

        List<Booking> bookings = switch (state) {
            case ALL ->
                this.bookingRepository.findAllByBooker_IdOrderByStartDesc(userId);
            case PAST ->
                this.bookingRepository.findAllByBooker_IdAndEndBeforeOrderByStartDesc(userId, now);
            case CURRENT ->
                this.bookingRepository.findAllByBooker_IdAndStartBeforeAndEndAfterOrderByStartDesc(userId, now, now);
            case FUTURE ->
                this.bookingRepository.findAllByBooker_IdAndStartAfterOrderByStartDesc(userId, now);
            case WAITING ->
                this.bookingRepository.findAllByBooker_IdAndStatusOrderByStartDesc(userId, BookingStatus.WAITING);
            case REJECTED ->
                this.bookingRepository.findAllByBooker_IdAndStatusOrderByStartDesc(userId, BookingStatus.REJECTED);
        };


        return bookings.stream().map(this.bookingMapper::mapToBookingDto).toList();
    }
}
