package com.barony.frontend.model;

import java.util.List;

public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    private int tickCount;
    private boolean gameOver;
    private Integer winnerId; // null if game not over, player ID if game over
    
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
