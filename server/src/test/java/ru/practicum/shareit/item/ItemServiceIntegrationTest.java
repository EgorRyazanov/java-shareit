package ru.practicum.shareit.item;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.model.Booking;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.NewCommentRequest;
import ru.practicum.shareit.item.dto.NewItemRequest;
import ru.practicum.shareit.item.dto.UpdateItemRequest;
import ru.practicum.shareit.item.model.Comment;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.CommentRepository;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.item.service.ItemService;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ItemServiceIntegrationTest {
    @Autowired
    private ItemService service;
    @Autowired
    private ItemRepository itemRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private EntityManager entityManager;
    private User owner;
    private User booker;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        owner = user("Owner", "item-owner@example.com");
        booker = user("Booker", "item-booker@example.com");
    }

    @Test
    void listsOnlyOwnItemsWithNearestApprovedBookingsAndNewestComments() {
        Item drill = item(owner, "Drill", "Cordless", true);
        Item empty = item(owner, "Hammer", "Heavy", false);
        item(booker, "Other user's item", "Other", true);
        booking(drill, booker, BookingStatus.APPROVED, -7, -6);
        Booking last = booking(drill, booker, BookingStatus.APPROVED, -3, -2);
        booking(drill, booker, BookingStatus.WAITING, -1, 1);
        Booking next = booking(drill, booker, BookingStatus.APPROVED, 2, 3);
        booking(drill, booker, BookingStatus.APPROVED, 5, 6);
        booking(drill, booker, BookingStatus.REJECTED, 1, 2);
        comment(drill, "Older", now.minusDays(2));
        Comment newest = comment(drill, "Newest", now.minusDays(1));
        entityManager.flush();
        entityManager.clear();

        List<ItemDto> results = service.getItemsByUserId(owner.getId());

        assertThat(results).extracting(ItemDto::getId).containsExactlyInAnyOrder(drill.getId(), empty.getId());
        ItemDto projected = results.stream().filter(dto -> dto.getId().equals(drill.getId())).findFirst().orElseThrow();
        assertThat(projected.getLastBooking().getId()).isEqualTo(last.getId());
        assertThat(projected.getLastBooking().getBookerId()).isEqualTo(booker.getId());
        assertThat(projected.getNextBooking().getId()).isEqualTo(next.getId());
        assertThat(projected.getComments()).extracting(CommentDto::getText).containsExactly("Newest", "Older");
        assertThat(projected.getComments().getFirst().getId()).isEqualTo(newest.getId());
        assertThat(projected.getComments().getFirst().getAuthorName()).isEqualTo("Booker");
        ItemDto unbooked = results.stream().filter(dto -> dto.getId().equals(empty.getId())).findFirst().orElseThrow();
        assertThat(unbooked.getNextBooking()).isNull();
        assertThat(unbooked.getLastBooking()).isNull();
        assertThat(unbooked.getComments()).isEmpty();
    }

    @Test
    void itemDetailsExposeBookingsOnlyToOwnerAndCommentsToBoth() {
        Item item = item(owner, "Drill", "Cordless", true);
        Booking last = booking(item, booker, BookingStatus.APPROVED, -2, -1);
        Booking next = booking(item, booker, BookingStatus.APPROVED, 1, 2);
        comment(item, "Older", now.minusDays(2));
        comment(item, "Newest", now.minusDays(1));
        ItemDto owned = service.getItemById(item.getId(), owner.getId());
        ItemDto publicView = service.getItemById(item.getId(), booker.getId());
        assertThat(owned.getLastBooking().getId()).isEqualTo(last.getId());
        assertThat(owned.getNextBooking().getId()).isEqualTo(next.getId());
        assertThat(publicView.getLastBooking()).isNull();
        assertThat(publicView.getNextBooking()).isNull();
        assertThat(publicView.getComments()).extracting(CommentDto::getText).containsExactly("Newest", "Older");
        assertThat(owned.getComments()).usingRecursiveComparison().isEqualTo(publicView.getComments());
    }

    @Test
    void searchMatchesNameOrDescriptionIgnoringCaseAndExcludesUnavailableItems() {
        Item byName = item(owner, "Cordless DRILL", "Battery", true);
        Item byDescription = item(booker, "Tool", "Useful dRiLl for repairs", true);
        item(owner, "Drill", "Unavailable drill", false);
        item(owner, "Saw", "Cuts wood", true);
        assertThat(service.search("DrIlL")).extracting(ItemDto::getId)
            .containsExactlyInAnyOrder(byName.getId(), byDescription.getId());
        assertThat(service.search("absent")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankSearchReturnsNoItems(String text) {
        item(owner, "Drill", "Cordless", true);
        assertThat(service.search(text)).isEmpty();
    }

    @Test
    void emptyOwnerListAndSearchReturnEmptyCollections() {
        assertThat(service.getItemsByUserId(owner.getId())).isEmpty();
        assertThat(service.search("drill")).isEmpty();
    }

    @Test
    void createsItemAndPersistsOwnerWithoutRequest() {
        NewItemRequest request = new NewItemRequest();
        request.setName("Drill");
        request.setDescription("Cordless");
        request.setAvailable(true);
        ItemDto created = service.createItem(owner.getId(), request);
        entityManager.flush();
        entityManager.clear();
        Item stored = itemRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(stored.getRequest()).isNull();
        assertThat(stored.getName()).isEqualTo("Drill");
        assertThat(created.getDescription()).isEqualTo("Cordless");
        assertThat(created.getAvailable()).isTrue();
    }

    @Test
    void partialPatchPersistsFalseAndPreservesOmittedFields() {
        Item original = item(owner, "Drill", "Cordless", true);
        UpdateItemRequest patch = new UpdateItemRequest();
        patch.setAvailable(false);
        service.updateItem(owner.getId(), original.getId(), patch);
        entityManager.flush();
        entityManager.clear();
        Item stored = itemRepository.findById(original.getId()).orElseThrow();
        assertThat(stored.getAvailable()).isFalse();
        assertThat(stored.getName()).isEqualTo("Drill");
        assertThat(stored.getDescription()).isEqualTo("Cordless");
    }

    @Test
    void patchByAnotherUserIsRejectedWithoutChangingItem() {
        Item original = item(owner, "Drill", "Cordless", true);
        UpdateItemRequest patch = new UpdateItemRequest();
        patch.setName("Changed");
        Long bookerId = booker.getId();
        Long itemId = original.getId();
        assertThatThrownBy(() -> service.updateItem(bookerId, itemId, patch))
            .isInstanceOf(NotFoundException.class);
        assertThat(itemRepository.findById(original.getId()).orElseThrow().getName()).isEqualTo("Drill");
    }

    @Test
    void createsAndPersistsCommentAfterSameAuthorsCompletedApprovedBooking() {
        Item item = item(owner, "Drill", "Cordless", true);
        booking(item, booker, BookingStatus.APPROVED, -2, -1);
        LocalDateTime before = LocalDateTime.now();
        CommentDto created = service.createComment(booker.getId(), item.getId(), newComment());
        entityManager.flush();
        entityManager.clear();
        Comment stored = commentRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getItem().getId()).isEqualTo(item.getId());
        assertThat(stored.getAuthor().getId()).isEqualTo(booker.getId());
        assertThat(stored.getText()).isEqualTo("Works well");
        assertThat(created.getAuthorName()).isEqualTo("Booker");
        assertThat(created.getCreated()).isBetween(before, LocalDateTime.now());
    }

    @ParameterizedTest
    @CsvSource({"WAITING,-3,-2", "REJECTED,-3,-2", "CANCELED,-3,-2", "APPROVED,2,3", "APPROVED,-1,1"})
    void rejectsCommentForNonCompletedOrNonApprovedBooking(BookingStatus status, int startDays, int endDays) {
        Item item = item(owner, "Drill", "Cordless", true);
        booking(item, booker, status, startDays, endDays);
        Long bookerId = booker.getId();
        Long itemId = item.getId();
        NewCommentRequest request = newComment();
        assertThatThrownBy(() -> service.createComment(bookerId, itemId, request))
            .isInstanceOf(BadRequestException.class);
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void rejectsCommentWhenOnlyAnotherUserHasCompletedBooking() {
        Item item = item(owner, "Drill", "Cordless", true);
        booking(item, booker, BookingStatus.APPROVED, -2, -1);
        Long ownerId = owner.getId();
        Long itemId = item.getId();
        NewCommentRequest request = newComment();
        assertThatThrownBy(() -> service.createComment(ownerId, itemId, request))
            .isInstanceOf(BadRequestException.class);
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void rejectsCommentWhenApprovedBookingBelongsToAnotherItem() {
        Item wanted = item(owner, "Drill", "Cordless", true);
        Item rented = item(owner, "Hammer", "Heavy", true);
        booking(rented, booker, BookingStatus.APPROVED, -2, -1);
        Long bookerId = booker.getId();
        Long itemId = wanted.getId();
        NewCommentRequest request = newComment();
        assertThatThrownBy(() -> service.createComment(bookerId, itemId, request))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsMissingUsersAndItemsAcrossServiceMethods() {
        Item item = item(owner, "Drill", "Cordless", true);
        Long ownerId = owner.getId();
        Long bookerId = booker.getId();
        Long itemId = item.getId();
        NewItemRequest newItemRequest = new NewItemRequest();
        UpdateItemRequest missingUserPatch = new UpdateItemRequest();
        UpdateItemRequest missingItemPatch = new UpdateItemRequest();
        NewCommentRequest missingUserComment = newComment();
        NewCommentRequest missingItemComment = newComment();
        assertThatThrownBy(() -> service.getItemsByUserId(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.getItemById(Long.MAX_VALUE, ownerId)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.createItem(Long.MAX_VALUE, newItemRequest)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateItem(Long.MAX_VALUE, itemId, missingUserPatch))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateItem(ownerId, Long.MAX_VALUE, missingItemPatch))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.createComment(Long.MAX_VALUE, itemId, missingUserComment))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.createComment(bookerId, Long.MAX_VALUE, missingItemComment))
            .isInstanceOf(NotFoundException.class);
    }

    private User user(String name, String email) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        return userRepository.save(user);
    }

    private Item item(User itemOwner, String name, String description, boolean available) {
        Item item = new Item();
        item.setOwner(itemOwner);
        item.setName(name);
        item.setDescription(description);
        item.setAvailable(available);
        return itemRepository.save(item);
    }

    private Booking booking(Item item, User author, BookingStatus status, int startDays, int endDays) {
        Booking booking = new Booking();
        booking.setItem(item);
        booking.setBooker(author);
        booking.setStatus(status);
        booking.setStart(now.plusDays(startDays));
        booking.setEnd(now.plusDays(endDays));
        return bookingRepository.save(booking);
    }

    private Comment comment(Item item, String text, LocalDateTime created) {
        Comment comment = new Comment();
        comment.setItem(item);
        comment.setAuthor(booker);
        comment.setText(text);
        comment.setCreated(created);
        return commentRepository.save(comment);
    }

    private NewCommentRequest newComment() {
        NewCommentRequest request = new NewCommentRequest();
        request.setText("Works well");
        return request;
    }
}
