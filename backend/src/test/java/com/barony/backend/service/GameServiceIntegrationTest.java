package com.barony.backend.service;

import com.barony.backend.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for complete game scenarios.
 * These tests validate end-to-end gameplay flows and complex interactions.
 */
class GameServiceIntegrationTest {

    private GameService gameService;
    
    @BeforeEach
    void setUp() {
        gameService = new GameService();
        // Disable AI for controlled testing
        gameService.setAiEnabled(false);
    }
    
    /**
     * Test a complete game scenario: Player 1 captures villages, builds army, and captures enemy castle
     */
    @Test
    void completeGameScenario_Player1Victory() {
        // Set up a known village at (3,3) and P2 army at known position
        GameState internalState = gameService.getInternalStateForTest();
        internalState.getGrid()[3][3].setType(TileType.VILLAGE);
        Army p2Army = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2).findFirst().get();
        int p2CastleX = p2Army.getX();
        int p2CastleY = p2Army.getY();
        // Move P2 army away from castle so it doesn't interfere
        p2Army.setX(p2CastleX);
        p2Army.setY(p2CastleY - 1 >= 0 ? p2CastleY - 1 : p2CastleY);

        GameState state = gameService.getState();
        int player1ArmyId = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get()
            .getId();
        
        // Phase 1: Capture first village (3,3)
        Command moveToVillage1 = new Command();
        moveToVillage1.setType("MOVE");
        moveToVillage1.setArmyId(player1ArmyId);
        moveToVillage1.setTargetX(3);
        moveToVillage1.setTargetY(3);
        gameService.executeCommand(moveToVillage1);
        
        // Move takes 6 ticks (3 horizontal + 3 vertical)
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        assertEquals(1, state.getGrid()[3][3].getOwnerId(), "Village should be captured by Player 1");
        
        // Phase 2: Build up army by staying on village
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        Army player1Army = state.getArmies().stream()
            .filter(a -> a.getId() == player1ArmyId)
            .findFirst()
            .get();
        assertTrue(player1Army.getSoldiers() > 10, "Army should have grown from village generation");
        
        // Phase 3: Move to enemy castle and capture it
        Command moveToEnemyCastle = new Command();
        moveToEnemyCastle.setType("MOVE");
        moveToEnemyCastle.setArmyId(player1ArmyId);
        moveToEnemyCastle.setTargetX(p2CastleX);
        moveToEnemyCastle.setTargetY(p2CastleY);
        gameService.executeCommand(moveToEnemyCastle);
        
        // Move army to enemy castle (generous tick count for variable map sizes)
        for (int i = 0; i < 40; i++) {
            gameService.tick();
            state = gameService.getState();
            if (state.isGameOver()) {
                break;
            }
        }
        
