package com.barony.backend.controller;

import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.RefreshSessions;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSessionFilterTest {

    private final AuthCookies cookies = new AuthCookies(true, 30);
    private RefreshSessions sessions;
    private AccountSessionFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        sessions = mock(RefreshSessions.class);
        filter = new AccountSessionFilter(cookies, sessions);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    private static String jwtExpiringIn(long seconds) {
        String claims = "{\"exp\":" + Instant.now().plusSeconds(seconds).getEpochSecond() + "}";
        return "h." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".s";
    }

    private static MockHttpServletRequest request(String path, Cookie... sent) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setCookies(sent);
        return request;
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, response, chain);
    }

    private List<String> setCookies() {
        return response.getHeaders("Set-Cookie");
    }

    @Test
    void aFreshAccessTokenIsLeftAlone() throws Exception {
        run(request("/api/session/state",
                new Cookie("barony_token", jwtExpiringIn(600)), new Cookie("barony_refresh", "rt")));

        verifyNoInteractions(sessions);
        assertNotNull(chain.getRequest());
    }

    @Test
    void noRefreshCookieIsLeftAlone() throws Exception {
        run(request("/api/session/state"));

        verifyNoInteractions(sessions);
        assertNotNull(chain.getRequest());
    }

    @Test
    void aTokenAboutToExpireIsRenewedAndTheControllerSeesTheNewOne() throws Exception {
        when(sessions.exchange("rt")).thenReturn(Optional.of(new UserAuthClient.RefreshedTokens("new", null, "next")));

        run(request("/api/session/state", new Cookie("barony_guest", "g"),
                new Cookie("barony_token", jwtExpiringIn(5)), new Cookie("barony_refresh", "rt")));

        assertEquals(2, setCookies().size());
        assertTrue(setCookies().get(0).startsWith("barony_token=new;"));
        assertTrue(setCookies().get(1).startsWith("barony_refresh=next;"));
        assertTrue(setCookies().stream().allMatch(c -> c.contains("HttpOnly") && c.contains("Secure") && c.contains("SameSite=Lax")));
        Cookie[] seen = ((HttpServletRequest) chain.getRequest()).getCookies();
        assertEquals(1, java.util.Arrays.stream(seen).filter(c -> c.getName().equals("barony_token")).count());
        assertEquals(Optional.of("new"), cookies.read((HttpServletRequest) chain.getRequest()));
        assertTrue(java.util.Arrays.stream(seen).anyMatch(c -> c.getName().equals("barony_guest")));
    }

    @Test
    void aRejectedRefreshTokenIsClearedAndTheRequestContinuesToItsUsualAnswer() throws Exception {
        when(sessions.exchange("rt")).thenReturn(Optional.empty());

        run(request("/api/session/state", new Cookie("barony_refresh", "rt")));

        assertEquals(1, setCookies().size());
        assertTrue(setCookies().get(0).startsWith("barony_refresh=;"));
        assertTrue(setCookies().get(0).contains("Max-Age=0"));
        assertNotNull(chain.getRequest());
    }

    @Test
    void anOutageWithNoUsableAccessTokenIs503AndKeepsTheCookies() throws Exception {
        when(sessions.exchange("rt")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        run(request("/api/session/state",
                new Cookie("barony_token", jwtExpiringIn(-5)), new Cookie("barony_refresh", "rt")));

        assertEquals(503, response.getStatus());
        assertTrue(setCookies().isEmpty());
        assertNull(chain.getRequest());
    }

    @Test
    void anOutageWhileTheAccessTokenStillWorksDoesNotInterruptPlay() throws Exception {
        when(sessions.exchange("rt")).thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        run(request("/api/session/state",
                new Cookie("barony_token", jwtExpiringIn(10)), new Cookie("barony_refresh", "rt")));

        assertEquals(200, response.getStatus());
        assertTrue(setCookies().isEmpty());
        assertNotNull(chain.getRequest());
    }

    @Test
    void anAccessTokenWithAnOutOfRangeExpiryIsLeftForUserAuthToJudge() throws Exception {
        String token = "h." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"exp\":1e30}".getBytes(StandardCharsets.UTF_8)) + ".s";

        run(request("/api/session/state",
                new Cookie("barony_token", token), new Cookie("barony_refresh", "rt")));

        verifyNoInteractions(sessions);
        assertNotNull(chain.getRequest());
    }

    @Test
    void guestAndPublicRequestsAreLeftAlone() throws Exception {
        MockHttpServletRequest guest = request("/api/session/state", new Cookie("barony_refresh", "rt"));
        guest.addHeader(GameController.PLAYER_MODE_HEADER, "guest");
        run(guest);
        chain = new MockFilterChain();
        run(request("/api/guest", new Cookie("barony_refresh", "rt")));

        verifyNoInteractions(sessions);
    }

    @Test
    void claimingAGuestGameRenewsTheSessionItNeeds() throws Exception {
        when(sessions.exchange("rt")).thenReturn(Optional.of(new UserAuthClient.RefreshedTokens("new", null, "next")));

        run(request("/api/guest/claim", new Cookie("barony_refresh", "rt")));

        assertEquals(Optional.of("new"), cookies.read((HttpServletRequest) chain.getRequest()));
    }
}
