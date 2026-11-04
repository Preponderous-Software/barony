package com.barony.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshSessionsTest {

    private static final UserAuthClient.RefreshedTokens SUCCESSOR =
            new UserAuthClient.RefreshedTokens("jwt", null, "next");

    private UserAuthClient client;
    private Instant now;
    private RefreshSessions sessions;

    @BeforeEach
    void setUp() {
        client = mock(UserAuthClient.class);
        now = Instant.parse("2026-11-03T12:00:00Z");
        sessions = new RefreshSessions(client, new Clock() {
            @Override
            public Instant instant() {
                return now;
            }

            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }
        });
    }

    @Test
    void concurrentRequestsSpendTheTokenOnceAndAllGetTheSuccessor() throws Exception {
        int requests = 8;
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.refresh("rt")).thenAnswer(call -> {
            inFlight.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return Optional.of(SUCCESSOR);
        });

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Optional<UserAuthClient.RefreshedTokens>>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                results.add(pool.submit(() -> sessions.exchange("rt")));
            }
            assertTrue(inFlight.await(5, TimeUnit.SECONDS));
            release.countDown();
            for (Future<Optional<UserAuthClient.RefreshedTokens>> result : results) {
                assertEquals(Optional.of(SUCCESSOR), result.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        verify(client, times(1)).refresh("rt");
    }

    @Test
    void aStragglerWithinTheGraceGetsTheSuccessorWithoutTouchingUserAuth() {
        when(client.refresh("rt")).thenReturn(Optional.of(SUCCESSOR));

        sessions.exchange("rt");
        now = now.plus(RefreshSessions.GRACE);

        assertEquals(Optional.of(SUCCESSOR), sessions.exchange("rt"));
        verify(client, times(1)).refresh("rt");
    }

    @Test
    void theGraceEnds() {
        when(client.refresh("rt")).thenReturn(Optional.of(SUCCESSOR), Optional.empty());

        sessions.exchange("rt");
        now = now.plus(RefreshSessions.GRACE).plus(Duration.ofSeconds(1));

        assertEquals(Optional.empty(), sessions.exchange("rt"));
        verify(client, times(2)).refresh("rt");
    }

    @Test
    void aRejectionIsNotRemembered() {
        when(client.refresh("rt")).thenReturn(Optional.empty(), Optional.of(SUCCESSOR));

        assertEquals(Optional.empty(), sessions.exchange("rt"));
        assertEquals(Optional.of(SUCCESSOR), sessions.exchange("rt"));
    }

    @Test
    void anOutageIsNotRememberedSoTheNextRequestRetries() {
        when(client.refresh("rt"))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(Optional.of(SUCCESSOR));

        assertThrows(ResponseStatusException.class, () -> sessions.exchange("rt"));
        assertEquals(Optional.of(SUCCESSOR), sessions.exchange("rt"));
    }

    @Test
    void aWaiterSharesTheOutage() throws Exception {
        CountDownLatch inFlight = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.refresh("rt")).thenAnswer(call -> {
            inFlight.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> sessions.exchange("rt"));
            assertTrue(inFlight.await(5, TimeUnit.SECONDS));
            Future<?> second = pool.submit(() -> sessions.exchange("rt"));
            Thread.sleep(100);
            release.countDown();
            assertInstanceOf(ResponseStatusException.class,
                    assertThrows(java.util.concurrent.ExecutionException.class, () -> first.get(5, TimeUnit.SECONDS)).getCause());
            assertInstanceOf(ResponseStatusException.class,
                    assertThrows(java.util.concurrent.ExecutionException.class, () -> second.get(5, TimeUnit.SECONDS)).getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
