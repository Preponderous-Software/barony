package com.barony.frontend.model;

public class RulerDecision {
    private PolicyCategory category;
    private String choice;
    
    public enum PolicyCategory {
        ECONOMIC,
        MILITARY,
        POPULATION
    }
    
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
}
