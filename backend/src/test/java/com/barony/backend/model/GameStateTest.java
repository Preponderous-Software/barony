package com.barony.backend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    @Test
    void gameStateInitializesWithCorrectDimensions() {
        GameState state = new GameState(10, 10);
        
        assertEquals(10, state.getWidth());
        assertEquals(10, state.getHeight());
        assertEquals(0, state.getTickCount());
    }
    
    @Test
    void gridInitializesWithEmptyTiles() {
        GameState state = new GameState(5, 5);
        Tile[][] grid = state.getGrid();
        
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                assertNotNull(grid[x][y]);
                assertEquals(TileType.EMPTY, grid[x][y].getType());
            }
        }
    }
    
    @Test
    void tickCountIncrements() {
        GameState state = new GameState(5, 5);
        
        assertEquals(0, state.getTickCount());
        state.incrementTick();
        assertEquals(1, state.getTickCount());
        state.incrementTick();
        assertEquals(2, state.getTickCount());
    }
    
    @Test
    void getArmiesReturnsDefensiveCopy() {
        GameState state = new GameState(5, 5);
        Army army = new Army(0, 0, 10, 1);
        state.getArmiesInternal().add(army);
        
        var armies1 = state.getArmies();
        var armies2 = state.getArmies();
        
        assertNotSame(armies1, armies2, "getArmies() should return a new list each time");
        assertEquals(armies1.size(), armies2.size());
    }
}
