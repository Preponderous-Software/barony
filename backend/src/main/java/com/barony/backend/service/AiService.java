package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all AI opponent decision-making for Player 2.
 * The AI evaluates threats, identifies targets, and issues movement orders
 * with a priority system: defend > capture neutral > attack weak > assault castle.
 */
class AiService {

    static final int AI_PLAYER_ID = 2;
    static final int MIN_SOLDIERS_TO_MOVE = 5;
    static final int MAX_ARMIES = 5;
    private static final int THREAT_DETECTION_RANGE = 3;
    private static final int CAPTURE_DANGER_RANGE = 2;
    private static final double VILLAGE_ATTACK_FORCE_RATIO = 1.5;
    private static final double CASTLE_ATTACK_FORCE_RATIO = 2.0;

    void executeAiTurn(GameState gameState) {
        int totalAiArmies = countAiArmies(gameState);
        List<Army> idleAiArmies = findIdleAiArmies(gameState);
        List<Army> newGarrisons = new ArrayList<>();

        for (Army army : idleAiArmies) {
            assignAiTarget(army, gameState);
            if (shouldLeaveGarrison(army, totalAiArmies + newGarrisons.size(), gameState, newGarrisons)) {
                Army garrison = splitGarrison(army);
                newGarrisons.add(garrison);
            }
        }

        gameState.getArmiesInternal().addAll(newGarrisons);
    }

    private void assignAiTarget(Army army, GameState gameState) {
        if (isGarrisoningAndBuilding(army, gameState)) {
            return;
        }

        int[] target = findDefensiveTarget(gameState);
        if (target != null) {
            army.setDestinationX(target[0]);
            army.setDestinationY(target[1]);
            return;
        }

        target = findNeutralVillageTarget(army, gameState);
        if (target != null) {
            army.setDestinationX(target[0]);
            army.setDestinationY(target[1]);
            return;
        }

        target = findWeakEnemyVillage(army, gameState);
        if (target != null) {
            army.setDestinationX(target[0]);
            army.setDestinationY(target[1]);
            return;
        }

        target = findCastleAssaultTarget(army, gameState);
        if (target != null) {
            army.setDestinationX(target[0]);
            army.setDestinationY(target[1]);
        }
    }

    private boolean isGarrisoningAndBuilding(Army army, GameState gameState) {
        int armyX = army.getX();
        int armyY = army.getY();
        if (!isWithinBounds(armyX, armyY, gameState)) {
            return false;
        }
        Tile currentTile = gameState.getGrid()[armyX][armyY];
        return currentTile.getType() == TileType.VILLAGE
                && currentTile.getOwnerId() == AI_PLAYER_ID
                && army.getSoldiers() < MIN_SOLDIERS_TO_MOVE;
    }

