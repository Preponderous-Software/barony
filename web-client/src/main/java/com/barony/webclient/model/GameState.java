package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    // Field battles: the engagements waiting on the player, and whether turns pause for them.
    private List<PendingBattle> pendingBattles;
    private Integer nextBattleId;
    private Boolean fieldBattles;
    private int tickCount;
    private boolean gameOver;
    private Integer winnerId;
    private boolean aiEnabled;
    private String economicPolicy;
    private String militaryPolicy;
    private String populationPolicy;
    private int lastPolicyChangeTick;
    
    public int getWidth() {
        return grid != null ? grid.length : 0;
    }
    
    public int getHeight() {
        return grid != null && grid.length > 0 ? grid[0].length : 0;
    }
}
