package com.barony.frontend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RulerDecisionTest {

    @Test
    void rulerDecisionCreatesWithCorrectValues() {
        RulerDecision decision = new RulerDecision(
            RulerDecision.PolicyCategory.ECONOMIC,
            "HEAVY_TAXATION"
        );
        
        assertEquals(RulerDecision.PolicyCategory.ECONOMIC, decision.getCategory());
        assertEquals("HEAVY_TAXATION", decision.getChoice());
    }
    
    @Test
    void rulerDecisionSettersWork() {
        RulerDecision decision = new RulerDecision();
        
        decision.setCategory(RulerDecision.PolicyCategory.MILITARY);
        decision.setChoice("AGGRESSIVE_TRAINING");
        
        assertEquals(RulerDecision.PolicyCategory.MILITARY, decision.getCategory());
        assertEquals("AGGRESSIVE_TRAINING", decision.getChoice());
    }
    
    @Test
    void rulerDecisionSupportsAllPolicyCategories() {
        RulerDecision economic = new RulerDecision(
            RulerDecision.PolicyCategory.ECONOMIC,
            "BALANCED_BUDGET"
        );
        RulerDecision military = new RulerDecision(
            RulerDecision.PolicyCategory.MILITARY,
            "STANDARD_SERVICE"
        );
        RulerDecision population = new RulerDecision(
            RulerDecision.PolicyCategory.POPULATION,
            "STABLE_POPULATION"
        );
        
        assertEquals(RulerDecision.PolicyCategory.ECONOMIC, economic.getCategory());
        assertEquals(RulerDecision.PolicyCategory.MILITARY, military.getCategory());
        assertEquals(RulerDecision.PolicyCategory.POPULATION, population.getCategory());
    }
}
