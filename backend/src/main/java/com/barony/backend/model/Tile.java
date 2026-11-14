package com.barony.backend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Tile {
    private TileType type;
    private int ownerId;
    private int occupationTicks;
    private int stability;
    private int population;
    /**
     * What the land is like on this tile (plain, forest, snow, sand, stone, badland, ice, water,
     * built or farm) on a map built from a world package; null on a random map and in every save
     * made before real maps, and left out of the JSON then. Only the battlefield's look reads it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String ground;
    
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
