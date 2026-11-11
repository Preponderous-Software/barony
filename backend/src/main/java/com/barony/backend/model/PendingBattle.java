package com.barony.backend.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An engagement the player's armies have walked into this turn, waiting to be fought out as a
 * field battle (the web client's /battle page) or resolved by the map's own rule.
 *
 * <p>While any battle is pending the turn is paused just before combat: captures, the win check,
 * desertion and respawns run once every battle is resolved (see {@code GameService}). The record
 * keeps what the battle starts from, so the result can be checked against it: side 0 is always
 * player 1's armies on the tile and side 1 the enemy's.
 */
@Getter
@Setter
@NoArgsConstructor
public class PendingBattle {
    private int id;
    private int x;
    private int y;
    private TileType terrain;
    /** Seeds the field battle, so the same engagement is fought on the same field. */
    private int seed;
    private int tick;
    private List<Integer> side0ArmyIds = new ArrayList<>();
    private List<Integer> side1ArmyIds = new ArrayList<>();
    private int side0Soldiers;
    private int side1Soldiers;
    /** Player 1's morale on the tile, weighted by soldiers (Barony counts morale for player 1 only). */
    private int side0Morale;

    public PendingBattle(PendingBattle other) {
        this.id = other.id;
        this.x = other.x;
        this.y = other.y;
        this.terrain = other.terrain;
        this.seed = other.seed;
        this.tick = other.tick;
        this.side0ArmyIds = new ArrayList<>(other.side0ArmyIds);
        this.side1ArmyIds = new ArrayList<>(other.side1ArmyIds);
        this.side0Soldiers = other.side0Soldiers;
        this.side1Soldiers = other.side1Soldiers;
        this.side0Morale = other.side0Morale;
    }
}
