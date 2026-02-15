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
}
