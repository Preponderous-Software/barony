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
    private static final long ROUNDING_OFFSET = 50L;
    private static final long MORALE_DIVISOR = 100L;
    /** Combat weight of each unit type, in hundredths of an infantryman. */
    static final long INFANTRY_WEIGHT = 100L;
    static final long ARCHER_WEIGHT = 125L;
    static final long CAVALRY_WEIGHT = 150L;

    void processCombat(GameState gameState) {
        resolveCombatBetweenArmies(gameState);
        removeDefeatedArmies(gameState);
    }

    /**
     * The same exchange as {@link #processCombat}, for the armies on one tile only: how a pending
     * battle that the player chose to auto-resolve is decided.
     */
    void processCombatAt(GameState gameState, int x, int y) {
        List<Army> armies = gameState.getArmiesInternal();
        for (int i = 0; i < armies.size(); i++) {
            for (int j = i + 1; j < armies.size(); j++) {
                Army attacker = armies.get(i);
                Army defender = armies.get(j);
                if (attacker.getX() == x && attacker.getY() == y && areOnSameTile(attacker, defender)
                        && areEnemies(attacker, defender)) {
                    resolveEngagement(attacker, defender);
                }
            }
        }
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

        // Losses come out of every unit type in proportion (Army.loseSoldiers), which for an
        // all-infantry army is exactly the old subtraction clamped at zero.
        attacker.loseSoldiers(defenderStrength);
        defender.loseSoldiers(attackerStrength);
    }

    /**
     * An army's strength in the one-exchange rule: its soldiers weighted by type (infantry 1.0,
     * archers 1.25, cavalry 1.5), held in hundredths so the arithmetic stays exact, then scaled by
     * morale for player 1, rounding half up. For an all-infantry army the weighted figure is
     * {@code soldiers * 100}, so this is exactly the old {@code (soldiers * morale + 50) / 100} for
     * player 1 and {@code soldiers} for everyone else.
     */
    static int calculateEffectiveStrength(Army army) {
        long weighted100 = weightedHundredths(army);
        if (army.getPlayerId() == PLAYER_1_ID) {
            return (int) ((weighted100 * army.getMorale() + ROUNDING_OFFSET * MORALE_DIVISOR)
                    / (MORALE_DIVISOR * MORALE_DIVISOR));
        }
        return (int) ((weighted100 + ROUNDING_OFFSET) / MORALE_DIVISOR);
    }

    /** {@code infantry*100 + archers*125 + cavalry*150}: an army's weighted size in hundredths. */
    static long weightedHundredths(Army army) {
        return (long) army.getInfantry() * INFANTRY_WEIGHT
                + (long) army.getArchers() * ARCHER_WEIGHT
                + (long) army.getCavalry() * CAVALRY_WEIGHT;
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
        boolean hasMovementOrder = survivingArmy.isMoving();
        Integer destinationX = survivingArmy.getDestinationX();
        Integer destinationY = survivingArmy.getDestinationY();

        for (int i = 1; i < group.size(); i++) {
            Army absorbedArmy = group.get(i);
            survivingArmy.absorb(absorbedArmy);   // soldiers, archers and cavalry all add up

            if (!hasMovementOrder && absorbedArmy.isMoving()) {
                hasMovementOrder = true;
                destinationX = absorbedArmy.getDestinationX();
                destinationY = absorbedArmy.getDestinationY();
            }

            gameState.getArmiesInternal().remove(absorbedArmy);
        }

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
