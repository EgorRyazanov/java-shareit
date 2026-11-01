package ru.practicum.shareit.booking;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.dto.BookingDto;
import ru.practicum.shareit.booking.dto.NewBookingRequest;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.model.BookingState;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.booking.service.BookingService;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.ForbiddenException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BookingServiceIntegrationTest {
    @Autowired
    private BookingService service;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private EntityManager entityManager;

    private User owner;
    private User booker;
    private User other;
    private Item item;
    private final LocalDateTime now = LocalDateTime.now().withNano(0);

    @BeforeEach
    void setUp() {
        owner = saveUser("owner");
        booker = saveUser("booker");
        other = saveUser("other");
        item = saveItem(owner, "Drill");
    }

    @Test
    void createsWaitingBookingWithPersistedRelationsAndDates() {
        NewBookingRequest request = request(item.getId());

        BookingDto result = service.createBooking(booker.getId(), request);
        entityManager.flush();
        entityManager.clear();

        Booking saved = bookingRepository.findById(result.getId()).orElseThrow();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.WAITING);
        assertThat(saved.getStart()).isEqualTo(request.getStart());
        assertThat(saved.getEnd()).isEqualTo(request.getEnd());
        assertThat(saved.getItem().getId()).isEqualTo(item.getId());
        assertThat(saved.getBooker().getId()).isEqualTo(booker.getId());
        assertThat(result.getItem().getName()).isEqualTo("Drill");
        assertThat(result.getBooker().getEmail()).isEqualTo("booker@example.com");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void ownerDecisionPersistsAndCannotBeRepeated(boolean approved) {
        BookingDto created = service.createBooking(booker.getId(), request(item.getId()));
        Long bookingId = created.getId();
        Long ownerId = owner.getId();

        BookingDto result = service.updateBooking(bookingId, ownerId, approved);
        entityManager.flush();
        entityManager.clear();

        BookingStatus expected = approved ? BookingStatus.APPROVED : BookingStatus.REJECTED;
        assertThat(result.getStatus()).isEqualTo(expected);
        assertThat(bookingRepository.findById(created.getId()).orElseThrow().getStatus()).isEqualTo(expected);
        assertThatThrownBy(() -> service.updateBooking(bookingId, ownerId, !approved))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnavailableAndOwnItemsWithoutCreatingBookings() {
        Long bookerId = booker.getId();
        Long ownerId = owner.getId();
        NewBookingRequest bookingRequest = request(item.getId());
        item.setAvailable(false);
        itemRepository.saveAndFlush(item);
        assertThatThrownBy(() -> service.createBooking(bookerId, bookingRequest))
            .isInstanceOf(BadRequestException.class);

        item.setAvailable(true);
        itemRepository.saveAndFlush(item);
        assertThatThrownBy(() -> service.createBooking(ownerId, bookingRequest))
            .isInstanceOf(BadRequestException.class);
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void rejectsUnknownBookerOrItemWithoutCreatingBookings() {
        Long bookerId = booker.getId();
        NewBookingRequest existingItemRequest = request(item.getId());
        NewBookingRequest missingItemRequest = request(-1L);
        assertThatThrownBy(() -> service.createBooking(-1L, existingItemRequest))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.createBooking(bookerId, missingItemRequest))
            .isInstanceOf(NotFoundException.class);
        assertThat(bookingRepository.count()).isZero();
    }

    @Test
    void ownerAndBookerCanReadButThirdPartyCannotReadOrDecide() {
        BookingDto created = service.createBooking(booker.getId(), request(item.getId()));
        Long bookingId = created.getId();
        Long bookerId = booker.getId();
        Long otherId = other.getId();

        assertThat(service.findBooking(created.getId(), owner.getId())).isEqualTo(created);
        assertThat(service.findBooking(created.getId(), booker.getId())).isEqualTo(created);
        assertThatThrownBy(() -> service.findBooking(bookingId, otherId))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateBooking(bookingId, bookerId, true))
            .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.updateBooking(bookingId, otherId, false))
            .isInstanceOf(ForbiddenException.class);
        assertThat(bookingRepository.findById(created.getId()).orElseThrow().getStatus())
            .isEqualTo(BookingStatus.WAITING);
    }

    @Test
    void rejectsMissingBookingsAndReaders() {
        Long bookerId = booker.getId();
        Long ownerId = owner.getId();
        assertThatThrownBy(() -> service.findBooking(-1L, bookerId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.findBooking(-1L, -1L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateBooking(-1L, ownerId, true))
            .isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void filtersBookerAndOwnerBookingsAndSortsByDescendingStart(BookingState state) {
        Booking past = saveBooking(item, booker, BookingStatus.APPROVED, -8, -7);
        Booking canceled = saveBooking(item, booker, BookingStatus.CANCELED, -6, -5);
        Booking current = saveBooking(item, booker, BookingStatus.APPROVED, -1, 1);
        Booking waiting = saveBooking(item, booker, BookingStatus.WAITING, 2, 3);
        Booking rejected = saveBooking(item, booker, BookingStatus.REJECTED, 4, 5);
        Booking laterWaiting = saveBooking(item, booker, BookingStatus.WAITING, 6, 7);
        Item secondOwnedItem = saveItem(owner, "Saw");
        Booking otherBooker = saveBooking(secondOwnedItem, other, BookingStatus.WAITING, 8, 9);
        Item foreignItem = saveItem(other, "Foreign item");
        Booking foreignItemBooking = saveBooking(foreignItem, booker, BookingStatus.WAITING, 10, 11);
        saveBooking(foreignItem, owner, BookingStatus.WAITING, 12, 13);
        entityManager.flush();
        entityManager.clear();

        List<Long> expectedBooker = switch (state) {
            case ALL -> ids(foreignItemBooking, laterWaiting, rejected, waiting, current, canceled, past);
            case PAST -> ids(canceled, past);
            case CURRENT -> ids(current);
            case FUTURE -> ids(foreignItemBooking, laterWaiting, rejected, waiting);
            case WAITING -> ids(foreignItemBooking, laterWaiting, waiting);
            case REJECTED -> ids(rejected);
        };
        List<Long> expectedOwner = switch (state) {
            case ALL -> ids(otherBooker, laterWaiting, rejected, waiting, current, canceled, past);
            case PAST -> ids(canceled, past);
            case CURRENT -> ids(current);
            case FUTURE -> ids(otherBooker, laterWaiting, rejected, waiting);
            case WAITING -> ids(otherBooker, laterWaiting, waiting);
            case REJECTED -> ids(rejected);
        };
        assertThat(service.findByBooker(booker.getId(), state)).extracting(BookingDto::getId)
            .containsExactlyElementsOf(expectedBooker);
        assertThat(service.findBookingOwner(owner.getId(), state)).extracting(BookingDto::getId)
            .containsExactlyElementsOf(expectedOwner);
    }

    @ParameterizedTest
    @EnumSource(BookingState.class)
    void emptyListsForExistingUserAndNotFoundForMissingUser(BookingState state) {
        assertThat(service.findByBooker(booker.getId(), state)).isEmpty();
        assertThat(service.findBookingOwner(owner.getId(), state)).isEmpty();
        assertThatThrownBy(() -> service.findByBooker(-1L, state)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.findBookingOwner(-1L, state)).isInstanceOf(NotFoundException.class);
    }

    private List<Long> ids(Booking... bookings) {
        return java.util.Arrays.stream(bookings).map(Booking::getId).toList();
    }

    private NewBookingRequest request(Long itemId) {
        NewBookingRequest request = new NewBookingRequest();
        request.setItemId(itemId);
        request.setStart(now.plusDays(1));
        request.setEnd(now.plusDays(2));
        return request;
    }

    private User saveUser(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(name + "@example.com");
        return userRepository.save(user);
    }

    private Item saveItem(User itemOwner, String name) {
        Item saved = new Item();
        saved.setOwner(itemOwner);
        saved.setName(name);
        saved.setDescription("Description of " + name);
        saved.setAvailable(true);
        return itemRepository.save(saved);
    }

    private Booking saveBooking(Item bookedItem, User bookingUser, BookingStatus status, int startDays, int endDays) {
        Booking booking = new Booking();
        booking.setItem(bookedItem);
        booking.setBooker(bookingUser);
        booking.setStatus(status);
        booking.setStart(now.plusDays(startDays));
        booking.setEnd(now.plusDays(endDays));
        return bookingRepository.save(booking);
    }
}
