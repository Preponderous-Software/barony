package com.barony.backend.controller;

import com.barony.backend.model.GameState;
import com.barony.backend.model.Session;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GameService;
import com.barony.backend.service.GuestCookies;
import com.barony.backend.service.GuestSessionService;
import com.barony.backend.service.PreferencesService;
import com.barony.backend.service.RefreshSessions;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UnreadableSaveException;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An account (or a guest) whose saved game cannot be read: every endpoint that needs the game
 * answers 409 {@code {"error": "unreadable-save"}} and saves nothing, and only the confirmed
 * start-fresh call gives the player a new game.
 */
@WebMvcTest(GameController.class)
@Import({AuthCookies.class, RefreshSessions.class, GuestCookies.class, GameService.class})
class GameControllerUnreadableSaveTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SessionService sessionService;

    @MockBean
    private UserAuthClient userAuthClient;

    @MockBean
    private PreferencesService preferencesService;

    @MockBean
    private GuestSessionService guestSessionService;

    @BeforeEach
    void anAccountWithAnUnreadableSave() {
        when(userAuthClient.validate("good")).thenReturn(Optional.of("alice"));
        when(sessionService.getOrCreateSession("alice")).thenThrow(new UnreadableSaveException("alice"));
    }

    private static Cookie account() {
        return new Cookie("barony_token", "good");
    }

    @Test
    void everyGameEndpointAnswersUnreadableSaveAndSavesNothing() throws Exception {
        List<RequestBuilder> calls = List.of(
                get("/api/session/state").cookie(account()),
                post("/api/session/tick").cookie(account()),
                post("/api/session/reset").cookie(account()),
                post("/api/session/command").cookie(account()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MOVE\",\"armyId\":1,\"targetX\":1,\"targetY\":1}"),
                post("/api/session/battle").cookie(account()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"battleId\":1,\"result\":\"AUTO\"}"),
                post("/api/session/field-battles").cookie(account()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"),
                post("/api/session/decision").cookie(account()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\",\"choice\":\"BALANCED\"}"),
                get("/api/session/ruler-stats").cookie(account()),
                get("/api/session/runs").cookie(account()),
                get("/api/session/preferences").cookie(account()),
                put("/api/session/preferences").cookie(account()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"));
        for (RequestBuilder call : calls) {
            mockMvc.perform(call)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("unreadable-save"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(UnreadableSaveHandler.MESSAGE));
        }
        verify(sessionService, never()).save(any());
        verify(sessionService, never()).startFresh(anyString());
        verify(preferencesService, never()).save(anyString(), any());
    }

    @Test
    void startFreshGivesTheConfirmedPlayerANewGame() throws Exception {
        GameState fresh = new GameState(5, 5);
        fresh.setTickCount(0);
        when(sessionService.startFresh("alice")).thenReturn(new Session("alice", fresh));

        mockMvc.perform(post("/api/session/start-fresh").cookie(account()).header("X-Barony-Player", "account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(0))
                .andExpect(jsonPath("$.fieldBattles").value(true));
        verify(sessionService).startFresh("alice");
    }

    @Test
    void startFreshNeedsAnAccountOrAGuest() throws Exception {
        mockMvc.perform(post("/api/session/start-fresh"))
                .andExpect(status().isUnauthorized());
        when(userAuthClient.validate("revoked")).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/session/start-fresh").cookie(new Cookie("barony_token", "revoked")))
                .andExpect(status().isUnauthorized());
        // An unknown or pruned guest has nothing to start fresh from.
        mockMvc.perform(post("/api/session/start-fresh").header("X-Barony-Player", "guest")
                        .cookie(guest()))
                .andExpect(status().isUnauthorized());
        verify(sessionService, never()).startFresh(anyString());
    }

    private static final String GUEST_TOKEN = "g".repeat(43);

    private static Cookie guest() {
        return new Cookie(GuestCookies.COOKIE_NAME, GUEST_TOKEN);
    }

    @Test
    void everyGameEndpointAnswersUnreadableSaveForAGuestAndSavesNothing() throws Exception {
        when(guestSessionService.find(GUEST_TOKEN)).thenThrow(new UnreadableSaveException("guest gggggggg…"));
        List<RequestBuilder> calls = List.of(
                get("/api/session/state").cookie(guest()).header("X-Barony-Player", "guest"),
                get("/api/session/state").cookie(guest()),
                post("/api/session/tick").cookie(guest()).header("X-Barony-Player", "guest"),
                post("/api/session/reset").cookie(guest()).header("X-Barony-Player", "guest"),
                post("/api/session/command").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MOVE\",\"armyId\":1,\"targetX\":1,\"targetY\":1}"),
                post("/api/session/battle").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"battleId\":1,\"result\":\"AUTO\"}"),
                post("/api/session/field-battles").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"),
                post("/api/session/decision").cookie(guest()).header("X-Barony-Player", "guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\",\"choice\":\"BALANCED\"}"),
                get("/api/session/ruler-stats").cookie(guest()).header("X-Barony-Player", "guest"),
                get("/api/session/runs").cookie(guest()).header("X-Barony-Player", "guest"));
        for (RequestBuilder call : calls) {
            mockMvc.perform(call)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("unreadable-save"))
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value(UnreadableSaveHandler.MESSAGE));
        }
        verify(guestSessionService, never()).save(any());
        verify(guestSessionService, never()).startFresh(anyString());
        verify(sessionService, never()).save(any());
    }

    @Test
    void startFreshGivesTheConfirmedGuestANewGame() throws Exception {
        GameState fresh = new GameState(5, 5);
        fresh.setTickCount(0);
        when(guestSessionService.startFresh(GUEST_TOKEN)).thenReturn(Optional.of(new Session("k", fresh)));

        mockMvc.perform(post("/api/session/start-fresh").cookie(guest()).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(0))
                .andExpect(jsonPath("$.fieldBattles").value(true));
        // Without the header a request with only a guest cookie is a guest too, as on every endpoint.
        mockMvc.perform(post("/api/session/start-fresh").cookie(guest()))
                .andExpect(status().isOk());
        verify(guestSessionService, org.mockito.Mockito.times(2)).startFresh(GUEST_TOKEN);
        verify(sessionService, never()).startFresh(anyString());
    }

    @Test
    void aGuestHeaderNeverStartsAnAccountFresh() throws Exception {
        when(guestSessionService.startFresh(GUEST_TOKEN)).thenReturn(Optional.of(new Session("k", new GameState(5, 5))));

        mockMvc.perform(post("/api/session/start-fresh").cookie(account(), guest()).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk());

        verify(sessionService, never()).startFresh(anyString());
        verify(guestSessionService).startFresh(GUEST_TOKEN);
    }
}
