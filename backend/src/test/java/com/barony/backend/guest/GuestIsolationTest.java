package com.barony.backend.guest;

import com.barony.backend.model.GameState;
import com.barony.backend.model.GuestGame;
import com.barony.backend.model.RunRecord;
import com.barony.backend.model.SavedGame;
import com.barony.backend.model.UserPreferences;
import com.barony.backend.repository.GuestGameRepository;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameRepository;
import com.barony.backend.repository.UserPreferencesRepository;
import com.barony.backend.service.UserAuthClient;
import com.barony.backend.service.GuestSessionService;
import com.barony.backend.model.Session;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end isolation of guest play from real accounts, through the real controllers and an H2
 * database. A real account's saved game, run history and preferences are seeded first; a guest is
 * then created and plays through every per-player endpoint, and the account's rows must come out
 * byte-for-byte unchanged, with no guest row ever written to an account table.
 */
@SpringBootTest(properties = {
        "guest.create.per-address-per-hour=3",
        "guest.create.global-per-hour=1000"
})
@AutoConfigureMockMvc
class GuestIsolationTest {

    private static final String ACCOUNT = "iso-real-alice";
    private static final String ACCOUNT_TOKEN = "alice-real-jwt";
    private static final Instant SEEDED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private SavedGameRepository savedGames;
    @Autowired private RunRecordRepository runRecords;
    @Autowired private UserPreferencesRepository preferences;
    @Autowired private GuestGameRepository guestGames;

    @Autowired private GuestSessionService guestSessionService;

    @MockBean private UserAuthClient userAuthClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private String seededState;

    @BeforeEach
    void seedRealAccount() throws Exception {
        when(userAuthClient.validate(anyString())).thenReturn(Optional.empty());
        when(userAuthClient.validate(ACCOUNT_TOKEN)).thenReturn(Optional.of(ACCOUNT));

        GameState state = new GameState(6, 6);
        state.setTickCount(77);
        seededState = mapper.writeValueAsString(state);
        SavedGame saved = new SavedGame(ACCOUNT);
        saved.setState(seededState);
        saved.setUpdatedAt(SEEDED_AT);
        savedGames.save(saved);

        if (runRecords.findByUsernameOrderByFinishedAtDesc(ACCOUNT).isEmpty()) {
            RunRecord run = new RunRecord();
            run.setUsername(ACCOUNT);
            run.setResult("WIN");
            run.setTurnsPlayed(12);
            run.setFinishedAt(SEEDED_AT);
            runRecords.save(run);
        }

        UserPreferences prefs = new UserPreferences(ACCOUNT);
        prefs.setPreferences("{\"theme\":\"dark\"}");
        prefs.setUpdatedAt(SEEDED_AT);
        preferences.save(prefs);
    }

    private String snapshotAccountTables() {
        String games = savedGames.findAll().stream()
                .map(g -> g.getUsername() + "|" + g.getState() + "|" + g.getUpdatedAt())
                .sorted().collect(Collectors.joining("\n"));
        String runs = runRecords.findAll().stream()
                .map(r -> r.getId() + "|" + r.getUsername() + "|" + r.getResult() + "|" + r.getTurnsPlayed())
                .sorted().collect(Collectors.joining("\n"));
        String prefs = preferences.findAll().stream()
                .map(p -> p.getUsername() + "|" + p.getPreferences() + "|" + p.getUpdatedAt())
                .sorted().collect(Collectors.joining("\n"));
        return games + "\n--\n" + runs + "\n--\n" + prefs;
    }

