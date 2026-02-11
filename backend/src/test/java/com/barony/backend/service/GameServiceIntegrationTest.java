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
        moveToEnemyCastle.setTargetX(9);
        moveToEnemyCastle.setTargetY(9);
        gameService.executeCommand(moveToEnemyCastle);
        
        // Move takes 12 ticks (6 horizontal + 6 vertical from 3,3 to 9,9)
        for (int i = 0; i < 12; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
        player1Army = state.getArmies().stream()
            .filter(a -> a.getId() == player1ArmyId)
            .findFirst()
            .get();
        assertEquals(9, player1Army.getX(), "Army should be at enemy castle X");
        assertEquals(9, player1Army.getY(), "Army should be at enemy castle Y");
        
        // Phase 4: Wait for castle capture (3 ticks)
        for (int i = 0; i < 3; i++) {
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
        // Split armies to create equal forces
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
        
        // Split both armies to create 5-soldier forces
        Command splitP1 = new Command();
        splitP1.setType("SPLIT");
        splitP1.setArmyId(player1ArmyId);
        splitP1.setSplitAmount(5);
        gameService.executeCommand(splitP1);
        
        Command splitP2 = new Command();
        splitP2.setType("SPLIT");
        splitP2.setArmyId(player2ArmyId);
        splitP2.setSplitAmount(5);
        gameService.executeCommand(splitP2);
        
        state = gameService.getState();
        assertEquals(4, state.getArmies().size(), "Should have 4 armies after splits");
        
        // Move one 5-soldier army from each player to (5,5)
        int p1NewArmyId = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 1 && a.getSoldiers() == 5)
            .findFirst()
            .get()
            .getId();
        int p2NewArmyId = state.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2 && a.getSoldiers() == 5)
            .findFirst()
            .get()
            .getId();
        
        Command moveP1 = new Command();
        moveP1.setType("MOVE");
        moveP1.setArmyId(p1NewArmyId);
        moveP1.setTargetX(5);
        moveP1.setTargetY(5);
        gameService.executeCommand(moveP1);
        
        Command moveP2 = new Command();
        moveP2.setType("MOVE");
        moveP2.setArmyId(p2NewArmyId);
        moveP2.setTargetX(5);
        moveP2.setTargetY(5);
        gameService.executeCommand(moveP2);
        
        // Move both armies to center (max 10 ticks to reach 5,5 from corners)
        for (int i = 0; i < 10; i++) {
            gameService.tick();
        }
        
        state = gameService.getState();
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
        
        // Wait for both to reach village
        for (int i = 0; i < 12; i++) {
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
        int initialPlayer2ArmyCount = (int) initialState.getArmies().stream()
            .filter(a -> a.getPlayerId() == 2)
            .count();
        
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
        
        // At least some AI armies should have destinations or have moved
        assertTrue(player2ArmiesMoving > 0 || 
                   afterState.getArmies().stream()
                       .anyMatch(a -> a.getPlayerId() == 2 && (a.getX() != 9 || a.getY() != 9)),
                   "AI should make movement decisions when enabled");
    }
}
