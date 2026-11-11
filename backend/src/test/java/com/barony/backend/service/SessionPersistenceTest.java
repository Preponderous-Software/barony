package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.SavedGame;
import com.barony.backend.model.SavedGameBackup;
import com.barony.backend.model.Session;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameBackupRepository;
import com.barony.backend.repository.SavedGameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves a player's game is persisted and restored across a backend restart, using the real
 * (in-memory H2) repository. A brand-new SessionService instance stands in for a restarted process
 * with an empty in-memory cache.
 */
@SpringBootTest
class SessionPersistenceTest {

    @Autowired
    private SavedGameRepository repository;

    @Autowired
    private RunRecordRepository runRecordRepository;

    @Autowired
    private SavedGameBackupRepository backupRepository;

    @Test
    void newGameIsPersistedOnCreation() {
        SessionService service = new SessionService(repository, runRecordRepository, backupRepository);
        service.getOrCreateSession("persist-new-user");
        assertTrue(repository.findById("persist-new-user").isPresent(),
                "creating a session should persist the fresh game");
    }

    @Test
    void gameSurvivesARestart() {
        SessionService before = new SessionService(repository, runRecordRepository, backupRepository);
        Session session = before.getOrCreateSession("persist-restart-user");
        session.getGameState().setTickCount(42);
        session.getGameState().setEconomicPolicy("HEAVY_TAXATION");
        before.save(session);

        // Simulate a restart: a fresh SessionService has an empty cache and must reload from the DB.
        SessionService after = new SessionService(repository, runRecordRepository, backupRepository);
        Session reloaded = after.getOrCreateSession("persist-restart-user");

        assertEquals("persist-restart-user", reloaded.getUsername());
        assertEquals(42, reloaded.getGameState().getTickCount(), "tick count should be restored");
        assertEquals("HEAVY_TAXATION", reloaded.getGameState().getEconomicPolicy());
        assertFalse(reloaded.getGameState().getArmies().isEmpty(), "armies should be restored");
    }

    private static final String UNREADABLE = "{ this is not a game state";
    private static final Instant SAVED_AT = Instant.parse("2025-03-03T03:03:03Z");

    private void storeUnreadable(String username) {
        SavedGame corrupt = new SavedGame(username);
        corrupt.setState(UNREADABLE);
        corrupt.setUpdatedAt(SAVED_AT);
        repository.save(corrupt);
    }

    private void assertRowUnchanged(String username) {
        SavedGame row = repository.findById(username).orElseThrow();
        assertEquals(UNREADABLE, row.getState(), "the unreadable save must be kept byte for byte");
        assertEquals(SAVED_AT, row.getUpdatedAt(), "the unreadable save must not be rewritten");
    }

    @Test
    void anUnreadableSaveIsRefusedAndLeftUntouched() {
        storeUnreadable("persist-corrupt-user");
        SessionService service = new SessionService(repository, runRecordRepository, backupRepository);

        UnreadableSaveException refused = assertThrows(UnreadableSaveException.class,
                () -> service.getOrCreateSession("persist-corrupt-user"));
        assertEquals("persist-corrupt-user", refused.getUsername());
        assertFalse(refused.getMessage().contains(UNREADABLE), "the save's text is not carried in the error");

        assertRowUnchanged("persist-corrupt-user");
        assertEquals(0, service.getActiveSessionCount(), "no fresh game is cached in its place");
        assertTrue(backupRepository.findByUsernameOrderByBackedUpAtDesc("persist-corrupt-user").isEmpty(),
                "nothing is set aside until the player chooses to start fresh");
    }

    @Test
    void repeatedLoadsAndRestartsKeepRefusing() {
        storeUnreadable("persist-corrupt-repeat-user");
        SessionService service = new SessionService(repository, runRecordRepository, backupRepository);
        for (int i = 0; i < 3; i++) {
            assertThrows(UnreadableSaveException.class, () -> service.getOrCreateSession("persist-corrupt-repeat-user"));
        }
        SessionService afterRestart = new SessionService(repository, runRecordRepository, backupRepository);
        assertThrows(UnreadableSaveException.class, () -> afterRestart.getOrCreateSession("persist-corrupt-repeat-user"));
        assertRowUnchanged("persist-corrupt-repeat-user");
    }

