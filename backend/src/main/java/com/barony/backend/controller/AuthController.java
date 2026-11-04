package com.barony.backend.controller;

import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.JwtExpiry;
import com.barony.backend.service.RefreshSessions;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentication endpoints. These proxy to the standalone UserAuth service so Barony never
 * stores credentials itself. On login the UserAuth-issued JWT and refresh token are placed in
 * HttpOnly cookies (see {@link AuthCookies}) rather than returned to JavaScript, so an XSS cannot
 * read them; the browser then sends them automatically and game endpoints validate the JWT on
 * every request.
 */
@RestController
@CrossOrigin(origins = {"http://localhost:8080", "http://127.0.0.1:8080", "http://localhost:3000", "http://127.0.0.1:3000"})
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAuthClient userAuthClient;
    private final AuthCookies authCookies;
    private final RefreshSessions refreshSessions;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        requireCredentials(username, password);
        Map<String, Object> created = userAuthClient.register(username.trim(), password);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> request,
                                     HttpServletResponse response) {
        String username = request.get("username");
        String password = request.get("password");
        requireCredentials(username, password);

        Map<String, Object> auth = userAuthClient.login(username.trim(), password);
        String token = stringValue(auth.get("token"));
        String expiresAt = stringValue(auth.get("expiresAt"));
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Authentication service did not return a token");
        }

        // Tokens go into HttpOnly cookies, never the response body.
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.issue(token, expiresAt).toString());
        String refreshToken = stringValue(auth.get("refreshToken"));
        response.addHeader(HttpHeaders.SET_COOKIE, (refreshToken == null
                ? authCookies.clearRefresh() : authCookies.issueRefresh(refreshToken)).toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username.trim());
        if (expiresAt != null) {
            body.put("expiresAt", expiresAt);
        }
        return body;
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletRequest request, HttpServletResponse response) {
        // Revoke whatever token the request carries (cookie preferred, bearer accepted), then clear
        // the cookies. UserAuth's logout takes a live access token and ends the refresh chain with
        // it, so a browser left with only a refresh cookie exchanges that first. If UserAuth cannot
        // be reached this fails rather than clearing cookies over a session that is still alive.
        String token = authCookies.read(request)
                .filter(accessToken -> !JwtExpiry.isExpired(accessToken))
                .orElseGet(() -> bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION)));
        if (token == null) {
            token = authCookies.readRefresh(request)
                    .flatMap(refreshSessions::exchange)
                    .map(UserAuthClient.RefreshedTokens::token)
                    .orElse(null);
        }
        if (token != null) {
            userAuthClient.logout(token);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.clear().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.clearRefresh().toString());
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

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorization.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
