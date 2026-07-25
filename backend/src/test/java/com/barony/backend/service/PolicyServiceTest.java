package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.RulerDecision;
import com.barony.backend.model.RulerStats;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PolicyServiceTest {

    private static final int PLAYER_1 = 1;
    private static final int PLAYER_2 = 2;

    private final PolicyService policyService = new PolicyService();

    private GameState newGameState() {
        return new GameState(10, 10);
    }

    private Tile placeVillage(GameState gameState, int x, int y, int ownerId) {
        Tile village = new Tile(TileType.VILLAGE, ownerId);
        gameState.getGrid()[x][y] = village;
        return village;
    }

    private Army placeArmy(GameState gameState, int x, int y, int soldiers, int playerId) {
        Army army = new Army(x, y, soldiers, playerId);
        gameState.getArmiesInternal().add(army);
        return army;
    }

    // --- Village stability drift -------------------------------------------------

    @Test
    void villageStabilityDriftsUpTowardTarget() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setStability(90);

        policyService.applyStatRecovery(gameState);

        // Default policies (BALANCED_BUDGET + STABLE_POPULATION) give a target of 100;
        // stability closes the gap 2 points per tick.
        assertEquals(92, village.getStability());
    }

    @Test
    void villageStabilityDriftsDownTowardTarget() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setStability(105);

        policyService.applyStatRecovery(gameState);

        assertEquals(103, village.getStability());
    }

    @Test
    void villageStabilityStopsAtTargetWithoutOvershooting() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setStability(99);

        policyService.applyStatRecovery(gameState);

        // One point below the target of 100, so the 2-point drift is clamped to the target.
        assertEquals(100, village.getStability());
    }

    @Test
    void heavyTaxationLowersTheStabilityTarget() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy("HEAVY_TAXATION");
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setStability(100);

        policyService.applyStatRecovery(gameState);

        // Target drops to 100 - 10 = 90, so a village sitting at 100 now drifts down.
        assertEquals(98, village.getStability());
    }

    @Test
    void stabilityTargetIsCappedAtMaximum() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy("INFRASTRUCTURE_INVESTMENT");
        gameState.setPopulationPolicy("QUALITY_OVER_QUANTITY");
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setStability(109);

        policyService.applyStatRecovery(gameState);

        // The +10/+10 modifiers would give 120, but the target is capped at 110.
        assertEquals(110, village.getStability());
    }

    @Test
    void villagesNotOwnedByPlayer1AreUnaffectedByStatRecovery() {
        GameState gameState = newGameState();
        Tile enemyVillage = placeVillage(gameState, 2, 3, PLAYER_2);
        enemyVillage.setStability(90);
        enemyVillage.setPopulation(1000);
        Tile neutralVillage = placeVillage(gameState, 4, 5, 0);
        neutralVillage.setStability(90);
        neutralVillage.setPopulation(1000);

        policyService.applyStatRecovery(gameState);

        assertEquals(90, enemyVillage.getStability());
        assertEquals(1000, enemyVillage.getPopulation());
        assertEquals(90, neutralVillage.getStability());
        assertEquals(1000, neutralVillage.getPopulation());
    }

    @Test
    void nonVillageTilesAreUnaffectedByStatRecovery() {
        GameState gameState = newGameState();
        Tile castle = new Tile(TileType.CASTLE, PLAYER_1);
        castle.setStability(90);
        castle.setPopulation(1000);
        gameState.getGrid()[2][3] = castle;

        policyService.applyStatRecovery(gameState);

        assertEquals(90, castle.getStability());
        assertEquals(1000, castle.getPopulation());
    }

    // --- Village population growth -----------------------------------------------

    @Test
    void villagePopulationGrowsOnePercentUnderStablePopulationPolicy() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setPopulation(1000);

        policyService.applyStatRecovery(gameState);

        assertEquals(1010, village.getPopulation());
    }

    @Test
    void growthFocusPolicyAcceleratesPopulationGrowth() {
        GameState gameState = newGameState();
        gameState.setPopulationPolicy("GROWTH_FOCUS");
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setPopulation(1000);

        policyService.applyStatRecovery(gameState);

        // Base 1% growth is scaled by the +15% growth modifier: 1000 * 1 * 115 / 10000 = 11.5 -> 12.
        assertEquals(1012, village.getPopulation());
    }

    @Test
    void qualityOverQuantityPolicySlowsPopulationGrowth() {
        GameState gameState = newGameState();
        gameState.setPopulationPolicy("QUALITY_OVER_QUANTITY");
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setPopulation(1000);

        policyService.applyStatRecovery(gameState);

        // 1000 * 1 * 90 / 10000 = 9.
        assertEquals(1009, village.getPopulation());
    }

    @Test
    void emptyVillagePopulationStaysAtZero() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 2, 3, PLAYER_1);
        village.setPopulation(0);

        policyService.applyStatRecovery(gameState);

        assertEquals(0, village.getPopulation());
    }

    // --- Army morale and loyalty drift --------------------------------------------

    @Test
    void aggressiveTrainingRaisesMoraleAndLowersLoyalty() {
        GameState gameState = newGameState();
        gameState.setMilitaryPolicy("AGGRESSIVE_TRAINING");
        Army army = placeArmy(gameState, 1, 1, 50, PLAYER_1);

        policyService.applyStatRecovery(gameState);

        // Morale target 110 (drift 1/tick), loyalty target 95 (drift 2/tick).
        assertEquals(101, army.getMorale());
        assertEquals(98, army.getLoyalty());
    }

    @Test
    void veteranBenefitsLowersMoraleAndRaisesLoyalty() {
        GameState gameState = newGameState();
        gameState.setMilitaryPolicy("VETERAN_BENEFITS");
        Army army = placeArmy(gameState, 1, 1, 50, PLAYER_1);

        policyService.applyStatRecovery(gameState);

        // Morale target 90, loyalty target 110.
        assertEquals(99, army.getMorale());
        assertEquals(102, army.getLoyalty());
    }

    @Test
    void moraleAndLoyaltyStopAtTargetWithoutOvershooting() {
        GameState gameState = newGameState();
        gameState.setMilitaryPolicy("VETERAN_BENEFITS");
        Army army = placeArmy(gameState, 1, 1, 50, PLAYER_1);
        army.setMorale(90);
        army.setLoyalty(109);

        policyService.applyStatRecovery(gameState);

        // Already at the morale target; loyalty is one below its 110 target, so it clamps there.
        assertEquals(90, army.getMorale());
        assertEquals(110, army.getLoyalty());
    }

    @Test
    void armyStatsAreUnchangedUnderTheDefaultMilitaryPolicy() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, 1, 1, 50, PLAYER_1);

        policyService.applyStatRecovery(gameState);

        // STANDARD_SERVICE targets 100/100, which is exactly where a fresh army starts.
        assertEquals(100, army.getMorale());
        assertEquals(100, army.getLoyalty());
    }

    @Test
    void armiesNotOwnedByPlayer1AreUnaffectedByStatRecovery() {
        GameState gameState = newGameState();
        gameState.setMilitaryPolicy("AGGRESSIVE_TRAINING");
        Army army = placeArmy(gameState, 1, 1, 50, PLAYER_2);
        army.setMorale(50);
        army.setLoyalty(50);

        policyService.applyStatRecovery(gameState);

        assertEquals(50, army.getMorale());
        assertEquals(50, army.getLoyalty());
    }

    // --- Village soldier generation ------------------------------------------------

    @Test
    void player1ArmyOnOwnVillageGainsSoldiersScaledByStability() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 4, 4, PLAYER_1);
        village.setPopulation(500);
        village.setStability(100);
        Army army = placeArmy(gameState, 4, 4, 10, PLAYER_1);

        policyService.processVillageSoldierGeneration(gameState);

        // 500 population -> 5 base soldiers, unscaled at full stability.
        assertEquals(15, army.getSoldiers());
    }

    @Test
    void lowVillageStabilityReducesPlayer1SoldierGeneration() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 4, 4, PLAYER_1);
        village.setPopulation(500);
        village.setStability(50);
        Army army = placeArmy(gameState, 4, 4, 10, PLAYER_1);

        policyService.processVillageSoldierGeneration(gameState);

        // 5 base soldiers at 50 stability: (5 * 50 + 50) / 100 = 3.
        assertEquals(13, army.getSoldiers());
    }

    @Test
    void nonPlayer1SoldierGenerationIgnoresStability() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 4, 4, PLAYER_2);
        village.setPopulation(500);
        village.setStability(50);
        Army army = placeArmy(gameState, 4, 4, 10, PLAYER_2);

        policyService.processVillageSoldierGeneration(gameState);

        assertEquals(15, army.getSoldiers());
    }

    @Test
    void armyOnEnemyOwnedVillageGeneratesNoSoldiers() {
        GameState gameState = newGameState();
        Tile village = placeVillage(gameState, 4, 4, PLAYER_2);
        village.setPopulation(500);
        Army army = placeArmy(gameState, 4, 4, 10, PLAYER_1);

        policyService.processVillageSoldierGeneration(gameState);

        assertEquals(10, army.getSoldiers());
    }

    @Test
    void armyOnNonVillageTileGeneratesNoSoldiers() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, 4, 4, 10, PLAYER_1);

        policyService.processVillageSoldierGeneration(gameState);

        assertEquals(10, army.getSoldiers());
    }

    @Test
    void armyOutsideTheGridIsSkippedWithoutError() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, -1, 20, 10, PLAYER_1);

        assertDoesNotThrow(() -> policyService.processVillageSoldierGeneration(gameState));
        assertEquals(10, army.getSoldiers());
    }

    // --- Desertion -------------------------------------------------------------------

    @Test
    void fullyLoyalArmyLosesNoSoldiersToDesertion() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, 1, 1, 100, PLAYER_1);

        policyService.processDesertion(gameState);

        assertEquals(100, army.getSoldiers());
    }

    @Test
    void lowLoyaltyCausesProportionalDesertion() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, 1, 1, 100, PLAYER_1);
        army.setLoyalty(60);

        policyService.processDesertion(gameState);

        // Desertion rate (100 - 60) / 20 = 2%, applied to 100 soldiers.
        assertEquals(98, army.getSoldiers());
    }

    @Test
    void zeroLoyaltyCausesMaximumDesertion() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, 1, 1, 100, PLAYER_1);
        army.setLoyalty(0);

        policyService.processDesertion(gameState);

        // Desertion rate is capped by the formula at (100 - 0) / 20 = 5%.
        assertEquals(95, army.getSoldiers());
    }

    @Test
    void desertionNeverWipesOutASurvivingArmy() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, 1, 1, 1, PLAYER_1);
        army.setLoyalty(0);

        policyService.processDesertion(gameState);

        // Characterizes current behaviour: at the maximum 5% rate, (1 * 5 + 50) / 100 rounds
        // down to 0, so even a totally disloyal single-soldier army survives.
        assertEquals(1, army.getSoldiers());
        assertEquals(1, gameState.getArmiesInternal().size());
    }

    @Test
    void player1ArmyWithNoSoldiersIsRemoved() {
        GameState gameState = newGameState();
        placeArmy(gameState, 1, 1, 0, PLAYER_1);

        policyService.processDesertion(gameState);

        assertTrue(gameState.getArmiesInternal().isEmpty());
    }

    @Test
    void nonPlayer1ArmiesAreExemptFromDesertion() {
        GameState gameState = newGameState();
        Army army = placeArmy(gameState, 1, 1, 100, PLAYER_2);
        army.setLoyalty(0);
        Army empty = placeArmy(gameState, 2, 2, 0, PLAYER_2);

        policyService.processDesertion(gameState);

        assertEquals(100, army.getSoldiers());
        // The empty-army cleanup is also scoped to player 1, so this one is left in place.
        assertEquals(2, gameState.getArmiesInternal().size());
        assertEquals(0, empty.getSoldiers());
    }

    // --- Policy cooldown and changes ---------------------------------------------------

    @Test
    void policyChangeIsAllowedOnAFreshGame() {
        GameState gameState = newGameState();

        assertTrue(policyService.canChangePolicy(gameState));
    }

    @Test
    void policyChangeIsBlockedImmediatelyAfterAChange() {
        GameState gameState = newGameState();

        policyService.changePolicy(gameState, RulerDecision.PolicyCategory.ECONOMIC, "HEAVY_TAXATION");

        assertFalse(policyService.canChangePolicy(gameState));
        assertEquals(gameState.getTickCount(), gameState.getLastPolicyChangeTick());
    }

    @Test
    void policyChangeUnlocksExactlyAtTheCooldownBoundary() {
        GameState gameState = newGameState();
        gameState.setLastPolicyChangeTick(0);

        gameState.setTickCount(PolicyService.POLICY_COOLDOWN_TICKS - 1);
        assertFalse(policyService.canChangePolicy(gameState));

        gameState.setTickCount(PolicyService.POLICY_COOLDOWN_TICKS);
        assertTrue(policyService.canChangePolicy(gameState));
    }

    @Test
    void changingPolicyOnCooldownThrowsIllegalState() {
        GameState gameState = newGameState();
        gameState.setLastPolicyChangeTick(0);
        gameState.setTickCount(1);

        assertThrows(IllegalStateException.class, () ->
            policyService.changePolicy(gameState, RulerDecision.PolicyCategory.ECONOMIC, "HEAVY_TAXATION"));
        assertEquals("BALANCED_BUDGET", gameState.getEconomicPolicy());
    }

    @Test
    void changePolicySetsTheChoiceForEachCategory() {
        GameState economic = newGameState();
        policyService.changePolicy(economic, RulerDecision.PolicyCategory.ECONOMIC, "HEAVY_TAXATION");
        assertEquals("HEAVY_TAXATION", economic.getEconomicPolicy());

        GameState military = newGameState();
        policyService.changePolicy(military, RulerDecision.PolicyCategory.MILITARY, "VETERAN_BENEFITS");
        assertEquals("VETERAN_BENEFITS", military.getMilitaryPolicy());

        GameState population = newGameState();
        policyService.changePolicy(population, RulerDecision.PolicyCategory.POPULATION, "GROWTH_FOCUS");
        assertEquals("GROWTH_FOCUS", population.getPopulationPolicy());
    }

    @Test
    void changePolicyRejectsAChoiceFromTheWrongCategory() {
        GameState gameState = newGameState();

        // A valid military policy name is not a valid economic choice.
        assertThrows(IllegalArgumentException.class, () ->
            policyService.changePolicy(gameState, RulerDecision.PolicyCategory.ECONOMIC, "VETERAN_BENEFITS"));
        assertEquals("BALANCED_BUDGET", gameState.getEconomicPolicy());
        assertEquals(-PolicyService.POLICY_COOLDOWN_TICKS, gameState.getLastPolicyChangeTick());
    }

    @Test
    void changePolicyRejectsAnUnknownChoice() {
        GameState gameState = newGameState();

        assertThrows(IllegalArgumentException.class, () ->
            policyService.changePolicy(gameState, RulerDecision.PolicyCategory.POPULATION, "NOT_A_POLICY"));
        assertEquals("STABLE_POPULATION", gameState.getPopulationPolicy());
    }

    // --- Ruler stats -------------------------------------------------------------------

    @Test
    void rulerStatsFallBackToFullScoresWhenTheRealmIsEmpty() {
        GameState gameState = newGameState();

        RulerStats stats = policyService.getRulerStats(gameState);

        assertEquals(100.0, stats.getAverageStability());
        assertEquals(100.0, stats.getAverageMorale());
        assertEquals(100.0, stats.getAverageLoyalty());
        assertEquals(0, stats.getTotalPopulation());
    }

    @Test
    void rulerStatsAverageOnlyPlayer1VillagesAndArmies() {
        GameState gameState = newGameState();

        Tile ownVillage = placeVillage(gameState, 1, 1, PLAYER_1);
        ownVillage.setStability(80);
        ownVillage.setPopulation(100);
        Tile otherOwnVillage = placeVillage(gameState, 2, 2, PLAYER_1);
        otherOwnVillage.setStability(100);
        otherOwnVillage.setPopulation(300);
        Tile enemyVillage = placeVillage(gameState, 3, 3, PLAYER_2);
        enemyVillage.setStability(40);
        enemyVillage.setPopulation(1000);

        Army ownArmy = placeArmy(gameState, 1, 1, 10, PLAYER_1);
        ownArmy.setMorale(80);
        ownArmy.setLoyalty(60);
        Army otherOwnArmy = placeArmy(gameState, 2, 2, 10, PLAYER_1);
        otherOwnArmy.setMorale(100);
        otherOwnArmy.setLoyalty(100);
        Army enemyArmy = placeArmy(gameState, 3, 3, 10, PLAYER_2);
        enemyArmy.setMorale(10);
        enemyArmy.setLoyalty(10);

        RulerStats stats = policyService.getRulerStats(gameState);

        assertEquals(90.0, stats.getAverageStability());
        assertEquals(400, stats.getTotalPopulation());
        assertEquals(90.0, stats.getAverageMorale());
        assertEquals(80.0, stats.getAverageLoyalty());
    }

    @Test
    void rulerStatsReportTheCurrentPolicies() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy("HEAVY_TAXATION");
        gameState.setMilitaryPolicy("VETERAN_BENEFITS");
        gameState.setPopulationPolicy("GROWTH_FOCUS");

        RulerStats stats = policyService.getRulerStats(gameState);

        assertEquals("HEAVY_TAXATION", stats.getEconomicPolicy());
        assertEquals("VETERAN_BENEFITS", stats.getMilitaryPolicy());
        assertEquals("GROWTH_FOCUS", stats.getPopulationPolicy());
    }

    @Test
    void rulerStatsCountDownTheTicksUntilTheNextDecision() {
        GameState gameState = newGameState();
        gameState.setLastPolicyChangeTick(0);
        gameState.setTickCount(5);

        RulerStats stats = policyService.getRulerStats(gameState);

        assertEquals(PolicyService.POLICY_COOLDOWN_TICKS - 5, stats.getTicksUntilNextDecision());
    }

    @Test
    void rulerStatsClampTheDecisionCountdownAtZero() {
        GameState gameState = newGameState();
        gameState.setLastPolicyChangeTick(0);
        gameState.setTickCount(100);

        RulerStats stats = policyService.getRulerStats(gameState);

        assertEquals(0, stats.getTicksUntilNextDecision());
    }

    // --- Income ---------------------------------------------------------------------

    @Test
    void incomeCountsVillagesOwnedByThePlayer() {
        GameState gameState = newGameState();
        placeVillage(gameState, 1, 1, PLAYER_1);
        placeVillage(gameState, 2, 2, PLAYER_1);
        placeVillage(gameState, 3, 3, PLAYER_2);
        placeVillage(gameState, 4, 4, 0);

        assertEquals(2, policyService.getPlayerIncome(PLAYER_1, gameState));
        assertEquals(1, policyService.getPlayerIncome(PLAYER_2, gameState));
    }

    @Test
    void heavyTaxationIncreasesPlayer1Income() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy("HEAVY_TAXATION");
        for (int i = 0; i < 5; i++) {
            placeVillage(gameState, i, 0, PLAYER_1);
        }

        // 5 villages with the +20% modifier: 5 * 120 / 100 = 6.
        assertEquals(6, policyService.getPlayerIncome(PLAYER_1, gameState));
    }

    @Test
    void infrastructureInvestmentReducesPlayer1Income() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy("INFRASTRUCTURE_INVESTMENT");
        for (int i = 0; i < 5; i++) {
            placeVillage(gameState, i, 0, PLAYER_1);
        }

        // 5 * 90 / 100 = 4 (integer division).
        assertEquals(4, policyService.getPlayerIncome(PLAYER_1, gameState));
    }

    @Test
    void economicPolicyDoesNotAffectOtherPlayersIncome() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy("HEAVY_TAXATION");
        for (int i = 0; i < 5; i++) {
            placeVillage(gameState, i, 0, PLAYER_2);
        }

        assertEquals(5, policyService.getPlayerIncome(PLAYER_2, gameState));
    }

    @Test
    void unrecognisedEconomicPolicyFallsBackToUnmodifiedIncome() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy("NOT_A_POLICY");
        for (int i = 0; i < 5; i++) {
            placeVillage(gameState, i, 0, PLAYER_1);
        }

        assertEquals(5, policyService.getPlayerIncome(PLAYER_1, gameState));
    }

    @Test
    void missingEconomicPolicyFallsBackToUnmodifiedIncome() {
        GameState gameState = newGameState();
        gameState.setEconomicPolicy(null);
        for (int i = 0; i < 5; i++) {
            placeVillage(gameState, i, 0, PLAYER_1);
        }

        assertEquals(5, policyService.getPlayerIncome(PLAYER_1, gameState));
    }
}