    private Cookie startGuest(String address) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/guest").header("X-Forwarded-For", address))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumed").value(false))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("barony_guest");
        assertNotNull(cookie, "a new guest gets the guest cookie");
        assertTrue(cookie.isHttpOnly(), "the guest cookie is HttpOnly");
        assertNull(result.getResponse().getCookie("barony_token"), "a guest is never given an account cookie");
        return cookie;
    }

    @Test
    void guestPlayNeverTouchesAccountTables() throws Exception {
        String before = snapshotAccountTables();
        long guestsBefore = guestGames.count();

        Cookie guest = startGuest("10.0.0.1");
        mockMvc.perform(get("/api/session/state").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(post("/api/session/tick").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(post("/api/session/tick").cookie(guest).header("X-Barony-Player", "guest"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/session/command").cookie(guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MOVE\",\"armyId\":999999,\"targetX\":0,\"targetY\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/session/decision").cookie(guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"ECONOMIC\",\"choice\":\"HEAVY_TAXATION\"}"));
        mockMvc.perform(get("/api/session/ruler-stats").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(get("/api/session/runs").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins").value(0))
                .andExpect(jsonPath("$.losses").value(0));
        mockMvc.perform(post("/api/session/reset").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(put("/api/session/preferences").cookie(guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"light\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/session/preferences").cookie(guest)).andExpect(status().isForbidden());

        assertEquals(before, snapshotAccountTables(),
                "guest play must leave every account row (saved games, runs, preferences) unchanged");
        assertEquals(guestsBefore + 1, guestGames.count(), "the guest is exactly one guest_game row");
    }

    @Test
    void guestStatePersistsAndIsTheGuestsOwnNotTheAccounts() throws Exception {
        Cookie guest = startGuest("10.0.0.2");
        mockMvc.perform(post("/api/session/tick").cookie(guest)).andExpect(status().isOk());
        mockMvc.perform(post("/api/session/tick").cookie(guest)).andExpect(status().isOk());

        mockMvc.perform(get("/api/session/state").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(2));

        // Resuming with the same cookie keeps the same guest (no new row, no new cookie).
        long guests = guestGames.count();
        MvcResult resumed = mockMvc.perform(post("/api/guest").cookie(guest).header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumed").value(true))
                .andReturn();
        assertNull(resumed.getResponse().getCookie("barony_guest"));
        assertEquals(guests, guestGames.count());

        // The account still sees its own game (tick 77), not the guest's.
        mockMvc.perform(get("/api/session/state").cookie(new Cookie("barony_token", ACCOUNT_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickCount").value(77));
    }

    @Test
    void guestCookieIsNeverAcceptedAsAnAccountAndViceVersa() throws Exception {
        Cookie guest = startGuest("10.0.0.3");

        // A guest token presented as the account cookie is validated by UserAuth (and refused).
        mockMvc.perform(get("/api/session/state").cookie(new Cookie("barony_token", guest.getValue())))
                .andExpect(status().isUnauthorized());
        // An account page (header says account) with only a guest cookie is refused, not shown the guest game.
        mockMvc.perform(get("/api/session/state").cookie(guest).header("X-Barony-Player", "account"))
                .andExpect(status().isUnauthorized());
        // An invalid account token never falls back to the guest cookie.
        mockMvc.perform(get("/api/session/state").cookie(guest, new Cookie("barony_token", "expired")))
                .andExpect(status().isUnauthorized());
        // An account token named as the guest cookie is not a guest.
        mockMvc.perform(get("/api/session/state").cookie(new Cookie("barony_guest", ACCOUNT_TOKEN)))
                .andExpect(status().isUnauthorized());
        // A well-formed but unknown guest token is refused.
        mockMvc.perform(get("/api/session/state")
                        .cookie(new Cookie("barony_guest", "A".repeat(43))))
                .andExpect(status().isUnauthorized());
        // A guest cannot claim without an account.
        mockMvc.perform(post("/api/guest/claim").cookie(guest)).andExpect(status().isUnauthorized());
    }

    @Test
    void guestRowIsKeyedByAHashNotTheCookieToken() throws Exception {
        Cookie guest = startGuest("10.0.0.4");
        assertFalse(guestGames.existsById(guest.getValue()), "the raw token must not be stored");
        List<GuestGame> all = guestGames.findAll();
        assertTrue(all.stream().allMatch(g -> g.getGuestKey().matches("[0-9a-f]{64}")));
    }

    @Test
    void finishedGuestRunIsRecordedForTheGuestOnly() throws Exception {
        String before = snapshotAccountTables();
        Cookie guest = startGuest("10.0.0.5");
        Session session = guestSessionService.find(guest.getValue()).orElseThrow();
        session.getGameState().setGameOver(true);
        session.getGameState().setWinnerId(1);
        guestSessionService.save(session);
        guestSessionService.save(session); // a repeat save must not count the run twice

        mockMvc.perform(get("/api/session/runs").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wins").value(1))
                .andExpect(jsonPath("$.losses").value(0))
                .andExpect(jsonPath("$.runs.length()").value(1));
        assertEquals(before, snapshotAccountTables(), "a guest's finished run never goes into run_record");
    }

    @Test
    void guestCreationIsRateLimitedPerAddressByTheTrustedForwardedEntry() throws Exception {
        for (int i = 0; i < 3; i++) {
            startGuest("10.9.9.9");
        }
        mockMvc.perform(post("/api/guest").header("X-Forwarded-For", "10.9.9.9"))
                .andExpect(status().isTooManyRequests());
        // A forged earlier entry does not help: the last entry (added by the proxy) is what counts.
        mockMvc.perform(post("/api/guest").header("X-Forwarded-For", "1.2.3.4, 10.9.9.9"))
                .andExpect(status().isTooManyRequests());
        // Another address is unaffected.
        startGuest("10.9.9.10");
    }
}
