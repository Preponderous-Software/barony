package com.barony.backend.controller;

import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.RefreshSessions;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({AuthCookies.class, RefreshSessions.class, RefreshSessions.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserAuthClient userAuthClient;

    @Test
    void registerReturns201AndProxiesToUserAuth() throws Exception {
        when(userAuthClient.register(eq("alice"), eq("password123")))
                .thenReturn(Map.of("id", 1, "username", "alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));

        verify(userAuthClient).register("alice", "password123");
    }

    @Test
    void registerRejectsMissingPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAuthClient);
    }

    @Test
    void loginSetsHttpOnlyCookieAndDoesNotReturnTokenInBody() throws Exception {
        when(userAuthClient.login(eq("alice"), eq("password123")))
                .thenReturn(Map.of("token", "jwt-abc", "tokenType", "Bearer"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("barony_token=jwt-abc")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
    }

    @Test
    void loginRejectsMissingUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userAuthClient);
    }

    @Test
    void logoutRevokesCookieTokenAndClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("barony_token", "jwt-abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("logged out"))
                .andExpect(header().string("Set-Cookie", containsString("barony_token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verify(userAuthClient).logout("jwt-abc");
    }

    @Test
    void logoutWithoutTokenStillClearsCookieAndRevokesNothing() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

        verifyNoInteractions(userAuthClient);
    }

    @Test
    void logoutAlsoAcceptsBearerTokenFallback() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer jwt-xyz"))
                .andExpect(status().isOk());

        verify(userAuthClient).logout("jwt-xyz");
    }

    @Test
    void loginKeepsBothTokensInHttpOnlyCookiesAndOutOfTheBody() throws Exception {
        when(userAuthClient.login("alice", "password123"))
                .thenReturn(Map.of("token", "jwt-abc", "refreshToken", "refresh-abc"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("barony_refresh", "refresh-abc"))
                .andExpect(cookie().httpOnly("barony_refresh", true))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void logoutClearsTheRefreshCookieToo() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("barony_token", "jwt-abc"), new Cookie("barony_refresh", "refresh-abc")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("barony_token", 0))
                .andExpect(cookie().maxAge("barony_refresh", 0));

        verify(userAuthClient).logout("jwt-abc");
        verify(userAuthClient, never()).refresh(anyString());
    }

    @Test
    void logoutWithOnlyARefreshCookieExchangesItSoTheSessionIsRevoked() throws Exception {
        when(userAuthClient.refresh("refresh-abc"))
                .thenReturn(Optional.of(new UserAuthClient.RefreshedTokens("renewed", null, "refresh-def")));

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("barony_refresh", "refresh-abc")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("barony_refresh", 0));

        verify(userAuthClient).logout("renewed");
    }

    @Test
    void logoutWithARejectedRefreshTokenStillClearsTheCookies() throws Exception {
        when(userAuthClient.refresh("spent")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("barony_refresh", "spent")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("barony_refresh", 0));

        verify(userAuthClient, never()).logout(anyString());
    }

    @Test
    void logoutDuringAnOutageKeepsTheCookiesRatherThanLeavingASessionAlive() throws Exception {
        when(userAuthClient.refresh("refresh-during-outage"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("barony_refresh", "refresh-during-outage")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
