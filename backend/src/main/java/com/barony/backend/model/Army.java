package com.barony.backend.model;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class Army {
    private int id;
    private int x;
    private int y;
    private int soldiers;
    private int playerId;
    private Integer destinationX;
    private Integer destinationY;
    private int morale; // 0-200, affects combat effectiveness (default 100)
    private int loyalty; // 0-110, affects desertion rate (default 100, 100-110 is bonus)

    private static final AtomicInteger nextId = new AtomicInteger(1);

    public Army(int x, int y, int soldiers, int playerId) {
        this.id = nextId.getAndIncrement();
        this.x = x;
        this.y = y;
        this.soldiers = soldiers;
        this.playerId = playerId;
        this.morale = 100; // Default morale
        this.loyalty = 100; // Default loyalty
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
        this.morale = other.morale;
        this.loyalty = other.loyalty;
    }

    public boolean isMoving() {
        return destinationX != null && destinationY != null
            && (x != destinationX || y != destinationY);
    }

    public void setMorale(int morale) {
        this.morale = Math.max(0, Math.min(200, morale)); // Clamp between 0 and 200
    }

    public void setLoyalty(int loyalty) {
        this.loyalty = Math.max(0, Math.min(110, loyalty)); // Clamp between 0 and 110 (allow bonus)
    }
}
