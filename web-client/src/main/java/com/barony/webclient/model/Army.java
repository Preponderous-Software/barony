package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

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
}
