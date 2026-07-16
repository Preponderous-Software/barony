package com.barony.webclient.service;

import com.barony.webclient.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;

/**
 * Proxies game and auth calls to the backend. Authentication rides on the HttpOnly
 * {@code barony_token} cookie: the browser sends it to this web client, which forwards the
 * {@code Cookie} header to the backend, and relays the backend's {@code Set-Cookie} back to the
 * browser on login/logout. The token is never read or held in JavaScript or this proxy's code.
 *
 * (In the gateway deployment Traefik routes {@code /api/*} straight to the backend, so this proxy
 * is exercised mainly by the local docker-compose setup; the cookie forwarding keeps that working.)
 */
@Service
public class BackendService {

    @Value("${backend.url:http://localhost:8080}")
    private String backendUrl;

    private final RestTemplate restTemplate;

    public BackendService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    // Authentication (proxied through the backend to UserAuth)
    public Map<String, Object> register(Map<String, String> request) {
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, jsonHeaders(null));
        return restTemplate.postForObject(backendUrl + "/api/auth/register", entity, Map.class);
    }

    /** Login returns the full response so the caller can relay the backend's Set-Cookie. */
    public ResponseEntity<Map> login(Map<String, String> request) {
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, jsonHeaders(null));
        return restTemplate.exchange(backendUrl + "/api/auth/login", HttpMethod.POST, entity, Map.class);
    }

    /** Logout forwards the cookie (to revoke) and returns the response so the caller can relay
        the Set-Cookie that clears it. */
    public ResponseEntity<Map> logout(String cookie) {
        HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders(cookie));
        return restTemplate.exchange(backendUrl + "/api/auth/logout", HttpMethod.POST, entity, Map.class);
    }

    public GameState getState() {
        return restTemplate.getForObject(backendUrl + "/state", GameState.class);
    }

    public GameState tick() {
        return restTemplate.postForObject(backendUrl + "/tick", null, GameState.class);
    }

    public GameState sendCommand(Command command) {
        HttpEntity<Command> request = new HttpEntity<>(command, jsonHeaders(null));
        return restTemplate.postForObject(backendUrl + "/command", request, GameState.class);
    }

    public GameState reset() {
        return restTemplate.postForObject(backendUrl + "/api/reset", null, GameState.class);
    }

    public GameState changePolicy(RulerDecision decision) {
        HttpEntity<RulerDecision> request = new HttpEntity<>(decision, jsonHeaders(null));
        return restTemplate.postForObject(backendUrl + "/api/decision", request, GameState.class);
    }

    public RulerStats getRulerStats() {
        return restTemplate.getForObject(backendUrl + "/api/ruler-stats", RulerStats.class);
    }

    // Authenticated, per-user backend API calls (forward the auth cookie)
    public GameState getSessionState(String cookie) {
        HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders(cookie));
        return restTemplate.exchange(backendUrl + "/api/session/state", HttpMethod.GET, entity, GameState.class).getBody();
    }

    public GameState sessionTick(String cookie) {
        HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders(cookie));
        return restTemplate.postForObject(backendUrl + "/api/session/tick", entity, GameState.class);
    }

    public GameState sessionCommand(String cookie, Command command) {
        HttpEntity<Command> entity = new HttpEntity<>(command, jsonHeaders(cookie));
        return restTemplate.postForObject(backendUrl + "/api/session/command", entity, GameState.class);
    }

    public GameState sessionReset(String cookie) {
        HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders(cookie));
        return restTemplate.postForObject(backendUrl + "/api/session/reset", entity, GameState.class);
    }

    public GameState sessionChangePolicy(String cookie, RulerDecision decision) {
        HttpEntity<RulerDecision> entity = new HttpEntity<>(decision, jsonHeaders(cookie));
        return restTemplate.postForObject(backendUrl + "/api/session/decision", entity, GameState.class);
    }

    public RulerStats sessionRulerStats(String cookie) {
        HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders(cookie));
        return restTemplate.exchange(backendUrl + "/api/session/ruler-stats", HttpMethod.GET, entity, RulerStats.class).getBody();
    }

    private HttpHeaders jsonHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null && !cookie.isBlank()) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }
        return headers;
    }
}
