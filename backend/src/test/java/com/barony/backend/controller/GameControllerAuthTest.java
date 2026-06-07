package com.barony.backend.controller;

import com.barony.backend.model.GameState;
import com.barony.backend.model.Session;
import com.barony.backend.service.GameService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that the authenticated game endpoints reject missing/invalid/revoked tokens and
 * accept a valid UserAuth-issued token. Token validation is delegated to {@link UserAuthClient},
 * which is mocked here; a returned-empty Optional represents an invalid, expired, or revoked token.
 */
@WebMvcTest(GameController.class)
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
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/session/state"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userAuthClient);
    }

    @Test
    void rejectsInvalidOrRevokedToken() throws Exception {
        when(userAuthClient.validate("revoked")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/session/state").header("Authorization", "Bearer revoked"))
                .andExpect(status().isUnauthorized());

        verify(sessionService, never()).getOrCreateSession(anyString());
    }

    @Test
    void acceptsValidTokenAndScopesStateToUser() throws Exception {
        Session session = new Session("alice", new GameState(5, 5));
        when(userAuthClient.validate("good")).thenReturn(Optional.of("alice"));
        when(sessionService.getOrCreateSession("alice")).thenReturn(session);
        when(gameService.getState()).thenReturn(session.getGameState());

        mockMvc.perform(get("/api/session/state").header("Authorization", "Bearer good"))
                .andExpect(status().isOk());

        verify(userAuthClient).validate("good");
        verify(sessionService).getOrCreateSession("alice");
    }
}
