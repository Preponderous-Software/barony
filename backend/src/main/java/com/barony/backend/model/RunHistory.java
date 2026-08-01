package com.barony.backend.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** A player's win/loss tally plus their most recent finished runs, newest first. */
@Getter
@Setter
public class RunHistory {
    private int wins;
    private int losses;
    private List<RunRecord> runs;
}
