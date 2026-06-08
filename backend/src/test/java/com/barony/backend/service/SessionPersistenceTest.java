package com.barony.backend.service;

import com.barony.backend.model.Session;
import com.barony.backend.repository.SavedGameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

    @Test
    void newGameIsPersistedOnCreation() {
        SessionService service = new SessionService(repository);
        service.getOrCreateSession("persist-new-user");
        assertTrue(repository.findById("persist-new-user").isPresent(),
                "creating a session should persist the fresh game");
    }

    @Test
    void gameSurvivesARestart() {
        SessionService before = new SessionService(repository);
        Session session = before.getOrCreateSession("persist-restart-user");
        session.getGameState().setTickCount(42);
        session.getGameState().setEconomicPolicy("HEAVY_TAXATION");
        before.save(session);

        // Simulate a restart: a fresh SessionService has an empty cache and must reload from the DB.
        SessionService after = new SessionService(repository);
        Session reloaded = after.getOrCreateSession("persist-restart-user");

        assertEquals("persist-restart-user", reloaded.getUsername());
        assertEquals(42, reloaded.getGameState().getTickCount(), "tick count should be restored");
        assertEquals("HEAVY_TAXATION", reloaded.getGameState().getEconomicPolicy());
        assertFalse(reloaded.getGameState().getArmies().isEmpty(), "armies should be restored");
    }
}
