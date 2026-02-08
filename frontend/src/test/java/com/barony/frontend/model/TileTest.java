package com.barony.frontend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TileTest {

    @Test
    void tileCanBeCreated() {
        Tile tile = new Tile();
        assertNotNull(tile);
    }
    
    @Test
    void tileTypeCanBeSetAndRetrieved() {
        Tile tile = new Tile();
        
        tile.setType(TileType.CASTLE);
        assertEquals(TileType.CASTLE, tile.getType());
        
        tile.setType(TileType.VILLAGE);
        assertEquals(TileType.VILLAGE, tile.getType());
        
        tile.setType(TileType.EMPTY);
        assertEquals(TileType.EMPTY, tile.getType());
    }
}
