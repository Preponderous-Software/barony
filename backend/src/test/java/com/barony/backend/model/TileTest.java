package com.barony.backend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TileTest {

    @Test
    void tileCreatesWithType() {
        Tile tile = new Tile(TileType.CASTLE);
        assertEquals(TileType.CASTLE, tile.getType());
    }
    
    @Test
    void tileTypeCanBeChanged() {
        Tile tile = new Tile(TileType.EMPTY);
        assertEquals(TileType.EMPTY, tile.getType());
        
        tile.setType(TileType.VILLAGE);
        assertEquals(TileType.VILLAGE, tile.getType());
    }
    
    @Test
    void tileCanBeSetToAllTypes() {
        Tile tile = new Tile(TileType.EMPTY);
        
        tile.setType(TileType.CASTLE);
        assertEquals(TileType.CASTLE, tile.getType());
        
        tile.setType(TileType.VILLAGE);
        assertEquals(TileType.VILLAGE, tile.getType());
        
        tile.setType(TileType.EMPTY);
        assertEquals(TileType.EMPTY, tile.getType());
    }
}
