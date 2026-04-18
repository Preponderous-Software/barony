package com.barony.backend.model;

public class RulerDecision {
    
    public enum PolicyCategory {
        ECONOMIC,
        MILITARY,
        POPULATION
    }
    
    public enum EconomicPolicy {
        HEAVY_TAXATION,
        BALANCED_BUDGET,
        INFRASTRUCTURE_INVESTMENT
    }
    
    public enum MilitaryPolicy {
        AGGRESSIVE_TRAINING,
        STANDARD_SERVICE,
        VETERAN_BENEFITS
    }
    
    public enum PopulationPolicy {
        GROWTH_FOCUS,
        STABLE_POPULATION,
        QUALITY_OVER_QUANTITY
    }
    
    private PolicyCategory category;
    private String choice;
    
    public RulerDecision() {
    }
    
    public RulerDecision(PolicyCategory category, String choice) {
        this.category = category;
        this.choice = choice;
    }
    
    public PolicyCategory getCategory() {
        return category;
    }
    
    public void setCategory(PolicyCategory category) {
        this.category = category;
    }
    
    public String getChoice() {
        return choice;
    }
    
    public void setChoice(String choice) {
        this.choice = choice;
    }
    
    public static int getIncomeModifier(EconomicPolicy policy) {
        switch (policy) {
            case HEAVY_TAXATION:
                return 20;
            case INFRASTRUCTURE_INVESTMENT:
                return -10;
            case BALANCED_BUDGET:
            default:
                return 0;
        }
    }
    
    public static int getStabilityModifier(EconomicPolicy policy) {
        switch (policy) {
            case HEAVY_TAXATION:
                return -10;
            case INFRASTRUCTURE_INVESTMENT:
                return 10;
            case BALANCED_BUDGET:
            default:
                return 0;
        }
    }
    
    public static int getMoraleModifier(MilitaryPolicy policy) {
        switch (policy) {
            case AGGRESSIVE_TRAINING:
                return 10;
            case VETERAN_BENEFITS:
                return -10;
            case STANDARD_SERVICE:
            default:
                return 0;
        }
    }
    
    public static int getLoyaltyModifier(MilitaryPolicy policy) {
        switch (policy) {
            case AGGRESSIVE_TRAINING:
                return -5;
            case VETERAN_BENEFITS:
                return 10;
            case STANDARD_SERVICE:
            default:
                return 0;
        }
    }
    
    public static int getPopulationGrowthModifier(PopulationPolicy policy) {
        switch (policy) {
            case GROWTH_FOCUS:
                return 15;
            case QUALITY_OVER_QUANTITY:
                return -10;
            case STABLE_POPULATION:
            default:
                return 0;
        }
    }
    
    public static int getPopulationStabilityModifier(PopulationPolicy policy) {
        switch (policy) {
            case GROWTH_FOCUS:
                return -5;
            case QUALITY_OVER_QUANTITY:
                return 10;
            case STABLE_POPULATION:
            default:
                return 0;
        }
    }
}
