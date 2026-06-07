package com.barony.backend.service;

import com.barony.backend.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService();
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
