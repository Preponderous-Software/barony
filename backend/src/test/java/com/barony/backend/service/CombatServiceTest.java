package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CombatServiceTest {

    private static final int PLAYER_1 = 1;
    private static final int PLAYER_2 = 2;

    private final CombatService combatService = new CombatService();

    private GameState newGameState() {
        return new GameState(10, 10);
    }

    @Test
    void engagedArmiesDamageEachOtherByOpposingEffectiveStrength() {
        GameState gameState = newGameState();
        Army attacker = new Army(0, 0, 100, PLAYER_1);
        Army defender = new Army(0, 0, 30, PLAYER_2);
        gameState.getArmiesInternal().add(attacker);
        gameState.getArmiesInternal().add(defender);

        combatService.processCombat(gameState);

        // Player 1 at full (100) morale hits for its full 100 soldiers; the player-2 defender
        // (30 soldiers) is wiped out and removed. The attacker takes the defender's raw strength (30).
        assertEquals(70, attacker.getSoldiers());
        assertEquals(1, gameState.getArmiesInternal().size());
        assertSame(attacker, gameState.getArmiesInternal().get(0));
    }

    @Test
    void player1EffectiveStrengthIsScaledByMorale() {
        GameState gameState = newGameState();
        Army attacker = new Army(0, 0, 100, PLAYER_1);
        attacker.setMorale(50);
        Army defender = new Army(0, 0, 200, PLAYER_2);
        gameState.getArmiesInternal().add(attacker);
        gameState.getArmiesInternal().add(defender);

        combatService.processCombat(gameState);

        // Attacker's effective strength is halved by 50 morale: round(100 * 50 / 100) = 50.
        // Defender (non-player-1) fights at its raw strength (200), which wipes out the attacker.
        assertEquals(150, defender.getSoldiers());
        assertEquals(1, gameState.getArmiesInternal().size());
        assertSame(defender, gameState.getArmiesInternal().get(0));
    }

    @Test
    void nonPlayer1ArmiesFightAtRawSoldierStrength() {
        GameState gameState = newGameState();
        Army attacker = new Army(0, 0, 100, PLAYER_2);
        Army defender = new Army(0, 0, 40, 3);
        gameState.getArmiesInternal().add(attacker);
        gameState.getArmiesInternal().add(defender);

        combatService.processCombat(gameState);

        assertEquals(60, attacker.getSoldiers());
        assertEquals(1, gameState.getArmiesInternal().size());
    }

    @Test
    void armiesOnDifferentTilesDoNotFight() {
        GameState gameState = newGameState();
        Army attacker = new Army(0, 0, 100, PLAYER_1);
        Army defender = new Army(5, 5, 50, PLAYER_2);
        gameState.getArmiesInternal().add(attacker);
        gameState.getArmiesInternal().add(defender);

        combatService.processCombat(gameState);

        assertEquals(100, attacker.getSoldiers());
        assertEquals(50, defender.getSoldiers());
        assertEquals(2, gameState.getArmiesInternal().size());
    }

    @Test
    void friendlyArmiesOnSameTileDoNotFight() {
        GameState gameState = newGameState();
        Army first = new Army(0, 0, 50, PLAYER_1);
        Army second = new Army(0, 0, 60, PLAYER_1);
        gameState.getArmiesInternal().add(first);
        gameState.getArmiesInternal().add(second);

        combatService.processCombat(gameState);

        assertEquals(50, first.getSoldiers());
        assertEquals(60, second.getSoldiers());
        assertEquals(2, gameState.getArmiesInternal().size());
    }

    @Test
    void mutuallyDestroyedArmiesAreBothRemoved() {
        GameState gameState = newGameState();
        Army attacker = new Army(0, 0, 50, PLAYER_1);
        Army defender = new Army(0, 0, 50, PLAYER_2);
        gameState.getArmiesInternal().add(attacker);
        gameState.getArmiesInternal().add(defender);

        combatService.processCombat(gameState);

        assertTrue(gameState.getArmiesInternal().isEmpty());
    }

    @Test
    void mergeCombinesFriendlyArmiesAtSameLocationIntoLowestId() {
        GameState gameState = newGameState();
        Army first = new Army(0, 0, 10, PLAYER_1);
        Army second = new Army(0, 0, 20, PLAYER_1);
        Army third = new Army(0, 0, 5, PLAYER_1);
        // Add out of id order to confirm the surviving army is chosen by id, not list position.
        gameState.getArmiesInternal().add(third);
        gameState.getArmiesInternal().add(first);
        gameState.getArmiesInternal().add(second);

        combatService.mergeFriendlyArmies(gameState);

        List<Army> remaining = gameState.getArmiesInternal();
        assertEquals(1, remaining.size());
        assertEquals(first.getId(), remaining.get(0).getId());
        assertEquals(35, remaining.get(0).getSoldiers());
    }

    @Test
    void mergePreservesSurvivingArmysExistingMovementOrder() {
        GameState gameState = newGameState();
        Army surviving = new Army(0, 0, 10, PLAYER_1);
        surviving.setDestinationX(7);
        surviving.setDestinationY(8);
        Army absorbed = new Army(0, 0, 20, PLAYER_1);

        gameState.getArmiesInternal().add(surviving);
        gameState.getArmiesInternal().add(absorbed);

        combatService.mergeFriendlyArmies(gameState);

        List<Army> remaining = gameState.getArmiesInternal();
        assertEquals(1, remaining.size());
        assertEquals(7, remaining.get(0).getDestinationX());
        assertEquals(8, remaining.get(0).getDestinationY());
    }

    @Test
    void mergeInheritsMovementOrderFromAbsorbedArmyWhenSurvivorHasNone() {
        GameState gameState = newGameState();
        Army surviving = new Army(0, 0, 10, PLAYER_1);
        Army absorbed = new Army(0, 0, 20, PLAYER_1);
        absorbed.setDestinationX(3);
        absorbed.setDestinationY(4);

        gameState.getArmiesInternal().add(surviving);
        gameState.getArmiesInternal().add(absorbed);

        combatService.mergeFriendlyArmies(gameState);

        List<Army> remaining = gameState.getArmiesInternal();
        assertEquals(1, remaining.size());
        assertEquals(3, remaining.get(0).getDestinationX());
        assertEquals(4, remaining.get(0).getDestinationY());
    }

    @Test
    void armiesAtDifferentLocationsDoNotMerge() {
        GameState gameState = newGameState();
        Army first = new Army(0, 0, 10, PLAYER_1);
        Army second = new Army(1, 1, 20, PLAYER_1);
        gameState.getArmiesInternal().add(first);
        gameState.getArmiesInternal().add(second);

        combatService.mergeFriendlyArmies(gameState);

        assertEquals(2, gameState.getArmiesInternal().size());
        assertEquals(10, first.getSoldiers());
        assertEquals(20, second.getSoldiers());
    }

    @Test
    void enemyArmiesAtSameLocationDoNotMerge() {
        GameState gameState = newGameState();
        Army first = new Army(0, 0, 10, PLAYER_1);
        Army second = new Army(0, 0, 20, PLAYER_2);
        gameState.getArmiesInternal().add(first);
        gameState.getArmiesInternal().add(second);

        combatService.mergeFriendlyArmies(gameState);

        assertEquals(2, gameState.getArmiesInternal().size());
    }

    @Test
    void singleArmyAtLocationIsUnaffectedByMerge() {
        GameState gameState = newGameState();
        Army only = new Army(0, 0, 15, PLAYER_1);
        gameState.getArmiesInternal().add(only);

        combatService.mergeFriendlyArmies(gameState);

        List<Army> remaining = gameState.getArmiesInternal();
        assertEquals(1, remaining.size());
        assertEquals(only.getId(), remaining.get(0).getId());
        assertEquals(15, remaining.get(0).getSoldiers());
    }
}
