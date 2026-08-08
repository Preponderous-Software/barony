package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One finished run, mirroring the backend's {@code RunRecord} so the proxy relays the whole record
 * rather than silently dropping the fields it does not model. The Run History panel reads
 * {@code result}, {@code turnsPlayed}, and the castle/village counts; the rest is carried through
 * for whatever the page shows next.
 */
@Getter
@Setter
public class RunRecord {
    private Long id;
    private String username;

    /** "WIN" or "LOSS", from the player's perspective. */
    private String result;

    private int turnsPlayed;
    private int castlesHeld;
    private int castlesTotal;
    private int villagesHeld;
    private int villagesTotal;
    private int armiesRemaining;
    private int soldiersRemaining;
    private Instant finishedAt;
}
