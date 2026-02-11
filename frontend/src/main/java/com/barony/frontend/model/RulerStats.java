package com.barony.frontend.model;

public class RulerStats {
    private double averageStability;
    private double averageMorale;
    private double averageLoyalty;
    private int totalPopulation;
    private String economicPolicy;
    private String militaryPolicy;
    private String populationPolicy;
    private int ticksUntilNextDecision;
    
    public double getAverageStability() {
        return averageStability;
    }
    
    public void setAverageStability(double averageStability) {
        this.averageStability = averageStability;
    }
    
    public double getAverageMorale() {
        return averageMorale;
    }
    
    public void setAverageMorale(double averageMorale) {
        this.averageMorale = averageMorale;
    }
    
    public double getAverageLoyalty() {
        return averageLoyalty;
    }
    
    public void setAverageLoyalty(double averageLoyalty) {
        this.averageLoyalty = averageLoyalty;
    }
    
    public int getTotalPopulation() {
        return totalPopulation;
    }
    
    public void setTotalPopulation(int totalPopulation) {
        this.totalPopulation = totalPopulation;
    }
    
    public String getEconomicPolicy() {
        return economicPolicy;
    }
    
    public void setEconomicPolicy(String economicPolicy) {
        this.economicPolicy = economicPolicy;
    }
    
    public String getMilitaryPolicy() {
        return militaryPolicy;
    }
    
    public void setMilitaryPolicy(String militaryPolicy) {
        this.militaryPolicy = militaryPolicy;
    }
    
    public String getPopulationPolicy() {
        return populationPolicy;
    }
    
    public void setPopulationPolicy(String populationPolicy) {
        this.populationPolicy = populationPolicy;
    }
    
    public int getTicksUntilNextDecision() {
        return ticksUntilNextDecision;
    }
    
    public void setTicksUntilNextDecision(int ticksUntilNextDecision) {
        this.ticksUntilNextDecision = ticksUntilNextDecision;
    }
}
