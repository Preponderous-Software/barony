package com.barony.backend.controller;

import com.barony.backend.service.UserAuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Authentication endpoints. These proxy to the standalone UserAuth service so Barony never
 * stores credentials itself: registration and login go to UserAuth, login returns a signed JWT,
 * and logout revokes the token. Game endpoints (see {@code GameController}) then authenticate
 * each request by validating the bearer token with UserAuth.
 */
@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000"})
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAuthClient userAuthClient;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        requireCredentials(username, password);
        Map<String, Object> created = userAuthClient.register(username.trim(), password);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        requireCredentials(username, password);
        return userAuthClient.login(username.trim(), password);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = bearerToken(authorization);
        if (token == null) {
            // Nothing to revoke: report the missing token rather than a misleading success.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "A bearer token is required to log out");
        }
        userAuthClient.logout(token);
        return Map.of("message", "logged out");
    }

    private void requireCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return authorization.substring(7).trim();
    }
}
