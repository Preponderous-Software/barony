package com.barony.backend.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Tile {
    private TileType type;
    private int ownerId;
    private int occupationTicks;
    private int stability;
    private int population;
    
    public Tile(TileType type) {
        this.type = type;
        this.ownerId = 0;
        this.occupationTicks = 0;
        this.stability = 100;
        this.population = 100;
    }
    
    public Tile(TileType type, int ownerId) {
        this.type = type;
        this.ownerId = ownerId;
        this.occupationTicks = 0;
        this.stability = 100;
        this.population = 100;
    }
    
    public void setStability(int stability) {
        this.stability = Math.max(0, Math.min(110, stability));
    }
    
    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }
}
