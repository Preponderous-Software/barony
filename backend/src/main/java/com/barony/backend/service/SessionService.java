package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RunHistory;
import com.barony.backend.model.RunRecord;
import com.barony.backend.model.SavedGame;
import com.barony.backend.model.Session;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private final RunRecordRepository runRecordRepository;

    // Dedicated, lenient mapper: GameState exposes derived getters (getWidth, isMoving) with no
    // setters, so the round trip must tolerate "unknown" properties.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Evict idle sessions from the in-memory cache after this long (the DB copy is kept).
    private static final int SESSION_TIMEOUT_MINUTES = 60;

    // How many of a player's most recent finished runs to return from getRunHistory.
    private static final int MAX_RUN_HISTORY = 20;

    private static final int PLAYER_ID = 1;

    public SessionService(SavedGameRepository savedGameRepository, RunRecordRepository runRecordRepository) {
        this.savedGameRepository = savedGameRepository;
        this.runRecordRepository = runRecordRepository;
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
        recordRunIfJustFinished(session);
        persist(session.getUsername(), session.getGameState());
    }

    /**
     * Return the player's win/loss tally and most recent finished runs, newest first.
     */
    public RunHistory getRunHistory(String username) {
        List<RunRecord> runs = runRecordRepository.findByUsernameOrderByFinishedAtDesc(username);
        RunHistory history = new RunHistory();
        history.setWins((int) runs.stream().filter(r -> "WIN".equals(r.getResult())).count());
        history.setLosses((int) runs.stream().filter(r -> "LOSS".equals(r.getResult())).count());
        history.setRuns(runs.size() > MAX_RUN_HISTORY ? runs.subList(0, MAX_RUN_HISTORY) : runs);
        return history;
    }

    // Writes a RunRecord the first time a session's game reaches game-over, guarded by
    // GameState.runRecorded so a repeated tick (or a restart mid-game-over) can't double-count it.
    private void recordRunIfJustFinished(Session session) {
        GameState state = session.getGameState();
        if (!state.isGameOver() || state.isRunRecorded()) {
            return;
        }
        runRecordRepository.save(buildRunRecord(session.getUsername(), state));
        state.setRunRecorded(true);
    }

    private RunRecord buildRunRecord(String username, GameState state) {
        RunRecord run = new RunRecord();
        run.setUsername(username);
        run.setResult(Integer.valueOf(PLAYER_ID).equals(state.getWinnerId()) ? "WIN" : "LOSS");
        run.setTurnsPlayed(state.getTickCount());
        run.setFinishedAt(Instant.now());

        int castlesHeld = 0, castlesTotal = 0, villagesHeld = 0, villagesTotal = 0;
        for (int x = 0; x < state.getWidth(); x++) {
            for (int y = 0; y < state.getHeight(); y++) {
                Tile tile = state.getGrid()[x][y];
                if (tile.getType() == TileType.CASTLE) {
                    castlesTotal++;
                    if (tile.getOwnerId() == PLAYER_ID) castlesHeld++;
                } else if (tile.getType() == TileType.VILLAGE) {
                    villagesTotal++;
                    if (tile.getOwnerId() == PLAYER_ID) villagesHeld++;
                }
            }
        }
        run.setCastlesHeld(castlesHeld);
        run.setCastlesTotal(castlesTotal);
        run.setVillagesHeld(villagesHeld);
        run.setVillagesTotal(villagesTotal);

        List<Army> playerArmies = state.getArmies().stream()
                .filter(a -> a.getPlayerId() == PLAYER_ID)
                .collect(Collectors.toList());
        run.setArmiesRemaining(playerArmies.size());
        run.setSoldiersRemaining(playerArmies.stream().mapToInt(Army::getSoldiers).sum());

        return run;
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
