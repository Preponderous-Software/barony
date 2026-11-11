package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.BattleResult;
import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.PendingBattle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plays a game through every rule that touches an army's headcount (movement, merging, village
 * recruiting, the AI and its garrison splits, player splits, auto-resolved and field-fought battles
 * with retreats, desertion) and digests what the player can see after every turn.
 *
 * <p>It deliberately uses only the API that existed before unit types, so the very same source was
 * run against the code before unit types to record the digests {@code PreUnitTypesSaveTest}
 * expects: an all-infantry game must play out bit for bit as it always did.
 */
final class LegacyGameHarness {

    private LegacyGameHarness() { }

    /** Plays up to {@code turns} turns and returns a SHA-256 over the state after each one. */
    static String play(GameState state, int turns) throws Exception {
        GameService service = new GameService();
        service.setGameState(state);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int turn = 0; turn < turns && !state.isGameOver(); turn++) {
            if (turn % 2 == 0) {
                sendAtEnemy(service, state);
            }
            if (turn % 5 == 3) {
                splitFirstLargeArmy(service, state);
            }
            service.tick();
            int n = 0;
            for (PendingBattle battle : new ArrayList<>(state.getPendingBattlesInternal())) {
                service.resolveBattle(resultFor(battle, turn + n++));
            }
            digest.update(describe(state).getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Alternates the map's rule, a field win for the larger side, and a field draw. */
    private static BattleResult resultFor(PendingBattle battle, int k) {
        BattleResult r = new BattleResult();
        r.setBattleId(battle.getId());
        int s0 = battle.getSide0Soldiers();
        int s1 = battle.getSide1Soldiers();
        switch (k % 3) {
            case 0 -> r.setResult("AUTO");
            case 1 -> {
                r.setResult("FIELD");
                int winner = s0 >= s1 ? 0 : 1;
                r.setWinner(winner);
                int[] keep = new int[2];
                keep[winner] = Math.max(1, (winner == 0 ? s0 : s1) * 2 / 3);
                keep[1 - winner] = (winner == 0 ? s1 : s0) / 4;
                r.setSurvivors(keep);
            }
            default -> {
                r.setResult("FIELD");
                r.setWinner(null);
                r.setSurvivors(new int[] {s0 / 2, s1 / 2});
            }
        }
        return r;
    }

    private static void splitFirstLargeArmy(GameService service, GameState state) {
        for (Army army : state.getArmiesInternal()) {
            if (army.getPlayerId() == 1 && army.getSoldiers() >= 4) {
                Command split = new Command();
                split.setType("SPLIT");
                split.setArmyId(army.getId());
                split.setSplitAmount(army.getSoldiers() / 3);
                try {
                    service.executeCommand(split);
                } catch (IllegalStateException inBattle) {
                    // an army in a pending battle cannot split; there are none between turns
                }
                return;
            }
        }
    }

    /** Orders each of player 1's armies at the nearest enemy army, so the two sides keep meeting. */
    static void sendAtEnemy(GameService service, GameState state) {
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

    /** Everything a player sees of the game, without army ids (games allocate them apart). */
    static String describe(GameState state) {
        StringBuilder out = new StringBuilder();
        out.append(state.getTickCount()).append(' ').append(state.isGameOver()).append(' ').append(state.getWinnerId())
                .append(' ').append(state.getNextBattleId()).append(' ').append(state.getPendingBattlesInternal().size()).append('\n');
        List<String> armies = state.getArmiesInternal().stream()
                .map(a -> a.getPlayerId() + "@" + a.getX() + "," + a.getY() + " n=" + a.getSoldiers() + " m=" + a.getMorale()
                        + " l=" + a.getLoyalty() + " c=" + a.getDesertionCarryBasisPoints()
                        + " d=" + a.getDestinationX() + "," + a.getDestinationY())
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
}
