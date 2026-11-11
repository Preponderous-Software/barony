package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.BattleResult;
import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.PendingBattle;
import com.barony.backend.model.TileType;
import com.barony.backend.model.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Archers and cavalry: how they fight in the map's rule, how they are trained, merged and split,
 * and how a field battle's survivors are shared out by type.
 */
class UnitTypesTest {

    /** A 6x6 board: player 1's castle at (0,0), player 2's at (5,5), a village of player 1's at (2,2). */
    private static GameState board() {
        GameState state = new GameState(6, 6);
        state.setAiEnabled(false);
        state.getGrid()[0][0].setType(TileType.CASTLE);
        state.getGrid()[0][0].setOwnerId(1);
        state.getGrid()[5][5].setType(TileType.CASTLE);
        state.getGrid()[5][5].setOwnerId(2);
        state.getGrid()[2][2].setType(TileType.VILLAGE);
        state.getGrid()[2][2].setOwnerId(1);
        // no village recruits, so headcounts in these tests only change by the rule under test
        state.getGrid()[2][2].setPopulation(0);
        return state;
    }

    private static Army army(GameState state, int x, int y, int inf, int arch, int cav, int playerId) {
        Army army = new Army(x, y, inf + arch + cav, playerId);
        army.setArchers(arch);
        army.setCavalry(cav);
        state.getArmiesInternal().add(army);
        return army;
    }

    private static GameService serviceWith(GameState state) {
        GameService service = new GameService();
        service.setGameState(state);
        return service;
    }

    private static Command train(int armyId, String type, int count) {
        Command c = new Command();
        c.setType("TRAIN");
        c.setArmyId(armyId);
        c.setUnitType(type);
        c.setCount(count);
        return c;
    }

    private static Command move(int armyId, int x, int y) {
        Command c = new Command();
        c.setType("MOVE");
        c.setArmyId(armyId);
        c.setTargetX(x);
        c.setTargetY(y);
        return c;
    }

    private static void assertMix(Army army, int inf, int arch, int cav) {
        assertEquals(inf, army.getInfantry(), "infantry");
        assertEquals(arch, army.getArchers(), "archers");
        assertEquals(cav, army.getCavalry(), "cavalry");
        assertEquals(inf + arch + cav, army.getSoldiers(), "soldiers");
    }

    // ------------------------------------------------------------------ the map's rule

    @Test
    void anAllInfantryArmysStrengthIsExactlyTheOldOne() {
        for (int soldiers = 0; soldiers <= 400; soldiers += 7) {
            for (int morale = 0; morale <= 200; morale++) {
                Army mine = new Army(0, 0, soldiers, 1);
                mine.setMorale(morale);
                assertEquals((soldiers * morale + 50) / 100, CombatService.calculateEffectiveStrength(mine));
            }
            assertEquals(soldiers, CombatService.calculateEffectiveStrength(new Army(0, 0, soldiers, 2)));
        }
    }

    @Test
    void archersAndCavalryWeighMore() {
        GameState state = board();
        Army enemy = army(state, 3, 3, 16, 5, 3, 2);
        // 1600 + 625 + 450 = 2675 hundredths -> 27 (rounded half up)
        assertEquals(27, CombatService.calculateEffectiveStrength(enemy));
        Army mine = army(state, 3, 3, 16, 5, 3, 1);
        assertEquals(27, CombatService.calculateEffectiveStrength(mine));   // morale 100
        mine.setMorale(80);
        assertEquals(21, CombatService.calculateEffectiveStrength(mine));   // (2675*80 + 5000) / 10000
        assertEquals(10, CombatService.calculateEffectiveStrength(army(state, 1, 1, 0, 8, 0, 2)));   // 8 * 1.25
        assertEquals(15, CombatService.calculateEffectiveStrength(army(state, 1, 2, 0, 0, 10, 2)));  // 10 * 1.5
    }

    @Test
    void autoResolvedLossesComeOutOfEveryTypeInProportion() {
        GameState state = board();
        Army mine = army(state, 3, 3, 10, 0, 0, 1);
        Army theirs = army(state, 3, 3, 12, 8, 0, 2);   // 2200 hundredths -> 22
        serviceWith(state).tick();
        assertFalse(state.getArmiesInternal().contains(mine), "10 against 22 is wiped out");
        assertMix(theirs, 6, 4, 0);   // lost 10 of 12/8: 6 infantry, 4 archers
    }

