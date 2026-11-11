package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * An engagement waiting to be fought on the 3D battlefield or auto-resolved, as the backend
 * reports it in the game state (see the backend's PendingBattle). Side 0 is the player's armies.
 */
@Getter
@Setter
public class PendingBattle {
    private int id;
    private int x;
    private int y;
    private TileType terrain;
    private int seed;
    private int tick;
    private List<Integer> side0ArmyIds;
    private List<Integer> side1ArmyIds;
    private int side0Soldiers;
    private int side1Soldiers;
    private int side0Morale;
}
