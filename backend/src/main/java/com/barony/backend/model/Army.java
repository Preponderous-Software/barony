package com.barony.backend.model;

import java.util.concurrent.atomic.AtomicInteger;

public class Army {
    private int id;
    private int x;
    private int y;
    private int soldiers;
    private int playerId;
    private Integer destinationX;
    private Integer destinationY;
    
    private static final AtomicInteger nextId = new AtomicInteger(1);
    
    public Army(int x, int y, int soldiers, int playerId) {
        this.id = nextId.getAndIncrement();
        this.x = x;
        this.y = y;
        this.soldiers = soldiers;
        this.playerId = playerId;
    }
    
    // Copy constructor for creating snapshots
    public Army(Army other) {
        this.id = other.id;
        this.x = other.x;
        this.y = other.y;
        this.soldiers = other.soldiers;
        this.playerId = other.playerId;
        this.destinationX = other.destinationX;
        this.destinationY = other.destinationY;
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
    
    public Integer getDestinationX() {
        return destinationX;
    }
    
    public void setDestinationX(Integer destinationX) {
        this.destinationX = destinationX;
    }
    
    public Integer getDestinationY() {
        return destinationY;
    }
    
    public void setDestinationY(Integer destinationY) {
        this.destinationY = destinationY;
    }
    
    public boolean isMoving() {
        return destinationX != null && destinationY != null 
            && (x != destinationX || y != destinationY);
    }
}
