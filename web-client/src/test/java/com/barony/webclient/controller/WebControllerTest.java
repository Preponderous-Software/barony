package com.barony.webclient.controller;

import com.barony.webclient.model.Command;
import com.barony.webclient.model.GameState;
import com.barony.webclient.model.RulerStats;
import com.barony.webclient.model.RunHistory;
import com.barony.webclient.model.RunRecord;
import com.barony.webclient.service.BackendService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the web-client proxy passes the backend's status through to the browser instead of
 * collapsing backend errors into a 500. This matters for the per-player game endpoints: the game
 * page relies on receiving a real 401 to trigger its re-login flow.
 */
@WebMvcTest(WebController.class)
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BackendService backendService;

    @Test
    void sessionStatePassesBackend401ThroughToBrowser() throws Exception {
        when(backendService.getSessionState(any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                        "Unauthorized", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        mockMvc.perform(get("/api/session/state").header("Authorization", "Bearer revoked"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionTickReturns503WhenBackendUnreachable() throws Exception {
        when(backendService.sessionTick(any()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));

        mockMvc.perform(post("/api/session/tick"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void sessionRunsReturnsBackendRunHistoryToBrowser() throws Exception {
        RunRecord run = new RunRecord();
        run.setResult("WIN");
        run.setTurnsPlayed(42);
        RunHistory history = new RunHistory();
        history.setWins(2);
        history.setLosses(1);
        history.setRuns(List.of(run));
        when(backendService.sessionRuns(any())).thenReturn(history);

        mockMvc.perform(get("/api/session/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins").value(2))
                .andExpect(jsonPath("$.losses").value(1))
                .andExpect(jsonPath("$.runs[0].result").value("WIN"))
                .andExpect(jsonPath("$.runs[0].turnsPlayed").value(42));
    }

    @Test
    void sessionRunsPassesBackend401ThroughToBrowser() throws Exception {
        when(backendService.sessionRuns(any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                        "Unauthorized", HttpHeaders.EMPTY, new byte[0], null));

        mockMvc.perform(get("/api/session/runs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionPreferencesReturnsTheStoredArrangementToBrowser() throws Exception {
        when(backendService.sessionPreferences(any()))
                .thenReturn(Map.of("settings", Map.of("theme", "classic")));

        mockMvc.perform(get("/api/session/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.theme").value("classic"));
    }

    @Test
    void savingPreferencesForwardsTheBodyToTheBackend() throws Exception {
        when(backendService.saveSessionPreferences(any(), any()))
                .thenAnswer(call -> call.getArgument(1));

        mockMvc.perform(put("/api/session/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"panelState\":{\"armies\":false}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panelState.armies").value(false));

        verify(backendService).saveSessionPreferences(any(), eq(Map.of("panelState", Map.of("armies", false))));
    }

    @Test
    void savingPreferencesPassesBackend400ThroughToBrowser() throws Exception {
        when(backendService.saveSessionPreferences(any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,
                        "Bad Request", HttpHeaders.EMPTY, new byte[0], null));

        mockMvc.perform(put("/api/session/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginRelaysBackendSetCookieToBrowser() throws Exception {
        HttpHeaders backendHeaders = new HttpHeaders();
        backendHeaders.add(HttpHeaders.SET_COOKIE, "barony_token=jwt-abc; HttpOnly; Path=/");
        when(backendService.login(any()))
                .thenReturn(new ResponseEntity<>(Map.of("username", "alice"), backendHeaders, HttpStatus.OK));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(header().string("Set-Cookie", containsString("barony_token=jwt-abc")));
    }

    @Test
    void guestStartRelaysTheGuestCookieAndClaimConflictPassesThrough() throws Exception {
        HttpHeaders backendHeaders = new HttpHeaders();
        backendHeaders.add(HttpHeaders.SET_COOKIE, "barony_guest=tok; HttpOnly; Path=/");
        when(backendService.guest(any()))
                .thenReturn(new ResponseEntity<>(Map.of("guest", true), backendHeaders, HttpStatus.OK));
        mockMvc.perform(post("/api/guest"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("barony_guest=tok")));

        when(backendService.claimGuest(any())).thenThrow(
                org.springframework.web.client.HttpClientErrorException.create(HttpStatus.CONFLICT,
                        "Conflict", new HttpHeaders(), new byte[0], null));
        mockMvc.perform(post("/api/guest/claim")).andExpect(status().isConflict());
    }

    @Test
    void registerReturns201WithTheBackendBody() throws Exception {
        when(backendService.register(any())).thenReturn(Map.of("username", "alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));

        verify(backendService).register(Map.of("username", "alice", "password", "password123"));
    }

    @Test
    void registerPassesBackend409ThroughWithAnErrorBody() throws Exception {
        when(backendService.register(any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY,
                        "Username already taken".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Username already taken"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void loginReturns503WithAnErrorBodyWhenBackendUnreachable() throws Exception {
        when(backendService.login(any()))
                .thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Could not connect to backend"))
                .andExpect(jsonPath("$.message").value("connection refused"));
    }

    @Test
    void logoutForwardsTheCookieAndRelaysTheClearingSetCookie() throws Exception {
        HttpHeaders backendHeaders = new HttpHeaders();
        backendHeaders.add(HttpHeaders.SET_COOKIE, "barony_token=; Max-Age=0; HttpOnly; Path=/");
        when(backendService.logout(any()))
                .thenReturn(new ResponseEntity<>(Map.of("message", "Logged out"), backendHeaders, HttpStatus.OK));

        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out"))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(backendService).logout("barony_token=jwt-abc");
    }

    @Test
    void sessionCommandForwardsTheCookieAndTheCommand() throws Exception {
        GameState state = new GameState();
        state.setTickCount(7);
        when(backendService.sessionCommand(any(), any())).thenReturn(state);

        mockMvc.perform(post("/api/session/command")
                        .header(HttpHeaders.COOKIE, "barony_token=jwt-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MOVE\",\"armyId\":3,\"targetX\":4,\"targetY\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(7));

        ArgumentCaptor<Command> captor = ArgumentCaptor.forClass(Command.class);
        verify(backendService).sessionCommand(eq("barony_token=jwt-abc"), captor.capture());
        assertEquals("MOVE", captor.getValue().getType());
        assertEquals(3, captor.getValue().getArmyId());
        assertEquals(4, captor.getValue().getTargetX());
        assertEquals(5, captor.getValue().getTargetY());
    }

    @Test
    void sessionResetForwardsTheCookieAndReturnsTheFreshGame() throws Exception {
        when(backendService.sessionReset(any())).thenReturn(new GameState());

        mockMvc.perform(post("/api/session/reset").header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(0));

        verify(backendService).sessionReset("barony_token=jwt-abc");
    }

    @Test
    void sessionDecisionOnCooldownPassesBackend409Through() throws Exception {
        when(backendService.sessionChangePolicy(any(), any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT,
                        "Conflict", HttpHeaders.EMPTY, new byte[0], null));

        mockMvc.perform(post("/api/session/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\",\"choice\":\"HEAVY_TAXATION\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void sessionRulerStatsReturnsTheBackendStatsToBrowser() throws Exception {
        RulerStats stats = new RulerStats();
        stats.setAverageStability(72.5);
        stats.setEconomicPolicy("HEAVY_TAXATION");
        stats.setTicksUntilNextDecision(4);
        when(backendService.sessionRulerStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/session/ruler-stats").header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageStability").value(72.5))
                .andExpect(jsonPath("$.economicPolicy").value("HEAVY_TAXATION"))
                .andExpect(jsonPath("$.ticksUntilNextDecision").value(4));

        verify(backendService).sessionRulerStats("barony_token=jwt-abc");
    }

    @Test
    void rootRedirectsToTheLoginPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
