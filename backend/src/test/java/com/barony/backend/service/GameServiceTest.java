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
        // Disable AI for most tests to avoid interference
        gameService.setAiEnabled(false);
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
        
        // Move Player 1 army away to avoid combat
        Command move1Away = new Command();
        move1Away.setType("MOVE");
        move1Away.setArmyId(army1Id);
        move1Away.setTargetX(0);
        move1Away.setTargetY(0);
        gameService.executeCommand(move1Away);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        // Village should still be owned by Player 1 (ownership persists when abandoned)
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
        
        // Village should now be owned by Player 2 since Player 2 army occupies it
        assertEquals(2, state.getGrid()[3][3].getOwnerId());
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
    
    @Test
    void splitArmyCreatesNewArmyAtSameLocation() {
        GameState state = gameService.getState();
        Army originalArmy = state.getArmies().get(0);
        int originalId = originalArmy.getId();
        int originalSoldiers = originalArmy.getSoldiers();
        int originalX = originalArmy.getX();
        int originalY = originalArmy.getY();
        
        // Split 3 soldiers from the army
        gameService.splitArmy(originalId, 3);
        
        state = gameService.getState();
        assertEquals(3, state.getArmies().size()); // Should now have 3 armies
        
        // Find the original army and verify its soldier count decreased
        Army updatedOriginal = null;
        Army newArmy = null;
        for (Army army : state.getArmies()) {
            if (army.getId() == originalId) {
                updatedOriginal = army;
            } else if (army.getX() == originalX && army.getY() == originalY && army.getId() != originalId) {
                newArmy = army;
            }
        }
        
        assertNotNull(updatedOriginal);
        assertNotNull(newArmy);
        assertEquals(originalSoldiers - 3, updatedOriginal.getSoldiers());
        assertEquals(3, newArmy.getSoldiers());
        assertEquals(originalX, newArmy.getX());
        assertEquals(originalY, newArmy.getY());
        assertEquals(updatedOriginal.getPlayerId(), newArmy.getPlayerId());
    }
    
    @Test
    void splitArmyValidatesMinimumSoldiers() {
        GameState state = gameService.getState();
        Army army = state.getArmies().get(0);
        int originalSoldiers = army.getSoldiers();
        
        // Try to split all soldiers (invalid - would leave 0 in original)
        gameService.splitArmy(army.getId(), originalSoldiers);
        
        state = gameService.getState();
        assertEquals(2, state.getArmies().size()); // Should still be 2, no split occurred
        
        // Verify soldier count unchanged
        Army sameArmy = null;
        for (Army a : state.getArmies()) {
            if (a.getId() == army.getId()) {
                sameArmy = a;
                break;
            }
        }
        assertNotNull(sameArmy);
        assertEquals(originalSoldiers, sameArmy.getSoldiers());
    }
    
    @Test
    void splitArmyValidatesPositiveSoldierCount() {
        GameState state = gameService.getState();
        Army army = state.getArmies().get(0);
        int originalSoldiers = army.getSoldiers();
        
        // Try to split 0 soldiers (invalid)
        gameService.splitArmy(army.getId(), 0);
        
        state = gameService.getState();
        assertEquals(2, state.getArmies().size()); // Should still be 2, no split occurred
        
        // Try to split negative soldiers (invalid)
        gameService.splitArmy(army.getId(), -5);
        
        state = gameService.getState();
        assertEquals(2, state.getArmies().size()); // Should still be 2, no split occurred
    }
    
    @Test
    void splitArmyHandlesInvalidArmyId() {
        GameState state = gameService.getState();
        int armyCount = state.getArmies().size();
        
        // Try to split from non-existent army
        gameService.splitArmy(9999, 5);
        
        state = gameService.getState();
        assertEquals(armyCount, state.getArmies().size()); // No new army created
    }
    
    @Test
    void coLocatedFriendlyArmiesMergeAutomatically() {
        // Clear the board and add two armies at same location for player 1
        gameService = new GameService();
        GameState state = gameService.getState();
        
        // Get player 1's starting army and split it
        Army p1Army = null;
        for (Army army : state.getArmies()) {
            if (army.getPlayerId() == 1) {
                p1Army = army;
                break;
            }
        }
        assertNotNull(p1Army);
        
        // Split the army
        gameService.splitArmy(p1Army.getId(), 3);
        
        state = gameService.getState();
        assertEquals(3, state.getArmies().size()); // Now 3 armies (P1 split + P2)
        
        // Tick should merge the two P1 armies
        gameService.tick();
        
        state = gameService.getState();
        assertEquals(2, state.getArmies().size()); // Back to 2 armies (merged P1 + P2)
        
        // Verify the merged army has combined soldier count
        Army mergedP1Army = null;
        for (Army army : state.getArmies()) {
            if (army.getPlayerId() == 1) {
                mergedP1Army = army;
                break;
            }
        }
        assertNotNull(mergedP1Army);
        assertEquals(10, mergedP1Army.getSoldiers()); // 7 + 3 = 10 (castles don't generate soldiers)
    }
    
    @Test
    void mergeKeepsLowestArmyId() {
        gameService = new GameService();
        GameState state = gameService.getState();
        
        Army p1Army = null;
        for (Army army : state.getArmies()) {
            if (army.getPlayerId() == 1) {
                p1Army = army;
                break;
            }
        }
        assertNotNull(p1Army);
        int originalId = p1Army.getId();
        
        // Split to create a new army with higher ID
        gameService.splitArmy(p1Army.getId(), 3);
        
        // Tick to trigger merge
        gameService.tick();
        
        state = gameService.getState();
        
        // Verify the surviving army has the original (lower) ID
        Army survivingArmy = null;
        for (Army army : state.getArmies()) {
            if (army.getPlayerId() == 1) {
                survivingArmy = army;
                break;
            }
        }
        assertNotNull(survivingArmy);
        assertEquals(originalId, survivingArmy.getId());
    }
    
    @Test
    void enemyArmiesDoNotMerge() {
        gameService = new GameService();
        gameService.setAiEnabled(false); // Disable AI for this test
        
        // Move both armies to same location
        GameState state = gameService.getState();
        Army p1Army = state.getArmies().get(0);
        Army p2Army = state.getArmies().get(1);
        
        Command moveP1 = new Command();
        moveP1.setType("MOVE");
        moveP1.setArmyId(p1Army.getId());
        moveP1.setTargetX(5);
        moveP1.setTargetY(5);
        gameService.executeCommand(moveP1);
        
        Command moveP2 = new Command();
        moveP2.setType("MOVE");
        moveP2.setArmyId(p2Army.getId());
        moveP2.setTargetX(5);
        moveP2.setTargetY(5);
        gameService.executeCommand(moveP2);
        
        // Move armies to same location
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        // Enemy armies fight instead of merging, both should be destroyed (10 vs 10)
        assertEquals(0, state.getArmies().size());
    }
    
    @Test
    void splitCommandExecutedThroughExecuteCommand() {
        GameState state = gameService.getState();
        Army army = state.getArmies().get(0);
        int originalSoldiers = army.getSoldiers();
        
        Command splitCmd = new Command();
        splitCmd.setType("SPLIT");
        splitCmd.setArmyId(army.getId());
        splitCmd.setSplitAmount(4);
        
        gameService.executeCommand(splitCmd);
        
        state = gameService.getState();
        assertEquals(3, state.getArmies().size()); // 3 armies now
        
        // Find the original army and verify it lost soldiers
        Army updatedArmy = null;
        for (Army a : state.getArmies()) {
            if (a.getId() == army.getId()) {
                updatedArmy = a;
                break;
            }
        }
        assertNotNull(updatedArmy);
        assertEquals(originalSoldiers - 4, updatedArmy.getSoldiers());
    }
    
    @Test
    void multipleFriendlyArmiesMergeIntoLowestId() {
        gameService = new GameService();
        GameState state = gameService.getState();
        
        Army p1Army = null;
        for (Army army : state.getArmies()) {
            if (army.getPlayerId() == 1) {
                p1Army = army;
                break;
            }
        }
        assertNotNull(p1Army);
        int lowestId = p1Army.getId();
        
        // Create multiple splits
        gameService.splitArmy(p1Army.getId(), 2);
        state = gameService.getState();
        
        // Find the new army and split it too
        Army newArmy = null;
        for (Army army : state.getArmies()) {
            if (army.getPlayerId() == 1 && army.getId() != lowestId) {
                newArmy = army;
                break;
            }
        }
        assertNotNull(newArmy);
        gameService.splitArmy(newArmy.getId(), 1);
        
        state = gameService.getState();
        assertEquals(4, state.getArmies().size()); // P1 original + 2 splits + P2
        
        // Tick to merge all P1 armies
        gameService.tick();
        
        state = gameService.getState();
        assertEquals(2, state.getArmies().size()); // Back to 2 (merged P1 + P2)
        
        // Verify surviving army has lowest ID
        Army survivingP1 = null;
        for (Army army : state.getArmies()) {
            if (army.getPlayerId() == 1) {
                survivingP1 = army;
                break;
            }
        }
        assertNotNull(survivingP1);
        assertEquals(lowestId, survivingP1.getId());
    }
    
    // Castle Capture Tests
    
    @Test
    void castleHasOccupationTicksField() {
        GameState state = gameService.getState();
        Tile castle = state.getGrid()[0][0];
        assertEquals(TileType.CASTLE, castle.getType());
        assertEquals(0, castle.getOccupationTicks());
    }
    
    @Test
    void castleCaptureRequiresThreeTicks() {
        GameState state = gameService.getState();
        
        // Move Player 2 army to Player 1 castle at (0,0)
        int army2Id = state.getArmies().get(1).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army2Id);
        move.setTargetX(0);
        move.setTargetY(0);
        gameService.executeCommand(move);
        
        // Move Player 1 army away so it doesn't fight at the castle
        int army1Id = state.getArmies().get(0).getId();
        Command moveAway = new Command();
        moveAway.setType("MOVE");
        moveAway.setArmyId(army1Id);
        moveAway.setTargetX(5);
        moveAway.setTargetY(0); // Move horizontally away
        gameService.executeCommand(moveAway);
        
        // Wait for Player 1 to leave (5 ticks) then Player 2 to arrive (18 ticks) + 3 for capture
        for (int i = 0; i < 26; i++) {
            gameService.tick();
        }
        
        // Verify Player 2 army is at castle
        state = gameService.getState();
        Army army2 = state.getArmies().stream()
            .filter(a -> a.getId() == army2Id)
            .findFirst()
            .orElse(null);
        assertNotNull(army2);
        assertEquals(0, army2.getX());
        assertEquals(0, army2.getY());
        
        // Castle should have been captured after 3 ticks of occupation
        Tile castle = state.getGrid()[0][0];
        assertEquals(2, castle.getOwnerId());
        assertEquals(0, castle.getOccupationTicks()); // Reset after capture
    }
    
    @Test
    void castleOccupationResetsWhenEnemyLeaves() {
        GameState state = gameService.getState();
        
        // Move Player 2 army to Player 1 castle
        int army2Id = state.getArmies().get(1).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army2Id);
        move.setTargetX(0);
        move.setTargetY(0);
        gameService.executeCommand(move);
        
        // Move Player 1 army away
        int army1Id = state.getArmies().get(0).getId();
        Command moveAway = new Command();
        moveAway.setType("MOVE");
        moveAway.setArmyId(army1Id);
        moveAway.setTargetX(5);
        moveAway.setTargetY(5);
        gameService.executeCommand(moveAway);
        
        // Wait for Player 2 to reach castle and start occupation (but not complete it)
        // 18 ticks to reach + 1 tick of occupation = 19 ticks
        for (int i = 0; i < 19; i++) {
            gameService.tick();
        }
        
        // Occupation should have started but not completed
        state = gameService.getState();
        Tile castle = state.getGrid()[0][0];
        int ticksBefore = castle.getOccupationTicks();
        assertTrue(ticksBefore > 0 && ticksBefore < 3);
        assertEquals(1, castle.getOwnerId()); // Still owned by Player 1
        
        // Move Player 2 army away
        Command moveAway2 = new Command();
        moveAway2.setType("MOVE");
        moveAway2.setArmyId(army2Id);
        moveAway2.setTargetX(1);
        moveAway2.setTargetY(1);
        gameService.executeCommand(moveAway2);
        
        gameService.tick();
        
        // Occupation should reset when army leaves
        state = gameService.getState();
        castle = state.getGrid()[0][0];
        assertEquals(0, castle.getOccupationTicks());
        assertEquals(1, castle.getOwnerId()); // Still owned by Player 1
    }
    
    @Test
    void castleOccupationResetsWhenFriendlyArmyPresent() {
        GameState state = gameService.getState();
        
        // Move Player 2 army toward Player 1 castle
        int army2Id = state.getArmies().get(1).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army2Id);
        move.setTargetX(0);
        move.setTargetY(0);
        gameService.executeCommand(move);
        
        // Player 1 army stays at castle
        
        // Tick several times
        for (int i = 0; i < 20; i++) {
            gameService.tick();
            state = gameService.getState();
            
            // Check if armies are at same location
            Army army1 = state.getArmies().stream()
                .filter(a -> a.getPlayerId() == 1)
                .findFirst()
                .orElse(null);
            Army army2 = state.getArmies().stream()
                .filter(a -> a.getId() == army2Id)
                .findFirst()
                .orElse(null);
            
            if (army2 != null && army1 != null && 
                army2.getX() == 0 && army2.getY() == 0) {
                // Both armies at castle - occupation should reset or not increase
                Tile castle = state.getGrid()[0][0];
                assertEquals(0, castle.getOccupationTicks());
            }
        }
    }
    
    @Test
    void playerLosesWhenAllCastlesCaptured() {
        GameState state = gameService.getState();
        
        // Initial state - no game over
        assertFalse(state.isGameOver());
        assertNull(state.getWinnerId());
        
        // Move Player 2 army to Player 1 castle
        int army2Id = state.getArmies().get(1).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army2Id);
        move.setTargetX(0);
        move.setTargetY(0);
        gameService.executeCommand(move);
        
        // Move Player 1 army away
        int army1Id = state.getArmies().get(0).getId();
        Command moveAway = new Command();
        moveAway.setType("MOVE");
        moveAway.setArmyId(army1Id);
        moveAway.setTargetX(5);
        moveAway.setTargetY(5);
        gameService.executeCommand(moveAway);
        
        // Wait for Player 2 to capture Player 1's castle
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        // Game should be over with Player 2 winning
        state = gameService.getState();
        assertTrue(state.isGameOver());
        assertEquals(2, state.getWinnerId());
    }
    
    @Test
    void playerWinsWhenEnemyHasNoCastles() {
        GameState state = gameService.getState();
        
        // Move Player 1 army to Player 2 castle
        int army1Id = state.getArmies().get(0).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army1Id);
        move.setTargetX(9);
        move.setTargetY(9);
        gameService.executeCommand(move);
        
        // Move Player 2 army away
        int army2Id = state.getArmies().get(1).getId();
        Command moveAway = new Command();
        moveAway.setType("MOVE");
        moveAway.setArmyId(army2Id);
        moveAway.setTargetX(5);
        moveAway.setTargetY(5);
        gameService.executeCommand(moveAway);
        
        // Wait for Player 1 to capture Player 2's castle
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        // Game should be over with Player 1 winning
        state = gameService.getState();
        assertTrue(state.isGameOver());
        assertEquals(1, state.getWinnerId());
    }
    
    @Test
    void commandsRejectedWhenGameOver() {
        GameState state = gameService.getState();
        
        // Move Player 1 army to Player 2 castle to win
        int army1Id = state.getArmies().get(0).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army1Id);
        move.setTargetX(9);
        move.setTargetY(9);
        gameService.executeCommand(move);
        
        // Move Player 2 away
        int army2Id = state.getArmies().get(1).getId();
        Command moveAway = new Command();
        moveAway.setType("MOVE");
        moveAway.setArmyId(army2Id);
        moveAway.setTargetX(5);
        moveAway.setTargetY(5);
        gameService.executeCommand(moveAway);
        
        // Wait for game to end
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        assertTrue(state.isGameOver());
        
        // Try to issue a command - should be ignored
        Army army1Before = state.getArmies().stream()
            .filter(a -> a.getId() == army1Id)
            .findFirst()
            .orElse(null);
        assertNotNull(army1Before);
        int xBefore = army1Before.getX();
        int yBefore = army1Before.getY();
        
        Command newMove = new Command();
        newMove.setType("MOVE");
        newMove.setArmyId(army1Id);
        newMove.setTargetX(5);
        newMove.setTargetY(5);
        gameService.executeCommand(newMove);
        
        // Command should be ignored - destination not set
        state = gameService.getState();
        Army army1After = state.getArmies().stream()
            .filter(a -> a.getId() == army1Id)
            .findFirst()
            .orElse(null);
        assertNotNull(army1After);
        assertFalse(army1After.isMoving());
    }
    
    @Test
    void resetGameResetsAllState() {
        // Modify game state
        gameService.tick();
        gameService.tick();
        
        GameState state = gameService.getState();
        int army1Id = state.getArmies().get(0).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army1Id);
        move.setTargetX(5);
        move.setTargetY(5);
        gameService.executeCommand(move);
        
        gameService.tick();
        
        state = gameService.getState();
        assertTrue(state.getTickCount() > 0);
        
        // Reset game
        gameService.resetGame();
        
        // Verify reset
        state = gameService.getState();
        assertEquals(0, state.getTickCount());
        assertEquals(2, state.getArmies().size());
        assertFalse(state.isGameOver());
        assertNull(state.getWinnerId());
        
        // Verify castles are reset to original ownership
        assertEquals(1, state.getGrid()[0][0].getOwnerId());
        assertEquals(2, state.getGrid()[9][9].getOwnerId());
        
        // Verify armies are at starting positions
        Army army1 = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .orElse(null);
        Army army2 = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .orElse(null);
        
        assertNotNull(army1);
        assertNotNull(army2);
        assertEquals(0, army1.getX());
        assertEquals(0, army1.getY());
        assertEquals(9, army2.getX());
        assertEquals(9, army2.getY());
    }
    
    @Test
    void castleOccupationTicksVisibleInState() {
        GameState state = gameService.getState();
        
        // Move Player 2 to Player 1 castle
        int army2Id = state.getArmies().get(1).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army2Id);
        move.setTargetX(0);
        move.setTargetY(0);
        gameService.executeCommand(move);
        
        // Move Player 1 away
        int army1Id = state.getArmies().get(0).getId();
        Command moveAway = new Command();
        moveAway.setType("MOVE");
        moveAway.setArmyId(army1Id);
        moveAway.setTargetX(5);
        moveAway.setTargetY(5);
        gameService.executeCommand(moveAway);
        
        // Wait for Player 2 to reach castle (18 ticks to reach)
        for (int i = 0; i < 18; i++) {
            gameService.tick();
        }
        
        // Verify Player 2 army is at castle
        state = gameService.getState();
        Army army2 = state.getArmies().stream()
            .filter(a -> a.getId() == army2Id)
            .findFirst()
            .orElse(null);
        assertNotNull(army2);
        assertEquals(0, army2.getX());
        assertEquals(0, army2.getY());
        
        // Check that occupation ticks are visible and have started
        Tile castle = state.getGrid()[0][0];
        assertEquals(1, castle.getOccupationTicks()); // Occupation starts on arrival tick
        
        // Tick once more to verify it increments
        gameService.tick();
        state = gameService.getState();
        castle = state.getGrid()[0][0];
        assertEquals(2, castle.getOccupationTicks()); // Occupation continues
        
        // Tick again to verify it continues
        gameService.tick();
        state = gameService.getState();
        castle = state.getGrid()[0][0];
        assertEquals(0, castle.getOccupationTicks()); // Captured after 3 ticks, resets to 0
        assertEquals(2, castle.getOwnerId()); // Castle now owned by Player 2
    }
    
    @Test
    void castleCaptureResetsCaptureProgress() {
        GameState state = gameService.getState();
        
        // Move Player 2 to Player 1 castle
        int army2Id = state.getArmies().get(1).getId();
        Command move = new Command();
        move.setType("MOVE");
        move.setArmyId(army2Id);
        move.setTargetX(0);
        move.setTargetY(0);
        gameService.executeCommand(move);
        
        // Move Player 1 away
        int army1Id = state.getArmies().get(0).getId();
        Command moveAway = new Command();
        moveAway.setType("MOVE");
        moveAway.setArmyId(army1Id);
        moveAway.setTargetX(5);
        moveAway.setTargetY(5);
        gameService.executeCommand(moveAway);
        
        // Wait for castle to be captured
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        // After capture, occupation ticks should be reset
        state = gameService.getState();
        Tile castle = state.getGrid()[0][0];
        assertEquals(2, castle.getOwnerId());
        assertEquals(0, castle.getOccupationTicks());
    }
    
    // AI Tests
    
    @Test
    void aiIsEnabledByDefault() {
        GameService freshService = new GameService();
        GameState state = freshService.getState();
        assertTrue(state.isAiEnabled());
    }
    
    @Test
    void aiCanBeDisabled() {
        gameService.resetGame();
        
        // Disable AI (it's already disabled in setUp, but let's be explicit)
        gameService.setAiEnabled(false);
        
        // Execute some ticks - AI should not spawn armies
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        // Should only have initial 2 armies (no AI spawns)
        long player2Armies = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .count();
        assertEquals(1, player2Armies); // Only initial army
    }
    
    @Test
    void aiDoesNotAutoSpawnArmies() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Get initial soldier count for P2
        GameState initialState = gameService.getState();
        int initialSoldiers = initialState.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .mapToInt(Army::getSoldiers)
            .sum();
        
        // Execute 5 ticks (previously would have spawned +10 soldiers)
        for (int i = 0; i < 5; i++) {
            gameService.tick();
        }
        
        // Verify no automatic spawning occurred (soldier count should be same or only increased by village income)
        GameState state = gameService.getState();
        int currentSoldiers = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .mapToInt(Army::getSoldiers)
            .sum();
        
        // Should be same as initial (no auto-spawn), or slightly higher if AI captured villages
        // Allow up to 5 extra soldiers (5 ticks * 1 soldier/tick if AI captured 1 village early)
        assertTrue(currentSoldiers <= initialSoldiers + 5, "AI should not auto-spawn armies");
    }
    
    @Test
    void aiDoesNotAutoSpawnMultipleTimes() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        GameState initialState = gameService.getState();
        int initialSoldiers = initialState.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .mapToInt(Army::getSoldiers)
            .sum();
        
        // Execute 10 ticks (previously would trigger spawns at tick 5 and 10)
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        int currentSoldiers = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .mapToInt(Army::getSoldiers)
            .sum();
        
        // Old system would have spawned +20 soldiers (tick 5 and 10). 
        // New system only allows village income (max ~10 soldiers if AI captured both villages quickly).
        // Using 20 as upper bound to verify no automatic spawning occurred.
        assertTrue(currentSoldiers < initialSoldiers + 20, 
            "AI should not auto-spawn multiple times");
    }
    
    @Test
    void aiDefendsThreatenedVillage() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Set up: AI owns village at (6,6), place AI army closer to village for this test
        GameState internalState = gameService.getInternalStateForTest();
        internalState.getGrid()[6][6].setOwnerId(2);
        
        // Place AI army closer to village (7,7) so it can defend
        Army aiArmy = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .get();
        aiArmy.setX(7);
        aiArmy.setY(7);
        
        // Get P1 army ID and move it close to the village
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        
        Command moveCommand = new Command();
        moveCommand.setType("MOVE");
        moveCommand.setArmyId(p1Army.getId());
        moveCommand.setTargetX(5);
        moveCommand.setTargetY(5);
        gameService.executeCommand(moveCommand);
        
        // Move P1 army to threatened position
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        // Check that AI army has moved toward the threatened village
        GameState state = gameService.getState();
        boolean aiArmyMovingToVillage = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .anyMatch(a -> a.isMoving() && 
                           a.getDestinationX() == 6 && 
                           a.getDestinationY() == 6);
        
        assertTrue(aiArmyMovingToVillage, "AI should defend threatened village");
    }
    
    @Test
    void aiCapturesNeutralVillageWhenSafe() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Neutral village already exists at (3,3) and (6,6)
        // Execute ticks and verify AI eventually targets a neutral village
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        
        // Check that at least one AI army is moving toward a village or has captured one
        boolean aiMovingToVillage = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .anyMatch(a -> a.isMoving());
        
        // Or AI has captured a village
        boolean aiOwnsVillage = false;
        for (int x = 0; x < state.getWidth(); x++) {
            for (int y = 0; y < state.getHeight(); y++) {
                if (state.getGrid()[x][y].getType() == TileType.VILLAGE && 
                    state.getGrid()[x][y].getOwnerId() == 2) {
                    aiOwnsVillage = true;
                    break;
                }
            }
        }
        
        assertTrue(aiMovingToVillage || aiOwnsVillage, "AI should move toward or capture neutral village");
    }
    
    @Test
    void aiDoesNotMakeSuicidalAttacks() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Set up: Give P1 a very strong army near AI castle
        GameState internalState = gameService.getInternalStateForTest();
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setSoldiers(100);
        p1Army.setX(8);
        p1Army.setY(8);
        
        // Execute ticks
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        // AI should not move weak armies toward P1's strong army
        GameState state = gameService.getState();
        
        // Check that no AI army is moving directly to P1's position
        boolean aiMovingToStrongEnemy = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .anyMatch(a -> a.isMoving() && 
                          a.getDestinationX() == 8 && 
                          a.getDestinationY() == 8 &&
                          a.getSoldiers() < 50); // Weak army
        
        assertFalse(aiMovingToStrongEnemy, "AI should not send weak armies against overwhelming force");
    }
    
    @Test
    void aiAttacksWeakEnemyVillage() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Set up: P1 owns a village with no defense
        GameState internalState = gameService.getInternalStateForTest();
        internalState.getGrid()[6][6].setOwnerId(1);
        
        // Give AI a strong army at its starting position
        Army aiArmy = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .get();
        aiArmy.setSoldiers(50);
        
        // Move P1 army far away
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setSoldiers(5);
        p1Army.setX(0);
        p1Army.setY(0);
        
        // Execute more ticks to give AI time to make decisions and move
        for (int i = 0; i < 30; i++) {
            gameService.tick();
        }
        
        // AI should eventually target or capture the weak enemy village
        GameState state = gameService.getState();
        
        // Check if AI captured the village OR is moving toward it
        boolean aiCapturedVillage = state.getGrid()[6][6].getOwnerId() == 2;
        boolean aiMovingToVillage = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .anyMatch(a -> (a.isMoving() && a.getDestinationX() == 6 && a.getDestinationY() == 6) ||
                          (a.getX() == 6 && a.getY() == 6));
        
        assertTrue(aiCapturedVillage || aiMovingToVillage, 
            "AI should capture or move toward weak enemy village");
    }
    
    @Test
    void aiAttacksCastleWithOverwhelmingForce() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Give AI overwhelming force
        GameState internalState = gameService.getInternalStateForTest();
        Army aiArmy = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .get();
        aiArmy.setSoldiers(100);
        
        // Weaken P1
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setSoldiers(5);
        p1Army.setX(0);
        p1Army.setY(0);
        
        // Execute more ticks to give AI time to reach castle
        for (int i = 0; i < 40; i++) {
            gameService.tick();
        }
        
        // AI should move toward or capture P1 castle with overwhelming force
        GameState state = gameService.getState();
        
        // Check if AI captured castle OR a strong army is at/moving to it
        boolean aiCapturedCastle = state.getGrid()[0][0].getOwnerId() == 2;
        boolean aiMovingToCastle = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2 && a.getSoldiers() >= 30)
            .anyMatch(a -> (a.isMoving() && a.getDestinationX() == 0 && a.getDestinationY() == 0) ||
                          (a.getX() == 0 && a.getY() == 0));
        
        assertTrue(aiCapturedCastle || aiMovingToCastle, 
            "AI should capture or move toward enemy castle with overwhelming force");
    }
    
    @Test
    void aiDoesNotAttackCastleWithoutOverwhelmingForce() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Give AI moderate force (not overwhelming)
        GameState internalState = gameService.getInternalStateForTest();
        Army aiArmy = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .get();
        aiArmy.setSoldiers(20);
        
        // P1 has similar force at castle
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setSoldiers(15);
        p1Army.setX(0);
        p1Army.setY(0);
        
        // Execute ticks
        for (int i = 0; i < 15; i++) {
            gameService.tick();
        }
        
        // AI should NOT target castle without overwhelming force
        GameState state = gameService.getState();
        boolean aiTargetedCastle = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .anyMatch(a -> a.isMoving() && a.getDestinationX() == 0 && a.getDestinationY() == 0);
        
        assertFalse(aiTargetedCastle, "AI should not attack castle without overwhelming force");
    }
    
    @Test
    void aiDoesNotMakeSuicidalAttacksWhenOutnumbered() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        
        // Remove all villages to eliminate village income
        GameState internalState = gameService.getInternalStateForTest();
        for (int x = 0; x < internalState.getWidth(); x++) {
            for (int y = 0; y < internalState.getHeight(); y++) {
                if (internalState.getGrid()[x][y].getType() == TileType.VILLAGE) {
                    internalState.getGrid()[x][y].setType(TileType.EMPTY);
                }
            }
        }
        
        // Give P1 overwhelming force to make attacks unsafe
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setSoldiers(200);
        
        // Get AI's initial position
        Army aiArmy = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .get();
        int initialX = aiArmy.getX();
        int initialY = aiArmy.getY();
        
        // Execute ticks - AI should not attack when outmatched
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        
        // AI should not have moved toward the P1 castle (0,0) or the P1 army
        boolean aiStayedSafe = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .noneMatch(a -> a.isMoving() && a.getDestinationX() == 0 && a.getDestinationY() == 0);
        
        assertTrue(aiStayedSafe, "AI should not make suicidal attacks when heavily outnumbered");
    }
    
    @Test
    void aiOnlyControlsPlayer2() {
        gameService.resetGame();
        gameService.setAiEnabled(true); // Enable AI for this test
        GameState state = gameService.getState();
        
        // Get initial positions of P1 army
        Army p1Army = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        int p1InitialX = p1Army.getX();
        int p1InitialY = p1Army.getY();
        
        // Execute ticks
        for (int i = 0; i < 5; i++) {
            gameService.tick();
        }
        
        // P1 army should not have moved (AI doesn't control it)
        state = gameService.getState();
        p1Army = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        
        assertEquals(p1InitialX, p1Army.getX());
        assertEquals(p1InitialY, p1Army.getY());
        assertFalse(p1Army.isMoving(), "AI should not control Player 1 armies");
    }
    
    // Ruler Decision System Tests
    
    @Test
    void newArmiesStartWithDefaultMoraleAndLoyalty() {
        GameState state = gameService.getState();
        Army army = state.getArmies().get(0);
        
        assertEquals(100, army.getMorale());
        assertEquals(100, army.getLoyalty());
    }
    
    @Test
    void newVillagesStartWithDefaultStabilityAndPopulation() {
        GameState state = gameService.getState();
        Tile village = state.getGrid()[3][3];
        
        assertEquals(100, village.getStability());
        assertEquals(100, village.getPopulation());
    }
    
    @Test
    void stabilityAffectsSoldierGeneration() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Set village at (3,3) to Player 1 with higher population
        internalState.getGrid()[3][3].setOwnerId(1);
        internalState.getGrid()[3][3].setPopulation(200); // 2 soldiers/tick baseline
        
        // Move Player 1 army to village
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setX(3);
        p1Army.setY(3);
        p1Army.setSoldiers(10);
        
        // Test with 100% stability as control
        internalState.getGrid()[3][3].setStability(100);
        int initialSoldiers100 = p1Army.getSoldiers();
        final int p1ArmyId = p1Army.getId(); // Store ID for lambda
        
        gameService.tick();
        gameService.tick();
        
        GameState state100 = gameService.getState();
        Army army100 = state100.getArmies().stream()
            .filter(a -> a.getId() == p1ArmyId)
            .findFirst()
            .get();
        int generated100 = army100.getSoldiers() - initialSoldiers100;
        
        // Reset and test with 50% stability
        gameService.resetGame();
        internalState = gameService.getInternalStateForTest();
        internalState.getGrid()[3][3].setOwnerId(1);
        internalState.getGrid()[3][3].setPopulation(200); // Same population
        Army p1Army2 = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army2.setX(3);
        p1Army2.setY(3);
        p1Army2.setSoldiers(10);
        internalState.getGrid()[3][3].setStability(50);
        
        int initialSoldiers50 = p1Army2.getSoldiers();
        final int p1Army2Id = p1Army2.getId(); // Store ID for lambda
        
        gameService.tick();
        gameService.tick();
        
        GameState state50 = gameService.getState();
        Army army50 = state50.getArmies().stream()
            .filter(a -> a.getId() == p1Army2Id)
            .findFirst()
            .get();
        int generated50 = army50.getSoldiers() - initialSoldiers50;
        
        // With 50% stability should generate less than 100% stability
        // At 200 population: base = 2, with 100% stability = 2, with 50% stability = 1
        assertTrue(generated50 < generated100, 
            "50% stability should generate fewer soldiers than 100% stability. Got: " + 
            generated50 + " vs " + generated100);
    }
    
    @Test
    void moraleAffectsCombatEffectiveness() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Create two Player 1 armies with different morale
        Army strongArmy = new Army(5, 5, 10, 1);
        strongArmy.setMorale(150); // 150% morale = 50% bonus
        internalState.getArmiesInternal().add(strongArmy);
        
        Army weakArmy = new Army(7, 7, 10, 1);
        weakArmy.setMorale(50); // 50% morale = 50% penalty
        internalState.getArmiesInternal().add(weakArmy);
        
        // Create Player 2 armies to fight
        Army enemy1 = new Army(5, 5, 10, 2);
        internalState.getArmiesInternal().add(enemy1);
        
        Army enemy2 = new Army(7, 7, 10, 2);
        internalState.getArmiesInternal().add(enemy2);
        
        // Process combat
        gameService.tick();
        
        GameState state = gameService.getState();
        
        // Strong army (150% morale) should deal 15 damage (10 * 1.5)
        // Enemy1 should have 10 - 15 = -5 (dead)
        boolean enemy1Survived = state.getArmies().stream()
            .anyMatch(a -> a.getId() == enemy1.getId());
        assertFalse(enemy1Survived, "Enemy facing high morale army should be defeated");
        
        // Weak army (50% morale) should deal 5 damage (10 * 0.5)
        // Enemy2 should have 10 - 5 = 5 soldiers left
        Army survivedEnemy2 = state.getArmies().stream()
            .filter(a -> a.getId() == enemy2.getId())
            .findFirst()
            .orElse(null);
        assertNotNull(survivedEnemy2, "Enemy facing low morale army should survive");
        assertEquals(5, survivedEnemy2.getSoldiers(), "Enemy should have 5 soldiers left after facing weak army");
    }
    
    @Test
    void desertionOccursWithLowLoyalty() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Create Player 1 army with low loyalty
        Army disloyalArmy = new Army(5, 5, 100, 1);
        disloyalArmy.setLoyalty(0); // 0% loyalty = 5% desertion per tick
        internalState.getArmiesInternal().add(disloyalArmy);
        
        int initialSoldiers = disloyalArmy.getSoldiers();
        
        // Process desertion over 3 ticks
        gameService.tick();
        gameService.tick();
        gameService.tick();
        
        GameState state = gameService.getState();
        Army updatedArmy = state.getArmies().stream()
            .filter(a -> a.getId() == disloyalArmy.getId())
            .findFirst()
            .orElse(null);
        
        if (updatedArmy != null) {
            // With 0% loyalty, desertion rate is (100-0)/20 = 5% per tick
            // After 3 ticks: roughly 15% desertion
            assertTrue(updatedArmy.getSoldiers() < initialSoldiers, "Low loyalty should cause desertion");
        }
        // Army might be completely removed if all soldiers deserted
    }
    
    @Test
    void stabilityRecoversTowardBaseline() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Set village to Player 1 with low stability
        internalState.getGrid()[3][3].setOwnerId(1);
        internalState.getGrid()[3][3].setStability(50);
        
        // Tick 10 times
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        int newStability = state.getGrid()[3][3].getStability();
        
        // Stability should recover at 2% per tick toward 100
        // After 10 ticks: 50 + (2*10) = 70
        assertTrue(newStability > 50, "Stability should recover toward 100");
        assertTrue(newStability <= 100, "Stability should not exceed 100");
    }
    
    @Test
    void moraleDecaysTowardBaseline() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Set Player 1 army with high morale
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setMorale(150);
        
        // Tick 10 times
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        Army updatedArmy = state.getArmies().stream()
            .filter(a -> a.getId() == p1Army.getId())
            .findFirst()
            .get();
        
        // Morale should decay at 1% per tick toward 100
        // After 10 ticks: 150 - 10 = 140
        assertTrue(updatedArmy.getMorale() < 150, "Morale should decay toward 100");
        assertTrue(updatedArmy.getMorale() >= 100, "Morale should not go below 100 when decaying");
    }
    
    @Test
    void loyaltyRecoversTowardBaseline() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Set Player 1 army with low loyalty
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        p1Army.setLoyalty(50);
        
        // Tick 10 times
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        Army updatedArmy = state.getArmies().stream()
            .filter(a -> a.getId() == p1Army.getId())
            .findFirst()
            .get();
        
        // Loyalty should recover at 2% per tick toward 100
        // After 10 ticks: 50 + 20 = 70
        assertTrue(updatedArmy.getLoyalty() > 50, "Loyalty should recover toward 100");
        assertTrue(updatedArmy.getLoyalty() <= 100, "Loyalty should not exceed 100");
    }
    
    @Test
    void policyChangeCooldownWorks() {
        gameService.resetGame();
        
        // Should allow immediate first change
        assertTrue(gameService.canChangePolicy());
        
        // Change policy
        gameService.changePolicy(RulerDecision.PolicyCategory.ECONOMIC, "HEAVY_TAXATION");
        
        // Should not allow immediate second change
        assertFalse(gameService.canChangePolicy());
        
        // Tick 14 times (not enough)
        for (int i = 0; i < 14; i++) {
            gameService.tick();
        }
        assertFalse(gameService.canChangePolicy());
        
        // Tick once more (15 total)
        gameService.tick();
        assertTrue(gameService.canChangePolicy(), "Should allow policy change after 15 ticks");
    }
    
    @Test
    void economicPolicyAffectsStability() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Set village to Player 1
        internalState.getGrid()[3][3].setOwnerId(1);
        
        // Change to HEAVY_TAXATION (target stability = 100 - 10 = 90)
        gameService.changePolicy(RulerDecision.PolicyCategory.ECONOMIC, "HEAVY_TAXATION");
        
        // Tick several times to allow stability to drift toward target
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        int finalStability = state.getGrid()[3][3].getStability();
        
        // Should have drifted toward 90 (target for heavy taxation)
        assertTrue(finalStability <= 90, "Heavy taxation should reduce stability toward 90, got: " + finalStability);
        assertTrue(finalStability >= 80, "Stability should be drifting toward 90, got: " + finalStability);
    }
    
    @Test
    void militaryPolicyAffectsMoraleAndLoyalty() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Get Player 1 army
        Army p1Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get();
        
        // Change to AGGRESSIVE_TRAINING (target morale = 100 + 10 = 110, target loyalty = 100 - 5 = 95)
        gameService.changePolicy(RulerDecision.PolicyCategory.MILITARY, "AGGRESSIVE_TRAINING");
        
        // Tick several times to allow morale/loyalty to drift toward target
        for (int i = 0; i < 15; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        Army updatedArmy = state.getArmies().stream()
            .filter(a -> a.getId() == p1Army.getId())
            .findFirst()
            .get();
        
        // Should have drifted toward targets
        assertTrue(updatedArmy.getMorale() >= 105, "Morale should drift toward 110, got: " + updatedArmy.getMorale());
        assertTrue(updatedArmy.getLoyalty() <= 95, "Loyalty should drift toward 95, got: " + updatedArmy.getLoyalty());
    }
    
    @Test
    void getRulerStatsReturnsCorrectValues() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Set village to Player 1
        internalState.getGrid()[3][3].setOwnerId(1);
        internalState.getGrid()[3][3].setStability(80);
        internalState.getGrid()[3][3].setPopulation(120);
        
        RulerStats stats = gameService.getRulerStats();
        
        assertEquals(80.0, stats.getAverageStability(), 0.01);
        assertEquals(120, stats.getTotalPopulation());
        assertEquals(100.0, stats.getAverageMorale(), 0.01);
        assertEquals(100.0, stats.getAverageLoyalty(), 0.01);
        assertEquals("BALANCED_BUDGET", stats.getEconomicPolicy());
        assertEquals("STANDARD_SERVICE", stats.getMilitaryPolicy());
        assertEquals("STABLE_POPULATION", stats.getPopulationPolicy());
    }
    
    @Test
    void populationPolicyAffectsGrowthRate() {
        gameService.resetGame();
        GameState internalState = gameService.getInternalStateForTest();
        
        // Set village to Player 1 with initial population
        internalState.getGrid()[3][3].setOwnerId(1);
        internalState.getGrid()[3][3].setPopulation(1000);
        
        // Test GROWTH_FOCUS (+15% growth)
        gameService.changePolicy(RulerDecision.PolicyCategory.POPULATION, "GROWTH_FOCUS");
        
        int initialPopGrowth = internalState.getGrid()[3][3].getPopulation();
        
        // Tick 10 times
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState stateGrowth = gameService.getState();
        int finalPopGrowth = stateGrowth.getGrid()[3][3].getPopulation();
        int growthWithBonus = finalPopGrowth - initialPopGrowth;
        
        // Reset and test QUALITY_OVER_QUANTITY (-10% growth)
        gameService.resetGame();
        internalState = gameService.getInternalStateForTest();
        internalState.getGrid()[3][3].setOwnerId(1);
        internalState.getGrid()[3][3].setPopulation(1000);
        
        gameService.changePolicy(RulerDecision.PolicyCategory.POPULATION, "QUALITY_OVER_QUANTITY");
        
        int initialPopQuality = internalState.getGrid()[3][3].getPopulation();
        
        // Tick 10 times
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        GameState stateQuality = gameService.getState();
        int finalPopQuality = stateQuality.getGrid()[3][3].getPopulation();
        int growthWithPenalty = finalPopQuality - initialPopQuality;
        
        // Growth Focus should produce more population than Quality Over Quantity
        assertTrue(growthWithBonus > growthWithPenalty, 
            "GROWTH_FOCUS should increase population more than QUALITY_OVER_QUANTITY. Got: " + 
            growthWithBonus + " vs " + growthWithPenalty);
    }
}
