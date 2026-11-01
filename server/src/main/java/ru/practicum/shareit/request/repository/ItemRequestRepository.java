package ru.practicum.shareit.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.shareit.request.ItemRequest;

import java.util.List;

public interface ItemRequestRepository extends JpaRepository<ItemRequest, Long> {
    List<ItemRequest> findAllByRequestor_IdOrderByCreatedDescIdDesc(Long requestorId);

    List<ItemRequest> findAllByRequestor_IdNotOrderByCreatedDescIdDesc(Long requestorId);
}
