package com.barony.backend.model;

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
 * A durable record of one finished run, written once when a player's game reaches game-over so it
 * survives a reset and a backend restart (unlike {@link SavedGame}, which only ever holds the
 * current in-progress game).
 */
@Entity
@Table(name = "run_record")
@Getter
@Setter
@NoArgsConstructor
public class RunRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    /** "WIN" or "LOSS", from the player's (player 1's) perspective. */
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
