package ru.practicum.shareit.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BaseClientTest {
    private MockRestServiceServer server;
    private BaseClient client;

    @BeforeEach
    void setUp() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new BaseClient(rest);
    }

    @Test
    void forwardsGetParametersAndUserHeader() {
        server.expect(requestTo("http://localhost:9090/items/search?text=drill"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-Sharer-User-Id", "7"))
            .andExpect(header("Accept", "application/json"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = client.get("http://localhost:9090/items/search?text={text}", 7L, Map.of("text", "drill"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOfSatisfying(byte[].class,
            body -> assertThat(body).containsExactly("[]".getBytes(StandardCharsets.UTF_8)));
        server.verify();
    }

    @Test
    void forwardsPostJsonWithoutUserHeaderAndPreservesResponseStatus() {
        server.expect(requestTo("http://localhost:9090/users"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(headerDoesNotExist("X-Sharer-User-Id"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"name\":\"Reader\",\"email\":\"reader@example.com\"}"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{\"id\":42}"));

        ResponseEntity<Object> response = client.post("http://localhost:9090/users", Map.of("name", "Reader", "email", "reader@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isInstanceOfSatisfying(byte[].class,
            body -> assertThat(body).containsExactly("{\"id\":42}".getBytes(StandardCharsets.UTF_8)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 409, 500})
    void preservesErrorStatusJsonContentTypeAndUtf8Body(int status) {
        byte[] error = "{\"error\":\"Ошибка сервера\"}".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo("http://localhost:9090/items/42"))
            .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(error));

        ResponseEntity<Object> response = client.get("http://localhost:9090/items/42", 7L);

        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isInstanceOfSatisfying(byte[].class,
            body -> assertThat(body).containsExactly(error));
        server.verify();
    }
}
