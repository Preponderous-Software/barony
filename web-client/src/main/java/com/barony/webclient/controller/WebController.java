package com.barony.webclient.controller;

import com.barony.webclient.model.*;
import com.barony.webclient.service.BackendService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
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
        // Game page is loaded and the JavaScript handles redirecting to login on a 401.
        return "game";
    }

    // Auth proxy endpoints (forward to the backend, which talks to UserAuth). On login/logout the
    // backend sets/clears the HttpOnly auth cookie; we relay that Set-Cookie to the browser.
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
    public ResponseEntity<?> proxyLogin(@RequestBody Map<String, String> request,
                                        HttpServletResponse response) {
        try {
            ResponseEntity<Map> backend = backendService.login(request);
            relaySetCookie(backend, response);
            return ResponseEntity.status(backend.getStatusCode()).body(backend.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return passthrough(e);
        } catch (RestClientException e) {
            return unavailable(e);
        }
    }

    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseEntity<?> proxyLogout(HttpServletRequest request, HttpServletResponse response) {
        try {
            ResponseEntity<Map> backend = backendService.logout(cookie(request));
            relaySetCookie(backend, response);
            return ResponseEntity.status(backend.getStatusCode()).body(backend.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            return passthrough(e);
        } catch (RestClientException e) {
            return unavailable(e);
        }
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

    // Authenticated, per-user proxy endpoints (forward the auth cookie to the backend)
    @GetMapping("/api/session/state")
    @ResponseBody
    public ResponseEntity<?> getSessionState(HttpServletRequest request) {
        return proxy(() -> backendService.getSessionState(cookie(request)));
    }

    @PostMapping("/api/session/tick")
    @ResponseBody
    public ResponseEntity<?> sessionTick(HttpServletRequest request) {
        return proxy(() -> backendService.sessionTick(cookie(request)));
    }

    @PostMapping("/api/session/command")
    @ResponseBody
    public ResponseEntity<?> sessionCommand(HttpServletRequest request, @RequestBody Command command) {
        return proxy(() -> backendService.sessionCommand(cookie(request), command));
    }

    @PostMapping("/api/session/reset")
    @ResponseBody
    public ResponseEntity<?> sessionReset(HttpServletRequest request) {
        return proxy(() -> backendService.sessionReset(cookie(request)));
    }

    @PostMapping("/api/session/decision")
    @ResponseBody
    public ResponseEntity<?> sessionDecision(HttpServletRequest request, @RequestBody RulerDecision decision) {
        return proxy(() -> backendService.sessionChangePolicy(cookie(request), decision));
    }

    @GetMapping("/api/session/ruler-stats")
    @ResponseBody
    public ResponseEntity<?> getSessionRulerStats(HttpServletRequest request) {
        return proxy(() -> backendService.sessionRulerStats(cookie(request)));
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

    /** The browser's Cookie header (carries the HttpOnly auth cookie), forwarded to the backend. */
    private String cookie(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.COOKIE);
    }

    /** Relay the backend's Set-Cookie header(s) (login sets, logout clears) back to the browser. */
    private void relaySetCookie(ResponseEntity<?> backendResponse, HttpServletResponse response) {
        List<String> setCookies = backendResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        if (setCookies != null) {
            for (String value : setCookies) {
                response.addHeader(HttpHeaders.SET_COOKIE, value);
            }
        }
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
