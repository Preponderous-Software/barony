package com.barony.frontend.model;

public class Command {
    private String type;
    private int armyId;
    private int targetX;
    private int targetY;
    private int splitAmount;
    
    public Command(String type, int armyId, int targetX, int targetY) {
        this.type = type;
        this.armyId = armyId;
        this.targetX = targetX;
        this.targetY = targetY;
    }
    
    public Command(String type, int armyId, int splitAmount) {
        this.type = type;
        this.armyId = armyId;
        this.splitAmount = splitAmount;
    }
    
    public String getType() {
        return type;
    }
    
    public int getArmyId() {
        return armyId;
    }
    
    public int getTargetX() {
        return targetX;
    }
    
    public int getTargetY() {
        return targetY;
    }
    
    public int getSplitAmount() {
        return splitAmount;
    }
}
