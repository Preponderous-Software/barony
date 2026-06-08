package com.barony.backend.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Tile {
    private TileType type;
    private int ownerId; // 0=neutral, 1=player1, 2=player2
    private int occupationTicks; // For castle capture progress
    private int stability; // 0-110 (clamped), affects soldier generation efficiency (default 100, villages only; up to 110 with bonuses)
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
    
    public void setStability(int stability) {
        this.stability = Math.max(0, Math.min(110, stability)); // Clamp between 0 and 110 (allow bonus)
    }
    
    public void setPopulation(int population) {
        this.population = Math.max(0, population); // Min 0, no max limit
    }
}
