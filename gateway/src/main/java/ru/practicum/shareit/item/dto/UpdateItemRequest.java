package ru.practicum.shareit.item.dto;

import lombok.Data;

@Data
public class UpdateItemRequest {
    String name;
    String description;
    Boolean available;
}
