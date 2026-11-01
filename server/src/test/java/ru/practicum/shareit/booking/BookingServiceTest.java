package ru.practicum.shareit.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.booking.mapper.BookingMapper;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.model.BookingState;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.booking.service.BookingServiceImpl;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.mapper.ItemMapper;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.user.mapper.UserMapper;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemRepository itemRepository;

    private BookingServiceImpl service;
    private User owner;
    private User booker;
    private Item item;
    private Booking booking;
    private NewBookingRequest request;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(bookingRepository,
            new BookingMapper(new UserMapper(), new ItemMapper()), userRepository, itemRepository);
        owner = user(1L);
        booker = user(2L);
        item = new Item();
        item.setId(10L);
        item.setName("Drill");
        item.setDescription("Cordless drill");
        item.setOwner(owner);
        item.setAvailable(true);
        request = new NewBookingRequest();
        request.setItemId(item.getId());
        request.setStart(LocalDateTime.of(2030, 1, 10, 12, 0));
        request.setEnd(request.getStart().plusDays(1));
        booking = new Booking();
        booking.setId(20L);
        booking.setItem(item);
        booking.setBooker(booker);
        booking.setStart(request.getStart());
        booking.setEnd(request.getEnd());
        booking.setStatus(BookingStatus.WAITING);
    }

    @Test
    void createsWaitingBookingAndMapsSavedItemBookerAndDates() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(booker));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            assertThat(saved.getItem()).isSameAs(item);
            assertThat(saved.getBooker()).isSameAs(booker);
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.WAITING);
            saved.setId(20L);
            return saved;
        });

        assertBooking(service.createBooking(2L, request), BookingStatus.WAITING);
    }

    @Test
    void rejectsUnavailableItemWithoutSaving() {
        item.setAvailable(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(booker));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createBooking(2L, request))
            .isInstanceOf(BadRequestException.class).hasMessage("Item is not available");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsBookingOwnItemWithoutSaving() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.createBooking(1L, request))
            .isInstanceOf(BadRequestException.class).hasMessage("You can't book your own item");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsMissingBookerBeforeLookingUpItem() {
        assertThatThrownBy(() -> service.createBooking(2L, request)).isInstanceOf(NotFoundException.class);
        verify(itemRepository, never()).findById(any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsMissingItemWithoutSaving() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(booker));
        assertThatThrownBy(() -> service.createBooking(2L, request)).isInstanceOf(NotFoundException.class);
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ownerCanApproveOrRejectWaitingBooking(boolean approved) {
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(booking)).thenReturn(booking);
        BookingStatus expected = approved ? BookingStatus.APPROVED : BookingStatus.REJECTED;

        assertBooking(service.updateBooking(20L, 1L, approved), expected);
        assertThat(booking.getStatus()).isEqualTo(expected);
        verify(bookingRepository).save(booking);
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 3L})
    void bookerAndThirdPartyCannotDecideBooking(Long userId) {
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.updateBooking(20L, userId, true))
            .isInstanceOf(ForbiddenException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.WAITING);
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"APPROVED", "REJECTED", "CANCELED"})
    void rejectsDecisionOnBookingThatIsNoLongerWaiting(BookingStatus status) {
        booking.setStatus(status);
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service.updateBooking(20L, 1L, false))
            .isInstanceOf(BadRequestException.class).hasMessage("Booking status must be WAITING");
        assertThat(booking.getStatus()).isEqualTo(status);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsDecisionForMissingBooking() {
        assertThatThrownBy(() -> service.updateBooking(20L, 1L, true)).isInstanceOf(NotFoundException.class);
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2L})
    void ownerAndBookerCanReadBooking(Long userId) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId)));
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));

        assertBooking(service.findBooking(20L, userId), BookingStatus.WAITING);
    }

    @Test
    void hidesBookingFromThirdParty() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(user(3L)));
        when(bookingRepository.findById(20L)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.findBooking(20L, 3L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsReadingAsMissingUser() {
        assertThatThrownBy(() -> service.findBooking(20L, 3L)).isInstanceOf(NotFoundException.class);
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    void rejectsReadingMissingBooking() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(booker));
        assertThatThrownBy(() -> service.findBooking(20L, 2L)).isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void rejectsListingForMissingUserBeforeQueryingBookings(BookingState state) {
        assertThatThrownBy(() -> service.findByBooker(99L, state)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.findBookingOwner(99L, state)).isInstanceOf(NotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(bookingRepository);
    }

    private void assertBooking(BookingDto dto, BookingStatus expectedStatus) {
        assertThat(dto.getId()).isEqualTo(20L);
        assertThat(dto.getStart()).isEqualTo(request.getStart());
        assertThat(dto.getEnd()).isEqualTo(request.getEnd());
        assertThat(dto.getStatus()).isEqualTo(expectedStatus);
        assertThat(dto.getItem().getId()).isEqualTo(10L);
        assertThat(dto.getItem().getName()).isEqualTo("Drill");
        assertThat(dto.getItem().getDescription()).isEqualTo("Cordless drill");
        assertThat(dto.getItem().getAvailable()).isTrue();
        assertThat(dto.getBooker().getId()).isEqualTo(2L);
        assertThat(dto.getBooker().getName()).isEqualTo("User 2");
        assertThat(dto.getBooker().getEmail()).isEqualTo("user2@example.com");
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setEmail("user" + id + "@example.com");
        return user;
    }
}
