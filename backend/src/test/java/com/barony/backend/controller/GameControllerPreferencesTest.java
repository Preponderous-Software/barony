package com.barony.backend.controller;

import com.barony.backend.model.GameState;
import com.barony.backend.model.Session;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GameService;
import com.barony.backend.service.PreferencesService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the per-account preference endpoints: they are scoped to the authenticated player (so one
 * player can neither read nor overwrite another's arrangement) and a rejected payload comes back as
 * a 400 rather than a 500.
 */
@WebMvcTest(GameController.class)
@Import(AuthCookies.class)
class GameControllerPreferencesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GameService gameService;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private UserAuthClient userAuthClient;

    @MockBean
    private PreferencesService preferencesService;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/session/preferences"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/session/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(preferencesService);
    }

    @Test
    void returnsTheAuthenticatedPlayersPreferences() throws Exception {
        authenticateAs("good", "alice");
        when(preferencesService.load("alice"))
                .thenReturn(Map.of("settings", Map.of("theme", "classic")));

        mockMvc.perform(get("/api/session/preferences").cookie(new Cookie("barony_token", "good")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("classic"));
    }

    @Test
    void storesPreferencesAgainstTheAuthenticatedPlayer() throws Exception {
        authenticateAs("good", "alice");
        when(preferencesService.save(anyString(), any())).thenAnswer(call -> call.getArgument(1));

        mockMvc.perform(put("/api/session/preferences")
                        .cookie(new Cookie("barony_token", "good"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"panelState\":{\"armies\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panelState.armies").value(false));

        verify(preferencesService).save("alice", Map.of("panelState", Map.of("armies", false)));
    }

    @Test
    void rejectedPreferencesComeBackAsBadRequest() throws Exception {
        authenticateAs("good", "alice");
        when(preferencesService.save(anyString(), any()))
                .thenThrow(new IllegalArgumentException("Preferences are too large: 9000 characters, limit 8192"));

        mockMvc.perform(put("/api/session/preferences")
                        .cookie(new Cookie("barony_token", "good"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{}}"))
                .andExpect(status().isBadRequest());
    }

    private void authenticateAs(String token, String username) {
        when(userAuthClient.validate(token)).thenReturn(Optional.of(username));
        when(sessionService.getOrCreateSession(username))
                .thenReturn(new Session(username, new GameState(5, 5)));
    }
}
