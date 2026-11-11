package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A command for one army, relayed to the backend: MOVE, SPLIT (optionally naming
 * {@code splitArchers} and {@code splitCavalry}) or TRAIN ({@code count} infantry into
 * {@code unitType}, ARCHERS or CAVALRY; a count of 0 cancels).
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
