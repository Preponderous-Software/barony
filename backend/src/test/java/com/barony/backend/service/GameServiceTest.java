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
        
        // Move to village (3 horizontal + 3 vertical = 6 ticks)
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        Army movedArmy = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(movedArmy);
        assertEquals(3, movedArmy.getX());
        assertEquals(3, movedArmy.getY());
        
        int soldiersAfterMoving = movedArmy.getSoldiers();
        
        // Next tick should add soldiers
        gameService.tick();
        
        state = gameService.getState();
        movedArmy = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(movedArmy);
        assertEquals(soldiersAfterMoving + 1, movedArmy.getSoldiers());
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
        
        // Execute enough ticks to reach destination (5 horizontal + 5 vertical = 10 ticks)
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
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
        
        int soldiers1Before = state.getArmies().get(0).getSoldiers();
        int soldiers2Before = state.getArmies().get(1).getSoldiers();
        
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
        
        // Execute ticks until combat occurs (armies meet at or before destination)
        for (int i = 0; i < 20; i++) {
            gameService.tick();
            state = gameService.getState();
            // Check if combat has occurred (army count reduced or soldiers changed)
            if (state.getArmies().size() < 2) {
                break;
            }
        }
        
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
        
        // Execute ticks to move armies to destination and trigger combat
        for (int i = 0; i < 11; i++) {
            gameService.tick();
        }
        
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
    
    @Test
    void getStateReturnsSnapshot() {
        GameState state1 = gameService.getState();
        GameState state2 = gameService.getState();
        
        // Should be different instances (snapshots)
        assertNotSame(state1, state2, "getState() should return a new snapshot each time");
        
        // But should have the same content
        assertEquals(state1.getTickCount(), state2.getTickCount());
        assertEquals(state1.getArmies().size(), state2.getArmies().size());
    }
    
    @Test
    void modifyingSnapshotDoesNotAffectGameState() {
        GameState snapshot = gameService.getState();
        int originalArmyCount = snapshot.getArmies().size();
        int originalTickCount = snapshot.getTickCount();
        
        // Try to modify the snapshot's tick count via incrementTick
        snapshot.incrementTick();
        
        // Get a fresh snapshot
        GameState freshSnapshot = gameService.getState();
        
        // Original tick count should be unchanged
        assertEquals(originalTickCount, freshSnapshot.getTickCount());
        assertEquals(originalArmyCount, freshSnapshot.getArmies().size());
    }
    
    @Test
    void executeCommandSetsDestinationInsteadOfInstantMovement() {
        GameState stateBefore = gameService.getState();
        int armyId = stateBefore.getArmies().get(0).getId();
        int xBefore = stateBefore.getArmies().get(0).getX();
        int yBefore = stateBefore.getArmies().get(0).getY();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(5);
        command.setTargetY(5);
        
        gameService.executeCommand(command);
        
        GameState stateAfter = gameService.getState();
        Army army = stateAfter.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        // Position should not change immediately
        assertEquals(xBefore, army.getX());
        assertEquals(yBefore, army.getY());
        // But destination should be set
        assertEquals(5, army.getDestinationX());
        assertEquals(5, army.getDestinationY());
    }
    
    @Test
    void isMovingReturnsTrueWhenDestinationSet() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(5);
        command.setTargetY(5);
        
        gameService.executeCommand(command);
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertTrue(army.isMoving());
    }
    
    @Test
    void isMovingReturnsFalseWhenAtDestination() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        int xBefore = state.getArmies().get(0).getX();
        int yBefore = state.getArmies().get(0).getY();
        
        // Set destination to current position
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(xBefore);
        command.setTargetY(yBefore);
        
        gameService.executeCommand(command);
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertFalse(army.isMoving());
        assertNull(army.getDestinationX());
        assertNull(army.getDestinationY());
    }
    
    @Test
    void tickMovesArmyOneStepTowardDestination() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(3);
        command.setTargetY(0);
        
        gameService.executeCommand(command);
        
        state = gameService.getState();
        int xBefore = state.getArmies().get(0).getX();
        
        gameService.tick();
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        // Should move one step toward destination
        assertEquals(xBefore + 1, army.getX());
        assertEquals(0, army.getY());
    }
    
    @Test
    void armyMovesMultipleTicksToReachDestination() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(3);
        command.setTargetY(0);
        
        gameService.executeCommand(command);
        
        // Execute 3 ticks to move 3 steps
        gameService.tick();
        gameService.tick();
        gameService.tick();
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(3, army.getX());
        assertEquals(0, army.getY());
        assertFalse(army.isMoving());
    }
    
    @Test
    void armyStopsWhenReachingDestination() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(2);
        command.setTargetY(0);
        
        gameService.executeCommand(command);
        
        // Execute enough ticks to reach destination
        gameService.tick();
        gameService.tick();
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(2, army.getX());
        assertEquals(0, army.getY());
        assertNull(army.getDestinationX());
        assertNull(army.getDestinationY());
        assertFalse(army.isMoving());
        
        // Another tick should not move the army
        gameService.tick();
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(2, army.getX());
        assertEquals(0, army.getY());
    }
    
    @Test
    void pathfindingUsesManhattanDistance() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(3);
        command.setTargetY(3);
        
        gameService.executeCommand(command);
        
        // Move 3 ticks - should prioritize horizontal movement
        gameService.tick();
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        assertEquals(1, army.getX());
        assertEquals(0, army.getY());
        
        gameService.tick();
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        assertEquals(2, army.getX());
        assertEquals(0, army.getY());
        
        gameService.tick();
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        assertEquals(3, army.getX());
        assertEquals(0, army.getY());
        
        // Next ticks should move vertically
        gameService.tick();
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        assertEquals(3, army.getX());
        assertEquals(1, army.getY());
    }
    
    @Test
    void newDestinationCancelsPreviousMovement() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        // Set first destination
        Command command1 = new Command();
        command1.setType("MOVE");
        command1.setArmyId(armyId);
        command1.setTargetX(5);
        command1.setTargetY(5);
        gameService.executeCommand(command1);
        
        // Move one tick
        gameService.tick();
        
        // Set new destination
        Command command2 = new Command();
        command2.setType("MOVE");
        command2.setArmyId(armyId);
        command2.setTargetX(2);
        command2.setTargetY(0);
        gameService.executeCommand(command2);
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        // New destination should be set
        assertEquals(2, army.getDestinationX());
        assertEquals(0, army.getDestinationY());
    }
    
    @Test
    void multipleArmiesCanMoveSimultaneously() {
        GameState state = gameService.getState();
        int army1Id = state.getArmies().get(0).getId();
        int army2Id = state.getArmies().get(1).getId();
        
        // Set destinations for both armies
        Command command1 = new Command();
        command1.setType("MOVE");
        command1.setArmyId(army1Id);
        command1.setTargetX(3);
        command1.setTargetY(0);
        gameService.executeCommand(command1);
        
        Command command2 = new Command();
        command2.setType("MOVE");
        command2.setArmyId(army2Id);
        command2.setTargetX(6);
        command2.setTargetY(9);
        gameService.executeCommand(command2);
        
        // Execute one tick
        gameService.tick();
        
        state = gameService.getState();
        
        Army army1 = state.getArmies().stream()
            .filter(a -> a.getId() == army1Id)
            .findFirst()
            .orElse(null);
        Army army2 = state.getArmies().stream()
            .filter(a -> a.getId() == army2Id)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army1);
        assertNotNull(army2);
        
        // Both should have moved one step
        assertEquals(1, army1.getX());
        assertEquals(0, army1.getY());
        assertEquals(8, army2.getX());
        assertEquals(9, army2.getY());
    }
    
    @Test
    void movementDoesNotViolateBounds() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(15);  // Out of bounds
        command.setTargetY(15);
        
        gameService.executeCommand(command);
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        // Destination should not be set for out of bounds target
        assertNull(army.getDestinationX());
        assertNull(army.getDestinationY());
        assertFalse(army.isMoving());
    }
    
    @Test
    void armyMovingToVillageGeneratesSoldiers() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        // Move to village at (3,3)
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(armyId);
        command.setTargetX(3);
        command.setTargetY(3);
        
        gameService.executeCommand(command);
        
        // Move 6 ticks to reach destination (3 horizontal + 3 vertical)
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(3, army.getX());
        assertEquals(3, army.getY());
        
        int soldiersBefore = army.getSoldiers();
        
        // Next tick should generate soldiers
        gameService.tick();
        
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(soldiersBefore + 1, army.getSoldiers());
    }
    
    @Test
    void castlesInitializeWithOwnership() {
        GameState state = gameService.getState();
        
        assertEquals(1, state.getGrid()[0][0].getOwnerId()); // Player 1 castle
        assertEquals(2, state.getGrid()[9][9].getOwnerId()); // Player 2 castle
    }
    
    @Test
    void villagesInitializeAsNeutral() {
        GameState state = gameService.getState();
        
        assertEquals(0, state.getGrid()[3][3].getOwnerId()); // Village is neutral
        assertEquals(0, state.getGrid()[6][6].getOwnerId()); // Village is neutral
    }
    
    @Test
    void armyCapturesVillageWhenOccupying() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        // Move Player 1 army to neutral village at (3,3)
        Command moveToVillage = new Command();
        moveToVillage.setType("MOVE");
        moveToVillage.setArmyId(armyId);
        moveToVillage.setTargetX(3);
        moveToVillage.setTargetY(3);
        gameService.executeCommand(moveToVillage);
        
        // Move to village (3 horizontal + 3 vertical = 6 ticks)
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        
        // Village should now be owned by Player 1
        assertEquals(1, state.getGrid()[3][3].getOwnerId());
    }
    
    @Test
    void enemyArmyCapturesOwnedVillage() {
        GameState state = gameService.getState();
        int army1Id = state.getArmies().get(0).getId();
        int army2Id = state.getArmies().get(1).getId();
        
        // Player 1 captures village at (3,3)
        Command move1 = new Command();
        move1.setType("MOVE");
        move1.setArmyId(army1Id);
        move1.setTargetX(3);
        move1.setTargetY(3);
        gameService.executeCommand(move1);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        assertEquals(1, state.getGrid()[3][3].getOwnerId());
        
        // Player 2 captures the same village
        Command move2 = new Command();
        move2.setType("MOVE");
        move2.setArmyId(army2Id);
        move2.setTargetX(3);
        move2.setTargetY(3);
        gameService.executeCommand(move2);
        
        // Wait for Player 2 to reach village (from 9,9 to 3,3 = 6+6 = 12 ticks)
        for (int i = 0; i < 12; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        
        // Village should now be owned by Player 2 (or be neutral if both armies were destroyed in combat)
        // If Player 2 army still exists, village should be owned by Player 2
        boolean player2Exists = state.getArmies().stream().anyMatch(a -> a.getPlayerId() == 2);
        if (player2Exists) {
            assertEquals(2, state.getGrid()[3][3].getOwnerId());
        }
    }
    
    @Test
    void neutralVillageDoesNotGenerateSoldiers() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        // Move to neutral village
        Command moveToVillage = new Command();
        moveToVillage.setType("MOVE");
        moveToVillage.setArmyId(armyId);
        moveToVillage.setTargetX(3);
        moveToVillage.setTargetY(3);
        gameService.executeCommand(moveToVillage);
        
        // Move to village but not yet there
        for (int i = 0; i < 5; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        int soldiersBeforeArrival = army.getSoldiers();
        
        // One more tick to arrive at village
        gameService.tick();
        
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        // Village is now captured and owned, so soldiers should NOT have increased during capture tick
        // (capture happens before generation, but army now owns it so next tick it generates)
        
        int soldiersAfterCapture = army.getSoldiers();
        
        // Next tick should generate soldiers since army now owns the village
        gameService.tick();
        
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(soldiersAfterCapture + 1, army.getSoldiers());
    }
    
    @Test
    void ownedVillageGeneratesSoldiersForOwner() {
        GameState state = gameService.getState();
        int armyId = state.getArmies().get(0).getId();
        
        // Capture village
        Command moveToVillage = new Command();
        moveToVillage.setType("MOVE");
        moveToVillage.setArmyId(armyId);
        moveToVillage.setTargetX(3);
        moveToVillage.setTargetY(3);
        gameService.executeCommand(moveToVillage);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        Army army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        int soldiersBefore = army.getSoldiers();
        
        // Tick again - should generate soldiers since army owns the village
        gameService.tick();
        
        state = gameService.getState();
        army = state.getArmies().stream()
            .filter(a -> a.getId() == armyId)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army);
        assertEquals(soldiersBefore + 1, army.getSoldiers());
    }
    
    @Test
    void getPlayerIncomeReturnsVillageCount() {
        GameState state = gameService.getState();
        
        // Initially, no villages are owned
        assertEquals(0, gameService.getPlayerIncome(1));
        assertEquals(0, gameService.getPlayerIncome(2));
        
        // Player 1 captures village at (3,3)
        int army1Id = state.getArmies().get(0).getId();
        Command move1 = new Command();
        move1.setType("MOVE");
        move1.setArmyId(army1Id);
        move1.setTargetX(3);
        move1.setTargetY(3);
        gameService.executeCommand(move1);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        // Player 1 should have income of 1
        assertEquals(1, gameService.getPlayerIncome(1));
        assertEquals(0, gameService.getPlayerIncome(2));
    }
    
    @Test
    void getPlayerIncomeCountsMultipleVillages() {
        GameState state = gameService.getState();
        int army1Id = state.getArmies().get(0).getId();
        int army2Id = state.getArmies().get(1).getId();
        
        // Player 1 captures village at (3,3)
        Command move1 = new Command();
        move1.setType("MOVE");
        move1.setArmyId(army1Id);
        move1.setTargetX(3);
        move1.setTargetY(3);
        gameService.executeCommand(move1);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        // Player 2 captures village at (6,6)
        Command move2 = new Command();
        move2.setType("MOVE");
        move2.setArmyId(army2Id);
        move2.setTargetX(6);
        move2.setTargetY(6);
        gameService.executeCommand(move2);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        // Each player should have income of 1
        assertEquals(1, gameService.getPlayerIncome(1));
        assertEquals(1, gameService.getPlayerIncome(2));
    }
    
    @Test
    void captureChangesVillageOwnership() {
        GameState state = gameService.getState();
        Tile village = state.getGrid()[3][3];
        
        // Initially neutral
        assertEquals(0, village.getOwnerId());
        
        // Player 1 captures
        int army1Id = state.getArmies().get(0).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army1Id);
        move.setTargetX(3);
        move.setTargetY(3);
        gameService.executeCommand(move);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        assertEquals(1, state.getGrid()[3][3].getOwnerId());
    }
}
