package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.BattleResult;
import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.PendingBattle;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates game tick processing and command execution.
 * Delegates combat, AI, and policy logic to focused service classes.
 */
@Service
public class GameService {
    private GameState gameState;
    private final AiService aiService = new AiService();
    private final CombatService combatService = new CombatService();
    private final PolicyService policyService = new PolicyService();

    public GameService() {
        initializeGame();
    }

    private void initializeGame() {
        MapGenerator generator = new MapGenerator();
        gameState = generator.generate();
    }

    public synchronized void setGameState(GameState state) {
        this.gameState = state;
    }

    /**
     * Atomically sets the game state and executes an action under the same lock,
     * preventing interleaved requests from operating on the wrong session's state.
     */
    public synchronized <T> T executeWithSessionState(GameState state, java.util.function.Function<GameService, T> action) {
        this.gameState = state;
        return action.apply(this);
    }

    public synchronized GameState getGameStateInternal() {
        return this.gameState;
    }

    public synchronized GameState getState() {
        return gameState.createSnapshot();
    }

    public synchronized void tick() {
        if (gameState.isGameOver()) {
            return;
        }

        // A turn still waiting on its battles is finished first by the map's rule, so a client
        // that only knows how to tick (or a player who ticks past a battle) never stalls.
        if (!gameState.getPendingBattlesInternal().isEmpty()) {
            resolveAllPendingByMapRule();
            if (gameState.isGameOver()) {
                return;
            }
        }

        gameState.incrementTick();
        policyService.applyStatRecovery(gameState);
        processMovement();
        combatService.mergeFriendlyArmies(gameState);
        policyService.processVillageSoldierGeneration(gameState);

        if (gameState.isAiEnabled()) {
            aiService.executeAiTurn(gameState);
        }

        if (gameState.isFieldBattlesEnabled() && recordPendingBattles()) {
            // The turn waits here for the player to fight (or auto-resolve) each battle;
            // resolveBattle finishes it once the last one is decided.
            return;
        }
        combatService.processCombat(gameState);
        finishTick();
    }

    /** The rest of a turn after combat: captures, the win check, desertion and respawns. */
    private void finishTick() {
        processVillageCapture();
        processCastleCapture();
        checkWinCondition();
        policyService.processDesertion(gameState);
        spawnArmyIfNoneRemaining();
    }

    // ------------------------------------------------------------------ field battles

    /** The battle {@code battleId}, or null if there is no such pending battle. */
    private PendingBattle findPendingBattle(int battleId) {
        for (PendingBattle battle : gameState.getPendingBattlesInternal()) {
            if (battle.getId() == battleId) {
                return battle;
            }
        }
        return null;
    }

    /**
     * Records a pending battle for every tile where player 1's armies stand with the enemy's.
     * Returns whether any were recorded.
     */
    private boolean recordPendingBattles() {
        Map<String, PendingBattle> byTile = new LinkedHashMap<>();
        Map<String, long[]> moraleByTile = new HashMap<>();
        for (Army army : gameState.getArmiesInternal()) {
            boolean enemyHere = false;
            boolean playerHere = false;
            for (Army other : gameState.getArmiesInternal()) {
                if (other.getX() == army.getX() && other.getY() == army.getY()) {
                    if (other.getPlayerId() == 1) {
                        playerHere = true;
                    } else {
                        enemyHere = true;
                    }
                }
            }
            if (!enemyHere || !playerHere) {
                continue;
            }
            String key = army.getX() + "," + army.getY();
            PendingBattle battle = byTile.computeIfAbsent(key, k -> newBattleAt(army.getX(), army.getY()));
            if (army.getPlayerId() == 1) {
                battle.getSide0ArmyIds().add(army.getId());
                battle.setSide0Soldiers(battle.getSide0Soldiers() + army.getSoldiers());
                long[] m = moraleByTile.computeIfAbsent(key, k -> new long[2]);
                m[0] += (long) army.getMorale() * army.getSoldiers();
                m[1] += army.getSoldiers();
            } else {
                battle.getSide1ArmyIds().add(army.getId());
                battle.setSide1Soldiers(battle.getSide1Soldiers() + army.getSoldiers());
            }
        }
        for (Map.Entry<String, PendingBattle> e : byTile.entrySet()) {
            long[] m = moraleByTile.get(e.getKey());
            e.getValue().setSide0Morale(m == null || m[1] == 0 ? 100 : (int) Math.round((double) m[0] / m[1]));
            gameState.getPendingBattlesInternal().add(e.getValue());
        }
        return !byTile.isEmpty();
    }

