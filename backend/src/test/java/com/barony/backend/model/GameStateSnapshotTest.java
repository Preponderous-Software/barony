package com.barony.backend.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterizes {@link GameState#createSnapshot()}, the copy {@code GameService.getState} hands
 * out for every state response: what it copies, and that the copy shares no mutable object with the
 * live game.
 */
class GameStateSnapshotTest {

    @Test
    void snapshotKeepsNonSquareDimensions() {
        GameState snapshot = new GameState(7, 4).createSnapshot();

        assertEquals(7, snapshot.getWidth());
        assertEquals(4, snapshot.getHeight());
    }

    @Test
    void snapshotCopiesEveryGameLevelField() {
        GameState state = new GameState(5, 5);
        state.setTickCount(42);
        state.setGameOver(true);
        state.setWinnerId(2);
        state.setAiEnabled(false);
        state.setEconomicPolicy("HEAVY_TAXATION");
        state.setMilitaryPolicy("AGGRESSIVE_TRAINING");
        state.setPopulationPolicy("GROWTH_FOCUS");
        state.setLastPolicyChangeTick(30);
        state.setNextBattleId(9);
        state.setFieldBattles(true);
        state.setUnitTypes(false);

        GameState snapshot = state.createSnapshot();

        assertEquals(42, snapshot.getTickCount());
        assertTrue(snapshot.isGameOver());
        assertEquals(2, snapshot.getWinnerId());
        assertFalse(snapshot.isAiEnabled());
        assertEquals("HEAVY_TAXATION", snapshot.getEconomicPolicy());
        assertEquals("AGGRESSIVE_TRAINING", snapshot.getMilitaryPolicy());
        assertEquals("GROWTH_FOCUS", snapshot.getPopulationPolicy());
        assertEquals(30, snapshot.getLastPolicyChangeTick());
        assertEquals(9, snapshot.getNextBattleId());
        assertEquals(Boolean.TRUE, snapshot.getFieldBattles());
        assertEquals(Boolean.FALSE, snapshot.getUnitTypes());
    }

    @Test
    void snapshotKeepsNullOptInFlagsNull() {
        GameState state = new GameState(3, 3);

        GameState snapshot = state.createSnapshot();

        assertNull(snapshot.getFieldBattles());
        assertNull(snapshot.getUnitTypes());
        assertNull(snapshot.getWinnerId());
    }

    /**
     * Current behavior: runRecorded is not copied, so a snapshot always reports false. Nothing reads
     * it back from a snapshot (sessions record runs on the live state), so this is harmless today.
     */
    @Test
    void snapshotDoesNotCopyRunRecorded() {
        GameState state = new GameState(3, 3);
        state.setRunRecorded(true);

        assertFalse(state.createSnapshot().isRunRecorded());
    }

    @Test
    void snapshotCopiesTilesByValue() {
        GameState state = new GameState(4, 3);
        Tile source = state.getGrid()[2][1];
        source.setType(TileType.VILLAGE);
        source.setOwnerId(2);
        source.setOccupationTicks(3);
        source.setStability(65);
        source.setPopulation(140);

        GameState snapshot = state.createSnapshot();
        Tile copy = snapshot.getGrid()[2][1];

        assertNotSame(source, copy);
        assertEquals(TileType.VILLAGE, copy.getType());
        assertEquals(2, copy.getOwnerId());
        assertEquals(3, copy.getOccupationTicks());
        assertEquals(65, copy.getStability());
        assertEquals(140, copy.getPopulation());
        assertEquals(TileType.EMPTY, snapshot.getGrid()[0][0].getType());

        copy.setOwnerId(1);
        copy.setType(TileType.CASTLE);
        assertEquals(2, source.getOwnerId());
        assertEquals(TileType.VILLAGE, source.getType());
    }

    @Test
    void snapshotCopiesArmiesWithEveryField() {
        GameState state = new GameState(5, 5);
        Army army = new Army(1, 2, 30, 1);
        army.setDestinationX(4);
        army.setDestinationY(3);
        army.setMorale(120);
        army.setLoyalty(70);
        army.setDesertionCarryBasisPoints(4321);
        army.setArchers(6);
        army.setCavalry(4);
        army.setTrainingType(UnitType.CAVALRY);
        army.setTrainingRemaining(5);
        state.getArmiesInternal().add(army);

        GameState snapshot = state.createSnapshot();

        assertEquals(1, snapshot.getArmiesInternal().size());
        Army copy = snapshot.getArmiesInternal().get(0);
        assertNotSame(army, copy);
        assertEquals(army.getId(), copy.getId());
        assertEquals(1, copy.getX());
        assertEquals(2, copy.getY());
        assertEquals(30, copy.getSoldiers());
        assertEquals(1, copy.getPlayerId());
        assertEquals(4, copy.getDestinationX());
        assertEquals(3, copy.getDestinationY());
        assertEquals(120, copy.getMorale());
        assertEquals(70, copy.getLoyalty());
        assertEquals(4321, copy.getDesertionCarryBasisPoints());
        assertEquals(6, copy.getArchers());
        assertEquals(4, copy.getCavalry());
        assertEquals(UnitType.CAVALRY, copy.getTrainingType());
        assertEquals(5, copy.getTrainingRemaining());
    }

    @Test
    void changingASnapshotArmyLeavesTheLiveArmyAlone() {
        GameState state = new GameState(5, 5);
        Army army = new Army(0, 0, 10, 1);
        state.getArmiesInternal().add(army);

        GameState snapshot = state.createSnapshot();
        snapshot.getArmiesInternal().get(0).setSoldiers(1);
        snapshot.getArmiesInternal().clear();

        assertEquals(1, state.getArmiesInternal().size());
        assertEquals(10, army.getSoldiers());
    }

    @Test
    void snapshotCopiesPendingBattlesWithIndependentArmyIdLists() {
        GameState state = new GameState(5, 5);
        PendingBattle battle = new PendingBattle();
        battle.setId(3);
        battle.setX(2);
        battle.setY(4);
        battle.setTerrain(TileType.CASTLE);
        battle.setSeed(777);
        battle.setTick(12);
        battle.setSide0ArmyIds(new ArrayList<>(List.of(5, 6)));
        battle.setSide1ArmyIds(new ArrayList<>(List.of(8)));
        battle.setSide0Soldiers(25);
        battle.setSide1Soldiers(18);
        battle.setSide0Archers(4);
        battle.setSide0Cavalry(2);
        battle.setSide1Archers(1);
        battle.setSide1Cavalry(3);
        battle.setSide0Morale(95);
        state.getPendingBattlesInternal().add(battle);

        GameState snapshot = state.createSnapshot();

        assertEquals(1, snapshot.getPendingBattles().size());
        PendingBattle copy = snapshot.getPendingBattlesInternal().get(0);
        assertNotSame(battle, copy);
        assertEquals(3, copy.getId());
        assertEquals(2, copy.getX());
        assertEquals(4, copy.getY());
        assertEquals(TileType.CASTLE, copy.getTerrain());
        assertEquals(777, copy.getSeed());
        assertEquals(12, copy.getTick());
        assertEquals(List.of(5, 6), copy.getSide0ArmyIds());
        assertEquals(List.of(8), copy.getSide1ArmyIds());
        assertEquals(25, copy.getSide0Soldiers());
        assertEquals(18, copy.getSide1Soldiers());
        assertEquals(4, copy.getSide0Archers());
        assertEquals(2, copy.getSide0Cavalry());
        assertEquals(1, copy.getSide1Archers());
        assertEquals(3, copy.getSide1Cavalry());
        assertEquals(95, copy.getSide0Morale());

        copy.getSide0ArmyIds().add(99);
        copy.getSide1ArmyIds().clear();
        assertEquals(List.of(5, 6), battle.getSide0ArmyIds());
        assertEquals(List.of(8), battle.getSide1ArmyIds());
    }

    /** Saves made before field battles load with a null pendingBattles list. */
    @Test
    void snapshotOfAStateWithNullPendingBattlesHasAnEmptyList() {
        GameState state = new GameState(3, 3);
        state.setPendingBattles(null);

        GameState snapshot = state.createSnapshot();

        assertNotNull(snapshot.getPendingBattlesInternal());
        assertTrue(snapshot.getPendingBattles().isEmpty());
    }

    @Test
    void laterChangesToTheLiveGameDoNotReachAnEarlierSnapshot() {
        GameState state = new GameState(4, 4);
        state.getArmiesInternal().add(new Army(0, 0, 10, 1));

        GameState snapshot = state.createSnapshot();
        state.incrementTick();
        state.getGrid()[1][1].setOwnerId(2);
        state.getArmiesInternal().add(new Army(1, 1, 5, 2));
        state.getPendingBattlesInternal().add(new PendingBattle());

        assertEquals(0, snapshot.getTickCount());
        assertEquals(0, snapshot.getGrid()[1][1].getOwnerId());
        assertEquals(1, snapshot.getArmiesInternal().size());
        assertTrue(snapshot.getPendingBattlesInternal().isEmpty());
    }
}
