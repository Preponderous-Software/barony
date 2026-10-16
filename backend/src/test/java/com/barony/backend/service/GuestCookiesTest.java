package com.barony.backend.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GuestCookiesTest {

    /** Same shape GuestSessionService issues: 32 bytes, base64url without padding. */
    private static final String TOKEN = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    @Test
    void issuedCookieIsHttpOnlyLaxAndCarriesTheToken() {
        ResponseCookie cookie = new GuestCookies(true).issue(TOKEN);
        assertEquals(GuestCookies.COOKIE_NAME, cookie.getName());
        assertEquals(TOKEN, cookie.getValue());
        assertTrue(cookie.isHttpOnly(), "cookie must be HttpOnly so JS can't read the token");
        assertTrue(cookie.isSecure(), "secure=true should mark the cookie Secure");
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/", cookie.getPath());
    }

    @Test
    void secureFlagIsConfigurable() {
        assertFalse(new GuestCookies(false).issue(TOKEN).isSecure());
    }

    @Test
    void issuedCookieOutlivesTheIdleWindow() {
        assertEquals(Duration.ofDays(400), new GuestCookies(false).issue(TOKEN).getMaxAge());
    }

    @Test
    void guestCookieIsDistinctFromTheAccountCookie() {
        assertNotEquals(AuthCookies.COOKIE_NAME, GuestCookies.COOKIE_NAME);
    }

    @Test
    void clearCookieExpiresImmediately() {
        ResponseCookie cookie = new GuestCookies(false).clear();
        assertEquals(GuestCookies.COOKIE_NAME, cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0, cookie.getMaxAge().getSeconds());
        assertEquals("/", cookie.getPath(), "clear must target the same path the cookie was issued on");
    }

    @Test
    void readReturnsWellFormedTokenFromCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(GuestCookies.COOKIE_NAME, TOKEN));
        assertEquals(Optional.of(TOKEN), new GuestCookies(false).read(request));
    }

    @Test
    void readIsEmptyWithoutTheGuestCookie() {
        GuestCookies cookies = new GuestCookies(false);
        assertTrue(cookies.read(new MockHttpServletRequest()).isEmpty(), "no cookies -> empty");

        MockHttpServletRequest accountOnly = new MockHttpServletRequest();
        accountOnly.setCookies(new Cookie(AuthCookies.COOKIE_NAME, TOKEN));
        assertTrue(cookies.read(accountOnly).isEmpty(), "account cookie is never read as a guest token");
    }

    @Test
    void readRejectsMalformedTokens() {
        GuestCookies cookies = new GuestCookies(false);
        for (String value : new String[] {"", "short", TOKEN + "A", TOKEN.substring(1) + "=", TOKEN.substring(1) + "+"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie(GuestCookies.COOKIE_NAME, value));
            assertTrue(cookies.read(request).isEmpty(), "malformed token should be ignored: '" + value + "'");
        }
    }

    @Test
    void readUsesOnlyTheFirstGuestCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(GuestCookies.COOKIE_NAME, "short"), new Cookie(GuestCookies.COOKIE_NAME, TOKEN));
        assertTrue(new GuestCookies(false).read(request).isEmpty(),
                "a malformed first guest cookie is not skipped in favour of a later one");
    }

    @Test
    void isWellFormedAcceptsOnlyTheIssuedShape() {
        assertTrue(GuestCookies.isWellFormed(TOKEN));
        assertTrue(GuestCookies.isWellFormed("-_" + "a".repeat(41)));
        assertFalse(GuestCookies.isWellFormed(null));
        assertFalse(GuestCookies.isWellFormed("a".repeat(42)));
        assertFalse(GuestCookies.isWellFormed("a".repeat(44)));
        assertFalse(GuestCookies.isWellFormed("a".repeat(42) + "/"));
    }
}
