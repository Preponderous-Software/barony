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
 * Issues, reads, and clears the auth cookies: one carries the UserAuth-issued JWT and the other the
 * refresh token that renews it (see {@code AccountSessionFilter}).
 *
 * Both tokens live in HttpOnly cookies so browser JavaScript can never read it — an XSS can no
 * longer exfiltrate it the way it could from localStorage. {@code Secure} is configurable
 * ({@code auth.cookie.secure}, true in production over HTTPS, false for local HTTP dev) and
 * {@code SameSite=Lax} keeps it from riding cross-site requests while still working for the
 * same-site login → game navigation.
 */
@Component
public class AuthCookies {

    public static final String COOKIE_NAME = "barony_token";
    public static final String REFRESH_COOKIE_NAME = "barony_refresh";

    private final boolean secure;
    private final Duration refreshLifetime;

    public AuthCookies(@Value("${auth.cookie.secure:false}") boolean secure,
                       @Value("${auth.refresh.cookie-days:30}") long refreshCookieDays) {
        this.secure = secure;
        this.refreshLifetime = Duration.ofDays(refreshCookieDays);
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

    /** Build the refresh cookie. UserAuth reports no expiry for this token, so the lifetime is configured. */
    public ResponseCookie issueRefresh(String refreshToken) {
        return baseBuilder(REFRESH_COOKIE_NAME, refreshToken).maxAge(refreshLifetime).build();
    }

    /** Build a cookie that immediately expires the auth cookie (used on logout). */
    public ResponseCookie clear() {
        return baseBuilder("").maxAge(0).build();
    }

    /** Build a cookie that immediately expires the refresh cookie. */
    public ResponseCookie clearRefresh() {
        return baseBuilder(REFRESH_COOKIE_NAME, "").maxAge(0).build();
    }

    /** Read the token from the request's auth cookie, if present and non-blank. */
    public Optional<String> read(HttpServletRequest request) {
        return readCookie(request, COOKIE_NAME);
    }

    /** Read the refresh token from the request's refresh cookie, if present and non-blank. */
    public Optional<String> readRefresh(HttpServletRequest request) {
        return readCookie(request, REFRESH_COOKIE_NAME);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        return baseBuilder(COOKIE_NAME, value);
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String name, String value) {
        return ResponseCookie.from(name, value)
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
