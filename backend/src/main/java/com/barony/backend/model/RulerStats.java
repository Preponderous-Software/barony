package com.barony.backend.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RulerStats {
    private double averageStability;
    private double averageMorale;
    private double averageLoyalty;
    private int totalPopulation;
    private String economicPolicy;
    private String militaryPolicy;
    private String populationPolicy;
    private int ticksUntilNextDecision;
}
