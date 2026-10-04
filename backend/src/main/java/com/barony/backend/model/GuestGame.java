package com.barony.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A guest's game. Guests have no UserAuth account, so their data lives in this table of its own and
 * never in {@code saved_game}, {@code run_record} or {@code user_preferences}, which belong to real
 * accounts. Keeping guests in a separate table is what makes the isolation structural: no guest code
 * path holds a repository for an account table, and pruning idle guests is a delete on this table
 * only.
 *
 * The key is the SHA-256 (hex) of the random token in the guest's HttpOnly cookie, not the token
 * itself, so a copy of this table cannot be replayed as cookies. A guest's finished runs are kept
 * in this row too (a JSON list plus a running tally), so a guest is exactly one row.
 */
@Entity
@Table(name = "guest_game")
@Getter
@Setter
@NoArgsConstructor
public class GuestGame {

    @Id
    @Column(length = 64)
    private String guestKey;

    @Column(columnDefinition = "TEXT")
    private String state;

    /** The guest's most recent finished runs (JSON list of run records), newest first. */
    @Column(columnDefinition = "TEXT")
    private String runs;

    private int wins;
    private int losses;

    private Instant createdAt;

    /** Last time the guest played or loaded their game; idle guests are pruned from this. */
    private Instant lastSeenAt;

    public GuestGame(String guestKey) {
        this.guestKey = guestKey;
    }
}
