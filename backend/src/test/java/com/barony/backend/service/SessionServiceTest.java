package com.barony.backend.service;

import com.barony.backend.model.Session;
import com.barony.backend.repository.SavedGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SessionServiceTest {

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        // Mocked repo: findById returns Optional.empty() by default, so every username yields a
        // fresh game; this unit test exercises the in-memory cache behavior only.
        sessionService = new SessionService(mock(SavedGameRepository.class));
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
}
