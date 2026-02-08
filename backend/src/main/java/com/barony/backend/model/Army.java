package com.barony.backend.model;

public class Army {
    private int id;
    private int x;
    private int y;
    private int soldiers;
    private int playerId;
    
    private static int nextId = 1;
    
    public Army(int x, int y, int soldiers, int playerId) {
        this.id = nextId++;
        this.x = x;
        this.y = y;
        this.soldiers = soldiers;
        this.playerId = playerId;
    }
    
    public int getId() {
        return id;
    }
    
    public int getX() {
        return x;
    }
    
    public void setX(int x) {
        this.x = x;
    }
    
    public int getY() {
        return y;
    }
    
    public void setY(int y) {
        this.y = y;
    }
    
    public int getSoldiers() {
        return soldiers;
    }
    
    public void setSoldiers(int soldiers) {
        this.soldiers = soldiers;
    }
    
    public int getPlayerId() {
        return playerId;
    }
    
    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }
}
