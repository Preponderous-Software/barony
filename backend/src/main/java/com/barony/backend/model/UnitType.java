package com.barony.backend.model;

/**
 * The kinds of soldier an army can train its infantry into. Infantry itself is not listed: it is
 * whatever part of an army is not archers or cavalry.
 */
public enum UnitType {
    /** Trained at a village the player owns, two per tick per army. */
    ARCHERS,
    /** Trained at a castle the player owns, one per tick per army. */
    CAVALRY;

    /** How many infantry one army converts per tick. */
    public int perTick() {
        return this == ARCHERS ? 2 : 1;
    }

    /** The tile an army must stand on, owned by its player, to train this type. */
    public TileType trainedAt() {
        return this == ARCHERS ? TileType.VILLAGE : TileType.CASTLE;
    }
}
