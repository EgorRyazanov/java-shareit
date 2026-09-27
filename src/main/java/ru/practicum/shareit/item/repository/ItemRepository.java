package ru.practicum.shareit.item.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.shareit.item.model.Item;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findAllByOwner_Id(Long ownerId);

    @Query("""
        select text from Item as text where text.available = true and (lower(text.name) like lower(concat('%', ?1, '%')) or lower(text.description) like lower(concat('%', ?1, '%')))
    """)
    List<Item> search(String lastNamePrefix);
}