    private int[] findDefensiveTarget(GameState gameState) {
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() == AI_PLAYER_ID) {
                    if (isVillageThreatened(x, y, gameState)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    private boolean isVillageThreatened(int villageX, int villageY, GameState gameState) {
        for (Army army : gameState.getArmiesInternal()) {
            if (army.getPlayerId() != AI_PLAYER_ID) {
                int distance = manhattanDistance(army.getX(), army.getY(), villageX, villageY);
                if (distance <= THREAT_DETECTION_RANGE) {
                    return true;
                }
            }
        }
        return false;
    }

    private int[] findNeutralVillageTarget(Army army, GameState gameState) {
        int[] nearest = findNearestNeutralVillage(army.getX(), army.getY(), gameState);
        if (nearest != null && isSafeToCapture(army, nearest[0], nearest[1], gameState)) {
            return nearest;
        }
        return null;
    }

    private int[] findNearestNeutralVillage(int originX, int originY, GameState gameState) {
        int[] nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() == 0) {
                    int distance = manhattanDistance(x, y, originX, originY);
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearest = new int[]{x, y};
                    }
                }
            }
        }
        return nearest;
    }

    private boolean isSafeToCapture(Army army, int targetX, int targetY, GameState gameState) {
        for (Army enemyArmy : gameState.getArmiesInternal()) {
            if (enemyArmy.getPlayerId() != army.getPlayerId()) {
                int distance = manhattanDistance(enemyArmy.getX(), enemyArmy.getY(), targetX, targetY);
                if (distance <= CAPTURE_DANGER_RANGE && enemyArmy.getSoldiers() >= army.getSoldiers()) {
                    return false;
                }
            }
        }
        return true;
    }

    private int[] findWeakEnemyVillage(Army army, GameState gameState) {
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() != 0
                        && tile.getOwnerId() != AI_PLAYER_ID) {
                    int enemyForce = countEnemyForceAt(x, y, AI_PLAYER_ID, gameState);
                    if (army.getSoldiers() >= enemyForce * VILLAGE_ATTACK_FORCE_RATIO) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    private int[] findCastleAssaultTarget(Army army, GameState gameState) {
        int enemyPlayerId = (AI_PLAYER_ID == 1) ? 2 : 1;
        int[] enemyCastle = findPlayerCastle(enemyPlayerId, gameState);
        if (enemyCastle != null && hasOverwhelmingForce(army, enemyCastle[0], enemyCastle[1], gameState)) {
            return enemyCastle;
        }
        return null;
    }

    private int[] findPlayerCastle(int playerId, GameState gameState) {
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.CASTLE && tile.getOwnerId() == playerId) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private boolean hasOverwhelmingForce(Army army, int targetX, int targetY, GameState gameState) {
        int enemyForce = countEnemyForceAt(targetX, targetY, army.getPlayerId(), gameState);
        return army.getSoldiers() >= enemyForce * CASTLE_ATTACK_FORCE_RATIO;
    }

    private int countEnemyForceAt(int x, int y, int friendlyPlayerId, GameState gameState) {
        int enemyForce = 0;
        for (Army army : gameState.getArmiesInternal()) {
            if (army.getPlayerId() != friendlyPlayerId && army.getX() == x && army.getY() == y) {
                enemyForce += army.getSoldiers();
            }
        }
        return enemyForce;
    }

    private boolean shouldLeaveGarrison(Army army, int currentArmyCount,
                                        GameState gameState, List<Army> pendingGarrisons) {
        if (!army.isMoving() || army.getSoldiers() <= 1 || currentArmyCount >= MAX_ARMIES) {
            return false;
        }
        int x = army.getX();
        int y = army.getY();
        Tile tile = gameState.getGrid()[x][y];
        if (tile.getType() != TileType.VILLAGE || tile.getOwnerId() != AI_PLAYER_ID) {
            return false;
        }
        return !hasExistingGarrison(x, y, army, gameState, pendingGarrisons);
    }

    private boolean hasExistingGarrison(int x, int y, Army excludeArmy,
                                        GameState gameState, List<Army> pendingGarrisons) {
        for (Army other : gameState.getArmiesInternal()) {
            if (other != excludeArmy && other.getPlayerId() == AI_PLAYER_ID
                    && other.getX() == x && other.getY() == y && !other.isMoving()) {
                return true;
            }
        }
        for (Army garrison : pendingGarrisons) {
            if (garrison.getX() == x && garrison.getY() == y) {
                return true;
            }
        }
        return false;
    }

    private Army splitGarrison(Army army) {
        Army garrison = new Army(army.getX(), army.getY(), 1, AI_PLAYER_ID);
        army.setSoldiers(army.getSoldiers() - 1);
        return garrison;
    }

    private int countAiArmies(GameState gameState) {
        int count = 0;
        for (Army army : gameState.getArmiesInternal()) {
            if (army.getPlayerId() == AI_PLAYER_ID) {
                count++;
            }
        }
        return count;
    }

    private List<Army> findIdleAiArmies(GameState gameState) {
        List<Army> idle = new ArrayList<>();
        for (Army army : gameState.getArmiesInternal()) {
            if (army.getPlayerId() == AI_PLAYER_ID && !army.isMoving()) {
                idle.add(army);
            }
        }
        return idle;
    }

    private boolean isWithinBounds(int x, int y, GameState gameState) {
        return x >= 0 && x < gameState.getWidth() && y >= 0 && y < gameState.getHeight();
    }

    private int manhattanDistance(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}
