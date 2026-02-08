package com.barony.frontend.model;

public class Tile {
    private TileType type;
    private int ownerId; // 0=neutral, 1=player1, 2=player2
    private int occupationTicks; // For castle capture progress
    
    public TileType getType() {
        return type;
    }
    
    public void setType(TileType type) {
        this.type = type;
    }
    
    public int getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(int ownerId) {
        this.ownerId = ownerId;
    }
    
    public int getOccupationTicks() {
        return occupationTicks;
    }
    
    public void setOccupationTicks(int occupationTicks) {
        this.occupationTicks = occupationTicks;
    }
}