    private PendingBattle newBattleAt(int x, int y) {
        PendingBattle battle = new PendingBattle();
        gameState.setNextBattleId(gameState.getNextBattleId() + 1);
        battle.setId(gameState.getNextBattleId());
        battle.setX(x);
        battle.setY(y);
        battle.setTerrain(gameState.getGrid()[x][y].getType());
        battle.setTick(gameState.getTickCount());
        // The same engagement is always fought on the same field.
        battle.setSeed((int) ((gameState.getTickCount() * 7919L + x * 104729L + y * 1299709L) % 999983L) + 1);
        return battle;
    }

    /**
     * Resolves one pending battle, by the field result the player fought or by the map's rule, and
     * finishes the paused turn once none are left.
     *
     * @throws IllegalArgumentException for an unknown battle or a result that does not fit it
     */
    public synchronized void resolveBattle(BattleResult result) {
        if (result == null) {
            throw new IllegalArgumentException("A battle result is required");
        }
        PendingBattle battle = findPendingBattle(result.getBattleId());
        if (battle == null) {
            throw new IllegalArgumentException("No pending battle " + result.getBattleId());
        }
        if ("AUTO".equals(result.getResult())) {
            combatService.processCombatAt(gameState, battle.getX(), battle.getY());
        } else if ("FIELD".equals(result.getResult())) {
            applyFieldResult(battle, result);
        } else {
            throw new IllegalArgumentException("result must be FIELD or AUTO");
        }
        gameState.getPendingBattlesInternal().remove(battle);
        if (gameState.getPendingBattlesInternal().isEmpty()) {
            finishTick();
        }
    }

    /** Auto-resolves every pending battle and finishes the paused turn. */
    private void resolveAllPendingByMapRule() {
        for (PendingBattle battle : new ArrayList<>(gameState.getPendingBattlesInternal())) {
            combatService.processCombatAt(gameState, battle.getX(), battle.getY());
        }
        gameState.getPendingBattlesInternal().clear();
        finishTick();
    }

    private void applyFieldResult(PendingBattle battle, BattleResult result) {
        int[] survivors = result.getSurvivors();
        if (survivors == null || survivors.length != 2) {
            throw new IllegalArgumentException("survivors must give a count for each side");
        }
        Integer winner = result.getWinner();
        if (winner != null && winner != 0 && winner != 1) {
            throw new IllegalArgumentException("winner must be 0, 1 or null for a draw");
        }
        int[] start = {battle.getSide0Soldiers(), battle.getSide1Soldiers()};
        for (int side = 0; side < 2; side++) {
            if (survivors[side] < 0 || survivors[side] > start[side]) {
                throw new IllegalArgumentException("side " + side + " cannot keep " + survivors[side]
                        + " of " + start[side] + " soldiers");
            }
        }
        if (winner != null && survivors[winner] < 1) {
            throw new IllegalArgumentException("the winner must have soldiers left");
        }
        List<List<Integer>> ids = List.of(battle.getSide0ArmyIds(), battle.getSide1ArmyIds());
        for (int side = 0; side < 2; side++) {
            List<Army> armies = new ArrayList<>();
            for (int id : ids.get(side)) {
                Army army = findArmyById(id);
                if (army != null) {
                    armies.add(army);
                }
            }
            shareSurvivors(armies, survivors[side]);
            if (winner != null && winner != side) {
                for (Army army : armies) {
                    if (army.getSoldiers() > 0) {
                        retreat(army);
                    }
                }
            }
        }
        gameState.getArmiesInternal().removeIf(army -> army.getSoldiers() <= 0);
    }

    /** Shares a side's survivors among its armies in proportion to their size (largest remainder). */
    private static void shareSurvivors(List<Army> armies, int survivors) {
        int total = 0;
        for (Army army : armies) {
            total += army.getSoldiers();
        }
        if (total == 0) {
            return;
        }
        int[] kept = new int[armies.size()];
        double[] remainder = new double[armies.size()];
        int assigned = 0;
        for (int i = 0; i < armies.size(); i++) {
            double exact = (double) survivors * armies.get(i).getSoldiers() / total;
            kept[i] = (int) Math.floor(exact);
            remainder[i] = exact - kept[i];
            assigned += kept[i];
        }
        while (assigned < survivors) {
            int best = 0;
            for (int i = 1; i < armies.size(); i++) {
                if (remainder[i] > remainder[best]) {
                    best = i;
                }
            }
            kept[best]++;
            remainder[best] = -1;
            assigned++;
        }
        for (int i = 0; i < armies.size(); i++) {
            armies.get(i).setSoldiers(kept[i]);
        }
    }

