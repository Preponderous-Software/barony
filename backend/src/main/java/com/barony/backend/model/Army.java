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
    private int morale;
    private int loyalty;

    private static final AtomicInteger nextId = new AtomicInteger(1);

    public Army(int x, int y, int soldiers, int playerId) {
        this.id = nextId.getAndIncrement();
        this.x = x;
        this.y = y;
        this.soldiers = soldiers;
        this.playerId = playerId;
        this.morale = 100;
        this.loyalty = 100;
    }

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
        this.morale = Math.max(0, Math.min(200, morale));
    }

    public void setLoyalty(int loyalty) {
        this.loyalty = Math.max(0, Math.min(110, loyalty));
    }
}
