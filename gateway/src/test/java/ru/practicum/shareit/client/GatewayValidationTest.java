package ru.practicum.shareit.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayValidationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private List<BaseClient> clients;
    private final List<MockRestServiceServer> servers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clients.forEach(client -> servers.add(MockRestServiceServer.bindTo(client.rest).build()));
    }

    @AfterEach
    void verifyNoServerCalls() {
        servers.forEach(MockRestServiceServer::verify);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void rejectsInvalidBodyBeforeCallingServer(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(request(method, path)
                .header("X-Sharer-User-Id", 7L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    static Stream<Arguments> invalidBodies() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        return Stream.of(
            Arguments.of(HttpMethod.POST, "/users", "{\"name\":\"Reader\"}"),
            Arguments.of(HttpMethod.POST, "/users", "{\"name\":\"Reader\",\"email\":\"invalid\"}"),
            Arguments.of(HttpMethod.POST, "/users", "{\"name\":\"   \",\"email\":\"reader@example.com\"}"),
            Arguments.of(HttpMethod.PATCH, "/users/1", "{\"email\":\"invalid\"}"),
            Arguments.of(HttpMethod.POST, "/items", "{\"description\":\"Drill\",\"available\":true,\"requestId\":5}"),
            Arguments.of(HttpMethod.POST, "/items", "{\"name\":\"Drill\",\"available\":true,\"requestId\":5}"),
            Arguments.of(HttpMethod.POST, "/items", "{\"name\":\"Drill\",\"description\":\"Drill\",\"requestId\":5}"),
            Arguments.of(HttpMethod.POST, "/items/1/comment", "{\"text\":\"   \"}"),
            Arguments.of(HttpMethod.POST, "/requests", "{}"),
            Arguments.of(HttpMethod.POST, "/requests", "{\"description\":null}"),
            Arguments.of(HttpMethod.POST, "/requests", "{\"description\":\"\"}"),
            Arguments.of(HttpMethod.POST, "/requests", "{\"description\":\"   \"}"),
            Arguments.of(HttpMethod.POST, "/bookings", "{\"start\":\"" + start + "\",\"end\":\"" + start.plusHours(1) + "\"}"),
            Arguments.of(HttpMethod.POST, "/bookings", "{\"itemId\":1,\"end\":\"" + start.plusHours(1) + "\"}"),
            Arguments.of(HttpMethod.POST, "/bookings", "{\"itemId\":1,\"start\":\"" + start + "\"}"),
            Arguments.of(HttpMethod.POST, "/bookings", booking(start.minusDays(2), start.plusHours(1))),
            Arguments.of(HttpMethod.POST, "/bookings", booking(start, start.minusDays(2))),
            Arguments.of(HttpMethod.POST, "/bookings", booking(start, start)),
            Arguments.of(HttpMethod.POST, "/bookings", booking(start, start.minusHours(1))),
            Arguments.of(HttpMethod.POST, "/bookings", "{\"itemId\":1,\"start\":\"invalid\",\"end\":\"" + start + "\"}"),
            Arguments.of(HttpMethod.POST, "/users", "{\"name\":")
        );
    }

    private static String booking(LocalDateTime start, LocalDateTime end) {
        return "{\"itemId\":1,\"start\":\"" + start + "\",\"end\":\"" + end + "\"}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"/items", "/items/1", "/bookings", "/bookings/owner", "/bookings/1", "/requests", "/requests/all", "/requests/1"})
    void rejectsMissingUserHeader(String path) throws Exception {
        mockMvc.perform(get(path))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/bookings", "/bookings/owner"})
    void rejectsUnknownBookingState(String path) throws Exception {
        mockMvc.perform(get(path).header("X-Sharer-User-Id", 7L).param("state", "UNKNOWN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Unknown state: UNKNOWN"));
    }

    @Test
    void rejectsMissingApprovalParameter() throws Exception {
        mockMvc.perform(patch("/bookings/1").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidItemId() throws Exception {
        mockMvc.perform(get("/items/invalid").header("X-Sharer-User-Id", 7L))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSearchWithoutText() throws Exception {
        mockMvc.perform(get("/items/search"))
            .andExpect(status().isBadRequest());
    }
}
