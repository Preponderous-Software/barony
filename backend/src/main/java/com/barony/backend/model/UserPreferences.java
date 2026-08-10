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
 * A player's interface preferences (sidebar arrangement, panel open/closed state, display
 * settings), persisted as JSON keyed by username so they follow the account rather than living in
 * one browser's storage. The payload is kept opaque text for the same reason a {@link SavedGame}
 * is: the page owns the shape, so a new preference needs no change here.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreferences {

    @Id
    private String username;

    @Column(columnDefinition = "TEXT")
    private String preferences;

    private Instant updatedAt;

    public UserPreferences(String username) {
        this.username = username;
    }
}
