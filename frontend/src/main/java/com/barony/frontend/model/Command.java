package com.barony.frontend.model;

public class Command {
    private String type;
    private int armyIndex;
    private int targetX;
    private int targetY;
    
    public Command(String type, int armyIndex, int targetX, int targetY) {
        this.type = type;
        this.armyIndex = armyIndex;
        this.targetX = targetX;
        this.targetY = targetY;
    }
    
    public String getType() {
        return type;
    }
    
    public int getArmyIndex() {
        return armyIndex;
    }
    
    public int getTargetX() {
        return targetX;
    }
    
    public int getTargetY() {
        return targetY;
    }
}
