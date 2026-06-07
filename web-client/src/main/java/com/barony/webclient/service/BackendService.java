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

import java.time.Duration;
import java.util.Map;

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
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, jsonHeaders());
        return restTemplate.postForObject(backendUrl + "/api/auth/register", entity, Map.class);
    }

    public Map<String, Object> login(Map<String, String> request) {
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, jsonHeaders());
        return restTemplate.postForObject(backendUrl + "/api/auth/login", entity, Map.class);
    }

    public Map<String, Object> logout(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(token));
        return restTemplate.postForObject(backendUrl + "/api/auth/logout", entity, Map.class);
    }

    public GameState getState() {
        return restTemplate.getForObject(backendUrl + "/state", GameState.class);
    }

    public GameState tick() {
        return restTemplate.postForObject(backendUrl + "/tick", null, GameState.class);
    }

    public GameState sendCommand(Command command) {
        HttpEntity<Command> request = new HttpEntity<>(command, jsonHeaders());
        return restTemplate.postForObject(backendUrl + "/command", request, GameState.class);
    }

    public GameState reset() {
        return restTemplate.postForObject(backendUrl + "/api/reset", null, GameState.class);
    }

    public GameState changePolicy(RulerDecision decision) {
        HttpEntity<RulerDecision> request = new HttpEntity<>(decision, jsonHeaders());
        return restTemplate.postForObject(backendUrl + "/api/decision", request, GameState.class);
    }

    public RulerStats getRulerStats() {
        return restTemplate.getForObject(backendUrl + "/api/ruler-stats", RulerStats.class);
    }

    // Authenticated, per-user backend API calls (forward the bearer token)
    public GameState getSessionState(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(token));
        return restTemplate.exchange(backendUrl + "/api/session/state", HttpMethod.GET, entity, GameState.class).getBody();
    }

    public GameState sessionTick(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(token));
        return restTemplate.postForObject(backendUrl + "/api/session/tick", entity, GameState.class);
    }

    public GameState sessionCommand(String token, Command command) {
        HttpEntity<Command> entity = new HttpEntity<>(command, bearerHeaders(token));
        return restTemplate.postForObject(backendUrl + "/api/session/command", entity, GameState.class);
    }

    public GameState sessionReset(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(token));
        return restTemplate.postForObject(backendUrl + "/api/session/reset", entity, GameState.class);
    }

    public GameState sessionChangePolicy(String token, RulerDecision decision) {
        HttpEntity<RulerDecision> entity = new HttpEntity<>(decision, bearerHeaders(token));
        return restTemplate.postForObject(backendUrl + "/api/session/decision", entity, GameState.class);
    }

    public RulerStats sessionRulerStats(String token) {
        HttpEntity<Void> entity = new HttpEntity<>(bearerHeaders(token));
        return restTemplate.exchange(backendUrl + "/api/session/ruler-stats", HttpMethod.GET, entity, RulerStats.class).getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
