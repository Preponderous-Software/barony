package com.barony.backend.model;

import java.util.ArrayList;
import java.util.List;

public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    private int tickCount;
    
    public GameState(int width, int height) {
        this.grid = new Tile[width][height];
        this.armies = new ArrayList<>();
        this.tickCount = 0;
        
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
}
