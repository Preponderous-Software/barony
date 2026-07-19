package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;
import org.springframework.stereotype.Service;

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

        gameState.incrementTick();
        policyService.applyStatRecovery(gameState);
        processMovement();
        combatService.mergeFriendlyArmies(gameState);
        policyService.processVillageSoldierGeneration(gameState);

        if (gameState.isAiEnabled()) {
            aiService.executeAiTurn(gameState);
        }

        combatService.processCombat(gameState);
        processVillageCapture();
        processCastleCapture();
        checkWinCondition();
        policyService.processDesertion(gameState);
        spawnArmyIfNoneRemaining();
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
        Army targetArmy = findArmyById(armyId);
        if (targetArmy == null || targetArmy.getPlayerId() != 1) {
            return;
        }

        if (soldierCount < 1 || soldierCount >= targetArmy.getSoldiers()) {
            return;
        }

        Army newArmy = new Army(targetArmy.getX(), targetArmy.getY(), soldierCount, targetArmy.getPlayerId());
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
        if (targetArmy == null) {
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
