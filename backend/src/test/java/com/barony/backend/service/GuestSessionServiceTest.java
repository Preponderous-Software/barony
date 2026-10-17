package com.barony.backend.service;

import com.barony.backend.model.GameState;
import com.barony.backend.model.GuestGame;
import com.barony.backend.model.RunHistory;
import com.barony.backend.model.Session;
import com.barony.backend.repository.GuestGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.StringJoiner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GuestSessionService} against a mocked {@link GuestGameRepository}: the
 * session cache, the lastSeenAt touch interval, recovery from a corrupt stored game, and how a
 * finished run is recorded. The H2-backed behavior (prune, capacity, isolation) is covered by
 * {@code GuestPruneTest} and {@code GuestIsolationTest}.
 */
class GuestSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");
    private static final String TOKEN = "A".repeat(43);

    private GuestGameRepository repository;
    private GuestSessionService service;

    @BeforeEach
    void setUp() {
        // Mocked repo: findById returns Optional.empty() unless a test stubs it.
        repository = mock(GuestGameRepository.class);
        service = new GuestSessionService(repository, 30, 1000, Clock.fixed(NOW, ZoneOffset.UTC));
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

    @Test
    void findStartsAFreshGameWhenTheStoredStateIsCorrupt() {
        GuestGame row = storedGuest("not json", null);

        Session session = service.find(TOKEN).orElseThrow();

        assertNotNull(session.getGameState());
        assertEquals(0, session.getGameState().getTickCount());
        assertNotEquals("not json", row.getState(), "the fresh game replaces the corrupt one");
        verify(repository).save(row);
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
    void unreadableStoredRunsAreTreatedAsNone() {
        GuestGame row = storedGuest(storedState(1), NOW);
        row.setRuns("not json");
        Session session = new Session(row.getGuestKey(), new MapGenerator().generate());

        assertTrue(service.getRuns(session).isEmpty());
        assertTrue(service.getRunHistory(session).getRuns().isEmpty());
    }

    @Test
    void deleteRemovesTheGuestFromTheRepositoryAndTheCache() {
        GuestSessionService.NewGuest created = service.create();

        service.delete(created.session());

        verify(repository).deleteById(created.session().getUsername());
        assertTrue(service.find(created.token()).isEmpty());
    }
}
