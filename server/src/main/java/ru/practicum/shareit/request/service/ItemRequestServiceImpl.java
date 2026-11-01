package ru.practicum.shareit.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.model.Item;
import ru.practicum.shareit.item.repository.ItemRepository;
import ru.practicum.shareit.request.ItemRequest;
import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.NewRequest;
import ru.practicum.shareit.request.mapper.ItemRequestMapper;
import ru.practicum.shareit.request.repository.ItemRequestRepository;
import ru.practicum.shareit.user.model.User;
import ru.practicum.shareit.user.repository.UserRepository;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemRequestServiceImpl implements ItemRequestService {
    private final ItemRequestRepository itemRequestRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final ItemRequestMapper itemRequestMapper;

    @Override
    @Transactional
    public ItemRequestDto createRequest(Long userId, NewRequest newRequest) {
        User user = this.userRepository.findById(userId).orElseThrow(NotFoundException::new);

        log.trace("Создание запроса вещи: {} для пользователя с id={}", newRequest, userId);

        ItemRequest request = this.itemRequestMapper.mapToItemRequest(newRequest);
        request.setRequestor(user);

        return this.itemRequestMapper.mapToItemRequestDto(this.itemRequestRepository.save(request));
    }

    @Override
    public List<ItemRequestDto> getRequests(Long userId) {
        this.userRepository.findById(userId).orElseThrow(NotFoundException::new);
        log.trace("Получение запросов вещей пользователя с id={}", userId);

        return this.getRequestsWithAnswers(this.itemRequestRepository.findAllByRequestor_IdOrderByCreatedDescIdDesc(userId));
    }

    @Override
    public List<ItemRequestDto> getOtherRequests(Long userId) {
        this.userRepository.findById(userId).orElseThrow(NotFoundException::new);
        log.trace("Получение запросов вещей других пользователей для пользователя с id={}", userId);

        return this.getRequestsWithAnswers(this.itemRequestRepository.findAllByRequestor_IdNotOrderByCreatedDescIdDesc(userId));
    }

    @Override
    public ItemRequestDto getRequestById(Long requestId, Long userId) {
        this.userRepository.findById(userId).orElseThrow(NotFoundException::new);
        log.trace("Получение запроса вещи с id={} пользователем с id={}", requestId, userId);

        ItemRequest request = this.itemRequestRepository.findById(requestId).orElseThrow(NotFoundException::new);
        return this.itemRequestMapper.mapToItemRequestDto(request, this.itemRepository.findAllByRequest_IdIn(List.of(requestId)));
    }

    private List<ItemRequestDto> getRequestsWithAnswers(List<ItemRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        List<Item> items = this.itemRepository.findAllByRequest_IdIn(requests.stream().map(ItemRequest::getId).toList());
        return this.itemRequestMapper.mapToItemRequestDtos(requests, items);
    }
}
