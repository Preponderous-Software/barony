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
        
        // Set up initial board
        gameState.getGrid()[0][0].setType(TileType.CASTLE);
        gameState.getGrid()[9][9].setType(TileType.CASTLE);
        gameState.getGrid()[3][3].setType(TileType.VILLAGE);
        gameState.getGrid()[6][6].setType(TileType.VILLAGE);
        
        // Add initial armies
        gameState.getArmies().add(new Army(0, 0, 10, 1));
        gameState.getArmies().add(new Army(9, 9, 10, 2));
    }
    
    public GameState getState() {
        return gameState;
    }
    
    public void tick() {
        gameState.incrementTick();
        
        // Villages generate soldiers
        for (Army army : gameState.getArmies()) {
            int x = army.getX();
            int y = army.getY();
            if (x >= 0 && x < gameState.getWidth() && y >= 0 && y < gameState.getHeight()) {
                TileType tileType = gameState.getGrid()[x][y].getType();
                if (tileType == TileType.VILLAGE) {
                    army.setSoldiers(army.getSoldiers() + 1);
                }
            }
        }
        
        // Process combat
        processCombat();
    }
    
    private void processCombat() {
        for (int i = 0; i < gameState.getArmies().size(); i++) {
            for (int j = i + 1; j < gameState.getArmies().size(); j++) {
                Army army1 = gameState.getArmies().get(i);
                Army army2 = gameState.getArmies().get(j);
                
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
        Iterator<Army> iterator = gameState.getArmies().iterator();
        while (iterator.hasNext()) {
            Army army = iterator.next();
            if (army.getSoldiers() <= 0) {
                iterator.remove();
            }
        }
    }
    
    public void executeCommand(Command command) {
        if ("MOVE".equals(command.getType())) {
            int armyIndex = command.getArmyIndex();
            if (armyIndex >= 0 && armyIndex < gameState.getArmies().size()) {
                Army army = gameState.getArmies().get(armyIndex);
                int targetX = command.getTargetX();
                int targetY = command.getTargetY();
                
                // Validate target position
                if (targetX >= 0 && targetX < gameState.getWidth() && 
                    targetY >= 0 && targetY < gameState.getHeight()) {
                    army.setX(targetX);
                    army.setY(targetY);
                }
            }
        }
    }
}
