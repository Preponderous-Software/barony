package com.barony.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UserAuthClientTest {

    private static final String BASE = "http://userauth:9998";

    private UserAuthClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() throws Exception {
        // Build the client with a real builder, then bind a MockRestServiceServer to the
        // RestTemplate it constructed (RestTemplateBuilder is final, so reach for the field).
        this.client = new UserAuthClient(new RestTemplateBuilder(), BASE);
        Field field = UserAuthClient.class.getDeclaredField("restTemplate");
        field.setAccessible(true);
        RestTemplate restTemplate = (RestTemplate) field.get(client);
        this.server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void registerPostsCredentialsAndReturnsBody() {
        server.expect(requestTo(BASE + "/register"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.password").value("password123"))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":1,\"username\":\"alice\"}"));

        Map<String, Object> result = client.register("alice", "password123");

        assertEquals("alice", result.get("username"));
        server.verify();
    }

    @Test
    void registerPropagatesConflictStatus() {
        server.expect(requestTo(BASE + "/register"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"username taken\"}"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.register("alice", "password123"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void loginReturnsTokenBody() {
        server.expect(requestTo(BASE + "/login"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"token\":\"jwt-abc\",\"tokenType\":\"Bearer\",\"expiresAt\":\"2030-01-01T00:00:00Z\"}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.login("alice", "password123");

        assertEquals("jwt-abc", result.get("token"));
        server.verify();
    }

    @Test
    void loginPropagatesUnauthorized() {
        server.expect(requestTo(BASE + "/login"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.login("alice", "wrong"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void validateReturnsUsernameOnValidToken() {
        server.expect(requestTo(BASE + "/session/validate"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer jwt-abc"))
                .andRespond(withSuccess("{\"valid\":true,\"username\":\"alice\"}", MediaType.APPLICATION_JSON));

        Optional<String> username = client.validate("jwt-abc");

        assertTrue(username.isPresent());
        assertEquals("alice", username.get());
        server.verify();
    }

    @Test
    void validateReturnsEmptyOnUnauthorized() {
        server.expect(requestTo(BASE + "/session/validate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertTrue(client.validate("revoked-token").isEmpty());
        server.verify();
    }

    @Test
    void validateReturnsEmptyForBlankTokenWithoutCallingServer() {
        // No server expectation set; a call would fail verification.
        assertTrue(client.validate("   ").isEmpty());
        assertTrue(client.validate(null).isEmpty());
        server.verify();
    }

    @Test
    void logoutSendsBearerToken() {
        server.expect(requestTo(BASE + "/logout"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-abc"))
                .andRespond(withSuccess("{\"message\":\"logged out\"}", MediaType.APPLICATION_JSON));

        client.logout("jwt-abc");

        server.verify();
    }
}
