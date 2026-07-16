package com.barony.webclient.controller;

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
