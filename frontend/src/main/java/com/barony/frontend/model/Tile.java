package com.barony.frontend.model;

public class Tile {
    private TileType type;
    private int ownerId; // 0=neutral, 1=player1, 2=player2
    private int occupationTicks; // For castle capture progress
    private int stability; // 0-110, affects soldier generation efficiency; policies can raise it above 100 up to the 110 cap (default 100, villages only)
    private int population; // Current population, affects generation capacity (default 100, villages only)
    
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
        this.stability = stability;
    }
    
    public int getPopulation() {
        return population;
    }
    
    public void setPopulation(int population) {
        this.population = population;
    }
}
