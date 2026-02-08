package com.barony.frontend.model;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    @Test
    void gameStateCanBeCreated() {
        GameState state = new GameState();
        assertNotNull(state);
    }
    
    @Test
    void gameStateGridCanBeSetAndRetrieved() {
        GameState state = new GameState();
        Tile[][] grid = new Tile[5][5];
        
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                grid[i][j] = new Tile();
                grid[i][j].setType(TileType.EMPTY);
            }
        }
        
        state.setGrid(grid);
        assertNotNull(state.getGrid());
        assertEquals(5, state.getGrid().length);
    }
    
    @Test
    void gameStateArmiesCanBeSetAndRetrieved() {
        GameState state = new GameState();
        List<Army> armies = new ArrayList<>();
        
        Army army1 = new Army();
        army1.setId(1);
        armies.add(army1);
        
        Army army2 = new Army();
        army2.setId(2);
        armies.add(army2);
        
        state.setArmies(armies);
        assertNotNull(state.getArmies());
        assertEquals(2, state.getArmies().size());
    }
    
    @Test
    void gameStateTickCountCanBeSetAndRetrieved() {
        GameState state = new GameState();
        
        state.setTickCount(0);
        assertEquals(0, state.getTickCount());
        
        state.setTickCount(10);
        assertEquals(10, state.getTickCount());
    }
    
    @Test
    void gameStateCanHandleEmptyArmiesList() {
        GameState state = new GameState();
        List<Army> armies = new ArrayList<>();
        
        state.setArmies(armies);
        assertNotNull(state.getArmies());
        assertEquals(0, state.getArmies().size());
    }
    
    @Test
    void gameStateTickCountCanIncrement() {
        GameState state = new GameState();
        state.setTickCount(5);
        
        // Simulate incrementing
        state.setTickCount(state.getTickCount() + 1);
        assertEquals(6, state.getTickCount());
    }
}