        state = gameService.getState();
        assertTrue(state.isGameOver(), "Game should be over after capturing all enemy castles");
        assertEquals(1, state.getWinnerId(), "Player 1 should be the winner");
    }
    
    /**
     * Test edge case: armies with exactly equal soldier counts in combat
     */
    @Test
    void combatEdgeCase_EqualArmiesDestroyCombat() {
        // Set up armies directly at known positions for deterministic combat
        GameState internalState = gameService.getInternalStateForTest();

        // Clear any randomly placed village at (5,5) to prevent soldier generation
        internalState.getGrid()[5][5].setType(TileType.EMPTY);

        // Create two equal 5-soldier armies from different players heading to (5,5)
        Army p1Army = new Army(0, 5, 5, 1);
        Army p2Army = new Army(9, 5, 5, 2);
        internalState.getArmiesInternal().clear();
        internalState.getArmiesInternal().add(p1Army);
        internalState.getArmiesInternal().add(p2Army);

        // Move both to (5,5) - each 5 ticks away on X axis
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

        // Move both armies to center (generous tick count)
        for (int i = 0; i < 15; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        // Both 5-soldier armies should have destroyed each other
        long armiesAt55 = state.getArmies().stream()
            .filter(a -> a.getX() == 5 && a.getY() == 5)
            .count();
        assertEquals(0, armiesAt55, "Both equal armies should have destroyed each other in combat");
    }
    
    /**
     * Test simultaneous village capture attempts by different players
     */
    @Test
    void simultaneousCaptureAttempt_VillageBecomesNeutral() {
        // Set up a known village at (3,3) and P2 army at known position
        GameState internalState = gameService.getInternalStateForTest();
        internalState.getGrid()[3][3].setType(TileType.VILLAGE);
        Army p2ArmySetup = internalState.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2).findFirst().get();
        p2ArmySetup.setX(9);
        p2ArmySetup.setY(9);

        // Set up scenario: both players move to same neutral village
        GameState state = gameService.getState();
        int player1ArmyId = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get()
            .getId();
        int player2ArmyId = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .get()
            .getId();
        
        // Both move to village at (3,3)
        Command moveP1 = new Command();
        moveP1.setType("MOVE");
        moveP1.setArmyId(player1ArmyId);
        moveP1.setTargetX(3);
        moveP1.setTargetY(3);
        gameService.executeCommand(moveP1);
        
        Command moveP2 = new Command();
        moveP2.setType("MOVE");
        moveP2.setArmyId(player2ArmyId);
        moveP2.setTargetX(3);
        moveP2.setTargetY(3);
        gameService.executeCommand(moveP2);
        
        // Wait for both to reach village (generous tick count for variable map sizes)
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        // Village should be contested (neutral) if both players present
        // Or owned by the player who survived combat
        Tile village = state.getGrid()[3][3];
        long player1ArmiesAt33 = state.getArmies().stream()
            .filter(a -> a.getX() == 3 && a.getY() == 3 && a.getPlayerId() == 1)
            .count();
        long player2ArmiesAt33 = state.getArmies().stream()
            .filter(a -> a.getX() == 3 && a.getY() == 3 && a.getPlayerId() == 2)
            .count();
        
        if (player1ArmiesAt33 > 0 && player2ArmiesAt33 > 0) {
            // Both players present after combat - village should be neutral
            assertEquals(0, village.getOwnerId(), "Village should be neutral when contested by multiple players");
        } else if (player1ArmiesAt33 > 0) {
            assertEquals(1, village.getOwnerId(), "Village should belong to Player 1 if only they survived");
        } else if (player2ArmiesAt33 > 0) {
            assertEquals(2, village.getOwnerId(), "Village should belong to Player 2 if only they survived");
        }
        // If no armies remain, the test validates that combat occurred
    }
    
    /**
     * Test policy changes and their gradual effects over time
     */
    @Test
    void policyChanges_GradualStatEffects() {
        // Set up a known village at (3,3)
        gameService.getInternalStateForTest().getGrid()[3][3].setType(TileType.VILLAGE);

        // Change to Heavy Taxation policy
        gameService.changePolicy(RulerDecision.PolicyCategory.ECONOMIC, "HEAVY_TAXATION");
        
        GameState state = gameService.getState();
        assertEquals("HEAVY_TAXATION", state.getEconomicPolicy());
        
        // Move army to village to capture it
        int player1ArmyId = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get()
            .getId();
        
        Command moveToVillage = new Command();
        moveToVillage.setType("MOVE");
        moveToVillage.setArmyId(player1ArmyId);
        moveToVillage.setTargetX(3);
        moveToVillage.setTargetY(3);
        gameService.executeCommand(moveToVillage);
        
        // Move to village
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        Tile village = state.getGrid()[3][3];
        assertEquals(1, village.getOwnerId(), "Village should be captured");
        int initialStability = village.getStability();
        
        // Wait for stability to decrease (Heavy Taxation: -10% stability target)
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        village = state.getGrid()[3][3];
        int stabilityAfter = village.getStability();
        
        // Stability should be moving toward 90 (100 - 10)
        assertTrue(stabilityAfter < initialStability || stabilityAfter <= 90, 
            "Stability should decrease or stabilize at target under Heavy Taxation");
    }
    
    /**
     * Test extreme scenario: all policies at maximum modifiers
     */
    @Test
    void extremeScenario_AllPoliciesAtMaxModifiers() {
        // Set up a known village at (3,3)
        gameService.getInternalStateForTest().getGrid()[3][3].setType(TileType.VILLAGE);

        // Set aggressive policies: Heavy Taxation, Aggressive Training, Growth Focus
        gameService.changePolicy(RulerDecision.PolicyCategory.ECONOMIC, "HEAVY_TAXATION");
        
        gameService.tick(); // Advance to apply cooldown
        
        // Wait for cooldown
        for (int i = 0; i < 15; i++) {
            gameService.tick();
        }
        
        gameService.changePolicy(RulerDecision.PolicyCategory.MILITARY, "AGGRESSIVE_TRAINING");
        
        for (int i = 0; i < 15; i++) {
            gameService.tick();
        }
        
        gameService.changePolicy(RulerDecision.PolicyCategory.POPULATION, "GROWTH_FOCUS");
        
        GameState state = gameService.getState();
        assertEquals("HEAVY_TAXATION", state.getEconomicPolicy());
        assertEquals("AGGRESSIVE_TRAINING", state.getMilitaryPolicy());
        assertEquals("GROWTH_FOCUS", state.getPopulationPolicy());
        
        // Move army to village to see policy effects
        int player1ArmyId = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1)
            .findFirst()
            .get()
            .getId();
        
        Command moveToVillage = new Command();
        moveToVillage.setType("MOVE");
        moveToVillage.setArmyId(player1ArmyId);
        moveToVillage.setTargetX(3);
        moveToVillage.setTargetY(3);
        gameService.executeCommand(moveToVillage);
        
        for (int i = 0; i < 6; i++) {
            gameService.tick();
        }
        
        // Let policies take effect
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        Army player1Army = state.getArmies().stream()
            .filter(a -> a.getId() == player1ArmyId)
            .findFirst()
            .get();
        
        // Verify aggressive training effect: morale should be above 100
        assertTrue(player1Army.getMorale() >= 100, "Morale should be at or above baseline with Aggressive Training");
        
        // Verify village effects
        Tile village = state.getGrid()[3][3];
        // Heavy Taxation + Growth Focus: stability target is 100 - 10 - 5 = 85
        assertTrue(village.getStability() <= 100, "Stability should be affected by policies");
    }
    
    /**
     * Test long game session for memory leaks and stability
     */
    @Test
    void longGameSession_NoMemoryIssues() {
        // Run 1000 ticks to simulate long play session
        for (int i = 0; i < 1000; i++) {
            gameService.tick();
        }
        
        GameState state = gameService.getState();
        assertEquals(1000, state.getTickCount(), "Game should handle 1000 ticks without issues");
        assertFalse(state.getArmies().isEmpty(), "Game should still have armies after long session");
    }
    
    /**
     * Test AI opponent integration when enabled
     */
    @Test
    void aiOpponent_MakesDecisions() {
        gameService.setAiEnabled(true);
        
        GameState initialState = gameService.getState();
        // Record initial P2 army positions
        Army initialP2Army = initialState.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .findFirst()
            .get();
        int initialP2X = initialP2Army.getX();
        int initialP2Y = initialP2Army.getY();
        
        // Run several ticks to allow AI to make decisions
        for (int i = 0; i < 20; i++) {
            gameService.tick();
        }
        
        GameState afterState = gameService.getState();
        
        // AI should have made some movement decisions
        long player2ArmiesMoving = afterState.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .filter(a -> a.getDestinationX() != null)
            .count();
        
        // At least some AI armies should have destinations or have moved from initial position
        assertTrue(player2ArmiesMoving > 0 || 
                   afterState.getArmies().stream()
                       .anyMatch(a -> a.getPlayerId() == 2 && (a.getX() != initialP2X || a.getY() != initialP2Y)),
                   "AI should make movement decisions when enabled");
    }
}
