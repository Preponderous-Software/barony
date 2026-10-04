package com.barony.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Limits how fast new guests can be created: a per-client-address cap and a global cap, each over
 * a sliding one-hour window. The global cap holds even if a caller spoofs or rotates addresses.
 * In memory on purpose: a restart resetting the windows is harmless, and the total-guest cap in
 * {@link GuestSessionService} bounds storage regardless.
 */
@Component
public class GuestRateLimiter {

    static final Duration WINDOW = Duration.ofHours(1);

    private final int perAddressPerHour;
    private final int globalPerHour;
    private final Clock clock;

    private final Map<String, Deque<Instant>> byAddress = new HashMap<>();
    private final Deque<Instant> global = new ArrayDeque<>();

    @org.springframework.beans.factory.annotation.Autowired
    public GuestRateLimiter(@Value("${guest.create.per-address-per-hour:5}") int perAddressPerHour,
                            @Value("${guest.create.global-per-hour:300}") int globalPerHour) {
        this(perAddressPerHour, globalPerHour, Clock.systemUTC());
    }

    GuestRateLimiter(int perAddressPerHour, int globalPerHour, Clock clock) {
        this.perAddressPerHour = perAddressPerHour;
        this.globalPerHour = globalPerHour;
        this.clock = clock;
    }

    /** Record a creation for {@code address} and return true, or return false if over a cap. */
    public synchronized boolean tryAcquire(String address) {
        Instant now = clock.instant();
        Instant horizon = now.minus(WINDOW);
        trim(global, horizon);
        Deque<Instant> mine = byAddress.computeIfAbsent(address == null ? "unknown" : address,
                k -> new ArrayDeque<>());
        trim(mine, horizon);
        if (mine.size() >= perAddressPerHour || global.size() >= globalPerHour) {
            return false;
        }
        mine.addLast(now);
        global.addLast(now);
        // Drop empty per-address windows so the map cannot grow without bound.
        for (Iterator<Map.Entry<String, Deque<Instant>>> it = byAddress.entrySet().iterator(); it.hasNext(); ) {
            Deque<Instant> q = it.next().getValue();
            trim(q, horizon);
            if (q.isEmpty()) {
                it.remove();
            }
        }
        return true;
    }

    private static void trim(Deque<Instant> q, Instant horizon) {
        while (!q.isEmpty() && !q.peekFirst().isAfter(horizon)) {
            q.pollFirst();
        }
    }
}
