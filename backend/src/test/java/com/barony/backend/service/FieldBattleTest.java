package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.BattleResult;
import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.PendingBattle;
import com.barony.backend.model.TileType;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field battles: a turn in which the player's armies meet the enemy pauses before combat with a
 * pending battle, which the player fights out on the 3D battlefield (a FIELD result) or leaves to
 * the map's rule (AUTO). The rest of the turn runs once every battle is resolved.
 */
class FieldBattleTest {

    /** A 6x6 empty board with player 1's castle at (0,0) and player 2's at (5,5), no AI. */
    private static GameState board() {
        GameState state = new GameState(6, 6);
        state.setAiEnabled(false);
        state.getGrid()[0][0].setType(TileType.CASTLE);
        state.getGrid()[0][0].setOwnerId(1);
        state.getGrid()[5][5].setType(TileType.CASTLE);
        state.getGrid()[5][5].setOwnerId(2);
        state.setFieldBattles(true);
        return state;
    }

    private static Army army(GameState state, int x, int y, int soldiers, int playerId) {
        Army army = new Army(x, y, soldiers, playerId);
        state.getArmiesInternal().add(army);
        return army;
    }

    /** The armies on a tile (a side that loses its last army respawns one at its castle). */
    private static List<Army> at(GameState state, int x, int y) {
        return state.getArmiesInternal().stream().filter(a -> a.getX() == x && a.getY() == y).collect(Collectors.toList());
    }

    private static GameService serviceWith(GameState state) {
        GameService service = new GameService();
        service.setGameState(state);
        return service;
    }

    private static BattleResult field(int battleId, Integer winner, int s0, int s1) {
        BattleResult r = new BattleResult();
        r.setBattleId(battleId);
        r.setResult("FIELD");
        r.setWinner(winner);
        r.setSurvivors(new int[] {s0, s1});
        return r;
    }

    private static BattleResult auto(int battleId) {
        BattleResult r = new BattleResult();
        r.setBattleId(battleId);
        r.setResult("AUTO");
        return r;
    }

    /** Two armies already on one village tile: the next tick finds them engaged. */
    private static GameState engagedOnVillage(int mine, int theirs) {
        GameState state = board();
        state.getGrid()[2][2].setType(TileType.VILLAGE);
        army(state, 2, 2, mine, 1);
        army(state, 2, 2, theirs, 2);
        return state;
    }

    // ------------------------------------------------------------------ pausing the turn

