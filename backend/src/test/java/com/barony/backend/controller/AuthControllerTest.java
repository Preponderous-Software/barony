package com.barony.backend.controller;

import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.UserAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(AuthCookies.class)
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
}
