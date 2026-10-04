package com.barony.backend.service;

import com.barony.backend.model.GuestGame;
import com.barony.backend.model.RunRecord;
import com.barony.backend.model.SavedGame;
import com.barony.backend.model.UserPreferences;
import com.barony.backend.repository.GuestGameRepository;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameRepository;
import com.barony.backend.repository.UserPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The idle-guest prune deletes guests idle past the window and nothing else: not active guests, and
 * never any real account's saved game, run history or preferences — even ones far older than the
 * window. Runs the real JPQL delete against H2.
 */
@SpringBootTest
class GuestPruneTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final Instant ANCIENT = Instant.parse("2001-01-01T00:00:00Z");

    @Autowired private GuestGameRepository guestGames;
    @Autowired private SavedGameRepository savedGames;
    @Autowired private RunRecordRepository runRecords;
    @Autowired private UserPreferencesRepository preferences;

    private GuestSessionService service;

    @BeforeEach
    void setUp() {
        guestGames.deleteAll(); // test-only reset of the guest table
        service = new GuestSessionService(guestGames, 30, 1000, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void guest(String key, Instant lastSeen) {
        GuestGame g = new GuestGame(key);
        g.setState("{}");
        g.setRuns("[]");
        g.setCreatedAt(lastSeen);
        g.setLastSeenAt(lastSeen);
        guestGames.save(g);
    }

    @Test
    void prunesOnlyGuestsIdlePastTheWindow() {
        guest("a".repeat(64), NOW.minus(Duration.ofDays(45)));                 // idle: pruned
        guest("b".repeat(64), NOW.minus(Duration.ofDays(30)).minusSeconds(1)); // just past: pruned
        guest("c".repeat(64), NOW.minus(Duration.ofDays(30)).plusSeconds(60)); // just inside: kept
        guest("d".repeat(64), NOW.minus(Duration.ofDays(1)));                  // active: kept

        assertEquals(2, service.pruneIdleGuests());

        assertFalse(guestGames.existsById("a".repeat(64)));
        assertFalse(guestGames.existsById("b".repeat(64)));
        assertTrue(guestGames.existsById("c".repeat(64)));
        assertTrue(guestGames.existsById("d".repeat(64)));
    }

    @Test
    void neverDeletesAccountRowsHoweverOld() {
        SavedGame saved = new SavedGame("prune-real-bob");
        saved.setState("{\"tickCount\":5}");
        saved.setUpdatedAt(ANCIENT);
        savedGames.save(saved);
        RunRecord run = new RunRecord();
        run.setUsername("prune-real-bob");
        run.setResult("LOSS");
        run.setFinishedAt(ANCIENT);
        runRecords.save(run);
        UserPreferences prefs = new UserPreferences("prune-real-bob");
        prefs.setPreferences("{}");
        prefs.setUpdatedAt(ANCIENT);
        preferences.save(prefs);
        long games = savedGames.count(), runs = runRecords.count(), prefsCount = preferences.count();

        guest("e".repeat(64), ANCIENT);
        assertEquals(1, service.pruneIdleGuests());

        assertEquals(games, savedGames.count());
        assertEquals(runs, runRecords.count());
        assertEquals(prefsCount, preferences.count());
        assertEquals("{\"tickCount\":5}", savedGames.findById("prune-real-bob").orElseThrow().getState());
        assertTrue(preferences.existsById("prune-real-bob"));
    }

    @Test
    void aPrunedGuestCannotBeResumedOrResurrected() {
        GuestSessionService live = new GuestSessionService(guestGames, 30, 1000,
                Clock.fixed(NOW.minus(Duration.ofDays(40)), ZoneOffset.UTC));
        GuestSessionService.NewGuest created = live.create();

        assertEquals(1, service.pruneIdleGuests());
        live.save(created.session()); // cached session saved after the prune
        assertEquals(0, guestGames.count(), "a save after pruning must not recreate the row");
        assertTrue(service.find(created.token()).isEmpty());
    }

    @Test
    void totalGuestCapRefusesNewGuests() {
        GuestSessionService capped = new GuestSessionService(guestGames, 30, 2, Clock.fixed(NOW, ZoneOffset.UTC));
        capped.create();
        capped.create();
        assertThrows(GuestSessionService.GuestCapacityException.class, capped::create);
        assertEquals(2, guestGames.count());
    }
}
