package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceTest {

    private static final int NEUTRAL = 0;
    private static final int PLAYER_1 = 1;
    private static final int PLAYER_2 = 2;

    private final AiService aiService = new AiService();

    private GameState newGameState() {
        return new GameState(10, 10);
    }

    private void setTile(GameState gameState, int x, int y, TileType type, int ownerId) {
        gameState.getGrid()[x][y] = new Tile(type, ownerId);
    }

    private Army addArmy(GameState gameState, int x, int y, int soldiers, int playerId) {
        Army army = new Army(x, y, soldiers, playerId);
        gameState.getArmiesInternal().add(army);
        return army;
    }

    @Test
    void idleAiArmyWithNothingToDoGetsNoDestination() {
        GameState gameState = newGameState();
        Army ai = addArmy(gameState, 0, 0, 10, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertNull(ai.getDestinationX());
        assertNull(ai.getDestinationY());
    }

    @Test
    void alreadyMovingAiArmyKeepsItsExistingOrder() {
        GameState gameState = newGameState();
        setTile(gameState, 1, 1, TileType.VILLAGE, NEUTRAL);
        Army ai = addArmy(gameState, 0, 0, 10, PLAYER_2);
        ai.setDestinationX(9);
        ai.setDestinationY(9);

        aiService.executeAiTurn(gameState);

        // The nearby neutral village does not pull an army that already has orders.
        assertEquals(9, ai.getDestinationX());
        assertEquals(9, ai.getDestinationY());
    }

    @Test
    void humanArmiesAreNeverGivenOrders() {
        GameState gameState = newGameState();
        setTile(gameState, 1, 1, TileType.VILLAGE, NEUTRAL);
        Army human = addArmy(gameState, 0, 0, 10, PLAYER_1);

        aiService.executeAiTurn(gameState);

        assertNull(human.getDestinationX());
        assertNull(human.getDestinationY());
    }

    @Test
    void idleAiArmyTargetsTheNearestNeutralVillage() {
        GameState gameState = newGameState();
        setTile(gameState, 5, 5, TileType.VILLAGE, NEUTRAL);
        setTile(gameState, 1, 0, TileType.VILLAGE, NEUTRAL);
        Army ai = addArmy(gameState, 0, 0, 10, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertEquals(1, ai.getDestinationX());
        assertEquals(0, ai.getDestinationY());
    }

    @Test
    void threatenedOwnVillageOutranksAnAdjacentNeutralVillage() {
        GameState gameState = newGameState();
        setTile(gameState, 0, 0, TileType.VILLAGE, PLAYER_2);
        setTile(gameState, 5, 5, TileType.VILLAGE, NEUTRAL);
        // Human army three tiles from the AI village puts it inside the threat detection range.
        addArmy(gameState, 2, 0, 10, PLAYER_1);
        Army ai = addArmy(gameState, 5, 4, 10, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertEquals(0, ai.getDestinationX());
        assertEquals(0, ai.getDestinationY());
    }

    @Test
    void defendingArmyAlreadyStandingOnTheThreatenedVillageIsOrderedToStayPut() {
        GameState gameState = newGameState();
        setTile(gameState, 0, 0, TileType.VILLAGE, PLAYER_2);
        addArmy(gameState, 2, 0, 10, PLAYER_1);
        Army ai = addArmy(gameState, 0, 0, 10, PLAYER_2);

        aiService.executeAiTurn(gameState);

        // Destination equals the current tile, so the army is not "moving" and leaves no garrison.
        assertEquals(0, ai.getDestinationX());
        assertEquals(0, ai.getDestinationY());
        assertFalse(ai.isMoving());
        assertEquals(2, gameState.getArmiesInternal().size());
    }

    @Test
    void understrengthAiArmyStaysOnItsOwnVillageToBuildUp() {
        GameState gameState = newGameState();
        setTile(gameState, 0, 0, TileType.VILLAGE, PLAYER_2);
        setTile(gameState, 3, 3, TileType.VILLAGE, NEUTRAL);
        Army ai = addArmy(gameState, 0, 0, 4, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertNull(ai.getDestinationX());
        assertNull(ai.getDestinationY());
        assertEquals(1, gameState.getArmiesInternal().size());
    }

    @Test
    void aiArmyAtTheMinimumStrengthLeavesItsOwnVillage() {
        GameState gameState = newGameState();
        setTile(gameState, 0, 0, TileType.VILLAGE, PLAYER_2);
        setTile(gameState, 3, 3, TileType.VILLAGE, NEUTRAL);
        Army ai = addArmy(gameState, 0, 0, 5, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertEquals(3, ai.getDestinationX());
        assertEquals(3, ai.getDestinationY());
    }

    @Test
    void neutralVillageIsSkippedWhenAnEqualOrStrongerEnemyIsCloseToIt() {
        GameState gameState = newGameState();
        setTile(gameState, 2, 2, TileType.VILLAGE, NEUTRAL);
        addArmy(gameState, 3, 2, 5, PLAYER_1);
        Army ai = addArmy(gameState, 0, 0, 5, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertNull(ai.getDestinationX());
        assertNull(ai.getDestinationY());
    }

    @Test
    void neutralVillageIsTakenWhenTheNearbyEnemyIsWeaker() {
        GameState gameState = newGameState();
        setTile(gameState, 2, 2, TileType.VILLAGE, NEUTRAL);
        addArmy(gameState, 3, 2, 4, PLAYER_1);
        Army ai = addArmy(gameState, 0, 0, 5, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertEquals(2, ai.getDestinationX());
        assertEquals(2, ai.getDestinationY());
    }

    @Test
    void enemyVillageIsAttackedWhenTheAiHasHalfAgainTheDefendingForce() {
        GameState gameState = newGameState();
        setTile(gameState, 4, 4, TileType.VILLAGE, PLAYER_1);
        addArmy(gameState, 4, 4, 4, PLAYER_1);
        Army ai = addArmy(gameState, 0, 0, 10, PLAYER_2);

        // 10 soldiers against a 4-soldier garrison clears the 1.5x attack ratio.
        aiService.executeAiTurn(gameState);

        assertEquals(4, ai.getDestinationX());
        assertEquals(4, ai.getDestinationY());
    }

    @Test
    void enemyVillageIsLeftAloneWhenTheAttackForceRatioIsNotMet() {
        GameState gameState = newGameState();
        setTile(gameState, 4, 4, TileType.VILLAGE, PLAYER_1);
        addArmy(gameState, 4, 4, 8, PLAYER_1);
        Army ai = addArmy(gameState, 0, 0, 10, PLAYER_2);

        // 10 soldiers falls short of the 12 needed against an 8-soldier garrison.
        aiService.executeAiTurn(gameState);

        assertNull(ai.getDestinationX());
        assertNull(ai.getDestinationY());
    }

    @Test
    void undefendedEnemyCastleIsAssaultedAsTheLastResortTarget() {
        GameState gameState = newGameState();
        setTile(gameState, 6, 6, TileType.CASTLE, PLAYER_1);
        Army ai = addArmy(gameState, 0, 0, 5, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertEquals(6, ai.getDestinationX());
        assertEquals(6, ai.getDestinationY());
    }

    @Test
    void defendedEnemyCastleIsNotAssaultedWithoutDoubleTheDefendingForce() {
        GameState gameState = newGameState();
        setTile(gameState, 6, 6, TileType.CASTLE, PLAYER_1);
        addArmy(gameState, 6, 6, 10, PLAYER_1);
        Army ai = addArmy(gameState, 0, 0, 15, PLAYER_2);

        // 15 soldiers falls short of the 20 needed against a 10-soldier castle garrison.
        aiService.executeAiTurn(gameState);

        assertNull(ai.getDestinationX());
        assertNull(ai.getDestinationY());
    }

    @Test
    void departingAiArmyLeavesASingleSoldierBehindToHoldItsVillage() {
        GameState gameState = newGameState();
        setTile(gameState, 0, 0, TileType.VILLAGE, PLAYER_2);
        setTile(gameState, 3, 3, TileType.VILLAGE, NEUTRAL);
        Army ai = addArmy(gameState, 0, 0, 10, PLAYER_2);

        aiService.executeAiTurn(gameState);

        List<Army> armies = gameState.getArmiesInternal();
        assertEquals(2, armies.size());
        assertEquals(9, ai.getSoldiers());

        Army garrison = armies.stream().filter(a -> a.getId() != ai.getId()).findFirst().orElseThrow();
        assertEquals(0, garrison.getX());
        assertEquals(0, garrison.getY());
        assertEquals(1, garrison.getSoldiers());
        assertEquals(PLAYER_2, garrison.getPlayerId());
        assertFalse(garrison.isMoving());
    }

    @Test
    void noGarrisonIsSplitOffWhenAnotherAiArmyAlreadyHoldsTheVillage() {
        GameState gameState = newGameState();
        setTile(gameState, 0, 0, TileType.VILLAGE, PLAYER_2);
        setTile(gameState, 3, 3, TileType.VILLAGE, NEUTRAL);
        Army departing = addArmy(gameState, 0, 0, 10, PLAYER_2);
        Army holding = addArmy(gameState, 0, 0, 4, PLAYER_2);

        aiService.executeAiTurn(gameState);

        assertEquals(2, gameState.getArmiesInternal().size());
        assertEquals(10, departing.getSoldiers());
        assertEquals(3, departing.getDestinationX());
        assertEquals(3, departing.getDestinationY());
        assertNull(holding.getDestinationX());
    }

    @Test
    void noGarrisonIsSplitOffOnceTheAiIsAtItsArmyCap() {
        GameState gameState = newGameState();
        setTile(gameState, 0, 0, TileType.VILLAGE, PLAYER_2);
        setTile(gameState, 3, 3, TileType.VILLAGE, NEUTRAL);
        Army departing = addArmy(gameState, 0, 0, 10, PLAYER_2);
        for (int i = 0; i < 4; i++) {
            addArmy(gameState, 9, 9, 10, PLAYER_2);
        }

        aiService.executeAiTurn(gameState);

        assertEquals(5, gameState.getArmiesInternal().size());
        assertEquals(10, departing.getSoldiers());
    }
}
