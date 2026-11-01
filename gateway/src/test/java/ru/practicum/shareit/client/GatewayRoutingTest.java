package ru.practicum.shareit.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayRoutingTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private Map<String, BaseClient> clients;
    private final Map<String, MockRestServiceServer> servers = new HashMap<>();

    @BeforeEach
    void setUp() {
        clients.forEach((name, client) -> servers.put(name, MockRestServiceServer.bindTo(client.rest).build()));
    }

    @AfterEach
    void verifyServerCalls() {
        servers.values().forEach(MockRestServiceServer::verify);
    }

    @ParameterizedTest
    @MethodSource("routes")
    void forwardsMethodPathHeadersAndBody(String client, HttpMethod verb, String path, String target, String body, boolean userHeader) throws Exception {
        var expected = servers.get(client).expect(requestTo("http://localhost:9090" + target)).andExpect(method(verb));
        if (userHeader) {
            expected.andExpect(header("X-Sharer-User-Id", "7"));
        } else {
            expected.andExpect(headerDoesNotExist("X-Sharer-User-Id"));
        }
        if (body != null) {
            expected.andExpect(MockRestRequestMatchers.content().json(body));
        }
        String response = verb == HttpMethod.DELETE ? "" : "{\"id\":42}";
        if (response.isEmpty()) {
            expected.andRespond(withStatus(HttpStatus.OK));
        } else {
            expected.andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        }

        MockHttpServletRequestBuilder incoming = request(verb, path);
        if (userHeader) {
            incoming.header("X-Sharer-User-Id", 7L);
        }
        if (body != null) {
            incoming.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(incoming)
            .andExpect(status().isOk())
            .andExpect(content().string(response));
    }

    static Stream<Arguments> routes() {
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        return Stream.of(
            Arguments.of("userClient", HttpMethod.POST, "/users", "/users", "{\"name\":\"Reader\",\"email\":\"reader@example.com\"}", false),
            Arguments.of("userClient", HttpMethod.GET, "/users/42", "/users/42", null, false),
            Arguments.of("userClient", HttpMethod.PATCH, "/users/42", "/users/42", "{\"name\":\"Reader\"}", false),
            Arguments.of("userClient", HttpMethod.DELETE, "/users/42", "/users/42", null, false),
            Arguments.of("itemClient", HttpMethod.GET, "/items", "/items", null, true),
            Arguments.of("itemClient", HttpMethod.GET, "/items/42", "/items/42", null, true),
            Arguments.of("itemClient", HttpMethod.POST, "/items", "/items", "{\"name\":\"Drill\",\"description\":\"Working\",\"available\":true,\"requestId\":5}", true),
            Arguments.of("itemClient", HttpMethod.PATCH, "/items/42", "/items/42", "{\"description\":\"Updated\"}", true),
            Arguments.of("itemClient", HttpMethod.GET, "/items/search?text=drill", "/items/search?text=drill", null, false),
            Arguments.of("itemClient", HttpMethod.POST, "/items/42/comment", "/items/42/comment", "{\"text\":\"Good drill\"}", true),
            Arguments.of("bookingClient", HttpMethod.POST, "/bookings", "/bookings", "{\"itemId\":42,\"start\":\"" + start + "\",\"end\":\"" + start.plusHours(1) + "\"}", true),
            Arguments.of("bookingClient", HttpMethod.GET, "/bookings/42", "/bookings/42", null, true),
            Arguments.of("bookingClient", HttpMethod.PATCH, "/bookings/42?approved=false", "/bookings/42?approved=false", null, true),
            Arguments.of("bookingClient", HttpMethod.GET, "/bookings", "/bookings?state=ALL", null, true),
            Arguments.of("bookingClient", HttpMethod.GET, "/bookings/owner", "/bookings/owner?state=ALL", null, true),
            Arguments.of("bookingClient", HttpMethod.GET, "/bookings?state=REJECTED", "/bookings?state=REJECTED", null, true),
            Arguments.of("itemRequestClient", HttpMethod.POST, "/requests", "/requests", "{\"description\":\"Need a drill\"}", true),
            Arguments.of("itemRequestClient", HttpMethod.GET, "/requests", "/requests", null, true),
            Arguments.of("itemRequestClient", HttpMethod.GET, "/requests/all", "/requests/all", null, true),
            Arguments.of("itemRequestClient", HttpMethod.GET, "/requests/42", "/requests/42", null, true)
        );
    }

    @Test
    void encodesSearchTextWithoutChangingItsValue() throws Exception {
        servers.get("itemClient")
            .expect(requestTo("http://localhost:9090/items/search?text=%D0%B4%D1%80%D0%B5%D0%BB%D1%8C%20%2B%20%D0%BD%D0%B0%D0%B1%D0%BE%D1%80%26"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/items/search").param("text", "дрель + набор&"))
            .andExpect(status().isOk())
            .andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @MethodSource("serverErrors")
    void preservesServerErrorStatusAndJson(String client, HttpMethod verb, String path, String body, int code) throws Exception {
        byte[] error = "{\"error\":\"Ошибка сервера\"}".getBytes(StandardCharsets.UTF_8);
        servers.get(client).expect(requestTo("http://localhost:9090" + path))
            .andRespond(withStatus(HttpStatus.valueOf(code)).contentType(MediaType.APPLICATION_JSON).body(error));
        MockHttpServletRequestBuilder incoming = request(verb, path).header("X-Sharer-User-Id", 7L);
        if (body != null) {
            incoming.contentType(MediaType.APPLICATION_JSON).content(body);
        }

        mockMvc.perform(incoming)
            .andExpect(status().is(code))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().bytes(error));
    }

    static Stream<Arguments> serverErrors() {
        return Stream.of(
            Arguments.of("userClient", HttpMethod.DELETE, "/users/42", null, 404),
            Arguments.of("itemClient", HttpMethod.PATCH, "/items/42", "{\"name\":\"Drill\"}", 403),
            Arguments.of("userClient", HttpMethod.POST, "/users", "{\"name\":\"Reader\",\"email\":\"reader@example.com\"}", 409),
            Arguments.of("bookingClient", HttpMethod.GET, "/bookings/42", null, 500),
            Arguments.of("itemRequestClient", HttpMethod.GET, "/requests/42", null, 404)
        );
    }

    @Test
    void returnsBadGatewayWhenServerIsUnavailable() throws Exception {
        servers.get("userClient").expect(requestTo("http://localhost:9090/users/42"))
            .andRespond(request -> {
                throw new IOException("Connection refused");
            });

        mockMvc.perform(get("/users/42"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("ShareIt server is unavailable"));
    }
}
