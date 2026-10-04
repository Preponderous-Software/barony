package com.barony.backend.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Issues, reads and clears the guest cookie. It carries a random, server-issued guest token and is
 * deliberately a different cookie from the account cookie ({@link AuthCookies#COOKIE_NAME}), so a
 * guest token can never be presented as an account token or the reverse. HttpOnly, SameSite=Lax and
 * (in production) Secure, like the account cookie.
 */
@Component
public class GuestCookies {

    public static final String COOKIE_NAME = "barony_guest";

    /** 32 random bytes, base64url without padding. Anything else is not a guest token. */
    private static final Pattern TOKEN_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    /** The cookie outlives the idle window; the server-side idle prune is the real expiry. */
    private static final Duration MAX_AGE = Duration.ofDays(400);

    private final boolean secure;

    public GuestCookies(@Value("${auth.cookie.secure:false}") boolean secure) {
        this.secure = secure;
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(MAX_AGE).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    /** The guest token from the request, if present and well formed. */
    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return isWellFormed(value) ? Optional.of(value) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    static boolean isWellFormed(String token) {
        return token != null && TOKEN_SHAPE.matcher(token).matches();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
    }
}
