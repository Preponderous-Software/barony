package com.barony.backend.controller;

import com.barony.backend.model.GameState;
import com.barony.backend.model.Session;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GameService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the authenticated game endpoints accept a valid token from the HttpOnly cookie (and the
 * Bearer fallback) and reject missing/invalid/revoked ones. Token validation is delegated to
 * {@link UserAuthClient}, mocked here; a returned-empty Optional means invalid/expired/revoked.
 */
@WebMvcTest(GameController.class)
@Import(AuthCookies.class)
class GameControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameService gameService;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private UserAuthClient userAuthClient;

    @Test
    void rejectsRequestWithoutCookieOrHeader() throws Exception {
        mockMvc.perform(get("/api/session/state"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAuthClient);
    }

    @Test
    void rejectsInvalidOrRevokedCookieToken() throws Exception {
        when(userAuthClient.validate("revoked")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/session/state").cookie(new Cookie("barony_token", "revoked")))
                .andExpect(status().isUnauthorized());

        verify(sessionService, never()).getOrCreateSession(anyString());
    }

    @Test
    void acceptsValidCookieTokenAndScopesStateToUser() throws Exception {
        Session session = new Session("alice", new GameState(5, 5));
        when(userAuthClient.validate("good")).thenReturn(Optional.of("alice"));
        when(sessionService.getOrCreateSession("alice")).thenReturn(session);
        when(gameService.getState()).thenReturn(session.getGameState());

        mockMvc.perform(get("/api/session/state").cookie(new Cookie("barony_token", "good")))
                .andExpect(status().isOk());

        verify(userAuthClient).validate("good");
        verify(sessionService).getOrCreateSession("alice");
    }

    @Test
    void acceptsBearerTokenFallback() throws Exception {
        Session session = new Session("bob", new GameState(5, 5));
        when(userAuthClient.validate("hdr")).thenReturn(Optional.of("bob"));
        when(sessionService.getOrCreateSession("bob")).thenReturn(session);
        when(gameService.getState()).thenReturn(session.getGameState());

        mockMvc.perform(get("/api/session/state").header("Authorization", "Bearer hdr"))
                .andExpect(status().isOk());

        verify(userAuthClient).validate("hdr");
    }
}
