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
 * The exact text of a {@link SavedGame} that could not be read, set aside when its player chose to
 * start a fresh game. A row is only ever inserted, never updated by a later save, so the original
 * stays available to inspect or repair. It lives in its own table, so the {@code saved_game} table
 * and every existing save are unchanged.
 */
@Entity
@Table(name = "saved_game_backup")
@Getter
@Setter
@NoArgsConstructor
public class SavedGameBackup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    /** The saved game's text exactly as it was stored. */
    @Column(columnDefinition = "TEXT")
    private String state;

    /** When the unreadable save was last written. */
    private Instant savedAt;

    /** When it was set aside. */
    private Instant backedUpAt;

    public SavedGameBackup(String username, String state, Instant savedAt, Instant backedUpAt) {
        this.username = username;
        this.state = state;
        this.savedAt = savedAt;
        this.backedUpAt = backedUpAt;
    }
}
