package com.barony.backend.service;

import com.barony.backend.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameServiceTest {

    private GameService gameService;
    
    @BeforeEach
    void setUp() {
        gameService = new GameService();
    }
    
    @Test
    void gameServiceInitializesWithCorrectBoardSize() {
        GameState state = gameService.getState();
        
        assertEquals(10, state.getWidth());
        assertEquals(10, state.getHeight());
        assertEquals(0, state.getTickCount());
    }
    
    @Test
    void gameServiceInitializesWithTwoArmies() {
        GameState state = gameService.getState();
        
        assertEquals(2, state.getArmies().size());
    }
    
    @Test
    void gameServiceInitializesWithCastlesAtCorners() {
        GameState state = gameService.getState();
        
        assertEquals(TileType.CASTLE, state.getGrid()[0][0].getType());
        assertEquals(TileType.CASTLE, state.getGrid()[9][9].getType());
    }
    
    @Test
    void gameServiceInitializesWithVillages() {
        GameState state = gameService.getState();
        
        assertEquals(TileType.VILLAGE, state.getGrid()[3][3].getType());
        assertEquals(TileType.VILLAGE, state.getGrid()[6][6].getType());
    }
    
    @Test
    void tickIncrementsTickCount() {
        GameState stateBefore = gameService.getState();
        int tickBefore = stateBefore.getTickCount();
        
        gameService.tick();
        
        GameState stateAfter = gameService.getState();
        assertEquals(tickBefore + 1, stateAfter.getTickCount());
    }
    
    @Test
    void armyOnVillageGainsSoldiersDuringTick() {
        // Move an army to a village
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        int soldiersBefore = state.getArmies().get(0).getSoldiers();
        
        Command moveToVillage = new Command();
        moveToVillage.setType("MOVE");
        moveToVillage.setArmyId(armyId);
        moveToVillage.setTargetX(3);
        moveToVillage.setTargetY(3);
        gameService.executeCommand(moveToVillage);
        
        // Tick should add soldiers
        gameService.tick();
        
        state = gameService.getState();
        Army movedArmy = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(movedArmy);
        assertEquals(soldiersBefore + 1, movedArmy.getSoldiers());
    }
    
    @Test
    void executeCommandMovesArmyToTarget() {
        GameState stateBefore = gameService.getState();
        int armyId = stateBefore.getArmies().get(0).getId();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(5);
        command.setTargetY(5);
        
        gameService.executeCommand(command);
        
        GameState stateAfter = gameService.getState();
        Army movedArmy = stateAfter.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(movedArmy);
        assertEquals(5, movedArmy.getX());
        assertEquals(5, movedArmy.getY());
    }
    
    @Test
    void executeCommandRejectsOutOfBoundsMove() {
        GameState stateBefore = gameService.getState();
        int armyId = stateBefore.getArmies().get(0).getId();
        int xBefore = stateBefore.getArmies().get(0).getX();
        int yBefore = stateBefore.getArmies().get(0).getY();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(15); // Out of bounds
        command.setTargetY(15);
        
        gameService.executeCommand(command);
        
        GameState stateAfter = gameService.getState();
        Army army = stateAfter.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(xBefore, army.getX());
        assertEquals(yBefore, army.getY());
    }
    
    @Test
    void combatReducesBothArmiesSoldiers() {
        GameState state = gameService.getState();
        int army1Id = state.getArmies().get(0).getId();
        int army2Id = state.getArmies().get(1).getId();
        
        // Move both armies to same location
        Command move1 = new Command();
        move1.setType("MOVE");
        move1.setArmyId(army1Id);
        move1.setTargetX(5);
        move1.setTargetY(5);
        gameService.executeCommand(move1);
        
        Command move2 = new Command();
        move2.setType("MOVE");
        move2.setArmyId(army2Id);
        move2.setTargetX(5);
        move2.setTargetY(5);
        gameService.executeCommand(move2);
        
        int soldiers1Before = state.getArmies().stream()
            .filter(a -> a.getId() == army1Id)
            .findFirst().get().getSoldiers();
        int soldiers2Before = state.getArmies().stream()
            .filter(a -> a.getId() == army2Id)
            .findFirst().get().getSoldiers();
        
        // Tick triggers combat
        gameService.tick();
        
        state = gameService.getState();
        
        // Both armies should have reduced soldiers
        if (soldiers1Before == soldiers2Before) {
            // Equal strength - both eliminated
            assertEquals(0, state.getArmies().size());
        } else {
            // One survives with reduced soldiers
            assertEquals(1, state.getArmies().size());
            int survivingSoldiers = state.getArmies().get(0).getSoldiers();
            assertEquals(Math.abs(soldiers1Before - soldiers2Before), survivingSoldiers);
        }
    }
    
    @Test
    void defeatedArmiesAreRemoved() {
        GameState state = gameService.getState();
        int army1Id = state.getArmies().get(0).getId();
        int army2Id = state.getArmies().get(1).getId();
        
        // Move armies together
        Command move1 = new Command();
        move1.setType("MOVE");
        move1.setArmyId(army1Id);
        move1.setTargetX(5);
        move1.setTargetY(5);
        gameService.executeCommand(move1);
        
        Command move2 = new Command();
        move2.setType("MOVE");
        move2.setArmyId(army2Id);
        move2.setTargetX(5);
        move2.setTargetY(5);
        gameService.executeCommand(move2);
        
        gameService.tick();
        
        state = gameService.getState();
        
        // At least one army should be removed (if equal) or both reduced
        assertTrue(state.getArmies().size() <= 1);
    }
    
    @Test
    void executeCommandWithInvalidArmyIdDoesNothing() {
        GameState stateBefore = gameService.getState();
        int armiesBefore = stateBefore.getArmies().size();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(9999); // Non-existent army
        command.setTargetX(5);
        command.setTargetY(5);
        
        gameService.executeCommand(command);
        
        GameState stateAfter = gameService.getState();
        assertEquals(armiesBefore, stateAfter.getArmies().size());
    }
}
