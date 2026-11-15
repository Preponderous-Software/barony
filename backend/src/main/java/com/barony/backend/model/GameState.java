package com.barony.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class GameState {
    private Tile[][] grid;
    private List<Army> armies;
    private int tickCount;
    private boolean gameOver;
    private Integer winnerId;
    // Set once a finished run has been written to run history, so a restart or a repeated tick
    // after game-over can't record the same run twice.
    private boolean runRecorded;
    private boolean aiEnabled;
    private String economicPolicy;
    private String militaryPolicy;
    private String populationPolicy;
    private int lastPolicyChangeTick;
    /**
     * Engagements waiting to be fought out or auto-resolved; while any are pending the turn is
     * paused just before combat. Absent from saves made before field battles existed, which load
     * with none.
     */
    private List<PendingBattle> pendingBattles;
    private int nextBattleId;
    /**
     * Whether the player's engagements pause the turn for a field battle. Null in saves made before
     * field battles existed and in games the engine runs directly: off. A player's session turns it
     * on when it is still null (the default for players), and the player can turn it off.
     */
    private Boolean fieldBattles;
    /**
     * Whether the AI trains archers and cavalry in this game. True for every game started since
     * unit types; null in saves made before them, so an old save's AI plays exactly as it did (the
     * player may still train their own armies there).
     */
    private Boolean unitTypes;

    public GameState(int width, int height) {
        this.grid = new Tile[width][height];
        this.armies = new ArrayList<>();
        this.tickCount = 0;
        this.gameOver = false;
        this.winnerId = null;
        this.runRecorded = false;
        this.aiEnabled = true;
        this.economicPolicy = "BALANCED_BUDGET";
        this.militaryPolicy = "STANDARD_SERVICE";
        this.populationPolicy = "STABLE_POPULATION";
        this.lastPolicyChangeTick = -15;
        this.pendingBattles = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                grid[x][y] = new Tile(TileType.EMPTY);
            }
        }
    }

    public GameState createSnapshot() {
        GameState snapshot = new GameState(getWidth(), getHeight());
        snapshot.setTickCount(tickCount);
        snapshot.setGameOver(gameOver);
        snapshot.setWinnerId(winnerId);
        snapshot.setAiEnabled(aiEnabled);
        snapshot.setEconomicPolicy(economicPolicy);
        snapshot.setMilitaryPolicy(militaryPolicy);
        snapshot.setPopulationPolicy(populationPolicy);
        snapshot.setLastPolicyChangeTick(lastPolicyChangeTick);
        snapshot.setNextBattleId(nextBattleId);
        snapshot.setFieldBattles(fieldBattles);
        snapshot.setUnitTypes(unitTypes);
        for (PendingBattle battle : getPendingBattlesInternal()) {
            snapshot.pendingBattles.add(new PendingBattle(battle));
        }
        for (int x = 0; x < getWidth(); x++) {
            for (int y = 0; y < getHeight(); y++) {
                Tile source = grid[x][y];
                Tile target = snapshot.grid[x][y];
                target.setType(source.getType());
                target.setOwnerId(source.getOwnerId());
                target.setOccupationTicks(source.getOccupationTicks());
                target.setStability(source.getStability());
                target.setPopulation(source.getPopulation());
                target.setGround(source.getGround());
            }
        }
        for (Army army : armies) {
            snapshot.armies.add(new Army(army));
        }
        return snapshot;
    }

    public List<Army> getArmies() {
        return new ArrayList<>(armies);
    }

    @JsonIgnore
    public List<Army> getArmiesInternal() {
        return armies;
    }

    public List<PendingBattle> getPendingBattles() {
        return new ArrayList<>(getPendingBattlesInternal());
    }

    @JsonIgnore
    public List<PendingBattle> getPendingBattlesInternal() {
        if (pendingBattles == null) {
            pendingBattles = new ArrayList<>();
        }
        return pendingBattles;
    }

    @JsonIgnore
    public boolean isAiTrainingEnabled() {
        return Boolean.TRUE.equals(unitTypes);
    }

    @JsonIgnore
    public boolean isFieldBattlesEnabled() {
        return Boolean.TRUE.equals(fieldBattles);
    }

    public void incrementTick() {
        this.tickCount++;
    }

    public int getWidth() {
        return grid.length;
    }

    public int getHeight() {
        return grid.length > 0 ? grid[0].length : 0;
    }
}
