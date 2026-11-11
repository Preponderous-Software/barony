package com.barony.backend.service;

import com.barony.backend.model.GameState;
import com.barony.backend.model.GuestGame;
import com.barony.backend.model.GuestGameBackup;
import com.barony.backend.repository.GuestGameBackupRepository;
import com.barony.backend.repository.GuestGameRepository;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameBackupRepository;
import com.barony.backend.repository.SavedGameRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A guest whose stored game cannot be read, through the real controllers and H2 (issue #150): every
 * path that loads the game answers 409 "unreadable-save" and leaves the row exactly as it was, a
 * claim copies and deletes nothing, and only the confirmed start-fresh sets the text aside in
 * {@code guest_game_backup} and gives the guest a fresh game that then plays and saves normally.
 */
@SpringBootTest(properties = "guest.create.per-address-per-hour=100")
@AutoConfigureMockMvc
class GuestUnreadableGameTest {

    private static final String UNREADABLE = "{ this is not a game state";
    private static final Instant LAST_SEEN = Instant.parse("2026-11-01T01:02:03Z");
    private static final String RUNS = "[{\"result\":\"WIN\",\"turnsPlayed\":6}]";

    @Autowired private MockMvc mockMvc;
    @Autowired private GuestGameRepository guestGames;
    @Autowired private GuestGameBackupRepository guestBackups;
    @Autowired private SavedGameRepository savedGames;
    @Autowired private SavedGameBackupRepository savedGameBackups;
    @Autowired private RunRecordRepository runRecords;
    @MockBean private UserAuthClient userAuthClient;

    @BeforeEach
    void tokens() {
        when(userAuthClient.validate(anyString())).thenReturn(Optional.empty());
        when(userAuthClient.validate("claimer-jwt")).thenReturn(Optional.of("unreadable-guest-claimer"));
    }

    /** A guest row stored directly (as an existing guest's would be), with unreadable game text. */
    private Cookie unreadableGuest(char fill) {
        String token = String.valueOf(fill).repeat(43);
        GuestGame row = new GuestGame(GuestSessionService.keyFor(token));
        row.setState(UNREADABLE);
        row.setRuns(RUNS);
        row.setWins(1);
        row.setCreatedAt(LAST_SEEN);
        row.setLastSeenAt(LAST_SEEN);
        guestGames.save(row);
        return new Cookie(GuestCookies.COOKIE_NAME, token);
    }

    private void assertRowUnchanged(Cookie guest) {
        GuestGame row = guestGames.findById(GuestSessionService.keyFor(guest.getValue())).orElseThrow();
        assertEquals(UNREADABLE, row.getState(), "the unreadable guest game must be kept byte for byte");
        assertEquals(LAST_SEEN, row.getLastSeenAt(), "the row must not be rewritten");
        assertEquals(RUNS, row.getRuns());
        assertEquals(1, row.getWins());
        assertEquals(0, row.getLosses());
    }

    private List<GuestGameBackup> backupsOf(Cookie guest) {
        return guestBackups.findByGuestKeyOrderByBackedUpAtDesc(GuestSessionService.keyFor(guest.getValue()));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder call, String body) {
        return call.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void everyGuestPathRefusesAndLeavesTheGameUntouched() throws Exception {
        Cookie guest = unreadableGuest('U');
        long guestsBefore = guestGames.count();

        List<RequestBuilder> calls = new ArrayList<>();
        for (String mode : new String[] {"guest", null}) {
            List<MockHttpServletRequestBuilder> perMode = List.of(
                    get("/api/session/state"),
                    post("/api/session/tick"),
                    post("/api/session/reset"),
                    json(post("/api/session/command"), "{\"type\":\"MOVE\",\"armyId\":1,\"targetX\":1,\"targetY\":1}"),
                    json(post("/api/session/battle"), "{\"battleId\":1,\"result\":\"AUTO\"}"),
                    json(post("/api/session/field-battles"), "{\"enabled\":false}"),
                    json(post("/api/session/decision"), "{\"category\":\"ECONOMIC\",\"choice\":\"BALANCED\"}"),
                    get("/api/session/ruler-stats"),
                    get("/api/session/runs"));
            for (MockHttpServletRequestBuilder call : perMode) {
                call.cookie(guest);
                if (mode != null) {
                    call.header("X-Barony-Player", mode);
                }
                calls.add(call);
            }
        }
        for (RequestBuilder call : calls) {
            mockMvc.perform(call)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("unreadable-save"));
        }

        // Resuming does not make a new guest in its place (no new row, no new cookie).
        MvcResult resume = mockMvc.perform(post("/api/guest").cookie(guest).header("X-Forwarded-For", "10.9.9.9"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("unreadable-save"))
                .andReturn();
        assertNull(resume.getResponse().getHeader(HttpHeaders.SET_COOKIE), "no replacement guest cookie");
        assertEquals(guestsBefore, guestGames.count());

        assertRowUnchanged(guest);
        assertTrue(backupsOf(guest).isEmpty(), "nothing is set aside until the guest chooses to start fresh");

        // A restart (a new service over the same tables) keeps refusing.
        GuestSessionService afterRestart = new GuestSessionService(guestGames, guestBackups, 30, 1000,
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
        assertThrows(UnreadableSaveException.class, () -> afterRestart.find(guest.getValue()));
        assertRowUnchanged(guest);
    }

    @Test
    void claimingAnUnreadableGuestGameCopiesAndDeletesNothing() throws Exception {
        Cookie guest = unreadableGuest('C');
        long runsBefore = runRecords.count();

        MvcResult r = mockMvc.perform(post("/api/guest/claim").cookie(guest, new Cookie("barony_token", "claimer-jwt")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("unreadable-save"))
                .andReturn();

        assertNull(r.getResponse().getHeader(HttpHeaders.SET_COOKIE), "the guest cookie is not cleared");
        assertFalse(savedGames.existsById("unreadable-guest-claimer"), "no game is copied into the account");
        assertEquals(runsBefore, runRecords.count(), "no runs are copied");
        assertRowUnchanged(guest);
        assertTrue(backupsOf(guest).isEmpty());
    }

    @Test
    void startFreshSetsTheGuestGameAsideThenPlaysNormally() throws Exception {
        Cookie guest = unreadableGuest('F');
        long accountBackupsBefore = savedGameBackups.count();

        mockMvc.perform(post("/api/session/start-fresh").cookie(guest).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(0))
                .andExpect(jsonPath("$.fieldBattles").value(true));

        List<GuestGameBackup> backups = backupsOf(guest);
        assertEquals(1, backups.size());
        GuestGameBackup backup = backups.get(0);
        assertEquals(UNREADABLE, backup.getState(), "the original text is preserved exactly");
        assertEquals(GuestSessionService.keyFor(guest.getValue()), backup.getGuestKey());
        assertNotEquals(guest.getValue(), backup.getGuestKey(), "the token itself is never stored");
        assertEquals(LAST_SEEN, backup.getLastSeenAt());
        assertNotNull(backup.getBackedUpAt());
        assertEquals(accountBackupsBefore, savedGameBackups.count(), "guest backups never go to the account table");

        // The fresh game plays and saves normally; the guest's run history is kept.
        mockMvc.perform(post("/api/session/tick").cookie(guest).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(1));
        mockMvc.perform(get("/api/session/runs").cookie(guest).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins").value(1))
                .andExpect(jsonPath("$.runs[0].turnsPlayed").value(6));

        // A repeated click returns that game unchanged, and nothing more is set aside.
        mockMvc.perform(post("/api/session/start-fresh").cookie(guest).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(1));
        GuestSessionService afterRestart = new GuestSessionService(guestGames, guestBackups, 30, 1000,
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
        GameState reloaded = afterRestart.find(guest.getValue()).orElseThrow().getGameState();
        assertEquals(1, reloaded.getTickCount());
        assertEquals(1, afterRestart.startFresh(guest.getValue()).orElseThrow().getGameState().getTickCount());
        assertEquals(1, backupsOf(guest).size(), "only the unreadable game is ever set aside");
        assertEquals(UNREADABLE, backupsOf(guest).get(0).getState(), "later saves never touch the backup");
    }

    @Test
    void aReadableGuestGameIsUnaffected() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/guest").header("X-Forwarded-For", "10.9.9.8"))
                .andExpect(status().isOk()).andReturn();
        Cookie guest = created.getResponse().getCookie(GuestCookies.COOKIE_NAME);
        mockMvc.perform(post("/api/session/tick").cookie(guest).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk());
        String stored = guestGames.findById(GuestSessionService.keyFor(guest.getValue())).orElseThrow().getState();

        mockMvc.perform(post("/api/guest").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumed").value(true));
        mockMvc.perform(post("/api/session/start-fresh").cookie(guest).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(1));
        GuestSessionService afterRestart = new GuestSessionService(guestGames, guestBackups, 30, 1000,
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
        assertEquals(1, afterRestart.startFresh(guest.getValue()).orElseThrow().getGameState().getTickCount());

        assertEquals(stored, guestGames.findById(GuestSessionService.keyFor(guest.getValue())).orElseThrow().getState());
        assertTrue(backupsOf(guest).isEmpty());
    }

    @Test
    void startFreshWithNoGuestIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/session/start-fresh").header("X-Barony-Player", "guest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/session/start-fresh").header("X-Barony-Player", "guest")
                        .cookie(new Cookie(GuestCookies.COOKIE_NAME, "N".repeat(43))))
                .andExpect(status().isUnauthorized());
    }
}
