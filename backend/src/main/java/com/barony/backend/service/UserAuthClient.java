package com.barony.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Client for the standalone UserAuth service (https://github.com/Preponderous-Software/UserAuth).
 *
 * Barony delegates all credential handling to UserAuth: registration, login (JWT issuance),
 * per-request token validation (which also honours revocation), and logout (token revocation).
 * The base URL is configured via {@code userauth.url} and defaults to {@code http://localhost:9998},
 * the port UserAuth listens on.
 */
@Service
public class UserAuthClient {

    private final RestTemplate restTemplate;
    private final String userAuthUrl;

    public UserAuthClient(RestTemplateBuilder restTemplateBuilder,
                          @Value("${userauth.url:http://localhost:9998}") String userAuthUrl) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.userAuthUrl = userAuthUrl;
    }

    /**
     * Proxy UserAuth {@code POST /register}. Returns the created user's public fields.
     * Propagates UserAuth's 4xx status verbatim (e.g. 400 validation, 409 username taken);
     * a UserAuth 5xx or a connection failure surfaces as 503 (see {@link #unavailable}).
     */
    public Map<String, Object> register(String username, String password) {
        HttpEntity<Map<String, String>> entity = jsonEntity(Map.of(
                "username", username == null ? "" : username,
                "password", password == null ? "" : password), null);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userAuthUrl + "/register", HttpMethod.POST, entity, Map.class);
            return castBody(response.getBody());
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), messageFrom(e));
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    /**
     * Proxy UserAuth {@code POST /login}. On success returns {@code {token, tokenType, expiresAt}}.
     * A 401 from UserAuth (bad credentials) is propagated as 401.
     */
    public Map<String, Object> login(String username, String password) {
        HttpEntity<Map<String, String>> entity = jsonEntity(Map.of(
                "username", username == null ? "" : username,
                "password", password == null ? "" : password), null);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userAuthUrl + "/login", HttpMethod.POST, entity, Map.class);
            return castBody(response.getBody());
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), messageFrom(e));
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    /**
     * Validate a bearer token against UserAuth {@code GET /session/validate}.
     * Returns the authenticated username when the token is valid (signature, expiry, and not revoked),
     * or {@link Optional#empty()} when UserAuth rejects it with 401. Other failures surface as 503.
     */
    public Optional<String> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(token));
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userAuthUrl + "/session/validate", HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = castBody(response.getBody());
            Object username = body.get("username");
            return username == null ? Optional.empty() : Optional.of(username.toString());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return Optional.empty();
            }
            throw new ResponseStatusException(e.getStatusCode(), messageFrom(e));
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    /**
     * Revoke a bearer token via UserAuth {@code POST /logout}. Idempotent on the UserAuth side.
     */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(token));
        try {
            restTemplate.exchange(userAuthUrl + "/logout", HttpMethod.POST, entity, Map.class);
        } catch (HttpClientErrorException e) {
            throw new ResponseStatusException(e.getStatusCode(), messageFrom(e));
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    private HttpEntity<Map<String, String>> jsonEntity(Map<String, String> body, String token) {
        return new HttpEntity<>(body, token == null ? jsonHeaders() : bearerHeaders(token));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castBody(Map body) {
        return body == null ? Map.of() : (Map<String, Object>) body;
    }

    private String messageFrom(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        return (body == null || body.isBlank()) ? e.getStatusText() : body;
    }

    private ResponseStatusException unavailable(RestClientException e) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service is unavailable: " + e.getMessage());
    }
}
