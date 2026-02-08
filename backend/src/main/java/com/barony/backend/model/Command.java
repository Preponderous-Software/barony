package com.barony.backend.model;

public class Command {
    private String type;
    private int armyId;
    private int targetX;
    private int targetY;
    private int splitAmount;
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public int getArmyId() {
        return armyId;
    }
    
    public void setArmyId(int armyId) {
        this.armyId = armyId;
    }
    
    public int getTargetX() {
        return targetX;
    }
    
    public void setTargetX(int targetX) {
        this.targetX = targetX;
    }
    
    public int getTargetY() {
        return targetY;
    }
    
    public void setTargetY(int targetY) {
        this.targetY = targetY;
    }
    
    public int getSplitAmount() {
        return splitAmount;
    }
    
    public void setSplitAmount(int splitAmount) {
        this.splitAmount = splitAmount;
    }
}
