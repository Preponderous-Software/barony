package com.barony.frontend.model;

public class Army {
    private int id;
    private int x;
    private int y;
    private int soldiers;
    private int playerId;
    private Integer destinationX;
    private Integer destinationY;
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
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