    /**
     * Moves a beaten army off the field to a neighbouring tile with no enemy on it: its own
     * player's land first, then a tile with a friendly army, then the first free one in a fixed
     * order (north, east, south, west). With nowhere to go it is lost. It stops marching: a routed
     * army does not carry on with its orders.
     */
    private void retreat(Army army) {
        int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int bestX = -1;
        int bestY = -1;
        int bestScore = -1;
        for (int[] step : steps) {
            int nx = army.getX() + step[0];
            int ny = army.getY() + step[1];
            if (nx < 0 || ny < 0 || nx >= gameState.getWidth() || ny >= gameState.getHeight()) {
                continue;
            }
            boolean enemy = false;
            boolean friend = false;
            for (Army other : gameState.getArmiesInternal()) {
                if (other.getX() == nx && other.getY() == ny && other != army && other.getSoldiers() > 0) {
                    if (other.getPlayerId() == army.getPlayerId()) {
                        friend = true;
                    } else {
                        enemy = true;
                    }
                }
            }
            if (enemy) {
                continue;
            }
            int score = (gameState.getGrid()[nx][ny].getOwnerId() == army.getPlayerId() ? 2 : 0) + (friend ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                bestX = nx;
                bestY = ny;
            }
        }
        if (bestScore < 0) {
            army.setSoldiers(0);
            return;
        }
        army.setX(bestX);
        army.setY(bestY);
        army.setDestinationX(null);
        army.setDestinationY(null);
    }

    /** Turns field battles on or off for this game. Turning them off resolves any pending battle by the map's rule. */
    public synchronized void setFieldBattles(boolean enabled) {
        gameState.setFieldBattles(enabled);
        if (!enabled && !gameState.getPendingBattlesInternal().isEmpty()) {
            resolveAllPendingByMapRule();
        }
    }

    public synchronized void executeCommand(Command command) {
        if (gameState.isGameOver()) {
            return;
        }

        if ("MOVE".equals(command.getType())) {
            executeMoveCommand(command);
        } else if ("SPLIT".equals(command.getType())) {
            splitArmy(command.getArmyId(), command.getSplitAmount());
        }
    }

    public synchronized void splitArmy(int armyId, int soldierCount) {
        for (PendingBattle battle : gameState.getPendingBattlesInternal()) {
            if (battle.getSide0ArmyIds().contains(armyId)) {
                throw new IllegalStateException("Army " + armyId + " is in a battle that has not been fought yet");
            }
        }
        Army targetArmy = findArmyById(armyId);
        if (targetArmy == null || targetArmy.getPlayerId() != 1) {
            return;
        }

        if (soldierCount < 1 || soldierCount >= targetArmy.getSoldiers()) {
            return;
        }

        Army newArmy = new Army(targetArmy.getX(), targetArmy.getY(), soldierCount, targetArmy.getPlayerId());
        // Detached soldiers keep the parent's morale and loyalty rather than arriving as fresh
        // recruits at 100/100 — otherwise splitting is a free reset of a disloyal army's desertion.
        // The pending desertion fraction stays with the parent instead of being duplicated.
        newArmy.setMorale(targetArmy.getMorale());
        newArmy.setLoyalty(targetArmy.getLoyalty());
        targetArmy.setSoldiers(targetArmy.getSoldiers() - soldierCount);
        gameState.getArmiesInternal().add(newArmy);
    }

    public synchronized int getPlayerIncome(int playerId) {
        return policyService.getPlayerIncome(playerId, gameState);
    }

    public synchronized void resetGame() {
        initializeGame();
    }

    public synchronized void setAiEnabled(boolean enabled) {
        gameState.setAiEnabled(enabled);
    }

    public synchronized boolean canChangePolicy() {
        return policyService.canChangePolicy(gameState);
    }

    public synchronized void changePolicy(RulerDecision.PolicyCategory category, String choice) {
        policyService.changePolicy(gameState, category, choice);
    }

    public synchronized RulerStats getRulerStats() {
        return policyService.getRulerStats(gameState);
    }

    synchronized GameState getInternalStateForTest() {
        return gameState;
    }

    // --- Private helpers ---

    private void executeMoveCommand(Command command) {
        Army targetArmy = findArmyById(command.getArmyId());
        if (targetArmy == null || targetArmy.getPlayerId() != 1) {
            return;
        }

        int targetX = command.getTargetX();
        int targetY = command.getTargetY();

        if (!isWithinBounds(targetX, targetY)) {
            return;
        }

        if (targetX == targetArmy.getX() && targetY == targetArmy.getY()) {
            targetArmy.setDestinationX(null);
            targetArmy.setDestinationY(null);
        } else {
            targetArmy.setDestinationX(targetX);
            targetArmy.setDestinationY(targetY);
        }
    }

