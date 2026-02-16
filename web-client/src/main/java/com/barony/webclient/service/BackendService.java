package com.barony.webclient.service;

import com.barony.webclient.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
    
    public Map<String, String> login(Map<String, String> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(request, headers);
        return restTemplate.postForObject(backendUrl + "/api/auth/login", entity, Map.class);
    }
    
    public GameState getState() {
        return restTemplate.getForObject(backendUrl + "/state", GameState.class);
    }
    
    public GameState tick() {
        return restTemplate.postForObject(backendUrl + "/tick", null, GameState.class);
    }
    
    public GameState sendCommand(Command command) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Command> request = new HttpEntity<>(command, headers);
        return restTemplate.postForObject(backendUrl + "/command", request, GameState.class);
    }
    
    public GameState reset() {
        return restTemplate.postForObject(backendUrl + "/api/reset", null, GameState.class);
    }
    
    public GameState changePolicy(RulerDecision decision) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<RulerDecision> request = new HttpEntity<>(decision, headers);
        return restTemplate.postForObject(backendUrl + "/api/decision", request, GameState.class);
    }
    
    public RulerStats getRulerStats() {
        return restTemplate.getForObject(backendUrl + "/api/ruler-stats", RulerStats.class);
    }
    
    // Session-aware backend API calls
    public GameState getSessionState(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Id", sessionId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(backendUrl + "/api/session/state", org.springframework.http.HttpMethod.GET, entity, GameState.class).getBody();
    }
    
    public GameState sessionTick(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Id", sessionId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.postForObject(backendUrl + "/api/session/tick", entity, GameState.class);
    }
    
    public GameState sessionCommand(String sessionId, Command command) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Session-Id", sessionId);
        HttpEntity<Command> entity = new HttpEntity<>(command, headers);
        return restTemplate.postForObject(backendUrl + "/api/session/command", entity, GameState.class);
    }
    
    public GameState sessionReset(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Id", sessionId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.postForObject(backendUrl + "/api/session/reset", entity, GameState.class);
    }
    
    public GameState sessionChangePolicy(String sessionId, RulerDecision decision) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Session-Id", sessionId);
        HttpEntity<RulerDecision> entity = new HttpEntity<>(decision, headers);
        return restTemplate.postForObject(backendUrl + "/api/session/decision", entity, GameState.class);
    }
    
    public RulerStats sessionRulerStats(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Session-Id", sessionId);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(backendUrl + "/api/session/ruler-stats", org.springframework.http.HttpMethod.GET, entity, RulerStats.class).getBody();
    }
}
