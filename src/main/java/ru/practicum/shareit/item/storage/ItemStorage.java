package ru.practicum.shareit.item.storage;

import ru.practicum.shareit.item.model.Item;

import java.util.List;
import java.util.Optional;

public interface ItemStorage {
    List<Item> getItemsByUserId(Long id);

    Item createItem(Long userId, Item item);

    Optional<Item> getItemById(Long id);

    Item updateItem(Item updatedItem);

    List<Item> search(String text);
}
