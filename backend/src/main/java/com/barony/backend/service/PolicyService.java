package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;

import java.util.Iterator;

/**
 * Manages ruler policy effects: stat recovery, village soldier generation,
 * army desertion, policy changes, income calculation, and ruler statistics.
 * All policy effects apply only to Player 1.
 */
class PolicyService {

    static final int POLICY_COOLDOWN_TICKS = 15;

    private static final int PLAYER_1_ID = 1;
    private static final int STABILITY_DRIFT_RATE = 2;
    private static final int MAX_STABILITY = 110;
    private static final int MORALE_DRIFT_RATE = 1;
    private static final int LOYALTY_DRIFT_RATE = 2;
    private static final int BASE_STABILITY = 100;
    private static final int BASE_MORALE = 100;
    private static final int BASE_LOYALTY = 100;
    private static final int BASE_GROWTH_RATE_PERCENT = 1;
    private static final long BASIS_POINTS_ROUNDING_OFFSET = 5000L;
    private static final long BASIS_POINTS_DIVISOR = 10000L;
    private static final int PERCENTAGE_ROUNDING_OFFSET = 50;
    private static final int PERCENTAGE_DIVISOR = 100;
    private static final int SOLDIERS_PER_POPULATION = 100;
    private static final int MAX_DESERTION_LOYALTY_THRESHOLD = 100;
    private static final int DESERTION_DIVISOR = 20;

    void applyStatRecovery(GameState gameState) {
        applyVillageStatRecovery(gameState);
        applyArmyStatRecovery(gameState);
    }

