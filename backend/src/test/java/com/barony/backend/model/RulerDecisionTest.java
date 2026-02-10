package com.barony.backend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulerDecisionTest {

    @Test
    void heavyTaxationGivesCorrectIncomeModifier() {
        int modifier = RulerDecision.getIncomeModifier(RulerDecision.EconomicPolicy.HEAVY_TAXATION);
        assertEquals(20, modifier);
    }
    
    @Test
    void heavyTaxationGivesCorrectStabilityModifier() {
        int modifier = RulerDecision.getStabilityModifier(RulerDecision.EconomicPolicy.HEAVY_TAXATION);
        assertEquals(-10, modifier);
    }
    
    @Test
    void balancedBudgetGivesNoModifiers() {
        int incomeModifier = RulerDecision.getIncomeModifier(RulerDecision.EconomicPolicy.BALANCED_BUDGET);
        int stabilityModifier = RulerDecision.getStabilityModifier(RulerDecision.EconomicPolicy.BALANCED_BUDGET);
        assertEquals(0, incomeModifier);
        assertEquals(0, stabilityModifier);
    }
    
    @Test
    void infrastructureInvestmentGivesCorrectModifiers() {
        int incomeModifier = RulerDecision.getIncomeModifier(RulerDecision.EconomicPolicy.INFRASTRUCTURE_INVESTMENT);
        int stabilityModifier = RulerDecision.getStabilityModifier(RulerDecision.EconomicPolicy.INFRASTRUCTURE_INVESTMENT);
        assertEquals(-10, incomeModifier);
        assertEquals(10, stabilityModifier);
    }
    
    @Test
    void aggressiveTrainingGivesCorrectModifiers() {
        int moraleModifier = RulerDecision.getMoraleModifier(RulerDecision.MilitaryPolicy.AGGRESSIVE_TRAINING);
        int loyaltyModifier = RulerDecision.getLoyaltyModifier(RulerDecision.MilitaryPolicy.AGGRESSIVE_TRAINING);
        assertEquals(10, moraleModifier);
        assertEquals(-5, loyaltyModifier);
    }
    
    @Test
    void standardServiceGivesNoModifiers() {
        int moraleModifier = RulerDecision.getMoraleModifier(RulerDecision.MilitaryPolicy.STANDARD_SERVICE);
        int loyaltyModifier = RulerDecision.getLoyaltyModifier(RulerDecision.MilitaryPolicy.STANDARD_SERVICE);
        assertEquals(0, moraleModifier);
        assertEquals(0, loyaltyModifier);
    }
    
    @Test
    void veteranBenefitsGivesCorrectModifiers() {
        int moraleModifier = RulerDecision.getMoraleModifier(RulerDecision.MilitaryPolicy.VETERAN_BENEFITS);
        int loyaltyModifier = RulerDecision.getLoyaltyModifier(RulerDecision.MilitaryPolicy.VETERAN_BENEFITS);
        assertEquals(-10, moraleModifier);
        assertEquals(10, loyaltyModifier);
    }
    
    @Test
    void growthFocusGivesCorrectModifiers() {
        int growthModifier = RulerDecision.getPopulationGrowthModifier(RulerDecision.PopulationPolicy.GROWTH_FOCUS);
        int stabilityModifier = RulerDecision.getPopulationStabilityModifier(RulerDecision.PopulationPolicy.GROWTH_FOCUS);
        assertEquals(15, growthModifier);
        assertEquals(-5, stabilityModifier);
    }
    
    @Test
    void stablePopulationGivesNoModifiers() {
        int growthModifier = RulerDecision.getPopulationGrowthModifier(RulerDecision.PopulationPolicy.STABLE_POPULATION);
        int stabilityModifier = RulerDecision.getPopulationStabilityModifier(RulerDecision.PopulationPolicy.STABLE_POPULATION);
        assertEquals(0, growthModifier);
        assertEquals(0, stabilityModifier);
    }
    
    @Test
    void qualityOverQuantityGivesCorrectModifiers() {
        int growthModifier = RulerDecision.getPopulationGrowthModifier(RulerDecision.PopulationPolicy.QUALITY_OVER_QUANTITY);
        int stabilityModifier = RulerDecision.getPopulationStabilityModifier(RulerDecision.PopulationPolicy.QUALITY_OVER_QUANTITY);
        assertEquals(-10, growthModifier);
        assertEquals(10, stabilityModifier);
    }
}