    @Test
    void startFreshSetsTheUnreadableSaveAsideThenPlaysNormally() {
        storeUnreadable("persist-corrupt-fresh-user");
        SessionService service = new SessionService(repository, runRecordRepository, backupRepository);
        assertThrows(UnreadableSaveException.class, () -> service.getOrCreateSession("persist-corrupt-fresh-user"));

        Session fresh = service.startFresh("persist-corrupt-fresh-user");
        assertEquals(0, fresh.getGameState().getTickCount());
        assertFalse(fresh.getGameState().getArmies().isEmpty(), "the fresh game is playable");

        List<SavedGameBackup> backups = backupRepository.findByUsernameOrderByBackedUpAtDesc("persist-corrupt-fresh-user");
        assertEquals(1, backups.size());
        assertEquals(UNREADABLE, backups.get(0).getState(), "the original text is preserved exactly");
        assertEquals(SAVED_AT, backups.get(0).getSavedAt());
        assertNotNull(backups.get(0).getBackedUpAt());

        // The fresh game now loads and saves normally, across a restart, and later saves never
        // touch the backup.
        assertSame(fresh, service.getOrCreateSession("persist-corrupt-fresh-user"));
        fresh.getGameState().setTickCount(7);
        service.save(fresh);
        SessionService afterRestart = new SessionService(repository, runRecordRepository, backupRepository);
        assertEquals(7, afterRestart.getOrCreateSession("persist-corrupt-fresh-user").getGameState().getTickCount());
        assertEquals(UNREADABLE, backupRepository.findByUsernameOrderByBackedUpAtDesc("persist-corrupt-fresh-user")
                .get(0).getState());
    }

    @Test
    void startFreshTwiceDoesNotReplaceTheNewGame() {
        storeUnreadable("persist-corrupt-twice-user");
        SessionService service = new SessionService(repository, runRecordRepository, backupRepository);
        Session fresh = service.startFresh("persist-corrupt-twice-user");
        fresh.getGameState().setTickCount(3);
        service.save(fresh);

        // A second click, or one after a restart, finds a readable game and returns it unchanged.
        assertSame(fresh, service.startFresh("persist-corrupt-twice-user"));
        SessionService afterRestart = new SessionService(repository, runRecordRepository, backupRepository);
        assertEquals(3, afterRestart.startFresh("persist-corrupt-twice-user").getGameState().getTickCount());
        assertEquals(1, backupRepository.findByUsernameOrderByBackedUpAtDesc("persist-corrupt-twice-user").size(),
                "only the unreadable save is ever set aside");
    }

    @Test
    void startFreshLeavesAReadableSaveAlone() {
        SessionService before = new SessionService(repository, runRecordRepository, backupRepository);
        Session session = before.getOrCreateSession("persist-readable-fresh-user");
        session.getGameState().setTickCount(11);
        before.save(session);
        String stored = repository.findById("persist-readable-fresh-user").orElseThrow().getState();

        SessionService after = new SessionService(repository, runRecordRepository, backupRepository);
        assertEquals(11, after.startFresh("persist-readable-fresh-user").getGameState().getTickCount());
        assertEquals(stored, repository.findById("persist-readable-fresh-user").orElseThrow().getState());
        assertTrue(backupRepository.findByUsernameOrderByBackedUpAtDesc("persist-readable-fresh-user").isEmpty());
    }

    @Test
    void aStoredSaveFromBeforeThisChangeStillLoads() {
        // A hand-written save in the stored shape, as an existing player's row would be.
        SavedGame old = new SavedGame("persist-old-shape-user");
        old.setState("{\"tickCount\":9,\"economicPolicy\":\"BALANCED\"}");
        old.setUpdatedAt(SAVED_AT);
        repository.save(old);

        SessionService service = new SessionService(repository, runRecordRepository, backupRepository);
        assertEquals(9, service.getOrCreateSession("persist-old-shape-user").getGameState().getTickCount());
        assertTrue(backupRepository.findByUsernameOrderByBackedUpAtDesc("persist-old-shape-user").isEmpty());
    }

    @Test
    void restoredGameAdvancesArmyIdsPastTheRestoredArmies() {
        SessionService before = new SessionService(repository, runRecordRepository, backupRepository);
        Session session = before.getOrCreateSession("persist-army-id-user");
        // A sentinel id well past anything the static counter has handed out in this JVM, so a new
        // army can only exceed it if the restore advanced the counter.
        int restoredId = new Army(0, 0, 1, 1).getId() + 1_000_000;
        session.getGameState().getArmiesInternal().get(0).setId(restoredId);
        before.save(session);

        SessionService after = new SessionService(repository, runRecordRepository, backupRepository);
        Session reloaded = after.getOrCreateSession("persist-army-id-user");
        assertTrue(reloaded.getGameState().getArmies().stream().anyMatch(a -> a.getId() == restoredId),
                "the sentinel army should be restored");

        Army split = new Army(0, 0, 1, 1);
        assertTrue(split.getId() > restoredId,
                "a new army should get an id past the restored max, was " + split.getId());
    }
}
