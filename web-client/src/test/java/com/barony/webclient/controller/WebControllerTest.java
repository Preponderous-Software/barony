package com.barony.webclient.controller;

import com.barony.webclient.model.RunHistory;
import com.barony.webclient.model.RunRecord;
import com.barony.webclient.service.BackendService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
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
}
