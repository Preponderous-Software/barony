package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.GuestGame;
import com.barony.backend.model.SavedGame;
import com.barony.backend.model.Session;
import com.barony.backend.repository.GuestGameBackupRepository;
import com.barony.backend.repository.GuestGameRepository;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameBackupRepository;
import com.barony.backend.repository.SavedGameRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every save fixture in {@code src/test/resources/saves/} loads through the app's real load paths
 * (a player's {@link SessionService} restore from {@code saved_game} and a guest's
 * {@link GuestSessionService} restore from {@code guest_game}, both on H2) and comes back as the
 * game it was saved as.
 *
 * <p>Each fixture is named here literally; the directory must hold no other file. {@code v0-*} is
 * the format from before formats were numbered; {@code v<N>-*} were written by the serializer of
 * {@link GameState#SAVE_VERSION} N (see {@link SaveFixtureWriter}). Released fixtures never change.
 */
@SpringBootTest
class SaveFixturesTest {

    private static final Path DIR = Path.of("src/test/resources/saves");

    /** What each fixture holds: tick, game over, winner, armies, pending battles. */
    private record Expected(String file, int tick, boolean gameOver, Integer winnerId, int armies, int pendingBattles) { }

    private static final List<Expected> FIXTURES = List.of(
            new Expected("v0-pre-unit-types.json", 22, false, null, 7, 1),
            new Expected("v1-new-game.json", 0, false, null, 2, 0),
            new Expected("v1-mid-game-field-battle.json", 19, false, null, 6, 1),
            new Expected("v1-game-over.json", 38, true, 2, 5, 0));

    private static final Instant SAVED_AT = Instant.parse("2026-11-20T00:00:00Z");

    // Configured exactly as SessionService's mapper, which writes every player's save.
    private final ObjectMapper playerMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired private SavedGameRepository savedGames;
    @Autowired private SavedGameBackupRepository savedGameBackups;
    @Autowired private RunRecordRepository runRecords;
    @Autowired private GuestGameRepository guestGames;
    @Autowired private GuestGameBackupRepository guestBackups;

    private static String read(String file) throws IOException {
        return Files.readString(DIR.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    void theDirectoryHoldsExactlyTheListedFixtures() throws IOException {
        Set<String> listed = FIXTURES.stream().map(Expected::file).collect(Collectors.toCollection(TreeSet::new));
        Set<String> present;
        try (Stream<Path> files = Files.list(DIR)) {
            present = files.map(p -> p.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(listed, present, "every fixture in " + DIR + " is listed here, and only those");
    }

    @Test
    void thereIsAFixtureForEveryNumberedFormat() {
        for (int v = 1; v <= GameState.SAVE_VERSION; v++) {
            String prefix = "v" + v + "-";
            assertTrue(FIXTURES.stream().anyMatch(e -> e.file().startsWith(prefix)), "no fixture for format " + v);
        }
    }

    @Test
    void eachFixtureLoadsAsAPlayersSave() throws Exception {
        for (Expected e : FIXTURES) {
            String json = read(e.file());
            String username = "fixture-" + e.file();
            SavedGame row = new SavedGame(username);
            row.setState(json);
            row.setUpdatedAt(SAVED_AT);
            savedGames.save(row);

            // a new service has an empty cache, so this is the restore a restarted backend does
            SessionService service = new SessionService(savedGames, runRecords, savedGameBackups);
            Session session = service.getOrCreateSession(username);
            assertLoadedAs(e, session.getGameState());

            SavedGame after = savedGames.findById(username).orElseThrow();
            assertEquals(json, after.getState(), e.file() + ": loading must not rewrite the save");
        }
    }

    @Test
    void eachFixtureLoadsAsAGuestsGame() throws Exception {
        GuestSessionService service = new GuestSessionService(guestGames, guestBackups, 30, 1000,
                Clock.fixed(SAVED_AT, ZoneOffset.UTC));
        char fill = 'a';
        for (Expected e : FIXTURES) {
            String json = read(e.file());
            String token = String.valueOf(fill++).repeat(43);
            GuestGame row = new GuestGame(GuestSessionService.keyFor(token));
            row.setState(json);
            row.setCreatedAt(SAVED_AT);
            row.setLastSeenAt(SAVED_AT);
            guestGames.save(row);

            Session session = service.find(token).orElseThrow(() -> new AssertionError(e.file() + ": guest not found"));
            assertLoadedAs(e, session.getGameState());
            assertEquals(json, guestGames.findById(row.getGuestKey()).orElseThrow().getState(),
                    e.file() + ": loading must not rewrite the guest's game");
        }
    }

    /**
     * A save of the current format is written back as the same JSON (the same fields with the same
     * values) by both the player and the guest serializer. Compared as JSON trees, not bytes: Jackson's
     * order for getter-only properties (Army's infantry, moving, training) can differ between JVMs.
     * If this fails, the stored format changed: bump {@link GameState#SAVE_VERSION} and add fixtures
     * of the new format instead of editing these.
     */
    @Test
    void theCurrentFormatsFixturesAreWhatTheAppWritesToday() throws Exception {
        GuestSessionService guests = new GuestSessionService(guestGames, guestBackups, 30, 1000,
                Clock.fixed(SAVED_AT, ZoneOffset.UTC));
        String prefix = "v" + GameState.SAVE_VERSION + "-";
        for (Expected e : FIXTURES) {
            if (!e.file().startsWith(prefix)) continue;
            String json = read(e.file());
            GameState loaded = playerMapper.readValue(json, GameState.class);
            JsonNode saved = playerMapper.readTree(json);
            assertEquals(saved, playerMapper.readTree(playerMapper.writeValueAsString(loaded)), e.file() + " (player serializer)");
            assertEquals(saved, playerMapper.readTree(guests.write(loaded)), e.file() + " (guest serializer)");
        }
    }

    private static void assertLoadedAs(Expected e, GameState state) {
        String f = e.file();
        assertEquals(e.tick(), state.getTickCount(), f + ": tick");
        assertEquals(e.gameOver(), state.isGameOver(), f + ": game over");
        assertEquals(e.winnerId(), state.getWinnerId(), f + ": winner");
        assertEquals(e.armies(), state.getArmiesInternal().size(), f + ": armies");
        assertEquals(e.pendingBattles(), state.getPendingBattlesInternal().size(), f + ": pending battles");
        assertTrue(state.getWidth() > 0 && state.getHeight() > 0, f + ": map");
        for (Army army : state.getArmiesInternal()) {
            assertEquals(army.getSoldiers(), army.getInfantry() + army.getArchers() + army.getCavalry(), f + ": army mix");
        }
        if (f.startsWith("v0-")) {
            assertNull(state.getUnitTypes(), f + ": an old save keeps the AI it had");
        } else {
            assertEquals(Boolean.TRUE, state.getUnitTypes(), f + ": unit types");
            assertEquals(Boolean.TRUE, state.getFieldBattles(), f + ": field battles");
        }
        if (f.equals("v1-mid-game-field-battle.json")) {
            assertTrue(state.getArmiesInternal().stream().anyMatch(a -> a.getArchers() + a.getCavalry() > 0),
                    f + ": trained archers or cavalry");
        }
        if (f.equals("v1-game-over.json")) {
            assertTrue(state.isRunRecorded(), f + ": its run was recorded");
        }
    }
}