    private void applyVillageStatRecovery(GameState gameState) {
        RulerDecision.EconomicPolicy economicPolicy = resolveEconomicPolicy(gameState);
        RulerDecision.PopulationPolicy populationPolicy = resolvePopulationPolicy(gameState);

        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() == PLAYER_1_ID) {
                    driftStabilityTowardTarget(tile, economicPolicy, populationPolicy);
                    applyPopulationGrowth(tile, populationPolicy);
                }
            }
        }
    }

    private void driftStabilityTowardTarget(Tile tile, RulerDecision.EconomicPolicy economicPolicy,
                                            RulerDecision.PopulationPolicy populationPolicy) {
        int targetStability = calculateTargetStability(economicPolicy, populationPolicy);
        int stability = tile.getStability();

        if (stability < targetStability) {
            tile.setStability(Math.min(targetStability, stability + STABILITY_DRIFT_RATE));
        } else if (stability > targetStability) {
            tile.setStability(Math.max(targetStability, stability - STABILITY_DRIFT_RATE));
        }
    }

    private int calculateTargetStability(RulerDecision.EconomicPolicy economicPolicy,
                                         RulerDecision.PopulationPolicy populationPolicy) {
        int economicModifier = RulerDecision.getStabilityModifier(economicPolicy);
        int populationModifier = RulerDecision.getPopulationStabilityModifier(populationPolicy);
        return Math.min(MAX_STABILITY, BASE_STABILITY + economicModifier + populationModifier);
    }

    private void applyPopulationGrowth(Tile tile, RulerDecision.PopulationPolicy populationPolicy) {
        int growthModifier = RulerDecision.getPopulationGrowthModifier(populationPolicy);
        int currentPopulation = tile.getPopulation();
        long growthNumerator = (long) currentPopulation * BASE_GROWTH_RATE_PERCENT * (100L + growthModifier);
        long growth = (growthNumerator + BASIS_POINTS_ROUNDING_OFFSET) / BASIS_POINTS_DIVISOR;
        long newPopulation = (long) currentPopulation + growth;

        if (newPopulation > Integer.MAX_VALUE) {
            newPopulation = Integer.MAX_VALUE;
        } else if (newPopulation < 0L) {
            newPopulation = 0L;
        }
        tile.setPopulation((int) newPopulation);
    }

    private void applyArmyStatRecovery(GameState gameState) {
        RulerDecision.MilitaryPolicy militaryPolicy = resolveMilitaryPolicy(gameState);

        for (Army army : gameState.getArmiesInternal()) {
            if (army.getPlayerId() == PLAYER_1_ID) {
                driftMoraleTowardTarget(army, militaryPolicy);
                driftLoyaltyTowardTarget(army, militaryPolicy);
            }
        }
    }

    private void driftMoraleTowardTarget(Army army, RulerDecision.MilitaryPolicy militaryPolicy) {
        int targetMorale = BASE_MORALE + RulerDecision.getMoraleModifier(militaryPolicy);
        int morale = army.getMorale();
        if (morale < targetMorale) {
            army.setMorale(Math.min(targetMorale, morale + MORALE_DRIFT_RATE));
        } else if (morale > targetMorale) {
            army.setMorale(Math.max(targetMorale, morale - MORALE_DRIFT_RATE));
        }
    }

    private void driftLoyaltyTowardTarget(Army army, RulerDecision.MilitaryPolicy militaryPolicy) {
        int targetLoyalty = BASE_LOYALTY + RulerDecision.getLoyaltyModifier(militaryPolicy);
        int loyalty = army.getLoyalty();
        if (loyalty < targetLoyalty) {
            army.setLoyalty(Math.min(targetLoyalty, loyalty + LOYALTY_DRIFT_RATE));
        } else if (loyalty > targetLoyalty) {
            army.setLoyalty(Math.max(targetLoyalty, loyalty - LOYALTY_DRIFT_RATE));
        }
    }

    void processVillageSoldierGeneration(GameState gameState) {
        for (Army army : gameState.getArmiesInternal()) {
            int x = army.getX();
            int y = army.getY();
            if (x >= 0 && x < gameState.getWidth() && y >= 0 && y < gameState.getHeight()) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() == army.getPlayerId()) {
                    int generated = calculateSoldiersGenerated(tile, army.getPlayerId());
                    army.setSoldiers(army.getSoldiers() + generated);
                }
            }
        }
    }

    private int calculateSoldiersGenerated(Tile village, int playerId) {
        int baseGeneration = village.getPopulation() / SOLDIERS_PER_POPULATION;
        if (playerId == PLAYER_1_ID) {
            return (baseGeneration * village.getStability() + PERCENTAGE_ROUNDING_OFFSET) / PERCENTAGE_DIVISOR;
        }
        return baseGeneration;
    }

    void processDesertion(GameState gameState) {
        Iterator<Army> iterator = gameState.getArmiesInternal().iterator();
        while (iterator.hasNext()) {
            Army army = iterator.next();
            if (army.getPlayerId() == PLAYER_1_ID) {
                int desertionRate = Math.max(0, (MAX_DESERTION_LOYALTY_THRESHOLD - army.getLoyalty()) / DESERTION_DIVISOR);
                int desertions = (army.getSoldiers() * desertionRate + PERCENTAGE_ROUNDING_OFFSET) / PERCENTAGE_DIVISOR;
                army.setSoldiers(Math.max(0, army.getSoldiers() - desertions));

                if (army.getSoldiers() <= 0) {
                    iterator.remove();
                }
            }
        }
    }

    boolean canChangePolicy(GameState gameState) {
        int ticksSinceLastChange = gameState.getTickCount() - gameState.getLastPolicyChangeTick();
        return ticksSinceLastChange >= POLICY_COOLDOWN_TICKS;
    }

    void changePolicy(GameState gameState, RulerDecision.PolicyCategory category, String choice) {
        if (!canChangePolicy(gameState)) {
            throw new IllegalStateException("Policy change on cooldown");
        }

        switch (category) {
            case ECONOMIC:
                RulerDecision.EconomicPolicy.valueOf(choice);
                gameState.setEconomicPolicy(choice);
                break;
            case MILITARY:
                RulerDecision.MilitaryPolicy.valueOf(choice);
                gameState.setMilitaryPolicy(choice);
                break;
            case POPULATION:
                RulerDecision.PopulationPolicy.valueOf(choice);
                gameState.setPopulationPolicy(choice);
                break;
        }

        gameState.setLastPolicyChangeTick(gameState.getTickCount());
    }

    RulerStats getRulerStats(GameState gameState) {
        RulerStats stats = new RulerStats();
        populateVillageStats(stats, gameState);
        populateArmyStats(stats, gameState);
        populatePolicyInfo(stats, gameState);
        return stats;
    }

    private void populateVillageStats(RulerStats stats, GameState gameState) {
        int villageCount = 0;
        int totalStability = 0;
        int totalPopulation = 0;

        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() == PLAYER_1_ID) {
                    villageCount++;
                    totalStability += tile.getStability();
                    totalPopulation += tile.getPopulation();
                }
            }
        }

        stats.setAverageStability(villageCount > 0 ? (double) totalStability / villageCount : 100.0);
        stats.setTotalPopulation(totalPopulation);
    }

    private void populateArmyStats(RulerStats stats, GameState gameState) {
        int armyCount = 0;
        int totalMorale = 0;
        int totalLoyalty = 0;

        for (Army army : gameState.getArmiesInternal()) {
            if (army.getPlayerId() == PLAYER_1_ID) {
                armyCount++;
                totalMorale += army.getMorale();
                totalLoyalty += army.getLoyalty();
            }
        }

        stats.setAverageMorale(armyCount > 0 ? (double) totalMorale / armyCount : 100.0);
        stats.setAverageLoyalty(armyCount > 0 ? (double) totalLoyalty / armyCount : 100.0);
    }

    private void populatePolicyInfo(RulerStats stats, GameState gameState) {
        stats.setEconomicPolicy(gameState.getEconomicPolicy());
        stats.setMilitaryPolicy(gameState.getMilitaryPolicy());
        stats.setPopulationPolicy(gameState.getPopulationPolicy());

        int ticksSinceLastChange = gameState.getTickCount() - gameState.getLastPolicyChangeTick();
        stats.setTicksUntilNextDecision(Math.max(0, POLICY_COOLDOWN_TICKS - ticksSinceLastChange));
    }

    int getPlayerIncome(int playerId, GameState gameState) {
        int baseIncome = countOwnedVillages(playerId, gameState);

        if (playerId == PLAYER_1_ID && gameState.getEconomicPolicy() != null) {
            try {
                RulerDecision.EconomicPolicy policy = RulerDecision.EconomicPolicy.valueOf(
                        gameState.getEconomicPolicy());
                int modifier = RulerDecision.getIncomeModifier(policy);
                return (baseIncome * (PERCENTAGE_DIVISOR + modifier)) / PERCENTAGE_DIVISOR;
            } catch (IllegalArgumentException e) {
                return baseIncome;
            }
        }

        return baseIncome;
    }

    private int countOwnedVillages(int playerId, GameState gameState) {
        int count = 0;
        for (int x = 0; x < gameState.getWidth(); x++) {
            for (int y = 0; y < gameState.getHeight(); y++) {
                Tile tile = gameState.getGrid()[x][y];
                if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() == playerId) {
                    count++;
                }
            }
        }
        return count;
    }

    private RulerDecision.EconomicPolicy resolveEconomicPolicy(GameState gameState) {
        String policyName = gameState.getEconomicPolicy();
        return RulerDecision.EconomicPolicy.valueOf(
                policyName != null ? policyName : "BALANCED_BUDGET");
    }

    private RulerDecision.MilitaryPolicy resolveMilitaryPolicy(GameState gameState) {
        String policyName = gameState.getMilitaryPolicy();
        return RulerDecision.MilitaryPolicy.valueOf(
                policyName != null ? policyName : "STANDARD_SERVICE");
    }

    private RulerDecision.PopulationPolicy resolvePopulationPolicy(GameState gameState) {
        String policyName = gameState.getPopulationPolicy();
        return RulerDecision.PopulationPolicy.valueOf(
                policyName != null ? policyName : "STABLE_POPULATION");
    }
}
