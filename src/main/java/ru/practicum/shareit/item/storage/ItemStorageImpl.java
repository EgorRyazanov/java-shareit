package ru.practicum.shareit.item.storage;

import org.springframework.stereotype.Component;
import ru.practicum.shareit.item.model.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ItemStorageImpl implements ItemStorage {
    private final Map<Long, Item> items = new HashMap<>();

    @Override
    public List<Item> getItemsByUserId(Long id) {
        return this.items.values().stream().filter(item -> item.getCreatedUserId().equals(id)).toList();
    }

    @Override
    public Item createItem(Long userId, Item item) {
        item.setId(getNextId());
        items.put(item.getId(), item);

        return item;
    }

    @Override
    public Optional<Item> getItemById(Long id) {
        Item item = items.get(id);
        if (item == null) {
            return Optional.empty();
        }

        return Optional.of(item);
    }

    @Override
    public Item updateItem(Item updatedItem) {
        items.put(updatedItem.getId(), updatedItem);
        return updatedItem;
    }

    @Override
    public List<Item> search(String text) {
        String formattedText = text.toLowerCase();

        return this.items.values().stream()
            .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
            .filter(item -> item.getName().toLowerCase().contains(formattedText) || item.getDescription().toLowerCase().contains(formattedText))
            .toList();
    }

    private Long getNextId() {
        long currentMaxId = items.keySet()
            .stream()
            .max(Long::compare)
            .orElse(0L);
        return ++currentMaxId;
    }
}
