package com.barony.backend.service;

import com.barony.backend.model.GameState;
import com.barony.backend.model.RunRecord;
import com.barony.backend.model.SavedGame;
import com.barony.backend.repository.GuestGameRepository;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * "Keep your progress": a new account takes over the browser's guest game. Insert-only: an account
 * that already has a game is never changed, and the guest game is then left in place.
 */
@SpringBootTest(properties = "guest.create.per-address-per-hour=100")
@AutoConfigureMockMvc
class GuestClaimTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SavedGameRepository savedGames;
    @Autowired private RunRecordRepository runRecords;
    @Autowired private GuestGameRepository guestGames;
    @Autowired private GuestSessionService guestSessionService;
    @MockBean private UserAuthClient userAuthClient;

    @BeforeEach
    void tokens() {
        when(userAuthClient.validate(anyString())).thenReturn(Optional.empty());
        when(userAuthClient.validate("new-jwt")).thenReturn(Optional.of("claim-new-carol"));
        when(userAuthClient.validate("old-jwt")).thenReturn(Optional.of("claim-old-dave"));
    }

    private Cookie guestWithProgress() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/guest").header("X-Forwarded-For", "10.7.7.7"))
                .andExpect(status().isOk()).andReturn();
        Cookie guest = r.getResponse().getCookie("barony_guest");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/session/tick").cookie(guest)).andExpect(status().isOk());
        }
        var session = guestSessionService.find(guest.getValue()).orElseThrow();
        session.getGameState().setGameOver(true);
        session.getGameState().setWinnerId(1);
        guestSessionService.save(session);
        session.getGameState().setGameOver(false);
        session.getGameState().setWinnerId(null);
        guestSessionService.save(session);
        return guest;
    }

    @Test
    void newAccountTakesOverTheGuestGame() throws Exception {
        Cookie guest = guestWithProgress();
        long guestsBefore = guestGames.count();

        MvcResult r = mockMvc.perform(post("/api/guest/claim")
                        .cookie(guest, new Cookie("barony_token", "new-jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimed").value(true))
                .andReturn();
        assertEquals(0, r.getResponse().getCookie("barony_guest").getMaxAge(), "the guest cookie is cleared");

        mockMvc.perform(get("/api/session/state").cookie(new Cookie("barony_token", "new-jwt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(3));
        assertEquals(1, runRecords.findByUsernameOrderByFinishedAtDesc("claim-new-carol").size(),
                "the guest's finished run moves with the game");
        assertEquals(guestsBefore - 1, guestGames.count(), "the claimed guest row is removed");
        mockMvc.perform(get("/api/session/state").cookie(guest)).andExpect(status().isUnauthorized());
    }

    @Test
    void anAccountWithAGameIsNeverOverwritten() throws Exception {
        SavedGame existing = new SavedGame("claim-old-dave");
        existing.setState("{\"tickCount\":500}");
        Instant at = Instant.parse("2025-05-05T05:05:05Z");
        existing.setUpdatedAt(at);
        savedGames.save(existing);
        long runsBefore = runRecords.count();

        Cookie guest = guestWithProgress();
        long guestsBefore = guestGames.count();
        mockMvc.perform(post("/api/guest/claim").cookie(guest, new Cookie("barony_token", "old-jwt")))
                .andExpect(status().isConflict());

        SavedGame after = savedGames.findById("claim-old-dave").orElseThrow();
        assertEquals("{\"tickCount\":500}", after.getState());
        assertEquals(at, after.getUpdatedAt());
        assertEquals(runsBefore, runRecords.count(), "no runs are copied on a refused claim");
        assertEquals(guestsBefore, guestGames.count(), "the guest keeps their game");
        mockMvc.perform(get("/api/session/state").cookie(guest)).andExpect(status().isOk());
    }

    @Test
    void claimNeedsAValidAccountAndAGuest() throws Exception {
        Cookie guest = guestWithProgress();
        mockMvc.perform(post("/api/guest/claim").cookie(guest, new Cookie("barony_token", "forged")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/guest/claim").cookie(new Cookie("barony_token", "new-jwt")))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceRefusesWhenTheAccountGameIsOnlyInTheCache() {
        SessionService sessions = new SessionService(savedGames, runRecords);
        sessions.getOrCreateSession("claim-cached-erin"); // creates and persists
        assertFalse(sessions.claimGuestGame("claim-cached-erin", new GameState(4, 4), java.util.List.of(new RunRecord())));
    }
}
