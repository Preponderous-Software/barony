package com.barony.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The exact text of a guest's game ({@link GuestGame}) that could not be read, set aside when the
 * guest chose to start a fresh game. The guest counterpart of {@link SavedGameBackup}, in a table of
 * its own so guest data never lands in an account table (see {@link GuestGame}).
 *
 * The guest is identified by their guest key, the SHA-256 (hex) of the cookie token that is also
 * {@code guest_game}'s key: never the token itself, which is a credential. A row is only ever
 * inserted, never updated by a later save.
 */
@Entity
@Table(name = "guest_game_backup")
@Getter
@Setter
@NoArgsConstructor
public class GuestGameBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The guest's key: SHA-256 (hex) of their cookie token, as in {@code guest_game}. */
    @Column(length = 64)
    private String guestKey;

    /** The guest game's text exactly as it was stored. */
    @Column(columnDefinition = "TEXT")
    private String state;

    /** When the guest last played or loaded that game (the row's lastSeenAt). */
    private Instant lastSeenAt;

    /** When it was set aside. */
    private Instant backedUpAt;

    public GuestGameBackup(String guestKey, String state, Instant lastSeenAt, Instant backedUpAt) {
        this.guestKey = guestKey;
        this.state = state;
        this.lastSeenAt = lastSeenAt;
        this.backedUpAt = backedUpAt;
    }
}
