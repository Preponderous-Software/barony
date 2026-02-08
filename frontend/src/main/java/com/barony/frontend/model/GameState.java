package com.barony.frontend.model;

import java.util.List;

public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    private int tickCount;
    
    public Tile[][] getGrid() {
        return grid;
    }
    
    public void setGrid(Tile[][] grid) {
        this.grid = grid;
    }
    
    public List<Army> getArmies() {
        return armies;
    }
    
    public void setArmies(List<Army> armies) {
        this.armies = armies;
    }
    
    public int getTickCount() {
        return tickCount;
    }
    
    public void setTickCount(int tickCount) {
        this.tickCount = tickCount;
    }
}
