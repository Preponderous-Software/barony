package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RulerDecision {
    private PolicyCategory category;
    private String choice;
    
    public enum PolicyCategory {
        ECONOMIC,
        MILITARY,
        POPULATION
    }
}
