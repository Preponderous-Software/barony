package com.barony.backend.service;

import com.barony.backend.model.*;
import org.springframework.stereotype.Service;

import java.util.Iterator;

@Service
public class GameService {
    private GameState gameState;
    
    public GameService() {
        initializeGame();
    }
    
    private void initializeGame() {
        gameState = new GameState(10, 10);
        
        // Set up initial board with ownership
        gameState.getGrid()[0][0].setType(TileType.CASTLE);
        gameState.getGrid()[0][0].setOwnerId(1); // Player 1 castle
        gameState.getGrid()[9][9].setType(TileType.CASTLE);
        gameState.getGrid()[9][9].setOwnerId(2); // Player 2 castle
        gameState.getGrid()[3][3].setType(TileType.VILLAGE);
        gameState.getGrid()[6][6].setType(TileType.VILLAGE);
        
        // Add initial armies
        gameState.getArmiesInternal().add(new Army(0, 0, 10, 1));
        gameState.getArmiesInternal().add(new Army(9, 9, 10, 2));
    }
    
    public synchronized GameState getState() {
        // Return a snapshot/deep copy to prevent concurrent modification during serialization
        GameState snapshot = new GameState(gameState.getWidth(), gameState.getHeight());
        
        // Copy tick count (need to access directly since there's no setter)
        for (int i = 0; i < gameState.getTickCount(); i++) {
            snapshot.incrementTick();
        }
        
        // Copy game over status
        snapshot.setGameOver(gameState.isGameOver());
        snapshot.setWinnerId(gameState.getWinnerId());
        
        // Deep copy the grid
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                snapshot.getGrid()[x][y].setType(gameState.getGrid()[x][y].getType());
                snapshot.getGrid()[x][y].setOwnerId(gameState.getGrid()[x][y].getOwnerId());
                snapshot.getGrid()[x][y].setOccupationTicks(gameState.getGrid()[x][y].getOccupationTicks());
            }
        }
        
        // Deep copy the armies using copy constructor
        for (Army army : gameState.getArmiesInternal()) {
            snapshot.getArmiesInternal().add(new Army(army));
        }
        
        return snapshot;
    }
    
    public synchronized void tick() {
        gameState.incrementTick();
        
        // Process army movement
        processMovement();
        
        // Merge co-located friendly armies
        mergeFriendlyArmies();
        
        // Villages generate soldiers only for their owner
        for (Army army : gameState.getArmiesInternal()) {
            int x = army.getX();
            int y = army.getY();
            if (x >= 0 && x < gameState.getWidth() && y >= 0 && y < gameState.getHeight()) {
                Tile tile = gameState.getGrid()[x][y];
                TileType tileType = tile.getType();
                if (tileType == TileType.VILLAGE && tile.getOwnerId() == army.getPlayerId()) {
                    army.setSoldiers(army.getSoldiers() + 1);
                }
            }
        }
        
        // Process combat
        processCombat();
        
        // Process village capture after combat - only surviving armies can capture
        // If multiple armies from different players occupy a village after combat, 
        // the village becomes neutral (contested)
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE) {
                    // Find all armies at this location
                    Integer occupyingPlayer = null;
                    boolean contested = false;
                    
                    for (Army army : gameState.getArmiesInternal()) {
                        if (army.getX() == x && army.getY() == y) {
                            if (occupyingPlayer == null) {
                                occupyingPlayer = army.getPlayerId();
                            } else if (occupyingPlayer != army.getPlayerId()) {
                                // Multiple players occupy the same village - contested
                                contested = true;
                                break;
                            }
                        }
                    }
                    
                    // Update village ownership
                    if (contested) {
                        // Contested villages become neutral
                        tile.setOwnerId(0);
                    } else if (occupyingPlayer != null && tile.getOwnerId() != occupyingPlayer) {
                        // Single player occupies - capture if not already owned
                        tile.setOwnerId(occupyingPlayer);
                    }
                    // Note: Villages retain ownership when abandoned (no occupyingPlayer)
                }
            }
        }
        
        // Process castle capture
        processCastleCapture();
        
        // Check win condition
        checkWinCondition();
    }
    
    private void processMovement() {
        for (Army army : gameState.getArmiesInternal()) {
            if (army.isMoving()) {
                // Calculate next position using Manhattan distance pathfinding
                int currentX = army.getX();
                int currentY = army.getY();
                int destX = army.getDestinationX();
                int destY = army.getDestinationY();
                
                // Move one step toward destination
                int nextX = currentX;
                int nextY = currentY;
                
                // Prefer horizontal movement first, then vertical
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
                
                // Clear destination if reached
                if (nextX == destX && nextY == destY) {
                    army.setDestinationX(null);
                    army.setDestinationY(null);
                }
            }
        }
    }
    
    private void processCombat() {
        int armyCount = gameState.getArmiesInternal().size();
        for (int i = 0; i < armyCount; i++) {
            for (int j = i + 1; j < armyCount; j++) {
                if (i >= gameState.getArmiesInternal().size() || j >= gameState.getArmiesInternal().size()) {
                    continue;
                }
                
                Army army1 = gameState.getArmiesInternal().get(i);
                Army army2 = gameState.getArmiesInternal().get(j);
                
                // Check if armies are on the same tile
                if (army1.getX() == army2.getX() && army1.getY() == army2.getY()) {
                    // Different players -> combat
                    if (army1.getPlayerId() != army2.getPlayerId()) {
                        int army1Soldiers = army1.getSoldiers();
                        int army2Soldiers = army2.getSoldiers();
                        
                        army1.setSoldiers(Math.max(0, army1Soldiers - army2Soldiers));
                        army2.setSoldiers(Math.max(0, army2Soldiers - army1Soldiers));
                    }
                }
            }
        }
        
        // Remove defeated armies
        Iterator<Army> iterator = gameState.getArmiesInternal().iterator();
        while (iterator.hasNext()) {
            Army army = iterator.next();
            if (army.getSoldiers() <= 0) {
                iterator.remove();
            }
        }
    }
    
    public synchronized void executeCommand(Command command) {
        // Prevent commands when game is over
        if (gameState.isGameOver()) {
            return;
        }
        
        if ("MOVE".equals(command.getType())) {
            int armyId = command.getArmyId();
            
            Army targetArmy = null;
            for (Army army : gameState.getArmiesInternal()) {
                if (army.getId() == armyId) {
                    targetArmy = army;
                    break;
                }
            }
            
            if (targetArmy != null) {
                int targetX = command.getTargetX();
                int targetY = command.getTargetY();
                
                // Validate target position
                if (targetX >= 0 && targetX < gameState.getWidth() && 
                    targetY >= 0 && targetY < gameState.getHeight()) {
                    // If target equals current position, clear any existing destination
                    if (targetX == targetArmy.getX() && targetY == targetArmy.getY()) {
                        targetArmy.setDestinationX(null);
                        targetArmy.setDestinationY(null);
                    } else {
                        // Set destination instead of instant movement
                        targetArmy.setDestinationX(targetX);
                        targetArmy.setDestinationY(targetY);
                    }
                }
            }
        } else if ("SPLIT".equals(command.getType())) {
            splitArmy(command.getArmyId(), command.getSplitAmount());
        }
    }
    
    private void mergeFriendlyArmies() {
        // Group armies by location and player ID for efficient merging (O(n) instead of O(n^3))
        java.util.Map<String, java.util.List<Army>> armyGroups = new java.util.HashMap<>();
        
        for (Army army : gameState.getArmiesInternal()) {
            String key = army.getX() + "," + army.getY() + "," + army.getPlayerId();
            armyGroups.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(army);
        }
        
        // Merge each group that has multiple armies
        for (java.util.List<Army> group : armyGroups.values()) {
            if (group.size() > 1) {
                // Sort by ID to find the lowest ID army (the one we'll keep)
                group.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
                
                Army keepArmy = group.get(0); // Lowest ID
                int totalSoldiers = keepArmy.getSoldiers();
                
                // Check if any army in the group is moving (for movement state preservation)
                boolean anyMoving = keepArmy.isMoving();
                Integer destX = keepArmy.getDestinationX();
                Integer destY = keepArmy.getDestinationY();
                
                // Merge all other armies into the lowest ID army
                for (int i = 1; i < group.size(); i++) {
                    Army removeArmy = group.get(i);
                    totalSoldiers += removeArmy.getSoldiers();
                    
                    // If keepArmy wasn't moving but this army is, adopt its movement
                    if (!anyMoving && removeArmy.isMoving()) {
                        anyMoving = true;
                        destX = removeArmy.getDestinationX();
                        destY = removeArmy.getDestinationY();
                    }
                    // Note: If both have destinations, keep the lower ID army's destination
                    
                    gameState.getArmiesInternal().remove(removeArmy);
                }
                
                // Update the kept army with merged values
                keepArmy.setSoldiers(totalSoldiers);
                if (anyMoving && destX != null && destY != null) {
                    keepArmy.setDestinationX(destX);
                    keepArmy.setDestinationY(destY);
                }
            }
        }
    }
    
    public synchronized void splitArmy(int armyId, int soldierCount) {
        // Find the target army
        Army targetArmy = null;
        for (Army army : gameState.getArmiesInternal()) {
            if (army.getId() == armyId) {
                targetArmy = army;
                break;
            }
        }
        
        if (targetArmy == null) {
            return; // Army not found
        }
        
        // Validate split amount
        if (soldierCount < 1 || soldierCount >= targetArmy.getSoldiers()) {
            return; // Invalid split - need at least 1 soldier in each army
        }
        
        // Create new army at same location with split amount
        Army newArmy = new Army(targetArmy.getX(), targetArmy.getY(), soldierCount, targetArmy.getPlayerId());
        
        // Reduce soldiers in original army
        targetArmy.setSoldiers(targetArmy.getSoldiers() - soldierCount);
        
        // Add new army to game state
        gameState.getArmiesInternal().add(newArmy);
    }
    
    public synchronized int getPlayerIncome(int playerId) {
        int income = 0;
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() == playerId) {
                    income++;
                }
            }
        }
        return income;
    }
    
    private void processCastleCapture() {
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.CASTLE) {
                    // Find all armies at this location
                    Integer occupyingPlayer = null;
                    boolean multiplePlayersPresent = false;
                    
                    for (Army army : gameState.getArmiesInternal()) {
                        if (army.getX() == x && army.getY() == y) {
                            if (occupyingPlayer == null) {
                                occupyingPlayer = army.getPlayerId();
                            } else if (occupyingPlayer != army.getPlayerId()) {
                                multiplePlayersPresent = true;
                                break;
                            }
                        }
                    }
                    
                    // Update castle occupation
                    if (multiplePlayersPresent || occupyingPlayer == null) {
                        // Reset occupation ticks if no army or multiple players present
                        tile.setOccupationTicks(0);
                    } else if (occupyingPlayer == tile.getOwnerId()) {
                        // Friendly army - reset occupation ticks
                        tile.setOccupationTicks(0);
                    } else {
                        // Enemy army present - increment occupation ticks
                        tile.setOccupationTicks(tile.getOccupationTicks() + 1);
                        
                        // Capture castle after 3 consecutive ticks
                        if (tile.getOccupationTicks() >= 3) {
                            tile.setOwnerId(occupyingPlayer);
                            tile.setOccupationTicks(0);
                        }
                    }
                }
            }
        }
    }
    
    private void checkWinCondition() {
        // Count castles owned by each player
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
        
        // Check if any player has lost all castles
        if (player1Castles == 0 && player2Castles > 0) {
            gameState.setGameOver(true);
            gameState.setWinnerId(2);
        } else if (player2Castles == 0 && player1Castles > 0) {
            gameState.setGameOver(true);
            gameState.setWinnerId(1);
        }
    }
    
    public synchronized void resetGame() {
        initializeGame();
    }
}
