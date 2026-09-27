package ru.practicum.shareit.item.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.booking.dto.BookingShortDto;
import ru.practicum.shareit.booking.mapper.BookingMapper;
import ru.practicum.shareit.booking.model.BookingStatus;
import ru.practicum.shareit.booking.repository.BookingRepository;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.dto.*;
import ru.practicum.shareit.item.mapper.CommentMapper;
import ru.practicum.shareit.item.mapper.ItemMapper;
import ru.practicum.shareit.item.model.Comment;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.CommentRepository;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemServiceImpl implements ItemService {
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final CommentRepository commentRepository;
    private final BookingMapper bookingMapper;
    private final CommentMapper commentMapper;
    private final ItemMapper itemMapper;

    @Override
    public List<ItemDto> getItemsByUserId(Long userId) {
        log.trace("Получение всех вещей пользователя с id={}", userId);

        this.userRepository.findById(userId).orElseThrow(NotFoundException::new);

        LocalDateTime now = LocalDateTime.now();

        return this.itemRepository.findAllByOwner_Id(userId).stream().map(item -> {
            ItemDto itemDto = this.itemMapper.mapToItemDto(item);

            BookingShortDto nextBooking = this.bookingRepository.findFirstByItem_IdAndStatusAndStartAfterOrderByStartAsc(item.getId(), BookingStatus.APPROVED, now).map(bookingMapper::mapToBookingShortDto).orElse(null);
            BookingShortDto lastBooking = this.bookingRepository.findFirstByItem_IdAndStatusAndStartBeforeOrderByStartDesc(item.getId(), BookingStatus.APPROVED, now).map(bookingMapper::mapToBookingShortDto).orElse(null);

            itemDto.setNextBooking(nextBooking);
            itemDto.setLastBooking(lastBooking);

            List<CommentDto> comments = this.commentRepository.findAllByItem_IdOrderByCreatedDesc(item.getId()).stream().map(commentMapper::mapToCommentDto).toList();
            itemDto.setComments(comments);

            return itemDto;
        }).toList();
    }

    @Override
    public List<ItemDto> search(String text) {
        if (text.isBlank()) {
            return List.of();
        }

        return this.itemRepository.search(text).stream().map(itemMapper::mapToItemDto).toList();
    }

    @Override
    @Transactional
    public CommentDto createComment(Long userId, Long itemId, NewCommentRequest commentRequest) {
        User user = this.userRepository.findById(userId).orElseThrow(NotFoundException::new);
        Item item = this.itemRepository.findById(itemId).orElseThrow(NotFoundException::new);

        if (!bookingRepository.existsByItem_IdAndBooker_IdAndStatusAndEndBefore(itemId, userId, BookingStatus.APPROVED, LocalDateTime.now())) {
            throw new BadRequestException("Can't create comment for item");
        }

        Comment comment = this.commentMapper.mapToComment(commentRequest);

        comment.setItem(item);
        comment.setAuthor(user);

        return this.commentMapper.mapToCommentDto(this.commentRepository.save(comment));
    }

    @Override
    @Transactional
    public ItemDto createItem(Long userId, NewItemRequest itemDto) {
        User user = this.userRepository.findById(userId).orElseThrow(NotFoundException::new);

        log.trace("Создание вещи: {}", itemDto);

        Item item = this.itemMapper.mapToItem(itemDto);
        item.setOwner(user);

        return this.itemMapper.mapToItemDto(this.itemRepository.save(item));
    }

    @Override
    public ItemDto getItemById(Long id, Long userId) {
        log.trace("Получение вещи по id={}", id);

        LocalDateTime now = LocalDateTime.now();

        return itemRepository.findById(id)
            .map(item -> {
                ItemDto itemDto = this.itemMapper.mapToItemDto(item);

                if (item.getOwner().getId().equals(userId)) {
                    BookingShortDto nextBooking = this.bookingRepository.findFirstByItem_IdAndStatusAndStartAfterOrderByStartAsc(item.getId(), BookingStatus.APPROVED, now).map(bookingMapper::mapToBookingShortDto).orElse(null);
                    BookingShortDto lastBooking = this.bookingRepository.findFirstByItem_IdAndStatusAndStartBeforeOrderByStartDesc(item.getId(), BookingStatus.APPROVED, now).map(bookingMapper::mapToBookingShortDto).orElse(null);

                    itemDto.setNextBooking(nextBooking);
                    itemDto.setLastBooking(lastBooking);
                }

                List<CommentDto> comments = this.commentRepository.findAllByItem_IdOrderByCreatedDesc(id).stream().map(commentMapper::mapToCommentDto).toList();
                itemDto.setComments(comments);

                return itemDto;
            })
            .orElseThrow(NotFoundException::new);
    }

    @Override
    @Transactional
    public ItemDto updateItem(Long userId, Long itemId, UpdateItemRequest item) {
        this.userRepository.findById(userId).orElseThrow(NotFoundException::new);
        Item itemToUpdate = this.itemRepository.findById(itemId).orElseThrow(NotFoundException::new);

        if (!Objects.equals(itemToUpdate.getOwner().getId(), userId)) {
            throw new NotFoundException();
        }

        if (item.getName() != null) {
            itemToUpdate.setName(item.getName());
        }

        if (item.getDescription() != null) {
            itemToUpdate.setDescription(item.getDescription());
        }

        if (item.getAvailable() != null) {
            itemToUpdate.setAvailable(item.getAvailable());
        }

        return this.itemMapper.mapToItemDto(itemRepository.save(itemToUpdate));
    }
}
