package com.barony.backend.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthCookiesTest {

    @Test
    void issuedCookieIsHttpOnlyLaxAndCarriesTheToken() {
        ResponseCookie cookie = new AuthCookies(true).issue("jwt-abc", null);
        assertEquals(AuthCookies.COOKIE_NAME, cookie.getName());
        assertEquals("jwt-abc", cookie.getValue());
        assertTrue(cookie.isHttpOnly(), "cookie must be HttpOnly so JS can't read the token");
        assertTrue(cookie.isSecure(), "secure=true should mark the cookie Secure");
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/", cookie.getPath());
    }

    @Test
    void secureFlagIsConfigurable() {
        assertFalse(new AuthCookies(false).issue("t", null).isSecure());
    }

    @Test
    void maxAgeTracksTokenExpiry() {
        String expiresAt = Instant.now().plus(30, ChronoUnit.MINUTES).toString();
        ResponseCookie cookie = new AuthCookies(false).issue("t", expiresAt);
        long seconds = cookie.getMaxAge().getSeconds();
        assertTrue(seconds > 25 * 60 && seconds <= 30 * 60, "max-age should mirror the token expiry, was " + seconds);
    }

    @Test
    void unparseableOrMissingExpiryYieldsSessionCookie() {
        assertEquals(-1, new AuthCookies(false).issue("t", null).getMaxAge().getSeconds());
        assertEquals(-1, new AuthCookies(false).issue("t", "not-a-date").getMaxAge().getSeconds());
    }

    @Test
    void clearCookieExpiresImmediately() {
        ResponseCookie cookie = new AuthCookies(false).clear();
        assertEquals(AuthCookies.COOKIE_NAME, cookie.getName());
        assertEquals(0, cookie.getMaxAge().getSeconds());
    }

    @Test
    void readReturnsTokenFromCookieOrEmpty() {
        AuthCookies cookies = new AuthCookies(false);

        MockHttpServletRequest withCookie = new MockHttpServletRequest();
        withCookie.setCookies(new Cookie(AuthCookies.COOKIE_NAME, "jwt-abc"));
        assertEquals(Optional.of("jwt-abc"), cookies.read(withCookie));

        assertTrue(cookies.read(new MockHttpServletRequest()).isEmpty(), "no cookies -> empty");

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie(AuthCookies.COOKIE_NAME, ""));
        assertTrue(cookies.read(blank).isEmpty(), "blank cookie value -> empty");
    }
}
