package com.barony.webclient.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
    /** What the land is on a map built from a real place (snow, sand...); null on a random map. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String ground;
}
