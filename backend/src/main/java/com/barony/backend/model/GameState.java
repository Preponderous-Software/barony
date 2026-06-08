package com.barony.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    private int tickCount;
    private boolean gameOver;
    private Integer winnerId; // null if game not over, player ID if game over
    private boolean aiEnabled; // AI opponent enabled/disabled
    private String economicPolicy; // Current economic policy (Player 1 only)
    private String militaryPolicy; // Current military policy (Player 1 only)
    private String populationPolicy; // Current population policy (Player 1 only)
    private int lastPolicyChangeTick; // Tick when last policy was changed
    
    public GameState(int width, int height) {
        this.grid = new Tile[width][height];
        this.armies = new ArrayList<>();
        this.tickCount = 0;
        this.gameOver = false;
        this.winnerId = null;
        this.aiEnabled = true; // AI enabled by default
        this.economicPolicy = "BALANCED_BUDGET"; // Default policies
        this.militaryPolicy = "STANDARD_SERVICE";
        this.populationPolicy = "STABLE_POPULATION";
        this.lastPolicyChangeTick = -15; // Allow immediate first policy change
        
        // Initialize grid with empty tiles
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = new Tile(TileType.EMPTY);
            }
        }
    }

    public List<Army> getArmies() {
        return new ArrayList<>(armies);
    }
    
    // Internal method for direct access to armies list for modifications
    // JsonIgnore prevents this from being serialized in API responses
    @JsonIgnore
    public List<Army> getArmiesInternal() {
        return armies;
    }

    
    public void incrementTick() {
        this.tickCount++;
    }
    
    public int getWidth() {
        return grid.length;
    }
    
    public int getHeight() {
        return grid.length > 0 ? grid[0].length : 0;
    }

}