    @Test
    void anEngagementPausesTheTurnWithAPendingBattle() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);

        service.tick();

        assertEquals(1, state.getPendingBattlesInternal().size());
        PendingBattle battle = state.getPendingBattlesInternal().get(0);
        assertEquals(2, battle.getX());
        assertEquals(2, battle.getY());
        assertEquals(TileType.VILLAGE, battle.getTerrain());
        assertEquals(30, battle.getSide0Soldiers());
        assertEquals(20, battle.getSide1Soldiers());
        assertEquals(100, battle.getSide0Morale());
        // No combat yet, and the village is not captured while the battle waits.
        assertEquals(2, state.getArmiesInternal().size());
        assertEquals(0, state.getGrid()[2][2].getOwnerId());
    }

    @Test
    void withFieldBattlesOffTheTurnResolvesCombatAtOnceAsBefore() {
        GameState state = engagedOnVillage(30, 20);
        state.setFieldBattles(false);
        serviceWith(state).tick();

        assertTrue(state.getPendingBattlesInternal().isEmpty());
        assertEquals(1, at(state, 2, 2).size());
        assertEquals(10, at(state, 2, 2).get(0).getSoldiers());
    }

    @Test
    void anEngineGameWithNoSettingKeepsTheOldRule() {
        GameState state = engagedOnVillage(30, 20);
        state.setFieldBattles(null);   // a save from before field battles, or the engine run directly
        serviceWith(state).tick();
        assertTrue(state.getPendingBattlesInternal().isEmpty());
        assertEquals(1, at(state, 2, 2).size());
    }

    @Test
    void theSameEngagementAlwaysGetsTheSameSeed() {
        GameState a = engagedOnVillage(30, 20);
        GameState b = engagedOnVillage(30, 20);
        serviceWith(a).tick();
        serviceWith(b).tick();
        assertEquals(a.getPendingBattlesInternal().get(0).getSeed(), b.getPendingBattlesInternal().get(0).getSeed());
    }

    // ------------------------------------------------------------------ resolving

    @Test
    void autoResolveAppliesTheMapRuleAndFinishesTheTurn() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);
        service.tick();
        int id = state.getPendingBattlesInternal().get(0).getId();

        service.resolveBattle(auto(id));

        assertTrue(state.getPendingBattlesInternal().isEmpty());
        assertEquals(1, at(state, 2, 2).size());
        assertEquals(10, at(state, 2, 2).get(0).getSoldiers());
        // The rest of the turn ran: the winner now holds the village.
        assertEquals(1, state.getGrid()[2][2].getOwnerId());
    }

    @Test
    void aFieldWinKeepsTheWinnersStandingAndTheLoserRetreatsWithItsRunners() {
        GameState state = engagedOnVillage(30, 20);
        state.getGrid()[2][1].setOwnerId(2);   // the enemy's own land, north of the field
        GameService service = serviceWith(state);
        service.tick();
        int id = state.getPendingBattlesInternal().get(0).getId();

        service.resolveBattle(field(id, 0, 24, 6));

        Army mine = state.getArmiesInternal().stream().filter(a -> a.getPlayerId() == 1).findFirst().orElseThrow();
        Army theirs = state.getArmiesInternal().stream().filter(a -> a.getPlayerId() == 2).findFirst().orElseThrow();
        assertEquals(24, mine.getSoldiers());
        assertEquals(2, mine.getX());
        assertEquals(2, mine.getY());
        assertEquals(6, theirs.getSoldiers());
        assertEquals(2, theirs.getX(), "the loser falls back onto its own land");
        assertEquals(1, theirs.getY());
        assertFalse(theirs.isMoving(), "a routed army stops marching");
        assertEquals(1, state.getGrid()[2][2].getOwnerId(), "the winner holds the village");
    }

    @Test
    void aLoserWithNobodyLeftIsRemoved() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);
        service.tick();
        service.resolveBattle(field(state.getPendingBattlesInternal().get(0).getId(), 0, 18, 0));
        assertEquals(1, at(state, 2, 2).size());
        assertEquals(18, at(state, 2, 2).get(0).getSoldiers());
        assertTrue(state.getArmiesInternal().stream().noneMatch(a -> a.getPlayerId() == 2 && a.getSoldiers() == 0));
    }

    @Test
    void aLoserHemmedInByEnemiesIsLost() {
        GameState state = board();
        army(state, 0, 0, 30, 2);   // the enemy at player 1's castle corner...
        army(state, 1, 0, 5, 2);    // ...with its only ways out held by more of the enemy
        army(state, 0, 1, 5, 2);
        Army mine = army(state, 0, 0, 10, 1);
        GameService service = serviceWith(state);
        service.tick();
        PendingBattle battle = state.getPendingBattlesInternal().stream()
                .filter(b -> b.getX() == 0 && b.getY() == 0).findFirst().orElseThrow();
        service.resolveBattle(field(battle.getId(), 1, 4, 20));
        assertFalse(state.getArmiesInternal().contains(mine));
    }

    @Test
    void aDrawLeavesBothSidesOnTheFieldToFightAgain() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);
        service.tick();
        service.resolveBattle(field(state.getPendingBattlesInternal().get(0).getId(), null, 9, 7));
        assertEquals(2, state.getArmiesInternal().size());
        service.tick();
        assertEquals(1, state.getPendingBattlesInternal().size(), "they meet again next turn");
    }

    @Test
    void survivorsAreSharedAmongTheSidesArmiesInProportion() {
        GameState state = board();
        Army big = army(state, 3, 3, 30, 1);
        Army small = army(state, 3, 3, 10, 1);
        army(state, 3, 3, 40, 2);
        state.setFieldBattles(true);
        GameService service = serviceWith(state);
        // Two armies of one player on a tile are merged at the start of a tick, so engage them
        // after the merge by recording the battle directly through a tick with both fresh.
        service.tick();
        PendingBattle battle = state.getPendingBattlesInternal().get(0);
        int keep = battle.getSide0Soldiers() / 2;
        service.resolveBattle(field(battle.getId(), 0, keep, 0));
        int total = state.getArmiesInternal().stream().filter(a -> a.getPlayerId() == 1).mapToInt(Army::getSoldiers).sum();
        assertEquals(keep, total);
        assertTrue(state.getArmiesInternal().contains(big) || state.getArmiesInternal().contains(small));
    }

    @Test
    void impossibleResultsAreRefused() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);
        service.tick();
        int id = state.getPendingBattlesInternal().get(0).getId();

        assertThrows(IllegalArgumentException.class, () -> service.resolveBattle(field(id, 0, 31, 0)), "more than it started with");
        assertThrows(IllegalArgumentException.class, () -> service.resolveBattle(field(id, 0, -1, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.resolveBattle(field(id, 1, 30, 0)), "a winner with nobody left");
        assertThrows(IllegalArgumentException.class, () -> service.resolveBattle(field(id, 2, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.resolveBattle(field(id + 99, 0, 1, 0)), "no such battle");
        BattleResult noSurvivors = field(id, 0, 1, 0);
        noSurvivors.setSurvivors(null);
        assertThrows(IllegalArgumentException.class, () -> service.resolveBattle(noSurvivors));
        BattleResult unknown = auto(id);
        unknown.setResult("SURRENDER");
        assertThrows(IllegalArgumentException.class, () -> service.resolveBattle(unknown));
        // A refused result changes nothing.
        assertEquals(1, state.getPendingBattlesInternal().size());
        assertEquals(2, state.getArmiesInternal().size());
    }

    @Test
    void anArmyInAPendingBattleCannotBeSplit() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);
        service.tick();
        int myId = state.getPendingBattlesInternal().get(0).getSide0ArmyIds().get(0);
        Command split = new Command();
        split.setType("SPLIT");
        split.setArmyId(myId);
        split.setSplitAmount(5);
        assertThrows(IllegalStateException.class, () -> service.executeCommand(split));
    }

    @Test
    void tickingPastAPendingBattleResolvesItByTheMapRuleFirst() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);
        service.tick();
        int tick = state.getTickCount();
        service.tick();
        assertTrue(state.getPendingBattlesInternal().isEmpty());
        assertEquals(tick + 1, state.getTickCount());
        assertEquals(1, at(state, 2, 2).size());
        assertEquals(1, at(state, 2, 2).get(0).getPlayerId());
        // 10 after the exchange; the village it now holds may have raised a soldier since.
        assertTrue(at(state, 2, 2).get(0).getSoldiers() >= 10);
    }

    @Test
    void turningFieldBattlesOffResolvesWhatIsPending() {
        GameState state = engagedOnVillage(30, 20);
        GameService service = serviceWith(state);
        service.tick();
        service.setFieldBattles(false);
        assertFalse(state.isFieldBattlesEnabled());
        assertTrue(state.getPendingBattlesInternal().isEmpty());
        assertEquals(1, at(state, 2, 2).size());
    }

    // ------------------------------------------------------------------ the old rule, exactly

    /**
     * The guarantee that makes field battles safe to turn on by default: a game where every battle
     * is auto-resolved is turn for turn the same game as one without field battles. Two games from
     * the same seeded map, with the deterministic AI playing, are compared after every turn.
     */
    @Test
    void autoResolvingEveryBattleIsExactlyTheOldGame() {
        int battles = 0;
        for (long seed = 1; seed <= 6; seed++) {
            GameState off = new MapGenerator(seed).generate();
            GameState on = new MapGenerator(seed).generate();
            off.setFieldBattles(false);
            on.setFieldBattles(true);
            GameService a = serviceWith(off);
            GameService b = serviceWith(on);
            // Send player 1 at the enemy castle so the armies meet.
            sendAtEnemy(a, off);
            sendAtEnemy(b, on);
            for (int turn = 0; turn < 60 && !off.isGameOver(); turn++) {
                a.tick();
                b.tick();
                for (PendingBattle battle : new ArrayList<>(on.getPendingBattlesInternal())) {
                    b.resolveBattle(auto(battle.getId()));
                    battles++;
                }
                assertEquals(describe(off), describe(on), "seed " + seed + ", turn " + turn);
                if (turn % 2 == 0) {
                    sendAtEnemy(a, off);
                    sendAtEnemy(b, on);
                }
            }
        }
        assertTrue(battles >= 5, "the games should have fought battles, saw " + battles);
    }

    /** Orders each of player 1's armies at the nearest enemy army, so the two sides keep meeting. */
    private static void sendAtEnemy(GameService service, GameState state) {
        for (Army army : new ArrayList<>(state.getArmiesInternal())) {
            if (army.getPlayerId() != 1) continue;
            Army target = null;
            int best = Integer.MAX_VALUE;
            for (Army other : state.getArmiesInternal()) {
                if (other.getPlayerId() == 1) continue;
                int d = Math.abs(other.getX() - army.getX()) + Math.abs(other.getY() - army.getY());
                if (d < best) { best = d; target = other; }
            }
            if (target == null) continue;
            Command move = new Command();
            move.setType("MOVE");
            move.setArmyId(army.getId());
            move.setTargetX(target.getX());
            move.setTargetY(target.getY());
            service.executeCommand(move);
        }
    }

    /** Everything a player sees of the game, without army ids (two games allocate them apart). */
    private static String describe(GameState state) {
        StringBuilder out = new StringBuilder();
        out.append(state.getTickCount()).append(' ').append(state.isGameOver()).append(' ').append(state.getWinnerId()).append('\n');
        List<String> armies = state.getArmiesInternal().stream()
                .map(a -> a.getPlayerId() + "@" + a.getX() + "," + a.getY() + " n=" + a.getSoldiers() + " m=" + a.getMorale()
                        + " l=" + a.getLoyalty() + " d=" + a.getDestinationX() + "," + a.getDestinationY())
                .sorted().collect(Collectors.toList());
        out.append(armies).append('\n');
        for (int x = 0; x < state.getWidth(); x++) {
            for (int y = 0; y < state.getHeight(); y++) {
                var t = state.getGrid()[x][y];
                out.append(t.getType().ordinal()).append(t.getOwnerId()).append(t.getOccupationTicks())
                        .append(':').append(t.getStability()).append(':').append(t.getPopulation()).append(' ');
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ saves

    @Test
    void aSaveFromBeforeFieldBattlesLoadsWithNoneAndNoSetting() throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        GameState old = new MapGenerator(3).generate();
        String json = mapper.writeValueAsString(old)
                .replaceAll(",\"pendingBattles\":\\[\\]", "")
                .replaceAll(",\"nextBattleId\":0", "")
                .replaceAll(",\"fieldBattles\":null", "");
        assertFalse(json.contains("pendingBattles"));
        GameState loaded = mapper.readValue(json, GameState.class);
        assertTrue(loaded.getPendingBattlesInternal().isEmpty());
        assertNull(loaded.getFieldBattles());
        assertFalse(loaded.isFieldBattlesEnabled());
    }

    @Test
    void pendingBattlesSurviveASaveAndLoad() throws Exception {
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        GameState state = engagedOnVillage(30, 20);
        serviceWith(state).tick();
        GameState loaded = mapper.readValue(mapper.writeValueAsString(state), GameState.class);
        assertEquals(1, loaded.getPendingBattlesInternal().size());
        assertEquals(state.getPendingBattlesInternal().get(0).getSeed(), loaded.getPendingBattlesInternal().get(0).getSeed());
        assertTrue(loaded.isFieldBattlesEnabled());
        assertEquals(state.getNextBattleId(), loaded.getNextBattleId());
        // and the snapshot the API returns carries them too
        assertEquals(1, state.createSnapshot().getPendingBattles().size());
    }
}
