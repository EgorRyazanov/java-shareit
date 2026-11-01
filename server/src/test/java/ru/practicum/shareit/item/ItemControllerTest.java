package ru.practicum.shareit.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.shareit.booking.dto.BookingShortDto;
import ru.practicum.shareit.exception.BadRequestException;
import ru.practicum.shareit.exception.NotFoundException;
import ru.practicum.shareit.item.dto.CommentDto;
import ru.practicum.shareit.item.dto.ItemDto;
import ru.practicum.shareit.item.dto.NewCommentRequest;
import ru.practicum.shareit.item.dto.NewItemRequest;
import ru.practicum.shareit.item.dto.UpdateItemRequest;
import ru.practicum.shareit.item.service.ItemService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemController.class)
class ItemControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ItemService service;

    @Test
    void listsOwnItemsWithBookingAndCommentJson() throws Exception {
        when(service.getItemsByUserId(7L)).thenReturn(List.of(item()));
        mockMvc.perform(get("/items").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].name").value("Drill"))
            .andExpect(jsonPath("$[0].description").value("Cordless"))
            .andExpect(jsonPath("$[0].available").value(true))
            .andExpect(jsonPath("$[0].lastBooking.id").value(20))
            .andExpect(jsonPath("$[0].nextBooking.bookerId").value(8))
            .andExpect(jsonPath("$[0].comments[0].authorName").value("Booker"))
            .andExpect(jsonPath("$[0].comments[0].created").value("2026-01-01T12:00:00"));
        verify(service).getItemsByUserId(7L);
    }

    @Test
    void readsItemUsingItemIdThenUserId() throws Exception {
        when(service.getItemById(10L, 7L)).thenReturn(item());
        mockMvc.perform(get("/items/10").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.comments[0].text").value("Works well"));
        verify(service).getItemById(10L, 7L);
    }

    @Test
    void createsItemForwardingAllFieldsAndOptionalRequest() throws Exception {
        when(service.createItem(eq(7L), any(NewItemRequest.class))).thenReturn(item());
        mockMvc.perform(post("/items").header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Drill\",\"description\":\"Cordless\",\"available\":true,\"requestId\":9}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(10));
        verify(service).createItem(eq(7L), argThat(request -> "Drill".equals(request.getName())
            && "Cordless".equals(request.getDescription()) && Boolean.TRUE.equals(request.getAvailable())
            && Long.valueOf(9L).equals(request.getRequestId())));
    }

    @Test
    void patchesOnlySuppliedFieldIncludingFalse() throws Exception {
        ItemDto result = item();
        result.setAvailable(false);
        when(service.updateItem(eq(7L), eq(10L), any(UpdateItemRequest.class))).thenReturn(result);
        mockMvc.perform(patch("/items/10").header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
        verify(service).updateItem(eq(7L), eq(10L), argThat(request -> request.getName() == null
            && request.getDescription() == null && Boolean.FALSE.equals(request.getAvailable())));
    }

    @Test
    void createsCommentForwardingAuthorItemAndText() throws Exception {
        when(service.createComment(eq(7L), eq(10L), any(NewCommentRequest.class))).thenReturn(comment());
        mockMvc.perform(post("/items/10/comment").header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Works well\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(30))
            .andExpect(jsonPath("$.text").value("Works well"))
            .andExpect(jsonPath("$.authorName").value("Booker"));
        verify(service).createComment(eq(7L), eq(10L), argThat(request -> "Works well".equals(request.getText())));
    }

    @Test
    void searchesWithoutUserHeaderAndPreservesQuery() throws Exception {
        when(service.search("dRiLl")).thenReturn(List.of(item()));
        mockMvc.perform(get("/items/search").param("text", "dRiLl"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(10));
        verify(service).search("dRiLl");
    }

    @Test
    void returnsEmptyCollectionForBlankSearch() throws Exception {
        when(service.search(" ")).thenReturn(List.of());
        mockMvc.perform(get("/items/search").param("text", " "))
            .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        verify(service).search(" ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/items", "/items/10"})
    void rejectsMissingUserHeaderForReads(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingHeadersForWrites() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/items/10").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/items/10/comment").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsSearchWithoutText() throws Exception {
        mockMvc.perform(get("/items/search")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void returns404ForMissingItem() throws Exception {
        when(service.getItemById(10L, 7L)).thenThrow(new NotFoundException());
        mockMvc.perform(get("/items/10").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns404ForPatchByNonOwner() throws Exception {
        when(service.updateItem(eq(7L), eq(10L), any(UpdateItemRequest.class))).thenThrow(new NotFoundException());
        mockMvc.perform(patch("/items/10").header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Changed\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void returns400WhenCommentAuthorHasNoCompletedBooking() throws Exception {
        when(service.createComment(eq(7L), eq(10L), any(NewCommentRequest.class)))
            .thenThrow(new BadRequestException("Can't create comment for item"));
        mockMvc.perform(post("/items/10/comment").header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Works well\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Can't create comment for item"));
    }

    @Test
    void rejectsMalformedJsonBeforeCallingService() throws Exception {
        mockMvc.perform(post("/items").header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private ItemDto item() {
        ItemDto item = new ItemDto();
        item.setId(10L);
        item.setName("Drill");
        item.setDescription("Cordless");
        item.setAvailable(true);
        BookingShortDto booking = new BookingShortDto();
        booking.setId(20L);
        booking.setBookerId(8L);
        item.setLastBooking(booking);
        item.setNextBooking(booking);
        item.setComments(List.of(comment()));
        return item;
    }

    private CommentDto comment() {
        CommentDto comment = new CommentDto();
        comment.setId(30L);
        comment.setText("Works well");
        comment.setAuthorName("Booker");
        comment.setCreated(LocalDateTime.of(2026, 1, 1, 12, 0));
        return comment;
    }
}
