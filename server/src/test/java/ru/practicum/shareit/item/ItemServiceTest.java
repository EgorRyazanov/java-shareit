package ru.practicum.shareit.item;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.practicum.shareit.booking.mapper.BookingMapper;
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
import ru.practicum.shareit.item.mapper.CommentMapper;
import ru.practicum.shareit.item.mapper.ItemMapper;
import ru.practicum.shareit.item.model.Comment;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.CommentRepository;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.item.service.ItemServiceImpl;
import ru.practicum.shareit.request.ItemRequest;
import ru.practicum.shareit.request.repository.ItemRequestRepository;
import ru.practicum.shareit.user.mapper.UserMapper;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ItemRequestRepository requestRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CommentRepository commentRepository;
    private ItemServiceImpl service;
    private User owner;
    private Item item;

    @BeforeEach
    void setUp() {
        ItemMapper mapper = new ItemMapper();
        service = new ItemServiceImpl(itemRepository, userRepository, requestRepository, bookingRepository,
            commentRepository, new BookingMapper(new UserMapper(), mapper), new CommentMapper(), mapper);
        owner = new User();
        owner.setId(1L);
        owner.setName("Owner");
        item = new Item();
        item.setId(10L);
        item.setOwner(owner);
        item.setName("Drill");
        item.setDescription("Cordless drill");
        item.setAvailable(true);
    }

    @Test
    void listsOwnItemsWithBookingAndCommentProjections() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(itemRepository.findAllByOwner_Id(1L)).thenReturn(List.of(item));
        stubBookings();
        when(commentRepository.findAllByItem_IdOrderByCreatedDesc(10L)).thenReturn(List.of(comment()));

        ItemDto result = service.getItemsByUserId(1L).getFirst();

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("Drill");
        assertThat(result.getDescription()).isEqualTo("Cordless drill");
        assertThat(result.getAvailable()).isTrue();
        assertThat(result.getLastBooking().getId()).isEqualTo(20L);
        assertThat(result.getNextBooking().getBookerId()).isEqualTo(2L);
        assertThat(result.getComments()).extracting(CommentDto::getText).containsExactly("Works well");
        assertThat(result.getComments().getFirst().getAuthorName()).isEqualTo("Owner");
    }

    @Test
    void listsEmptyItemsForExistingUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        assertThat(service.getItemsByUserId(1L)).isEmpty();
        verifyNoInteractions(bookingRepository, commentRepository);
    }

    @Test
    void rejectsListingForMissingUser() {
        assertThatThrownBy(() -> service.getItemsByUserId(1L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(itemRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void blankSearchDoesNotQueryRepository(String query) {
        assertThat(service.search(query)).isEmpty();
        verifyNoInteractions(itemRepository);
    }

    @Test
    void mapsSearchResults() {
        when(itemRepository.search("dRiLl")).thenReturn(List.of(item));
        assertThat(service.search("dRiLl")).extracting(ItemDto::getName).containsExactly("Drill");
    }

    @Test
    void createsItemWithoutRequestAndAssignsOwner() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            assertThat(saved.getOwner()).isSameAs(owner);
            assertThat(saved.getRequest()).isNull();
            saved.setId(11L);
            return saved;
        });

        ItemDto result = service.createItem(1L, newItem());

        assertThat(result.getId()).isEqualTo(11L);
        assertThat(result.getDescription()).isEqualTo("Cordless drill");
        assertThat(result.getAvailable()).isTrue();
        verifyNoInteractions(requestRepository);
    }

    @Test
    void createsItemLinkedToExistingRequest() {
        NewItemRequest input = newItem();
        input.setRequestId(8L);
        ItemRequest request = new ItemRequest();
        request.setId(8L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(requestRepository.findById(8L)).thenReturn(Optional.of(request));
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            assertThat(saved.getRequest()).isSameAs(request);
            return saved;
        });
        assertThat(service.createItem(1L, input).getName()).isEqualTo("Drill");
    }

    @Test
    void rejectsCreationForMissingUser() {
        NewItemRequest request = newItem();
        assertThatThrownBy(() -> service.createItem(1L, request)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(itemRepository, requestRepository);
    }

    @Test
    void rejectsMissingRequestBeforeSaving() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        NewItemRequest input = newItem();
        input.setRequestId(8L);
        assertThatThrownBy(() -> service.createItem(1L, input)).isInstanceOf(NotFoundException.class);
        verify(itemRepository, never()).save(any());
    }

    @Test
    void ownerSeesBookingDetails() {
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        stubBookings();
        ItemDto result = service.getItemById(10L, 1L);
        assertThat(result.getLastBooking().getBookerId()).isEqualTo(2L);
        assertThat(result.getNextBooking().getId()).isEqualTo(21L);
        assertThat(result.getComments()).isEmpty();
    }

    @Test
    void thirdPartySeesCommentsWithoutBookingDetails() {
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(commentRepository.findAllByItem_IdOrderByCreatedDesc(10L)).thenReturn(List.of(comment()));
        ItemDto result = service.getItemById(10L, 2L);
        assertThat(result.getNextBooking()).isNull();
        assertThat(result.getLastBooking()).isNull();
        assertThat(result.getComments()).hasSize(1);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void rejectsReadingMissingItem() {
        assertThatThrownBy(() -> service.getItemById(10L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatesAllSuppliedFieldsIncludingFalse() {
        stubUpdate();
        UpdateItemRequest input = new UpdateItemRequest();
        input.setName("Hammer");
        input.setDescription("Heavy");
        input.setAvailable(false);
        ItemDto result = service.updateItem(1L, 10L, input);
        assertThat(result.getName()).isEqualTo("Hammer");
        assertThat(result.getDescription()).isEqualTo("Heavy");
        assertThat(result.getAvailable()).isFalse();
    }

    @Test
    void partialUpdatePreservesOmittedFields() {
        stubUpdate();
        UpdateItemRequest input = new UpdateItemRequest();
        input.setName("Updated");
        ItemDto result = service.updateItem(1L, 10L, input);
        assertThat(result.getName()).isEqualTo("Updated");
        assertThat(result.getDescription()).isEqualTo("Cordless drill");
        assertThat(result.getAvailable()).isTrue();
    }

    @Test
    void emptyUpdatePreservesItem() {
        stubUpdate();
        assertThat(service.updateItem(1L, 10L, new UpdateItemRequest()).getName()).isEqualTo("Drill");
    }

    @Test
    void rejectsUpdateByNonOwner() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        UpdateItemRequest request = new UpdateItemRequest();
        assertThatThrownBy(() -> service.updateItem(2L, 10L, request))
            .isInstanceOf(NotFoundException.class);
        verify(itemRepository, never()).save(any());
    }

    @Test
    void rejectsUpdateForMissingUser() {
        UpdateItemRequest request = new UpdateItemRequest();
        assertThatThrownBy(() -> service.updateItem(1L, 10L, request))
            .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(itemRepository);
    }

    @Test
    void rejectsUpdateForMissingItem() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        UpdateItemRequest request = new UpdateItemRequest();
        assertThatThrownBy(() -> service.updateItem(1L, 10L, request))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createsCommentForBookerAfterApprovedBookingHasEnded() {
        stubCommentRecords();
        when(bookingRepository.existsByItem_IdAndBooker_IdAndStatusAndEndBefore(eq(10L), eq(1L),
            eq(BookingStatus.APPROVED), any(LocalDateTime.class))).thenReturn(true);
        LocalDateTime before = LocalDateTime.now();
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            assertThat(saved.getItem()).isSameAs(item);
            assertThat(saved.getAuthor()).isSameAs(owner);
            saved.setId(30L);
            return saved;
        });
        CommentDto result = service.createComment(1L, 10L, newComment());
        assertThat(result.getId()).isEqualTo(30L);
        assertThat(result.getText()).isEqualTo("Works well");
        assertThat(result.getAuthorName()).isEqualTo("Owner");
        assertThat(result.getCreated()).isBetween(before, LocalDateTime.now());
    }

    @Test
    void rejectsCommentWithoutCompletedApprovedBooking() {
        stubCommentRecords();
        NewCommentRequest request = newComment();
        assertThatThrownBy(() -> service.createComment(1L, 10L, request))
            .isInstanceOf(BadRequestException.class).hasMessage("Can't create comment for item");
        verify(commentRepository, never()).save(any());
    }

    @Test
    void rejectsCommentForMissingUser() {
        NewCommentRequest request = newComment();
        assertThatThrownBy(() -> service.createComment(1L, 10L, request)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(itemRepository, commentRepository, bookingRepository);
    }

    @Test
    void rejectsCommentForMissingItem() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        NewCommentRequest request = newComment();
        assertThatThrownBy(() -> service.createComment(1L, 10L, request)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(commentRepository, bookingRepository);
    }

    private void stubUpdate() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);
    }

    private void stubCommentRecords() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(itemRepository.findById(10L)).thenReturn(Optional.of(item));
    }

    private void stubBookings() {
        User booker = new User();
        booker.setId(2L);
        Booking last = new Booking();
        last.setId(20L);
        last.setBooker(booker);
        Booking next = new Booking();
        next.setId(21L);
        next.setBooker(booker);
        when(bookingRepository.findFirstByItem_IdAndStatusAndStartBeforeOrderByStartDesc(eq(10L),
            eq(BookingStatus.APPROVED), any(LocalDateTime.class))).thenReturn(Optional.of(last));
        when(bookingRepository.findFirstByItem_IdAndStatusAndStartAfterOrderByStartAsc(eq(10L),
            eq(BookingStatus.APPROVED), any(LocalDateTime.class))).thenReturn(Optional.of(next));
    }

    private Comment comment() {
        Comment comment = new Comment();
        comment.setId(30L);
        comment.setText("Works well");
        comment.setCreated(LocalDateTime.of(2026, 1, 1, 12, 0));
        comment.setAuthor(owner);
        return comment;
    }

    private NewItemRequest newItem() {
        NewItemRequest request = new NewItemRequest();
        request.setName("Drill");
        request.setDescription("Cordless drill");
        request.setAvailable(true);
        return request;
    }

    private NewCommentRequest newComment() {
        NewCommentRequest request = new NewCommentRequest();
        request.setText("Works well");
        return request;
    }
}
