package com.barony.backend.model;

public class Tile {
    private TileType type;
    private int ownerId; // 0=neutral, 1=player1, 2=player2
    private int occupationTicks; // For castle capture progress
    private int stability; // 0-100, affects soldier generation efficiency (default 100, villages only)
    private int population; // Current population, affects generation capacity (default 100, villages only)
    
    public Tile(TileType type) {
        this.type = type;
        this.ownerId = 0; // Default to neutral
        this.occupationTicks = 0;
        this.stability = 100; // Default stability
        this.population = 100; // Default population
    }
    
    public Tile(TileType type, int ownerId) {
        this.type = type;
        this.ownerId = ownerId;
        this.occupationTicks = 0;
        this.stability = 100; // Default stability
        this.population = 100; // Default population
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
    
    public int getOccupationTicks() {
        return occupationTicks;
    }
    
    public void setOccupationTicks(int occupationTicks) {
        this.occupationTicks = occupationTicks;
    }
    
    public int getStability() {
        return stability;
    }
    
    public void setStability(int stability) {
        this.stability = Math.max(0, Math.min(100, stability)); // Clamp between 0 and 100
    }
    
    public int getPopulation() {
        return population;
    }
    
    public void setPopulation(int population) {
        this.population = Math.max(0, population); // Min 0, no max limit
    }
}
