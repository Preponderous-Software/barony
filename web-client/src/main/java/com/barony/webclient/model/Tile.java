package com.barony.webclient.model;

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
}
