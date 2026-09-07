package com.barony.backend.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
@NoArgsConstructor
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

    /**
     * Fractional desertion carried over between ticks, in basis points of a single soldier.
     * Desertion is a fraction of a percent per tick applied to armies that are usually only a few
     * dozen soldiers strong, so truncating it to whole soldiers every tick would round the mechanic
     * away entirely. Part of the army state so it survives a save/load.
     */
    private int desertionCarryBasisPoints;

    private static final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * Ensure freshly-generated army ids stay above {@code id}. Called after loading a saved game so
     * a new army (e.g. from a split) can't reuse an id already present in the restored state — the
     * id counter is a static that otherwise resets to 1 on backend restart.
     */
    public static void ensureIdsAbove(int id) {
        nextId.updateAndGet(current -> Math.max(current, id + 1));
    }

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
        this.desertionCarryBasisPoints = other.desertionCarryBasisPoints;
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
