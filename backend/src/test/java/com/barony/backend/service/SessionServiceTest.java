package com.barony.backend.service;

import com.barony.backend.model.GameState;
import com.barony.backend.model.RunHistory;
import com.barony.backend.model.RunRecord;
import com.barony.backend.model.Session;
import com.barony.backend.repository.RunRecordRepository;
import com.barony.backend.repository.SavedGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private SessionService sessionService;
    private RunRecordRepository runRecordRepository;

    @BeforeEach
    void setUp() {
        // Mocked repo: findById returns Optional.empty() by default, so every username yields a
        // fresh game; this unit test exercises the in-memory cache behavior only.
        runRecordRepository = mock(RunRecordRepository.class);
        sessionService = new SessionService(mock(SavedGameRepository.class), runRecordRepository);
    }

    @Test
    void getOrCreateSessionReturnsSameSessionForSameUsername() {
        Session first = sessionService.getOrCreateSession("alice");
        Session second = sessionService.getOrCreateSession("alice");

        assertSame(first, second, "Game state should be keyed by username");
        assertEquals("alice", first.getUsername());
        assertEquals(1, sessionService.getActiveSessionCount());
    }

    @Test
    void getOrCreateSessionGivesDistinctSessionsToDifferentUsers() {
        Session alice = sessionService.getOrCreateSession("alice");
        Session bob = sessionService.getOrCreateSession("bob");

        assertNotSame(alice, bob);
        assertNotSame(alice.getGameState(), bob.getGameState());
        assertEquals(2, sessionService.getActiveSessionCount());
    }

    @Test
    void getOrCreateSessionRefreshesLastAccessedOnReuse() {
        Session session = sessionService.getOrCreateSession("alice");
        session.setLastAccessed(session.getLastAccessed().minusMinutes(30));

        sessionService.getOrCreateSession("alice");

        assertTrue(session.getLastAccessed().isAfter(java.time.LocalDateTime.now().minusMinutes(1)),
                "Reusing a session should refresh its last-accessed time");
    }

    @Test
    void getOrCreateSessionRejectsBlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> sessionService.getOrCreateSession("  "));
        assertThrows(IllegalArgumentException.class, () -> sessionService.getOrCreateSession(null));
    }

    @Test
    void saveDoesNotRecordARunWhileTheGameIsStillInProgress() {
        Session session = sessionService.getOrCreateSession("carol");

        sessionService.save(session);

        verify(runRecordRepository, never()).save(any());
    }

    @Test
    void saveRecordsAWinExactlyOnceWhenTheGameEnds() {
        Session session = sessionService.getOrCreateSession("alice");
        GameState state = session.getGameState();
        state.setGameOver(true);
        state.setWinnerId(1);
        state.setTickCount(37);

        sessionService.save(session);
        sessionService.save(session); // e.g. a repeated tick after game-over

        ArgumentCaptor<RunRecord> captor = ArgumentCaptor.forClass(RunRecord.class);
        verify(runRecordRepository, times(1)).save(captor.capture());
        RunRecord run = captor.getValue();
        assertEquals("alice", run.getUsername());
        assertEquals("WIN", run.getResult());
        assertEquals(37, run.getTurnsPlayed());
        // A fresh game starts with the player holding its own castle (of the two on the map) and
        // its single starting army of 10 soldiers.
        assertEquals(1, run.getCastlesHeld());
        assertEquals(2, run.getCastlesTotal());
        assertEquals(0, run.getVillagesHeld());
        assertEquals(1, run.getArmiesRemaining());
        assertEquals(10, run.getSoldiersRemaining());
        assertTrue(state.isRunRecorded());
    }

    @Test
    void saveRecordsALossWhenPlayerTwoWins() {
        Session session = sessionService.getOrCreateSession("bob");
        GameState state = session.getGameState();
        state.setGameOver(true);
        state.setWinnerId(2);

        sessionService.save(session);

        ArgumentCaptor<RunRecord> captor = ArgumentCaptor.forClass(RunRecord.class);
        verify(runRecordRepository).save(captor.capture());
        assertEquals("LOSS", captor.getValue().getResult());
    }

    @Test
    void getRunHistoryReturnsTallyAndRecentRuns() {
        RunRecord win = new RunRecord();
        win.setResult("WIN");
        RunRecord loss = new RunRecord();
        loss.setResult("LOSS");
        when(runRecordRepository.findByUsernameOrderByFinishedAtDesc("dave"))
                .thenReturn(List.of(win, loss));

        RunHistory history = sessionService.getRunHistory("dave");

        assertEquals(1, history.getWins());
        assertEquals(1, history.getLosses());
        assertEquals(2, history.getRuns().size());
    }

    @Test
    void getRunHistoryCapsTheRunListButTalliesEveryRun() {
        // 25 runs, newest first: 15 wins then 10 losses. The list is capped at 20, but the tally
        // must still count the 5 losses that fall past the cap.
        List<RunRecord> runs = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            RunRecord run = new RunRecord();
            run.setResult(i < 15 ? "WIN" : "LOSS");
            run.setTurnsPlayed(i);
            runs.add(run);
        }
        when(runRecordRepository.findByUsernameOrderByFinishedAtDesc("erin")).thenReturn(runs);

        RunHistory history = sessionService.getRunHistory("erin");

        assertEquals(15, history.getWins());
        assertEquals(10, history.getLosses());
        assertEquals(20, history.getRuns().size());
        assertEquals(0, history.getRuns().get(0).getTurnsPlayed(), "the newest run should come first");
        assertEquals(19, history.getRuns().get(19).getTurnsPlayed(), "the oldest runs should be dropped");
    }

    @Test
    void idleSessionIsEvictedAndTheNextRequestStillGetsAUsableSession() {
        Session idle = sessionService.getOrCreateSession("frank");
        idle.setLastAccessed(LocalDateTime.now().minusMinutes(61));

        assertEquals(0, sessionService.getActiveSessionCount(), "an hour-idle session should be evicted");

        Session next = sessionService.getOrCreateSession("frank");
        assertNotSame(idle, next);
        assertEquals("frank", next.getUsername());
        assertFalse(next.getGameState().getArmies().isEmpty(), "the replacement session should hold a playable game");
        assertEquals(1, sessionService.getActiveSessionCount());
    }

    @Test
    void sessionIdleForLessThanTheTimeoutIsKept() {
        Session session = sessionService.getOrCreateSession("gina");
        session.setLastAccessed(LocalDateTime.now().minusMinutes(59));

        assertEquals(1, sessionService.getActiveSessionCount());
        assertSame(session, sessionService.getOrCreateSession("gina"));
    }
}
