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
        
        // Deep copy the grid
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                snapshot.getGrid()[x][y].setType(gameState.getGrid()[x][y].getType());
                snapshot.getGrid()[x][y].setOwnerId(gameState.getGrid()[x][y].getOwnerId());
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
                    } else if (occupyingPlayer == null && tile.getOwnerId() != 0) {
                        // No army on the village - keep current ownership
                        // (Villages don't revert to neutral when abandoned)
                    }
                }
            }
        }
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
        }
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
}
