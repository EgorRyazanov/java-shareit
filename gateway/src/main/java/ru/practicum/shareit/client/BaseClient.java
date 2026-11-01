package ru.practicum.shareit.client;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

public class BaseClient {
    protected final RestTemplate rest;

    public BaseClient(RestTemplate rest) {
        this.rest = rest;
    }

    protected BaseClient(String serverUrl, RestTemplateBuilder builder) {
        this(builder.uriTemplateHandler(new DefaultUriBuilderFactory(serverUrl))
            .requestFactory(() -> new JdkClientHttpRequestFactory())
            .build());
    }

    protected ResponseEntity<Object> get(String path) {
        return get(path, null, null);
    }

    protected ResponseEntity<Object> get(String path, long userId) {
        return get(path, userId, null);
    }

    protected ResponseEntity<Object> get(String path, Long userId, @Nullable Map<String, Object> parameters) {
        return makeAndSendRequest(HttpMethod.GET, path, userId, parameters, null);
    }

    protected <T> ResponseEntity<Object> post(String path, T body) {
        return post(path, null, null, body);
    }

    protected <T> ResponseEntity<Object> post(String path, long userId, T body) {
        return post(path, userId, null, body);
    }

    protected <T> ResponseEntity<Object> post(String path, Long userId, @Nullable Map<String, Object> parameters, T body) {
        return makeAndSendRequest(HttpMethod.POST, path, userId, parameters, body);
    }

    protected <T> ResponseEntity<Object> put(String path, long userId, T body) {
        return put(path, userId, null, body);
    }

    protected <T> ResponseEntity<Object> put(String path, long userId, @Nullable Map<String, Object> parameters, T body) {
        return makeAndSendRequest(HttpMethod.PUT, path, userId, parameters, body);
    }

    protected <T> ResponseEntity<Object> patch(String path, T body) {
        return patch(path, null, null, body);
    }

    protected ResponseEntity<Object> patch(String path, long userId) {
        return patch(path, userId, null, null);
    }

    protected <T> ResponseEntity<Object> patch(String path, long userId, T body) {
        return patch(path, userId, null, body);
    }

    protected <T> ResponseEntity<Object> patch(String path, Long userId, @Nullable Map<String, Object> parameters, T body) {
        return makeAndSendRequest(HttpMethod.PATCH, path, userId, parameters, body);
    }

    protected ResponseEntity<Object> delete(String path) {
        return delete(path, null, null);
    }

    protected ResponseEntity<Object> delete(String path, long userId) {
        return delete(path, userId, null);
    }

    protected ResponseEntity<Object> delete(String path, Long userId, @Nullable Map<String, Object> parameters) {
        return makeAndSendRequest(HttpMethod.DELETE, path, userId, parameters, null);
    }

    private <T> ResponseEntity<Object> makeAndSendRequest(HttpMethod method, String path, Long userId, @Nullable Map<String, Object> parameters, @Nullable T body) {
        HttpEntity<T> requestEntity = new HttpEntity<>(body, defaultHeaders(userId));

        ResponseEntity<byte[]> shareitServerResponse;
        try {
            if (parameters != null) {
                shareitServerResponse = rest.exchange(path, method, requestEntity, byte[].class, parameters);
            } else {
                shareitServerResponse = rest.exchange(path, method, requestEntity, byte[].class);
            }
        } catch (HttpStatusCodeException e) {
            shareitServerResponse = new ResponseEntity<>(e.getResponseBodyAsByteArray(), e.getResponseHeaders(), e.getStatusCode());
        }
        return prepareGatewayResponse(shareitServerResponse);
    }

    private HttpHeaders defaultHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (userId != null) {
            headers.set("X-Sharer-User-Id", String.valueOf(userId));
        }
        return headers;
    }

    private static ResponseEntity<Object> prepareGatewayResponse(ResponseEntity<byte[]> response) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.remove(HttpHeaders.CONNECTION);
        return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
    }
}
