package com.barony.webclient.controller;

import com.barony.webclient.model.Command;
import com.barony.webclient.model.GameState;
import com.barony.webclient.model.RulerDecision;
import com.barony.webclient.model.RulerStats;
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
import java.util.function.Supplier;

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
        return "game";
    }

    @PostMapping("/api/auth/login")
    @ResponseBody
    public Map<String, String> proxyLogin(@RequestBody Map<String, String> request) {
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
        return executeWithBackendErrorHandling(() -> backendService.changePolicy(decision));
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

    @GetMapping("/api/session/state")
    @ResponseBody
    public GameState getSessionState(@RequestHeader("X-Session-Id") String sessionId) {
        return backendService.getSessionState(sessionId);
    }

    @PostMapping("/api/session/tick")
    @ResponseBody
    public GameState sessionTick(@RequestHeader("X-Session-Id") String sessionId) {
        return backendService.sessionTick(sessionId);
    }

    @PostMapping("/api/session/command")
    @ResponseBody
    public GameState sessionCommand(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody Command command) {
        return backendService.sessionCommand(sessionId, command);
    }

    @PostMapping("/api/session/reset")
    @ResponseBody
    public GameState sessionReset(@RequestHeader("X-Session-Id") String sessionId) {
        return backendService.sessionReset(sessionId);
    }

    @PostMapping("/api/session/decision")
    @ResponseBody
    public ResponseEntity<?> sessionDecision(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody RulerDecision decision) {
        return executeWithBackendErrorHandling(
                () -> backendService.sessionChangePolicy(sessionId, decision));
    }

    @GetMapping("/api/session/ruler-stats")
    @ResponseBody
    public RulerStats getSessionRulerStats(@RequestHeader("X-Session-Id") String sessionId) {
        return backendService.sessionRulerStats(sessionId);
    }

    private ResponseEntity<?> executeWithBackendErrorHandling(Supplier<GameState> action) {
        try {
            GameState state = action.get();
            return ResponseEntity.ok(state);
        } catch (HttpClientErrorException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getStatusText());
            error.put("message", e.getResponseBodyAsString());
            error.put("status", e.getStatusCode().value());
            return ResponseEntity.status(e.getStatusCode()).body(error);
        } catch (HttpServerErrorException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Backend error: " + e.getStatusText());
            error.put("status", e.getStatusCode().value());
            return ResponseEntity.status(e.getStatusCode()).body(error);
        } catch (RestClientException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Could not connect to backend");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
    }
}
