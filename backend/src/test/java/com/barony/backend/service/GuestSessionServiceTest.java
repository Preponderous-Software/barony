package com.barony.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.barony.backend.model.GameState;
import com.barony.backend.model.GuestGame;
import com.barony.backend.model.GuestGameBackup;
import com.barony.backend.model.RunHistory;
import com.barony.backend.model.Session;
import com.barony.backend.repository.GuestGameBackupRepository;
import com.barony.backend.repository.GuestGameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GuestSessionService} against a mocked {@link GuestGameRepository}: the
 * session cache, the lastSeenAt touch interval, keeping an unreadable stored game, and how a
 * finished run is recorded. The H2-backed behavior (prune, capacity, isolation) is covered by
 * {@code GuestPruneTest} and {@code GuestIsolationTest}.
 */
class GuestSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final String TOKEN = "A".repeat(43);

    private GuestGameRepository repository;
    private GuestGameBackupRepository backups;
    private GuestSessionService service;

    @BeforeEach
    void setUp() {
        // Mocked repo: findById returns Optional.empty() unless a test stubs it.
        repository = mock(GuestGameRepository.class);
        backups = mock(GuestGameBackupRepository.class);
        when(backups.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new GuestSessionService(repository, backups, 30, 1000, Clock.fixed(NOW, ZoneOffset.UTC));
        logs = new ListAppender<>();
        logs.start();
        serviceLogger().addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        serviceLogger().detachAppender(logs);
    }

    private ListAppender<ILoggingEvent> logs;

    private static Logger serviceLogger() {
        return (Logger) LoggerFactory.getLogger(GuestSessionService.class);
    }

    private List<String> warnings() {
        return logs.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private GuestGame storedGuest(String state, Instant lastSeen) {
        GuestGame row = new GuestGame(GuestSessionService.keyFor(TOKEN));
        row.setState(state);
        row.setRuns("[]");
        row.setCreatedAt(lastSeen);
        row.setLastSeenAt(lastSeen);
        when(repository.findById(row.getGuestKey())).thenReturn(Optional.of(row));
        return row;
    }

    private String storedState(int tickCount) {
        GameState state = new MapGenerator().generate();
        state.setTickCount(tickCount);
        return service.write(state);
    }

    private Session finishedGame(Integer winnerId) {
        GameState state = new MapGenerator().generate();
        state.setGameOver(true);
        state.setWinnerId(winnerId);
        return new Session(GuestSessionService.keyFor(TOKEN), state);
    }

    @Test
    void keyForIsTheHexSha256OfTheToken() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                GuestSessionService.keyFor("abc"));
    }

    @Test
    void createPersistsANewGuestKeyedByTheHashOfAWellFormedToken() {
        GuestSessionService.NewGuest created = service.create();

        assertTrue(GuestCookies.isWellFormed(created.token()));
        ArgumentCaptor<GuestGame> saved = ArgumentCaptor.forClass(GuestGame.class);
        verify(repository).save(saved.capture());
        GuestGame row = saved.getValue();
        assertEquals(GuestSessionService.keyFor(created.token()), row.getGuestKey());
        assertEquals(row.getGuestKey(), created.session().getUsername());
        assertEquals("[]", row.getRuns());
        assertEquals(NOW, row.getCreatedAt());
        assertEquals(NOW, row.getLastSeenAt());
        assertNotNull(row.getState());
    }

    @Test
    void createGivesEachGuestADistinctToken() {
        assertNotEquals(service.create().token(), service.create().token());
    }

    @Test
    void aCreatedGuestIsServedFromTheCacheWithoutReadingTheRepository() {
        GuestSessionService.NewGuest created = service.create();

        Optional<Session> found = service.find(created.token());

        assertSame(created.session(), found.orElseThrow());
        verify(repository, never()).findById(any());
    }

    @Test
    void findRejectsAMalformedTokenWithoutReadingTheRepository() {
        assertTrue(service.find(null).isEmpty());
        assertTrue(service.find("too-short").isEmpty());
        assertTrue(service.find("A".repeat(42) + "!").isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void findReturnsEmptyForAWellFormedButUnknownToken() {
        assertTrue(service.find(TOKEN).isEmpty());
        verify(repository).findById(GuestSessionService.keyFor(TOKEN));
    }

    @Test
    void findLoadsAStoredGuestOnceThenServesItFromTheCache() {
        storedGuest(storedState(9), NOW);

        Session first = service.find(TOKEN).orElseThrow();
        Session second = service.find(TOKEN).orElseThrow();

        assertSame(first, second);
        assertEquals(9, first.getGameState().getTickCount());
        assertEquals(GuestSessionService.keyFor(TOKEN), first.getUsername());
        verify(repository, times(1)).findById(any());
    }

    @Test
    void findReloadsASessionIdleInTheCacheForOverAnHour() {
        storedGuest(storedState(9), NOW);
        Session first = service.find(TOKEN).orElseThrow();
        first.setLastAccessed(first.getLastAccessed().minusMinutes(61));

        Session second = service.find(TOKEN).orElseThrow();

        assertNotSame(first, second);
        verify(repository, times(2)).findById(any());
    }

    @Test
    void findDoesNotWriteWhenLastSeenIsWithinTheTouchInterval() {
        GuestGame row = storedGuest(storedState(1), NOW.minus(Duration.ofMinutes(30)));

        service.find(TOKEN);

        verify(repository, never()).save(any());
        assertEquals(NOW.minus(Duration.ofMinutes(30)), row.getLastSeenAt());
    }

    @Test
    void findRefreshesLastSeenWhenOlderThanTheTouchInterval() {
        GuestGame row = storedGuest(storedState(1), NOW.minus(Duration.ofHours(2)));

        service.find(TOKEN);

        verify(repository).save(row);
        assertEquals(NOW, row.getLastSeenAt());
    }

    private static final String UNREADABLE = "{ this is not a game state";

    @Test
    void findRefusesAnUnreadableStoredGameAndLeavesTheRowUntouched() {
        Instant seen = NOW.minus(Duration.ofDays(2)); // past the touch interval: still not touched
        GuestGame row = storedGuest(UNREADABLE, seen);

        UnreadableSaveException refused = assertThrows(UnreadableSaveException.class, () -> service.find(TOKEN));

        assertFalse(refused.getMessage().contains(UNREADABLE), "the game's text is not carried in the error");
        assertFalse(refused.getMessage().contains(row.getGuestKey()), "nor the full guest key");
        assertEquals(UNREADABLE, row.getState(), "the unreadable game is kept byte for byte");
        assertEquals(seen, row.getLastSeenAt());
        verify(repository, never()).save(any());
        verifyNoInteractions(backups);
        String shortKey = row.getGuestKey().substring(0, 8);
        List<String> warnings = warnings();
        assertEquals(1, warnings.size(), warnings.toString());
        String warning = warnings.get(0);
        assertTrue(warning.contains(shortKey) && warning.contains("left untouched"), warning);
        assertFalse(warning.contains(row.getGuestKey()), "only the key prefix is logged");
        assertFalse(warning.contains(UNREADABLE), "the game's text is never logged");
        assertTrue(warning.contains("line 1, column"), "the parse position is logged: " + warning);
    }

    @Test
    void repeatedFindsKeepRefusingAndCacheNothing() {
        GuestGame row = storedGuest(UNREADABLE, null);

        for (int i = 0; i < 3; i++) {
            assertThrows(UnreadableSaveException.class, () -> service.find(TOKEN));
        }

        verify(repository, times(3)).findById(row.getGuestKey());
        verify(repository, never()).save(any());
        assertEquals(UNREADABLE, row.getState());
        assertNull(row.getLastSeenAt());
    }

    @Test
    void anEmptyStoredGameIsUnreadableToo() {
        GuestGame row = storedGuest("null", NOW);

        assertThrows(UnreadableSaveException.class, () -> service.find(TOKEN));

        assertEquals("null", row.getState());
        verify(repository, never()).save(any());
    }

    @Test
    void startFreshBacksUpTheUnreadableTextFirstThenWritesAFreshGame() {
        Instant seen = NOW.minus(Duration.ofDays(3));
        GuestGame row = storedGuest(UNREADABLE, seen);
        String runs = "[{\"result\":\"WIN\",\"turnsPlayed\":4}]";
        row.setRuns(runs);
        row.setWins(1);

        Session fresh = service.startFresh(TOKEN).orElseThrow();

        var order = inOrder(backups, repository);
        ArgumentCaptor<GuestGameBackup> backup = ArgumentCaptor.forClass(GuestGameBackup.class);
        order.verify(backups).saveAndFlush(backup.capture());
        order.verify(repository).save(row);
        assertEquals(row.getGuestKey(), backup.getValue().getGuestKey(), "keyed by the guest key");
        assertNotEquals(TOKEN, backup.getValue().getGuestKey(), "never by the cookie token");
        assertEquals(UNREADABLE, backup.getValue().getState(), "the original text is preserved exactly");
        assertEquals(seen, backup.getValue().getLastSeenAt());
        assertEquals(NOW, backup.getValue().getBackedUpAt());

        assertEquals(0, fresh.getGameState().getTickCount());
        assertEquals(service.write(fresh.getGameState()), row.getState());
        assertEquals(NOW, row.getLastSeenAt());
        assertEquals(1, row.getWins(), "the guest's run tally is kept");
        assertEquals(runs, row.getRuns(), "and so is their run history");
        assertSame(fresh, service.find(TOKEN).orElseThrow(), "the fresh game is now the guest's game");
    }

    @Test
    void startFreshDoesNotOverwriteWhenTheBackupFails() {
        GuestGame row = storedGuest(UNREADABLE, NOW);
        when(backups.saveAndFlush(any())).thenThrow(new IllegalStateException("disk full"));

        assertThrows(IllegalStateException.class, () -> service.startFresh(TOKEN));

        assertEquals(UNREADABLE, row.getState());
        verify(repository, never()).save(any());
        assertThrows(UnreadableSaveException.class, () -> service.find(TOKEN));
    }

    @Test
    void startFreshReturnsAReadableGameUnchanged() {
        String stored = storedState(12);
        GuestGame row = storedGuest(stored, NOW);

        Session session = service.startFresh(TOKEN).orElseThrow();

        assertEquals(12, session.getGameState().getTickCount());
        assertEquals(stored, row.getState());
        verifyNoInteractions(backups);
        verify(repository, never()).save(any());
        assertSame(session, service.startFresh(TOKEN).orElseThrow(), "a repeat returns the cached game");
    }

    @Test
    void startFreshIsEmptyForAnUnknownOrMalformedToken() {
        assertTrue(service.startFresh(TOKEN).isEmpty());
        assertTrue(service.startFresh("too-short").isEmpty());
        verify(repository, never()).save(any());
        verifyNoInteractions(backups);
    }

    @Test
    void saveDoesNotResurrectADeletedGuest() {
        GuestSessionService.NewGuest created = service.create();
        clearInvocations(repository);

        service.save(created.session());

        verify(repository, never()).save(any());
        // Dropped from the cache too, so the next find goes to the (empty) repository.
        assertTrue(service.find(created.token()).isEmpty());
        verify(repository, times(2)).findById(created.session().getUsername());
    }

    @Test
    void saveRecordsALossNewestFirstAndKeepsOnlyTheLatestTwentyRuns() {
        GuestGame row = storedGuest(storedState(1), NOW.minus(Duration.ofDays(1)));
        StringJoiner runs = new StringJoiner(",", "[", "]");
        for (int i = 0; i < 20; i++) {
            runs.add("{\"result\":\"WIN\",\"turnsPlayed\":" + i + "}");
        }
        row.setRuns(runs.toString());
        row.setWins(20);
        Session session = finishedGame(2);

        service.save(session);
        service.save(session); // a repeat save must not count the run twice

        RunHistory history = service.getRunHistory(session);
        assertEquals(20, history.getWins());
        assertEquals(1, history.getLosses());
        assertEquals(20, history.getRuns().size());
        assertEquals("LOSS", history.getRuns().get(0).getResult());
        assertEquals(18, history.getRuns().get(19).getTurnsPlayed(), "the oldest run is dropped");
        assertTrue(session.getGameState().isRunRecorded());
        assertEquals(NOW, row.getLastSeenAt());
    }

    @Test
    void saveDoesNotRecordARunWhileTheGameIsInProgress() {
        GuestGame row = storedGuest(storedState(1), NOW);
        Session session = new Session(row.getGuestKey(), new MapGenerator().generate());

        service.save(session);

        assertEquals("[]", row.getRuns());
        assertEquals(0, row.getWins());
        assertEquals(0, row.getLosses());
        verify(repository).save(row);
    }

    @Test
    void runHistoryOfAMissingGuestIsEmpty() {
        RunHistory history = service.getRunHistory(finishedGame(1));

        assertEquals(0, history.getWins());
        assertEquals(0, history.getLosses());
        assertNotNull(history.getRuns());
        assertTrue(history.getRuns().isEmpty());
        assertTrue(service.getRuns(finishedGame(1)).isEmpty());
    }

    @Test
    void unreadableStoredRunsAreShownAsNoneWithAWarning() {
        GuestGame row = storedGuest(storedState(1), NOW);
        row.setRuns("not json");
        Session session = new Session(row.getGuestKey(), new MapGenerator().generate());

        assertTrue(service.getRuns(session).isEmpty());
        assertTrue(service.getRunHistory(session).getRuns().isEmpty());
        assertEquals("not json", row.getRuns(), "reading never rewrites the stored history");
        List<String> warnings = warnings();
        assertEquals(2, warnings.size());
        String shortKey = row.getGuestKey().substring(0, 8);
        assertTrue(warnings.stream().allMatch(w -> w.contains(shortKey) && w.contains("run history")),
                warnings.toString());
    }

    @Test
    void aFinishedRunIsNotRecordedOverUnreadableStoredHistory() {
        GuestGame row = storedGuest(storedState(1), NOW.minus(Duration.ofDays(1)));
        String unreadable = "[{\"result\":\"WIN\",\"turnsPlayed\":";  // truncated write
        row.setRuns(unreadable);
        row.setWins(3);
        Session session = finishedGame(2);

        assertDoesNotThrow(() -> service.save(session));

        assertEquals(unreadable, row.getRuns(), "the unreadable history is kept byte-for-byte");
        assertEquals(3, row.getWins());
        assertEquals(0, row.getLosses());
        assertFalse(session.getGameState().isRunRecorded());
        verify(repository).save(row); // the game state itself is still saved
        assertEquals(NOW, row.getLastSeenAt());
        assertTrue(warnings().stream().anyMatch(w -> w.contains("Run not recorded")
                && w.contains(row.getGuestKey().substring(0, 8))), warnings().toString());
    }

    @Test
    void aFinishedRunStartsANewHistoryWhenNoneIsStored() {
        GuestGame row = storedGuest(storedState(1), NOW);
        row.setRuns(null);
        Session session = finishedGame(2);

        service.save(session);

        RunHistory history = service.getRunHistory(session);
        assertEquals(1, history.getRuns().size());
        assertEquals("LOSS", history.getRuns().get(0).getResult());
        assertEquals(1, history.getLosses());
        assertTrue(session.getGameState().isRunRecorded());
        assertTrue(warnings().isEmpty(), warnings().toString());
    }

    @Test
    void deleteRemovesTheGuestFromTheRepositoryAndTheCache() {
        GuestSessionService.NewGuest created = service.create();

        service.delete(created.session());

        verify(repository).deleteById(created.session().getUsername());
        assertTrue(service.find(created.token()).isEmpty());
    }
}
