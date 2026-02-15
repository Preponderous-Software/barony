package com.barony.webclient.controller;

import com.barony.webclient.model.*;
import com.barony.webclient.service.BackendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

@Controller
public class WebController {
    
    @Autowired
    private BackendService backendService;
    
    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }
    
    @GetMapping("/login")
    public String login() {
        return "login";
    }
    
    @GetMapping("/game")
    public String game(Model model) {
        // Game page will be loaded and JavaScript will handle session validation
        return "game";
    }
    
    // Proxy endpoints for session-based backend API calls
    @PostMapping("/api/auth/login")
    @ResponseBody
    public Map<String, String> proxyLogin(@RequestBody Map<String, String> request) {
        // Forward login request to backend
        return backendService.login(request);
    }
    
    @PostMapping("/api/tick")
    @ResponseBody
    public GameState tick() {
        return backendService.tick();
    }
    
    @PostMapping("/api/command")
    @ResponseBody
    public GameState command(@RequestBody Command command) {
        return backendService.sendCommand(command);
    }
    
    @PostMapping("/api/reset")
    @ResponseBody
    public GameState reset() {
        return backendService.reset();
    }
    
    @PostMapping("/api/decision")
    @ResponseBody
    public ResponseEntity<?> decision(@RequestBody RulerDecision decision) {
        try {
            GameState state = backendService.changePolicy(decision);
            return ResponseEntity.ok(state);
        } catch (HttpClientErrorException e) {
            // Backend returned 4xx error - pass through the status code and message
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getStatusText());
            error.put("message", e.getResponseBodyAsString());
            error.put("status", e.getStatusCode().value());
            return ResponseEntity.status(e.getStatusCode()).body(error);
        } catch (HttpServerErrorException e) {
            // Backend returned 5xx error
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Backend error: " + e.getStatusText());
            error.put("status", e.getStatusCode().value());
            return ResponseEntity.status(e.getStatusCode()).body(error);
        } catch (RestClientException e) {
            // Other network/connection errors
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Could not connect to backend");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
    }
    
    @GetMapping("/api/state")
    @ResponseBody
    public GameState getState() {
        return backendService.getState();
    }
    
    @GetMapping("/api/ruler-stats")
    @ResponseBody
    public RulerStats getRulerStats() {
        return backendService.getRulerStats();
    }
}
