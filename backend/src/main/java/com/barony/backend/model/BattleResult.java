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
 *
 * <p>{@code survivorsByType}, when a client sends it, breaks each side's survivors down by unit
 * type: {@code [[infantry, archers, cavalry], [infantry, archers, cavalry]]}, side 0 then side 1.
 * Each count must lie within what that side brought of the type and each side's three must add up
 * to its {@code survivors}; each type's survivors are shared among the side's armies in proportion
 * to how many of that type each brought. Without it (older clients) each army's losses are spread
 * across its types in proportion.
 */
@Getter
@Setter
@NoArgsConstructor
public class BattleResult {
    private int battleId;
    private String result;
    private Integer winner;
    private int[] survivors;
    private int[][] survivorsByType;
}
