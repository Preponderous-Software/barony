package com.barony.webclient.controller;

import com.barony.webclient.model.*;
import com.barony.webclient.service.BackendService;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class WebController {

    private final BackendService backendService;

    @GetMapping("/")
    public String index() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/game")
    public String game(Model model) {
        // Game page is loaded and the JavaScript handles token validation against the backend.
        return "game";
    }

    // Auth proxy endpoints (forward to the backend, which talks to UserAuth)
    @PostMapping("/api/auth/register")
    @ResponseBody
    public ResponseEntity<?> proxyRegister(@RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(backendService.register(request));
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return passthrough(e);
        } catch (RestClientException e) {
            return unavailable(e);
        }
    }

    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> proxyLogin(@RequestBody Map<String, String> request) {
        return proxy(() -> backendService.login(request));
    }

    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseEntity<?> proxyLogout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return proxy(() -> backendService.logout(token(authorization)));
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
        return proxy(() -> backendService.changePolicy(decision));
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

    // Authenticated, per-user proxy endpoints (forward the bearer token to the backend)
    @GetMapping("/api/session/state")
    @ResponseBody
    public ResponseEntity<?> getSessionState(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return proxy(() -> backendService.getSessionState(token(authorization)));
    }

    @PostMapping("/api/session/tick")
    @ResponseBody
    public ResponseEntity<?> sessionTick(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return proxy(() -> backendService.sessionTick(token(authorization)));
    }

    @PostMapping("/api/session/command")
    @ResponseBody
    public ResponseEntity<?> sessionCommand(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody Command command) {
        return proxy(() -> backendService.sessionCommand(token(authorization), command));
    }

    @PostMapping("/api/session/reset")
    @ResponseBody
    public ResponseEntity<?> sessionReset(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return proxy(() -> backendService.sessionReset(token(authorization)));
    }

    @PostMapping("/api/session/decision")
    @ResponseBody
    public ResponseEntity<?> sessionDecision(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RulerDecision decision) {
        return proxy(() -> backendService.sessionChangePolicy(token(authorization), decision));
    }

    @GetMapping("/api/session/ruler-stats")
    @ResponseBody
    public ResponseEntity<?> getSessionRulerStats(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return proxy(() -> backendService.sessionRulerStats(token(authorization)));
    }

    /**
     * Run a backend call and translate failures so the browser sees the backend's status.
     * Without this, a backend 401 (missing/invalid/expired/revoked token) would surface from the
     * proxy as a 500 and the client's `status === 401` re-login handling would never fire.
     */
    private ResponseEntity<?> proxy(java.util.function.Supplier<?> call) {
        try {
            return ResponseEntity.ok(call.get());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return passthrough(e);
        } catch (RestClientException e) {
            return unavailable(e);
        }
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private ResponseEntity<Map<String, Object>> passthrough(org.springframework.web.client.RestClientResponseException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", e.getStatusText());
        error.put("message", e.getResponseBodyAsString());
        error.put("status", e.getStatusCode().value());
        return ResponseEntity.status(e.getStatusCode()).body(error);
    }

    private ResponseEntity<Map<String, Object>> unavailable(RestClientException e) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Could not connect to backend");
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }
}
