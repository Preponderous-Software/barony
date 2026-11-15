package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.GuestGame;
import com.barony.backend.model.GuestGameBackup;
import com.barony.backend.model.RunHistory;
import com.barony.backend.model.RunRecord;
import com.barony.backend.model.Session;
import com.barony.backend.repository.GuestGameBackupRepository;
import com.barony.backend.repository.GuestGameRepository;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns guests' games: people who play without a UserAuth account.
 *
 * Isolation from real accounts is structural. This service holds only the {@link GuestGameRepository}
 * (table {@code guest_game}) and the {@link GuestGameBackupRepository} (table
 * {@code guest_game_backup}); it has no reference to the saved-game, saved-game-backup, run-record or
 * preferences repositories, so no guest request can read, write or delete an account's data. A guest's
 * {@link Session} is keyed by the guest key (SHA-256 of the cookie token) and lives in this
 * service's own cache, never in {@link SessionService}'s.
 *
 * Guests idle for longer than {@code guest.idle-days} (default 30) are deleted by
 * {@link #pruneIdleGuests()}, which is a bulk delete on the guest table only.
 *
 * A guest game that exists but cannot be read is never replaced on load: {@link #find} leaves the
 * row exactly as it is and throws {@link UnreadableSaveException} (answered 409 "unreadable-save"),
 * and only {@link #startFresh}, the guest's confirmed choice, sets the text aside in
 * {@code guest_game_backup} and then writes a fresh game.
 */
@Service
public class GuestSessionService {

    private static final Logger log = LoggerFactory.getLogger(GuestSessionService.class);

    private static final int SESSION_TIMEOUT_MINUTES = 60;
    private static final int MAX_RUN_HISTORY = 20;
    /** A cache-miss load refreshes lastSeenAt only if it is older than this, to avoid a write per read. */
    private static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    private final GuestGameRepository repository;
    private final GuestGameBackupRepository backupRepository;
    private final int idleDays;
    private final long maxGuests;
    private final Clock clock;
    private MapSource mapSource = new MapGenerator();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Autowired
    public GuestSessionService(GuestGameRepository repository,
                               GuestGameBackupRepository backupRepository,
                               @Value("${guest.idle-days:30}") int idleDays,
                               @Value("${guest.max-total:20000}") long maxGuests) {
        this(repository, backupRepository, idleDays, maxGuests, Clock.systemUTC());
    }

    GuestSessionService(GuestGameRepository repository, GuestGameBackupRepository backupRepository,
                        int idleDays, long maxGuests, Clock clock) {
        this.repository = repository;
        this.backupRepository = backupRepository;
        this.idleDays = idleDays;
        this.maxGuests = maxGuests;
        this.clock = clock;
    }

    /** Where new games' maps come from (see {@link MapSourceConfig}); random maps by default. */
    @Autowired(required = false)
    void setMapSource(MapSource mapSource) {
        this.mapSource = mapSource;
    }

    /** A newly created guest: the cookie token (given to the browser once) and their session. */
    public record NewGuest(String token, Session session) { }

    /** Thrown when the total-guest cap is reached; the caller answers 503. */
    public static class GuestCapacityException extends RuntimeException {
        public GuestCapacityException() {
            super("Guest play is full right now. Please try again later, or create a free account.");
        }
    }

    /** Create a guest with a fresh game and persist it. Rate limiting is the caller's job. */
    public synchronized NewGuest create() {
        if (repository.count() >= maxGuests) {
            throw new GuestCapacityException();
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String key = keyFor(token);

        GameState state = mapSource.generate();
        Instant now = clock.instant();
        GuestGame row = new GuestGame(key);
        row.setState(write(state));
        row.setRuns("[]");
        row.setCreatedAt(now);
        row.setLastSeenAt(now);
        repository.save(row);

        Session session = new Session(key, state);
        sessions.put(key, session);
        return new NewGuest(token, session);
    }

    /**
     * The guest's session for a cookie token, or empty if the token is unknown or pruned.
     *
     * @throws UnreadableSaveException if the guest's stored game cannot be read. The row is left
     *         exactly as it is (not even lastSeenAt is touched) and no fresh game is made or cached;
     *         see {@link #startFresh}.
     */
    public synchronized Optional<Session> find(String token) {
        if (!GuestCookies.isWellFormed(token)) {
            return Optional.empty();
        }
        evictIdleFromCache();
        String key = keyFor(token);
        Session cached = sessions.get(key);
        if (cached != null) {
            cached.updateLastAccessed();
            return Optional.of(cached);
        }
        return repository.findById(key).map(this::restore);
    }

    /**
     * Start a fresh game for a guest whose stored game cannot be read, once they have confirmed it.
     * The unreadable text is first copied, unchanged, into {@link GuestGameBackup} (keyed by the
     * guest key, never the token) and flushed; only then is the fresh game written over it, in the
     * same transaction. If the backup cannot be written, nothing is overwritten and the error
     * propagates. The guest's run history and tally are kept.
     *
     * Safe to repeat: a readable game (a second click, or one repaired meanwhile) is returned
     * unchanged. Empty when the token is unknown or pruned, as for {@link #find}.
     */
    @Transactional
    public synchronized Optional<Session> startFresh(String token) {
        if (!GuestCookies.isWellFormed(token)) {
            return Optional.empty();
        }
        evictIdleFromCache();
        String key = keyFor(token);
        Session cached = sessions.get(key);
        if (cached != null) {
            cached.updateLastAccessed();
            return Optional.of(cached);
        }
        Optional<GuestGame> row = repository.findById(key);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        GuestGame guest = row.get();
        try {
            return Optional.of(restore(guest));
        } catch (UnreadableSaveException e) {
            GuestGameBackup backup = backupRepository.saveAndFlush(new GuestGameBackup(
                    key, guest.getState(), guest.getLastSeenAt(), clock.instant()));
            log.warn("Unreadable guest game {}… set aside as backup {} before a fresh game replaces it",
                    shortKey(key), backup.getId());
        }
        GameState state = mapSource.generate();
        guest.setState(write(state));
        guest.setLastSeenAt(clock.instant());
        repository.save(guest);
        Session session = new Session(key, state);
        sessions.put(key, session);
        return Optional.of(session);
    }

    /** Read a stored guest row into a cached session; throws (row untouched) when unreadable. */
    private Session restore(GuestGame guest) {
        String key = guest.getGuestKey();
        GameState state = read(guest.getState(), key);
        Instant now = clock.instant();
        if (guest.getLastSeenAt() == null || guest.getLastSeenAt().isBefore(now.minus(TOUCH_INTERVAL))) {
            guest.setLastSeenAt(now);
            repository.save(guest);
        }
        advanceArmyIds(state);
        Session session = new Session(key, state);
        sessions.put(key, session);
        return session;
    }

    /** Persist a guest's game (after any change), recording a finished run once. */
    public synchronized void save(Session session) {
        session.updateLastAccessed();
        GuestGame guest = repository.findById(session.getUsername()).orElse(null);
        if (guest == null) {
            // Pruned or deleted (e.g. claimed) while cached: do not resurrect it.
            sessions.remove(session.getUsername());
            return;
        }
        GameState state = session.getGameState();
        if (state.isGameOver() && !state.isRunRecorded()) {
            List<RunRecord> runs = readRunsOrNull(guest.getRuns(), guest.getGuestKey());
            if (runs == null) {
                // Save integrity first: never overwrite stored history that cannot be read.
                log.warn("Run not recorded for guest {}… — the stored run history could not be read "
                        + "and is left untouched", shortKey(guest.getGuestKey()));
            } else {
                RunRecord run = SessionService.buildRunRecord("guest", state);
                runs.add(0, run);
                if (runs.size() > MAX_RUN_HISTORY) {
                    runs = new ArrayList<>(runs.subList(0, MAX_RUN_HISTORY));
                }
                guest.setRuns(write(runs));
                if ("WIN".equals(run.getResult())) {
                    guest.setWins(guest.getWins() + 1);
                } else {
                    guest.setLosses(guest.getLosses() + 1);
                }
                state.setRunRecorded(true);
            }
        }
        guest.setState(write(state));
        guest.setLastSeenAt(clock.instant());
        repository.save(guest);
    }

    public synchronized RunHistory getRunHistory(Session session) {
        RunHistory history = new RunHistory();
        repository.findById(session.getUsername()).ifPresent(guest -> {
            history.setWins(guest.getWins());
            history.setLosses(guest.getLosses());
            history.setRuns(readRuns(guest.getRuns(), guest.getGuestKey()));
        });
        if (history.getRuns() == null) {
            history.setRuns(new ArrayList<>());
        }
        return history;
    }

    /** The guest's stored finished runs, newest first (used when an account claims them). */
    public synchronized List<RunRecord> getRuns(Session session) {
        return repository.findById(session.getUsername())
                .map(g -> readRuns(g.getRuns(), g.getGuestKey()))
                .orElseGet(ArrayList::new);
    }

    /** Delete one guest (after their game was claimed by an account). Guest table only. */
    public synchronized void delete(Session session) {
        sessions.remove(session.getUsername());
        repository.deleteById(session.getUsername());
    }

    /**
     * Delete every guest idle for longer than the idle window. Touches only {@code guest_game}.
     * Runs on a schedule (see {@code GuestPruneScheduler}); returns how many guests were removed.
     */
    public synchronized int pruneIdleGuests() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(idleDays));
        int removed = repository.deleteIdleSince(cutoff);
        if (removed > 0) {
            log.info("Pruned {} guest game(s) idle since before {}", removed, cutoff);
        }
        return removed;
    }

    static String keyFor(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void evictIdleFromCache() {
        LocalDateTime expiry = LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES);
        sessions.entrySet().removeIf(e -> e.getValue().getLastAccessed().isBefore(expiry));
    }

    private void advanceArmyIds(GameState state) {
        if (state.getArmiesInternal() == null) {
            return;
        }
        int maxId = state.getArmiesInternal().stream().mapToInt(Army::getId).max().orElse(0);
        Army.ensureIdsAbove(maxId);
    }

    String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize guest data", e);
        }
    }

    /**
     * Read a stored guest game. One that cannot be read is logged (key prefix and the parse error,
     * never the game's text) and reported as {@link UnreadableSaveException}; nothing is written.
     */
    private GameState read(String json, String key) {
        try {
            GameState state = objectMapper.readValue(json, GameState.class);
            if (state == null) {
                throw new IllegalArgumentException("the guest game is empty");
            }
            return state;
        } catch (Exception e) {
            // Save integrity first: an unreadable guest game is kept, never replaced by a fresh one.
            log.warn("Guest game {}… could not be read and is left untouched: {}",
                    shortKey(key), describeReadError(e));
            throw new UnreadableSaveException("guest " + shortKey(key) + "…");
        }
    }

    // Jackson's own message quotes the start of the input it failed on; that is the guest's game,
    // so only the parse error and its position are logged (as SessionService does for accounts).
    private static String describeReadError(Exception e) {
        if (e instanceof JsonProcessingException jpe) {
            JsonLocation at = jpe.getLocation();
            return jpe.getOriginalMessage()
                    + (at == null ? "" : " (line " + at.getLineNr() + ", column " + at.getColumnNr() + ")");
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /** The stored runs for display: empty when blank or unreadable (the latter is logged). */
    private List<RunRecord> readRuns(String json, String key) {
        List<RunRecord> runs = readRunsOrNull(json, key);
        return runs == null ? new ArrayList<>() : runs;
    }

    /**
     * The stored runs as a mutable list: empty when blank or absent, {@code null} when the JSON is
     * present but unreadable (logged), so a caller can tell "no history yet" from "history it must
     * not overwrite".
     */
    private List<RunRecord> readRunsOrNull(String json, String key) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<RunRecord>>() { }));
        } catch (Exception e) {
            log.warn("Could not deserialize run history of guest {}…: {}", shortKey(key), e.getMessage());
            return null;
        }
    }

    private static String shortKey(String key) {
        return key == null ? "?" : key.substring(0, Math.min(8, key.length()));
    }
}