    @Test
    void cavalryCanTurnABattle() {
        GameState state = board();
        Army mine = army(state, 3, 3, 20, 0, 0, 1);
        Army theirs = army(state, 3, 3, 10, 0, 4, 2);   // 14 soldiers, strength 16
        serviceWith(state).tick();
        assertEquals(4, mine.getSoldiers());
        assertFalse(state.getArmiesInternal().contains(theirs));
    }

    // ------------------------------------------------------------------ merge and split

    @Test
    void mergingAddsUpEveryType() {
        GameState state = board();
        Army a = army(state, 3, 3, 10, 2, 0, 1);
        army(state, 3, 3, 5, 1, 3, 1);
        serviceWith(state).tick();
        assertEquals(1, state.getArmiesInternal().stream().filter(x -> x.getPlayerId() == 1).count());
        assertMix(a, 15, 3, 3);
    }

    @Test
    void aSplitTakesItsShareOfEachType() {
        GameState state = board();
        Army a = army(state, 3, 3, 12, 6, 2, 1);
        GameService service = serviceWith(state);
        service.splitArmy(a.getId(), 10);
        Army part = state.getArmiesInternal().get(1);
        assertMix(part, 6, 3, 1);
        assertMix(a, 6, 3, 1);
    }

    @Test
    void aSplitCanNameItsArchersAndCavalry() {
        GameState state = board();
        Army a = army(state, 3, 3, 12, 6, 2, 1);
        GameService service = serviceWith(state);
        Command split = new Command();
        split.setType("SPLIT");
        split.setArmyId(a.getId());
        split.setSplitAmount(8);
        split.setSplitArchers(6);
        split.setSplitCavalry(2);
        service.executeCommand(split);
        assertMix(state.getArmiesInternal().get(1), 0, 6, 2);
        assertMix(a, 12, 0, 0);

        split.setSplitAmount(3);
        split.setSplitArchers(1);
        split.setSplitCavalry(0);
        assertThrows(IllegalArgumentException.class, () -> service.executeCommand(split), "it has no archers left");
        assertEquals(12, a.getSoldiers(), "a refused split changes nothing");
    }

    // ------------------------------------------------------------------ training

    @Test
    void archersTrainAtAHeldVillageTwoATick() {
        GameState state = board();
        Army a = army(state, 2, 2, 10, 0, 0, 1);
        GameService service = serviceWith(state);
        service.executeCommand(train(a.getId(), "ARCHERS", 5));
        assertEquals(UnitType.ARCHERS, a.getTrainingType());
        assertMix(a, 10, 0, 0);   // nothing converts until the turn runs
        service.tick();
        assertMix(a, 8, 2, 0);
        service.tick();
        assertMix(a, 6, 4, 0);
        service.tick();
        assertMix(a, 5, 5, 0);
        assertFalse(a.isTraining(), "the order is done");
        service.tick();
        assertMix(a, 5, 5, 0);
    }

    @Test
    void cavalryTrainsAtAHeldCastleOneATick() {
        GameState state = board();
        Army a = army(state, 0, 0, 10, 0, 0, 1);
        GameService service = serviceWith(state);
        service.executeCommand(train(a.getId(), "cavalry", 2));
        service.tick();
        assertMix(a, 9, 0, 1);
        service.tick();
        assertMix(a, 8, 0, 2);
        assertFalse(a.isTraining());
    }

