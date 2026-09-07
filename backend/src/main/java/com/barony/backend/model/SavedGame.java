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
 * A player's saved game, persisted as JSON keyed by username so it survives backend restarts.
 * Storing the serialized {@link GameState} as text (rather than a relational mapping of the whole
 * board) keeps persistence resilient to changes in the game model.
 */
@Entity
@Table(name = "saved_game")
@Getter
@Setter
@NoArgsConstructor
public class SavedGame {

    @Id
    private String username;

    @Column(columnDefinition = "TEXT")
    private String state;

    private Instant updatedAt;

    public SavedGame(String username) {
        this.username = username;
    }
}
