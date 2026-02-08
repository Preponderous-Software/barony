package com.barony.backend.model;

public class Command {
    private String type;
    private int armyIndex;
    private int targetX;
    private int targetY;
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public int getArmyIndex() {
        return armyIndex;
    }
    
    public void setArmyIndex(int armyIndex) {
        this.armyIndex = armyIndex;
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
}
