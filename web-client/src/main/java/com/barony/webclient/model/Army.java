package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

/**
 * An army as the backend reports it. {@code soldiers} is the total; {@code archers} and
 * {@code cavalry} are part of it and {@code infantry} is the rest (the backend derives it).
 * {@code trainingType} ("ARCHERS" or "CAVALRY") and {@code trainingRemaining} describe the training
 * order it is working through, if any. All of these are relayed to the page unchanged.
 */
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
    private int archers;
    private int cavalry;
    private int infantry;
    private String trainingType;
    private int trainingRemaining;
}
