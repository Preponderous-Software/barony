package com.barony.webclient.service;

import com.barony.webclient.model.Command;
import com.barony.webclient.model.GameState;
import com.barony.webclient.model.RulerDecision;
import com.barony.webclient.model.RulerStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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

    @SuppressWarnings("unchecked")
    public Map<String, String> login(Map<String, String> request) {
        HttpEntity<Map<String, String>> entity = jsonEntity(request);
        return restTemplate.postForObject(backendUrl + "/api/auth/login", entity, Map.class);
    }

    public GameState getState() {
        return restTemplate.getForObject(backendUrl + "/state", GameState.class);
    }

    public GameState tick() {
        return restTemplate.postForObject(backendUrl + "/tick", null, GameState.class);
    }

    public GameState sendCommand(Command command) {
        HttpEntity<Command> entity = jsonEntity(command);
        return restTemplate.postForObject(backendUrl + "/command", entity, GameState.class);
    }

    public GameState reset() {
        return restTemplate.postForObject(backendUrl + "/api/reset", null, GameState.class);
    }

    public GameState changePolicy(RulerDecision decision) {
        HttpEntity<RulerDecision> entity = jsonEntity(decision);
        return restTemplate.postForObject(backendUrl + "/api/decision", entity, GameState.class);
    }

    public RulerStats getRulerStats() {
        return restTemplate.getForObject(backendUrl + "/api/ruler-stats", RulerStats.class);
    }

    public GameState getSessionState(String sessionId) {
        HttpEntity<Void> entity = sessionEntity(sessionId);
        return restTemplate.exchange(
                backendUrl + "/api/session/state", HttpMethod.GET, entity, GameState.class).getBody();
    }

    public GameState sessionTick(String sessionId) {
        HttpEntity<Void> entity = sessionEntity(sessionId);
        return restTemplate.postForObject(backendUrl + "/api/session/tick", entity, GameState.class);
    }

    public GameState sessionCommand(String sessionId, Command command) {
        HttpEntity<Command> entity = sessionJsonEntity(sessionId, command);
        return restTemplate.postForObject(backendUrl + "/api/session/command", entity, GameState.class);
    }

    public GameState sessionReset(String sessionId) {
        HttpEntity<Void> entity = sessionEntity(sessionId);
        return restTemplate.postForObject(backendUrl + "/api/session/reset", entity, GameState.class);
    }

    public GameState sessionChangePolicy(String sessionId, RulerDecision decision) {
        HttpEntity<RulerDecision> entity = sessionJsonEntity(sessionId, decision);
        return restTemplate.postForObject(backendUrl + "/api/session/decision", entity, GameState.class);
    }

    public RulerStats sessionRulerStats(String sessionId) {
        HttpEntity<Void> entity = sessionEntity(sessionId);
        return restTemplate.exchange(
                backendUrl + "/api/session/ruler-stats", HttpMethod.GET, entity, RulerStats.class).getBody();
    }

    private <T> HttpEntity<T> jsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> sessionEntity(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Id", sessionId);
        return new HttpEntity<>(headers);
    }

    private <T> HttpEntity<T> sessionJsonEntity(String sessionId, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Session-Id", sessionId);
        return new HttpEntity<>(body, headers);
    }
}