    @Test
    void trainingNeedsTheRightTileHeldByThePlayer() {
        GameState state = board();
        Army atVillage = army(state, 2, 2, 10, 0, 0, 1);
        Army atCastle = army(state, 0, 0, 10, 0, 0, 1);
        Army inTheOpen = army(state, 4, 1, 10, 0, 0, 1);
        state.getGrid()[1][4].setType(TileType.VILLAGE);
        state.getGrid()[1][4].setOwnerId(2);
        Army atEnemyVillage = army(state, 1, 4, 10, 0, 0, 1);
        GameService service = serviceWith(state);

        assertThrows(IllegalStateException.class, () -> service.executeCommand(train(atVillage.getId(), "CAVALRY", 1)));
        assertThrows(IllegalStateException.class, () -> service.executeCommand(train(atCastle.getId(), "ARCHERS", 1)));
        assertThrows(IllegalStateException.class, () -> service.executeCommand(train(inTheOpen.getId(), "ARCHERS", 1)));
        assertThrows(IllegalStateException.class, () -> service.executeCommand(train(atEnemyVillage.getId(), "ARCHERS", 1)));
        for (Army army : state.getArmiesInternal()) {
            assertFalse(army.isTraining());
        }
    }

    @Test
    void badTrainingRequestsAreRefused() {
        GameState state = board();
        Army a = army(state, 2, 2, 6, 4, 0, 1);
        Army enemy = army(state, 5, 5, 10, 0, 0, 2);
        GameService service = serviceWith(state);
        assertThrows(IllegalArgumentException.class, () -> service.executeCommand(train(a.getId(), "ARCHERS", 7)), "only 6 infantry");
        assertThrows(IllegalArgumentException.class, () -> service.executeCommand(train(a.getId(), "ARCHERS", -1)));
        assertThrows(IllegalArgumentException.class, () -> service.executeCommand(train(a.getId(), "KNIGHTS", 1)));
        assertThrows(IllegalArgumentException.class, () -> service.executeCommand(train(a.getId(), null, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.executeCommand(train(enemy.getId(), "CAVALRY", 1)), "not yours");
        assertThrows(IllegalArgumentException.class, () -> service.executeCommand(train(9999, "ARCHERS", 1)));
        a.setDestinationX(3);
        a.setDestinationY(2);
        assertThrows(IllegalStateException.class, () -> service.executeCommand(train(a.getId(), "ARCHERS", 1)), "on the march");
    }

    @Test
    void anArmyInAPendingBattleCannotTrain() {
        GameState state = board();
        state.setFieldBattles(true);
        Army a = army(state, 2, 2, 10, 0, 0, 1);
        army(state, 2, 2, 5, 0, 0, 2);
        GameService service = serviceWith(state);
        service.tick();
        assertEquals(1, state.getPendingBattlesInternal().size());
        assertThrows(IllegalStateException.class, () -> service.executeCommand(train(a.getId(), "ARCHERS", 2)));
    }

    @Test
    void marchingOffCancelsTraining() {
        GameState state = board();
        Army a = army(state, 2, 2, 10, 0, 0, 1);
        GameService service = serviceWith(state);
        service.executeCommand(train(a.getId(), "ARCHERS", 6));
        service.tick();
        assertMix(a, 8, 2, 0);
        service.executeCommand(move(a.getId(), 3, 2));
        assertFalse(a.isTraining(), "a move order drops the training at once");
        service.tick();
        assertMix(a, 8, 2, 0);
        // walking back does not resume it
        service.executeCommand(move(a.getId(), 2, 2));
        service.tick();
        service.tick();
        assertMix(a, 8, 2, 0);
    }

    @Test
    void losingTheTileCancelsTraining() {
        GameState state = board();
        Army a = army(state, 2, 2, 10, 0, 0, 1);
        GameService service = serviceWith(state);
        service.executeCommand(train(a.getId(), "ARCHERS", 6));
        state.getGrid()[2][2].setOwnerId(0);
        service.tick();
        assertMix(a, 10, 0, 0);
        assertFalse(a.isTraining());
    }

    @Test
    void aCountOfZeroCancelsAndANewOrderReplaces() {
        GameState state = board();
        Army a = army(state, 2, 2, 10, 0, 0, 1);
        GameService service = serviceWith(state);
        service.executeCommand(train(a.getId(), "ARCHERS", 6));
        service.executeCommand(train(a.getId(), "ARCHERS", 1));
        service.tick();
        assertMix(a, 9, 1, 0);
        service.executeCommand(train(a.getId(), "ARCHERS", 4));
        service.executeCommand(train(a.getId(), "ARCHERS", 0));
        assertFalse(a.isTraining());
        service.tick();
        assertMix(a, 9, 1, 0);
    }

    @Test
    void trainingStopsWhenTheInfantryRunsOut() {
        GameState state = board();
        Army a = army(state, 2, 2, 3, 0, 0, 1);
        GameService service = serviceWith(state);
        service.executeCommand(train(a.getId(), "ARCHERS", 3));
        // deserters or losses could leave fewer infantry than ordered; simulate one gone
        a.loseSoldiers(1);
        service.tick();
        service.tick();
        assertMix(a, 0, 2, 0);
        assertFalse(a.isTraining());
    }

    // ------------------------------------------------------------------ the AI

    /** An AI army sitting at its castle with nothing worth attacking. */
    private static GameState idleAiAtCastle(Boolean unitTypes) {
        GameState state = board();
        state.setAiEnabled(true);
        state.setUnitTypes(unitTypes);
        // player 1 holds its castle and its village too strongly for the AI to attack either
        army(state, 0, 0, 60, 0, 0, 1);
        army(state, 2, 2, 60, 0, 0, 1);
        army(state, 5, 5, 20, 0, 0, 2);
        return state;
    }

    @Test
    void theAiTrainsSomeCavalryAtItsCastleInANewGame() {
        GameState state = idleAiAtCastle(true);
        GameService service = serviceWith(state);
        for (int i = 0; i < 10; i++) {
            service.tick();
        }
        Army ai = state.getArmiesInternal().stream().filter(x -> x.getPlayerId() == 2).findFirst().orElseThrow();
        assertEquals(20, ai.getSoldiers());
        assertEquals(4, ai.getCavalry(), "a fifth of its strength, and no more");
        assertEquals(0, ai.getArchers());
    }

    @Test
    void theAiTrainsSomeArchersAtItsVillage() {
        GameState state = board();
        state.setAiEnabled(true);
        state.setUnitTypes(true);
        state.getGrid()[4][4].setType(TileType.VILLAGE);
        state.getGrid()[4][4].setOwnerId(2);
        state.getGrid()[4][4].setPopulation(0);
        army(state, 0, 0, 60, 0, 0, 1);
        // fewer than the AI's minimum to march, so it stays to garrison and build
        Army ai = army(state, 4, 4, 4, 0, 0, 2);
        GameService service = serviceWith(state);
        for (int i = 0; i < 5; i++) {
            service.tick();
        }
        assertEquals(1, ai.getArchers(), "a quarter of 4");
    }

    @Test
    void theAiInASaveFromBeforeUnitTypesNeverTrains() {
        GameState state = idleAiAtCastle(null);
        GameService service = serviceWith(state);
        for (int i = 0; i < 10; i++) {
            service.tick();
        }
        for (Army army : state.getArmiesInternal()) {
            assertMix(army, army.getSoldiers(), 0, 0);
        }
    }

    // ------------------------------------------------------------------ field battles

    @Test
    void aPendingBattleRecordsEachSidesMix() {
        GameState state = board();
        state.setFieldBattles(true);
        army(state, 3, 3, 10, 4, 2, 1);
        army(state, 3, 3, 12, 0, 3, 2);
        serviceWith(state).tick();
        PendingBattle battle = state.getPendingBattlesInternal().get(0);
        assertEquals(16, battle.getSide0Soldiers());
        assertEquals(4, battle.getSide0Archers());
        assertEquals(2, battle.getSide0Cavalry());
        assertEquals(15, battle.getSide1Soldiers());
        assertEquals(0, battle.getSide1Archers());
        assertEquals(3, battle.getSide1Cavalry());
        PendingBattle copy = state.createSnapshot().getPendingBattles().get(0);
        assertEquals(4, copy.getSide0Archers());
        assertEquals(3, copy.getSide1Cavalry());
    }

    /** Two of player 1's armies against one of player 2's, as a pending battle (a side can hold several). */
    private static final class TwoOnOne {
        final GameState state = board();
        final Army a = army(state, 3, 3, 10, 4, 0, 1);
        final Army b = army(state, 3, 3, 5, 2, 3, 1);
        final Army c = army(state, 3, 3, 20, 0, 0, 2);
        final PendingBattle battle = new PendingBattle();
        final GameService service;

        TwoOnOne() {
            state.setFieldBattles(true);
            battle.setId(1);
            battle.setX(3);
            battle.setY(3);
            battle.setTerrain(TileType.EMPTY);
            battle.setSide0ArmyIds(new java.util.ArrayList<>(List.of(a.getId(), b.getId())));
            battle.setSide1ArmyIds(new java.util.ArrayList<>(List.of(c.getId())));
            battle.setSide0Soldiers(24);
            battle.setSide0Archers(6);
            battle.setSide0Cavalry(3);
            battle.setSide1Soldiers(20);
            state.getPendingBattlesInternal().add(battle);
            service = serviceWith(state);
        }

        BattleResult result(int[] survivors, int[][] byType) {
            BattleResult r = new BattleResult();
            r.setBattleId(1);
            r.setResult("FIELD");
            r.setWinner(0);
            r.setSurvivors(survivors);
            r.setSurvivorsByType(byType);
            return r;
        }
    }

    @Test
    void survivorsByTypeAreSharedByWhatEachArmyBrought() {
        TwoOnOne f = new TwoOnOne();
        f.service.resolveBattle(f.result(new int[] {12, 0}, new int[][] {{7, 3, 2}, {0, 0, 0}}));
        // infantry 7 over 10/5 -> 5/2; archers 3 over 4/2 -> 2/1; cavalry 2 over 0/3 -> 0/2
        assertMix(f.a, 5, 2, 0);
        assertMix(f.b, 2, 1, 2);
        assertFalse(f.state.getArmiesInternal().contains(f.c));
    }

    @Test
    void withoutTypesEachArmysLossesAreSpreadAcrossItsMix() {
        TwoOnOne f = new TwoOnOne();
        f.service.resolveBattle(f.result(new int[] {12, 0}, null));
        // 12 of 24 shared 14/10 -> 7/5; a loses 7 of 10/4/0, b loses 5 of 5/2/3
        assertMix(f.a, 5, 2, 0);
        assertMix(f.b, 2, 1, 2);
    }

    @Test
    void survivorsByTypeThatDoNotFitAreRefused() {
        TwoOnOne f = new TwoOnOne();
        int[] s = {12, 0};
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{5, 7, 0}, {0, 0, 0}})), "only 6 archers came");
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{0, 0, 4}, {0, 0, 0}})), "only 3 cavalry came");
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{7, 3, 1}, {0, 0, 0}})), "adds up to 11, not 12");
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{13, -1, 0}, {0, 0, 0}})), "negative");
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{16, 0, 0}, {0, 0, 0}})), "only 15 infantry came");
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{12, 0, 0}, {0, 0, 1}})), "side 1 had no cavalry");
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{12, 0, 0}})), "one side only");
        assertThrows(IllegalArgumentException.class,
                () -> f.service.resolveBattle(f.result(s, new int[][] {{12, 0}, {0, 0}})), "two types only");
        // nothing changed
        assertEquals(1, f.state.getPendingBattlesInternal().size());
        assertMix(f.a, 10, 4, 0);
        assertMix(f.b, 5, 2, 3);
    }

    @Test
    void aRoutedArmyKeepsItsMixAndDropsItsTraining() {
        GameState state = board();
        state.setFieldBattles(true);
        Army mine = army(state, 3, 3, 10, 0, 0, 1);
        Army theirs = army(state, 3, 3, 6, 4, 2, 2);
        theirs.setTrainingType(UnitType.ARCHERS);
        theirs.setTrainingRemaining(2);
        GameService service = serviceWith(state);
        service.tick();
        BattleResult r = new BattleResult();
        r.setBattleId(state.getPendingBattlesInternal().get(0).getId());
        r.setResult("FIELD");
        r.setWinner(0);
        r.setSurvivors(new int[] {8, 6});
        r.setSurvivorsByType(new int[][] {{8, 0, 0}, {2, 2, 2}});
        service.resolveBattle(r);
        assertMix(mine, 8, 0, 0);
        assertMix(theirs, 2, 2, 2);
        assertTrue(theirs.getX() != 3 || theirs.getY() != 3, "it left the field");
        assertFalse(theirs.isTraining());
    }
}
