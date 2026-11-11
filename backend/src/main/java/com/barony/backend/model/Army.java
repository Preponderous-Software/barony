package com.barony.backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    /**
     * How many of {@link #soldiers} are archers and cavalry; the rest are infantry. {@code soldiers}
     * stays the total headcount, so everything that only counts soldiers reads the same as before.
     * Saves from before unit types have neither field and load as all infantry.
     * Invariant: {@code 0 <= archers + cavalry <= soldiers}.
     */
    private int archers;
    private int cavalry;

    /**
     * The training order the army is working through: which type it is converting infantry into
     * and how many are still to convert. Null / 0 when idle, which is how saves from before unit
     * types load.
     */
    private UnitType trainingType;
    private int trainingRemaining;

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
        this.archers = other.archers;
        this.cavalry = other.cavalry;
        this.trainingType = other.trainingType;
        this.trainingRemaining = other.trainingRemaining;
    }

    /** Infantry is whatever is not archers or cavalry. Read-only: never read back from JSON. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public int getInfantry() {
        return soldiers - archers - cavalry;
    }

    /** True while the army has a training order to work through. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isTraining() {
        return trainingType != null && trainingRemaining > 0;
    }

    public void clearTraining() {
        this.trainingType = null;
        this.trainingRemaining = 0;
    }

    /**
     * Removes {@code n} soldiers (clamped to the army's size), taken across infantry, archers and
     * cavalry in proportion to how many of each it has (see {@link #shareLosses}). Every rule that
     * shrinks an army goes through here, so the mix always stays within the headcount. An
     * all-infantry army simply loses {@code n} soldiers, exactly as before unit types.
     *
     * @return the soldiers removed, as {@code [infantry, archers, cavalry]}
     */
    public int[] loseSoldiers(int n) {
        int lose = Math.max(0, Math.min(n, soldiers));
        int[] lost = shareLosses(getInfantry(), archers, cavalry, lose);
        soldiers -= lose;
        archers -= lost[1];
        cavalry -= lost[2];
        return lost;
    }

    /**
     * Shares {@code lose} out of an army of {@code inf} infantry, {@code arch} archers and
     * {@code cav} cavalry in proportion to each type's count, by largest remainder: each type first
     * gives up the whole part of its exact share, then the soldiers still owed come one each from
     * the types with the largest fractional remainder, ties going to infantry, then archers, then
     * cavalry. Pure and deterministic; never takes more of a type than there is.
     *
     * @return {@code [infantry, archers, cavalry]} taken, summing to {@code lose}
     * @throws IllegalArgumentException if a count is negative or {@code lose} exceeds the total
     */
    public static int[] shareLosses(int inf, int arch, int cav, int lose) {
        return largestRemainder(lose, new int[] {inf, arch, cav});
    }

    /**
     * Shares {@code amount} across buckets in proportion to {@code weights} by largest remainder
     * (exact integer arithmetic), ties going to the lower index. {@code amount} must lie between
     * 0 and the weights' total.
     */
    public static int[] largestRemainder(int amount, int[] weights) {
        long total = 0;
        for (int w : weights) {
            if (w < 0) {
                throw new IllegalArgumentException("counts cannot be negative");
            }
            total += w;
        }
        if (amount < 0 || amount > total) {
            throw new IllegalArgumentException("cannot take " + amount + " of " + total);
        }
        int[] share = new int[weights.length];
        if (amount == 0) {
            return share;
        }
        long[] remainder = new long[weights.length];
        int assigned = 0;
        for (int i = 0; i < weights.length; i++) {
            long exact = (long) amount * weights[i];
            share[i] = (int) (exact / total);
            remainder[i] = exact % total;
            assigned += share[i];
        }
        while (assigned < amount) {
            int best = -1;
            for (int i = 0; i < weights.length; i++) {
                if (remainder[i] > 0 && (best < 0 || remainder[i] > remainder[best])) {
                    best = i;
                }
            }
            share[best]++;
            remainder[best] = 0;
            assigned++;
        }
        return share;
    }

    /**
     * Detaches {@code n} soldiers into a new army on the same tile, taking each type in proportion
     * (the same rule as {@link #shareLosses}). The new army gets a fresh id and default stats; the
     * caller sets morale and loyalty.
     */
    public Army detach(int n) {
        int[] taken = loseSoldiers(n);
        Army part = new Army(x, y, taken[0] + taken[1] + taken[2], playerId);
        part.archers = taken[1];
        part.cavalry = taken[2];
        return part;
    }

    /**
     * Detaches exactly {@code inf} infantry, {@code arch} archers and {@code cav} cavalry into a new
     * army on the same tile.
     *
     * @throws IllegalArgumentException if a count is negative or more than the army has of it
     */
    public Army detach(int inf, int arch, int cav) {
        if (inf < 0 || arch < 0 || cav < 0 || inf > getInfantry() || arch > archers || cav > cavalry) {
            throw new IllegalArgumentException("Army " + id + " has " + getInfantry() + " infantry, "
                    + archers + " archers and " + cavalry + " cavalry");
        }
        soldiers -= inf + arch + cav;
        archers -= arch;
        cavalry -= cav;
        Army part = new Army(x, y, inf + arch + cav, playerId);
        part.archers = arch;
        part.cavalry = cav;
        return part;
    }

    /**
     * Adds another army's soldiers to this one, type by type (a merge). Keeps this army's training
     * order, or takes the other's if this one has none.
     */
    public void absorb(Army other) {
        soldiers += other.soldiers;
        archers += other.archers;
        cavalry += other.cavalry;
        if (!isTraining() && other.isTraining()) {
            trainingType = other.trainingType;
            trainingRemaining = other.trainingRemaining;
        }
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
