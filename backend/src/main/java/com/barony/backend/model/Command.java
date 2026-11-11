package com.barony.backend.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A player's order to one army: {@code MOVE} (to {@code targetX, targetY}), {@code SPLIT} (off
 * {@code splitAmount} soldiers, optionally naming exactly {@code splitArchers} and
 * {@code splitCavalry} among them; without them each type splits in proportion) or {@code TRAIN}
 * ({@code count} infantry into {@code unitType}: ARCHERS at a held village, CAVALRY at a held
 * castle; a count of 0 cancels the army's training).
 */
@Getter
@Setter
public class Command {
    private String type;
    private int armyId;
    private int targetX;
    private int targetY;
    private int splitAmount;
    private Integer splitArchers;
    private Integer splitCavalry;
    private String unitType;
    private int count;
}
