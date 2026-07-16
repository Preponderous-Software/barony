package com.barony.backend.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Issues, reads, and clears the auth cookie that carries the UserAuth-issued JWT.
 *
 * The token lives in an HttpOnly cookie so browser JavaScript can never read it — an XSS can no
 * longer exfiltrate it the way it could from localStorage. {@code Secure} is configurable
 * ({@code auth.cookie.secure}, true in production over HTTPS, false for local HTTP dev) and
 * {@code SameSite=Lax} keeps it from riding cross-site requests while still working for the
 * same-site login → game navigation.
 */
@Component
public class AuthCookies {

    public static final String COOKIE_NAME = "barony_token";

    private final boolean secure;

    public AuthCookies(@Value("${auth.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    /**
     * Build the auth cookie for a freshly issued token. The cookie lifetime mirrors the token's
     * own expiry when {@code expiresAt} parses; otherwise it is a session cookie (cleared on
     * browser close), which is never longer-lived than the JWT the server will still reject.
     */
    public ResponseCookie issue(String token, String expiresAt) {
        ResponseCookie.ResponseCookieBuilder builder = baseBuilder(token)
                .maxAge(maxAgeFrom(expiresAt));
        return builder.build();
    }

    /** Build a cookie that immediately expires the auth cookie (used on logout). */
    public ResponseCookie clear() {
        return baseBuilder("").maxAge(0).build();
    }

    /** Read the token from the request's auth cookie, if present and non-blank. */
    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
    }

    private Duration maxAgeFrom(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return Duration.ofSeconds(-1); // session cookie
        }
        try {
            long seconds = Duration.between(Instant.now(), Instant.parse(expiresAt)).getSeconds();
            return seconds > 0 ? Duration.ofSeconds(seconds) : Duration.ofSeconds(-1);
        } catch (DateTimeParseException e) {
            return Duration.ofSeconds(-1); // session cookie on unparseable expiry
        }
    }
}
