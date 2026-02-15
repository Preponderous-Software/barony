package com.barony.webclient.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Command {
    private String type;
    private int armyId;
    private int targetX;
    private int targetY;
    private int splitAmount;
}
