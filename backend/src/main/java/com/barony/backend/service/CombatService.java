package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handles combat resolution between opposing armies and merging of co-located
 * friendly armies. Combat applies morale modifiers for Player 1 armies.
 */
class CombatService {

    private static final int PLAYER_1_ID = 1;
    private static final int MORALE_BASELINE = 100;
    private static final long ROUNDING_OFFSET = 50L;
    private static final long MORALE_DIVISOR = 100L;

    void processCombat(GameState gameState) {
        resolveCombatBetweenArmies(gameState);
        removeDefeatedArmies(gameState);
    }

    private void resolveCombatBetweenArmies(GameState gameState) {
        int armyCount = gameState.getArmiesInternal().size();
        for (int i = 0; i < armyCount; i++) {
            for (int j = i + 1; j < armyCount; j++) {
                if (i >= gameState.getArmiesInternal().size() || j >= gameState.getArmiesInternal().size()) {
                    continue;
                }

                Army attacker = gameState.getArmiesInternal().get(i);
                Army defender = gameState.getArmiesInternal().get(j);

                if (areOnSameTile(attacker, defender) && areEnemies(attacker, defender)) {
                    resolveEngagement(attacker, defender);
                }
            }
        }
    }

    private void resolveEngagement(Army attacker, Army defender) {
        int attackerStrength = calculateEffectiveStrength(attacker);
        int defenderStrength = calculateEffectiveStrength(defender);

        attacker.setSoldiers(Math.max(0, attacker.getSoldiers() - defenderStrength));
        defender.setSoldiers(Math.max(0, defender.getSoldiers() - attackerStrength));
    }

    private int calculateEffectiveStrength(Army army) {
        if (army.getPlayerId() == PLAYER_1_ID) {
            return (int) ((((long) army.getSoldiers() * army.getMorale()) + ROUNDING_OFFSET) / MORALE_DIVISOR);
        }
        return army.getSoldiers();
    }

    private void removeDefeatedArmies(GameState gameState) {
        Iterator<Army> iterator = gameState.getArmiesInternal().iterator();
        while (iterator.hasNext()) {
            Army army = iterator.next();
            if (army.getSoldiers() <= 0) {
                iterator.remove();
            }
        }
    }

    void mergeFriendlyArmies(GameState gameState) {
        Map<String, List<Army>> armyGroups = groupArmiesByLocationAndPlayer(gameState);

        for (List<Army> group : armyGroups.values()) {
            if (group.size() > 1) {
                mergeArmyGroup(group, gameState);
            }
        }
    }

    private Map<String, List<Army>> groupArmiesByLocationAndPlayer(GameState gameState) {
        Map<String, List<Army>> groups = new HashMap<>();
        for (Army army : gameState.getArmiesInternal()) {
            String key = army.getX() + "," + army.getY() + "," + army.getPlayerId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(army);
        }
        return groups;
    }

    private void mergeArmyGroup(List<Army> group, GameState gameState) {
        group.sort((a, b) -> Integer.compare(a.getId(), b.getId()));

        Army survivingArmy = group.get(0);
        int totalSoldiers = survivingArmy.getSoldiers();
        boolean hasMovementOrder = survivingArmy.isMoving();
        Integer destinationX = survivingArmy.getDestinationX();
        Integer destinationY = survivingArmy.getDestinationY();

        for (int i = 1; i < group.size(); i++) {
            Army absorbedArmy = group.get(i);
            totalSoldiers += absorbedArmy.getSoldiers();

            if (!hasMovementOrder && absorbedArmy.isMoving()) {
                hasMovementOrder = true;
                destinationX = absorbedArmy.getDestinationX();
                destinationY = absorbedArmy.getDestinationY();
            }

            gameState.getArmiesInternal().remove(absorbedArmy);
        }

        survivingArmy.setSoldiers(totalSoldiers);
        if (hasMovementOrder && destinationX != null && destinationY != null) {
            survivingArmy.setDestinationX(destinationX);
            survivingArmy.setDestinationY(destinationY);
        }
    }

    private boolean areOnSameTile(Army army1, Army army2) {
        return army1.getX() == army2.getX() && army1.getY() == army2.getY();
    }

    private boolean areEnemies(Army army1, Army army2) {
        return army1.getPlayerId() != army2.getPlayerId();
    }
}
