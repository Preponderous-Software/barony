package com.barony.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    private int tickCount;
    private boolean gameOver;
    private Integer winnerId;
    private boolean aiEnabled;
    private String economicPolicy;
    private String militaryPolicy;
    private String populationPolicy;
    private int lastPolicyChangeTick;
    
    public GameState(int width, int height) {
        this.grid = new Tile[width][height];
        this.armies = new ArrayList<>();
        this.tickCount = 0;
        this.gameOver = false;
        this.winnerId = null;
        this.aiEnabled = true;
        this.economicPolicy = "BALANCED_BUDGET";
        this.militaryPolicy = "STANDARD_SERVICE";
        this.populationPolicy = "STABLE_POPULATION";
        this.lastPolicyChangeTick = -15;
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
