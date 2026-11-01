package ru.practicum.shareit.request.service;

import ru.practicum.shareit.request.dto.ItemRequestDto;
import ru.practicum.shareit.request.dto.NewRequest;

import java.util.List;

public interface ItemRequestService {
    ItemRequestDto createRequest(Long userId, NewRequest newRequest);

    List<ItemRequestDto> getRequests(Long userId);

    List<ItemRequestDto> getOtherRequests(Long userId);

    ItemRequestDto getRequestById(Long requestId, Long userId);
}
