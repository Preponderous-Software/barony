package com.barony.backend.controller;

import com.barony.backend.model.GameState;
import com.barony.backend.model.RunRecord;
import com.barony.backend.model.Session;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GuestCookies;
import com.barony.backend.service.GuestRateLimiter;
import com.barony.backend.service.GuestSessionService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link GuestController} with its collaborators mocked. The end-to-end guest flows
 * (persistence, isolation from account tables) are covered by {@code GuestIsolationTest} and
 * {@code GuestClaimTest}; these pin the controller's own branching and status codes.
 */
@WebMvcTest(GuestController.class)
@Import({AuthCookies.class, GuestCookies.class})
class GuestControllerTest {

    /** A well-formed guest token: 43 base64url characters. */
    private static final String GUEST_TOKEN = "a".repeat(43);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GuestSessionService guestSessionService;

    @MockBean
    private GuestRateLimiter rateLimiter;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private UserAuthClient userAuthClient;

    private static Cookie guestCookie() {
        return new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN);
    }

    private static Cookie authCookie(String token) {
        return new Cookie(AuthCookies.COOKIE_NAME, token);
    }

    @Test
    void startResumesAnExistingGuestWithoutRateLimitingOrIssuingACookie() throws Exception {
        when(guestSessionService.find(GUEST_TOKEN)).thenReturn(Optional.of(new Session(null, new GameState())));

        mockMvc.perform(post("/api/guest").cookie(guestCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest").value(true))
                .andExpect(jsonPath("$.resumed").value(true))
                .andExpect(header().doesNotExist("Set-Cookie"));

        verifyNoInteractions(rateLimiter);
        verify(guestSessionService, never()).create();
    }

    @Test
    void startCreatesANewGuestAndSetsTheGuestCookie() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(guestSessionService.create()).thenReturn(
                new GuestSessionService.NewGuest(GUEST_TOKEN, new Session(null, new GameState())));

        mockMvc.perform(post("/api/guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guest").value(true))
                .andExpect(jsonPath("$.resumed").value(false))
                .andExpect(header().string("Set-Cookie", containsString(GuestCookies.COOKIE_NAME + "=" + GUEST_TOKEN)))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void startCreatesANewGuestWhenTheCookieNamesAnUnknownGuest() throws Exception {
        when(guestSessionService.find(GUEST_TOKEN)).thenReturn(Optional.empty());
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(guestSessionService.create()).thenReturn(
                new GuestSessionService.NewGuest("b".repeat(43), new Session(null, new GameState())));

        mockMvc.perform(post("/api/guest").cookie(guestCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumed").value(false))
                .andExpect(header().string("Set-Cookie", containsString(GuestCookies.COOKIE_NAME + "=" + "b".repeat(43))));
    }

    @Test
    void startReturns429WhenTheRateLimiterRefuses() throws Exception {
        when(rateLimiter.tryAcquire("10.1.1.1")).thenReturn(false);

        MvcResult result = mockMvc.perform(post("/api/guest").header("X-Forwarded-For", "10.1.1.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertEquals("Too many new guest games from here. Please wait a while, or create a free account.",
                reason(result));
        verify(guestSessionService, never()).create();
    }

    @Test
    void startReturns503WhenGuestCapacityIsFull() throws Exception {
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        when(guestSessionService.create()).thenThrow(new GuestSessionService.GuestCapacityException());

        MvcResult result = mockMvc.perform(post("/api/guest"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertEquals(new GuestSessionService.GuestCapacityException().getMessage(), reason(result));
    }

    @Test
    void claimReturns401WithoutAnAuthCookie() throws Exception {
        mockMvc.perform(post("/api/guest/claim").cookie(guestCookie()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAuthClient, sessionService);
    }

    @Test
    void claimReturns401WhenTheAuthTokenIsInvalid() throws Exception {
        when(userAuthClient.validate("revoked")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/guest/claim").cookie(guestCookie(), authCookie("revoked")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sessionService);
        verify(guestSessionService, never()).find(anyString());
    }

    @Test
    void claimReturns404WhenThereIsNoGuestGame() throws Exception {
        when(userAuthClient.validate("jwt")).thenReturn(Optional.of("alice"));

        mockMvc.perform(post("/api/guest/claim").cookie(authCookie("jwt")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(sessionService);
    }

    @Test
    void claimReturns409AndKeepsTheGuestWhenTheAccountAlreadyHasAGame() throws Exception {
        Session guest = new Session(null, new GameState());
        when(userAuthClient.validate("jwt")).thenReturn(Optional.of("alice"));
        when(guestSessionService.find(GUEST_TOKEN)).thenReturn(Optional.of(guest));
        when(guestSessionService.getRuns(guest)).thenReturn(List.of());
        when(sessionService.claimGuestGame(eq("alice"), any(), any())).thenReturn(false);

        mockMvc.perform(post("/api/guest/claim").cookie(guestCookie(), authCookie("jwt")))
                .andExpect(status().isConflict())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(guestSessionService, never()).delete(any());
    }

    @Test
    void claimMovesTheGuestGameDeletesTheGuestAndClearsTheGuestCookie() throws Exception {
        GameState state = new GameState();
        Session guest = new Session(null, state);
        List<RunRecord> runs = List.of();
        when(userAuthClient.validate("jwt")).thenReturn(Optional.of("alice"));
        when(guestSessionService.find(GUEST_TOKEN)).thenReturn(Optional.of(guest));
        when(guestSessionService.getRuns(guest)).thenReturn(runs);
        when(sessionService.claimGuestGame("alice", state, runs)).thenReturn(true);

        mockMvc.perform(post("/api/guest/claim").cookie(guestCookie(), authCookie("jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(header().string("Set-Cookie", containsString(GuestCookies.COOKIE_NAME + "=;")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(sessionService).claimGuestGame("alice", state, runs);
        verify(guestSessionService).delete(guest);
    }

    @Test
    void clientAddressUsesTheLastForwardedEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4,  10.9.9.9 ");

        assertEquals("10.9.9.9", GuestController.clientAddress(request));
    }

    @Test
    void clientAddressFallsBackToTheSocketAddressWithoutAForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertEquals("127.0.0.1", GuestController.clientAddress(request));
    }

    @Test
    void clientAddressFallsBackToTheSocketAddressForABlankForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "   ");

        assertEquals("127.0.0.1", GuestController.clientAddress(request));
    }

    @Test
    void clientAddressFallsBackToTheSocketAddressWhenTheLastEntryIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, ");

        assertEquals("127.0.0.1", GuestController.clientAddress(request));
    }

    private static String reason(MvcResult result) {
        return ((ResponseStatusException) result.getResolvedException()).getReason();
    }
}
