package com.barony.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class GuestRateLimiterTest {

    /** A clock the test can move forward. */
    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void capsEachAddressAndRecoversAfterTheWindow() {
        MovableClock clock = new MovableClock();
        GuestRateLimiter limiter = new GuestRateLimiter(2, 100, clock);
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("1.1.1.1"));
        assertFalse(limiter.tryAcquire("1.1.1.1"));
        assertTrue(limiter.tryAcquire("2.2.2.2"), "another address has its own allowance");

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        assertTrue(limiter.tryAcquire("1.1.1.1"), "the window slides");
    }

    @Test
    void globalCapHoldsAcrossAddresses() {
        GuestRateLimiter limiter = new GuestRateLimiter(10, 3, new MovableClock());
        assertTrue(limiter.tryAcquire("a"));
        assertTrue(limiter.tryAcquire("b"));
        assertTrue(limiter.tryAcquire("c"));
        assertFalse(limiter.tryAcquire("d"), "rotating addresses cannot pass the global cap");
    }
}
