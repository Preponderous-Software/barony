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
    
    @Test
    void tileDefaultsToNeutralOwnership() {
        Tile tile = new Tile(TileType.VILLAGE);
        assertEquals(0, tile.getOwnerId());
    }
    
    @Test
    void tileCanBeCreatedWithOwner() {
        Tile tile = new Tile(TileType.CASTLE, 1);
        assertEquals(TileType.CASTLE, tile.getType());
        assertEquals(1, tile.getOwnerId());
    }
    
    @Test
    void tileOwnershipCanBeChanged() {
        Tile tile = new Tile(TileType.VILLAGE);
        assertEquals(0, tile.getOwnerId());
        
        tile.setOwnerId(1);
        assertEquals(1, tile.getOwnerId());
        
        tile.setOwnerId(2);
        assertEquals(2, tile.getOwnerId());
    }
    
    @Test
    void castleCanHaveOwnership() {
        Tile castle = new Tile(TileType.CASTLE, 1);
        assertEquals(1, castle.getOwnerId());
    }
    
    @Test
    void villageCanHaveOwnership() {
        Tile village = new Tile(TileType.VILLAGE, 2);
        assertEquals(2, village.getOwnerId());
    }
}