    private void processMovement() {
        for (Army army : gameState.getArmiesInternal()) {
            if (army.isMoving()) {
                moveOneStep(army);
            }
        }
    }

    private void moveOneStep(Army army) {
        int currentX = army.getX();
        int currentY = army.getY();
        int destX = army.getDestinationX();
        int destY = army.getDestinationY();

        int nextX = currentX;
        int nextY = currentY;

        if (currentX < destX) {
            nextX = currentX + 1;
        } else if (currentX > destX) {
            nextX = currentX - 1;
        } else if (currentY < destY) {
            nextY = currentY + 1;
        } else if (currentY > destY) {
            nextY = currentY - 1;
        }

        army.setX(nextX);
        army.setY(nextY);

        if (nextX == destX && nextY == destY) {
            army.setDestinationX(null);
            army.setDestinationY(null);
        }
    }

    private void processVillageCapture() {
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE) {
                    updateVillageOwnership(tile, x, y);
                }
            }
        }
    }

    private void updateVillageOwnership(Tile village, int x, int y) {
        TileOccupancy occupancy = determineTileOccupancy(x, y);

        if (occupancy.contested) {
            village.setOwnerId(0);
        } else if (occupancy.playerId != null && village.getOwnerId() != occupancy.playerId) {
            village.setOwnerId(occupancy.playerId);
        }
    }

    private void processCastleCapture() {
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.CASTLE) {
                    updateCastleOccupation(tile, x, y);
                }
            }
        }
    }

    private void updateCastleOccupation(Tile castle, int x, int y) {
        TileOccupancy occupancy = determineTileOccupancy(x, y);

        if (occupancy.contested || occupancy.playerId == null) {
            castle.setOccupationTicks(0);
        } else if (occupancy.playerId == castle.getOwnerId()) {
            castle.setOccupationTicks(0);
        } else {
            castle.setOccupationTicks(castle.getOccupationTicks() + 1);
            if (castle.getOccupationTicks() >= 3) {
                castle.setOwnerId(occupancy.playerId);
                castle.setOccupationTicks(0);
            }
        }
    }

    private TileOccupancy determineTileOccupancy(int x, int y) {
        Integer occupyingPlayer = null;
        boolean contested = false;

        for (Army army : gameState.getArmiesInternal()) {
            if (army.getX() == x && army.getY() == y) {
                if (occupyingPlayer == null) {
                    occupyingPlayer = army.getPlayerId();
                } else if (occupyingPlayer != army.getPlayerId()) {
                    contested = true;
                    break;
                }
            }
        }

        return new TileOccupancy(occupyingPlayer, contested);
    }

    private static class TileOccupancy {
        final Integer playerId;
        final boolean contested;

        TileOccupancy(Integer playerId, boolean contested) {
            this.playerId = playerId;
            this.contested = contested;
        }
    }

    private void checkWinCondition() {
        int player1Castles = 0;
        int player2Castles = 0;

        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.CASTLE) {
                    if (tile.getOwnerId() == 1) {
                        player1Castles++;
                    } else if (tile.getOwnerId() == 2) {
                        player2Castles++;
                    }
                }
            }
        }

        if (player1Castles == 0 && player2Castles > 0) {
            gameState.setGameOver(true);
            gameState.setWinnerId(2);
        } else if (player2Castles == 0 && player1Castles > 0) {
            gameState.setGameOver(true);
            gameState.setWinnerId(1);
        }
    }

    private void spawnArmyIfNoneRemaining() {
        if (gameState.isGameOver()) {
            return;
        }
        for (int playerId = 1; playerId <= 2; playerId++) {
            if (!playerHasArmies(playerId)) {
                spawnArmyAtOwnedCastle(playerId);
            }
        }
    }

    private boolean playerHasArmies(int playerId) {
        for (Army army : gameState.getArmiesInternal()) {
            if (army.getPlayerId() == playerId) {
                return true;
            }
        }
        return false;
    }

    private void spawnArmyAtOwnedCastle(int playerId) {
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.CASTLE && tile.getOwnerId() == playerId) {
                    gameState.getArmiesInternal().add(new Army(x, y, 1, playerId));
                    return;
                }
            }
        }
    }

    private Army findArmyById(int armyId) {
        for (Army army : gameState.getArmiesInternal()) {
            if (army.getId() == armyId) {
                return army;
            }
        }
        return null;
    }

    private boolean isWithinBounds(int x, int y) {
        return x >= 0 && x < gameState.getWidth() && y >= 0 && y < gameState.getHeight();
    }
}
