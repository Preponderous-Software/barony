package com.barony.backend.model;

public class Tile {
    private TileType type;
    private int ownerId; // 0=neutral, 1=player1, 2=player2
    
    public Tile(TileType type) {
        this.type = type;
        this.ownerId = 0; // Default to neutral
    }
    
    public Tile(TileType type, int ownerId) {
        this.type = type;
        this.ownerId = ownerId;
    }
    
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
}
