package com.barony.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    private int tickCount;
    private boolean gameOver;
    private Integer winnerId; // null if game not over, player ID if game over
    
    public GameState(int width, int height) {
        this.grid = new Tile[width][height];
        this.armies = new ArrayList<>();
        this.tickCount = 0;
        this.gameOver = false;
        this.winnerId = null;
        
        // Initialize grid with empty tiles
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = new Tile(TileType.EMPTY);
            }
        }
    }
    
    public Tile[][] getGrid() {
        return grid;
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
    
    public int getTickCount() {
        return tickCount;
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
    
    public boolean isGameOver() {
        return gameOver;
    }
    
    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }
    
    public Integer getWinnerId() {
        return winnerId;
    }
    
    public void setWinnerId(Integer winnerId) {
        this.winnerId = winnerId;
    }
}
