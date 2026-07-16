package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.SavedGame;
import com.barony.backend.model.Session;
import com.barony.backend.repository.SavedGameRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns each player's game session, keyed by username. The in-memory map is a write-through cache
 * over the {@link SavedGameRepository}: a session is loaded from the database on a cache miss and
 * persisted (as serialized JSON) whenever it changes, so a player's game survives backend restarts
 * and redeploys instead of being lost.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final MapGenerator mapGenerator = new MapGenerator();
    private final SavedGameRepository savedGameRepository;

    // Dedicated, lenient mapper: GameState exposes derived getters (getWidth, isMoving) with no
    // setters, so the round trip must tolerate "unknown" properties.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Evict idle sessions from the in-memory cache after this long (the DB copy is kept).
    private static final int SESSION_TIMEOUT_MINUTES = 60;

    public SessionService(SavedGameRepository savedGameRepository) {
        this.savedGameRepository = savedGameRepository;
    }

    /**
     * Return the player's session, loading it from the database (or creating and persisting a fresh
     * game) if it isn't already cached in memory.
     */
    public synchronized Session getOrCreateSession(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        cleanupExpiredSessions();

        // 1. Cache hit.
        Session cached = sessions.values().stream()
                .filter(s -> s.getUsername().equals(username))
                .findFirst()
                .orElse(null);
        if (cached != null) {
            cached.updateLastAccessed();
            return cached;
        }

        // 2. Restore from the database.
        Session restored = loadFromDatabase(username);
        if (restored != null) {
            sessions.put(restored.getSessionId(), restored);
            return restored;
        }

        // 3. New game — create and persist immediately.
        GameState newGameState = mapGenerator.generate();
        Session newSession = new Session(username, newGameState);
        sessions.put(newSession.getSessionId(), newSession);
        persist(username, newGameState);
        return newSession;
    }

    /** Persist the session's current game state. Call this after any mutation. */
    public void save(Session session) {
        session.updateLastAccessed();
        persist(session.getUsername(), session.getGameState());
    }

    private Session loadFromDatabase(String username) {
        return savedGameRepository.findById(username).map(saved -> {
            try {
                GameState state = objectMapper.readValue(saved.getState(), GameState.class);
                advanceArmyIds(state);
                return new Session(username, state);
            } catch (Exception e) {
                log.warn("Could not deserialize saved game for '{}' — starting a fresh game: {}",
                        username, e.getMessage());
                return null;
            }
        }).orElse(null);
    }

    private void persist(String username, GameState state) {
        try {
            String json = objectMapper.writeValueAsString(state);
            SavedGame saved = savedGameRepository.findById(username).orElseGet(() -> new SavedGame(username));
            saved.setState(json);
            saved.setUpdatedAt(Instant.now());
            savedGameRepository.save(saved);
        } catch (Exception e) {
            // Persistence failures must not break gameplay; log and keep the in-memory state.
            log.warn("Could not persist game for '{}': {}", username, e.getMessage());
        }
    }

    // After loading, bump the static army-id counter past the restored armies so a new (e.g. split)
    // army can't reuse an existing id — the counter otherwise resets to 1 on restart.
    private void advanceArmyIds(GameState state) {
        if (state.getArmiesInternal() == null) {
            return;
        }
        int maxId = state.getArmiesInternal().stream().mapToInt(Army::getId).max().orElse(0);
        Army.ensureIdsAbove(maxId);
    }

    private void cleanupExpiredSessions() {
        LocalDateTime expirationTime = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        sessions.entrySet().removeIf(entry ->
            entry.getValue().getLastAccessed().isBefore(expirationTime)
        );
    }

    /** Number of sessions currently held in the in-memory cache. */
    public int getActiveSessionCount() {
        cleanupExpiredSessions();
        return sessions.size();
    }
}
