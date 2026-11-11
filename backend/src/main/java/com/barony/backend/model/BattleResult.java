package com.barony.backend.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * How the player resolves a {@link PendingBattle}: {@code FIELD} with the outcome of the battle
 * they fought, or {@code AUTO} to let the map's one-exchange rule decide it.
 *
 * <p>For a field result, {@code winner} is 0 (player 1), 1 (the enemy) or null for a draw, and
 * {@code survivors} is what each side keeps: the winner's soldiers still standing, the loser's
 * soldiers who escaped the field. The server checks it against the battle's starting numbers.
 */
@Getter
@Setter
@NoArgsConstructor
public class BattleResult {
    private int battleId;
    private String result;
    private Integer winner;
    private int[] survivors;
}
