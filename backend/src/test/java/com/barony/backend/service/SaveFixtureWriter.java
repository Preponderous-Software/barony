package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.BattleResult;
import com.barony.backend.model.Command;
import com.barony.backend.model.GameState;
import com.barony.backend.model.PendingBattle;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Writes the save fixtures of the current format ({@link GameState#SAVE_VERSION}) with the
 * serializer the app saves with. It is how the {@code v1-*} fixtures were made; it never overwrites
 * an existing file, because a released fixture never changes. Run it only when adding a format:
 *
 * <pre>mvn test -Dtest=SaveFixtureWriter -Dbarony.writeSaveFixtures=true</pre>
 *
 * then list the new files in {@link SaveFixturesTest}.
 */
@EnabledIfSystemProperty(named = "barony.writeSaveFixtures", matches = "true")
class SaveFixtureWriter {

    private static final Path DIR = Path.of("src/test/resources/saves");

    // Configured exactly as SessionService's mapper, which writes every player's save.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void writeTheCurrentFormatsFixtures() throws Exception {
        int v = GameState.SAVE_VERSION;
        write("v" + v + "-new-game.json", newGame(1));
        write("v" + v + "-mid-game-field-battle.json", midGameWithAFieldBattlePending(2));
        write("v" + v + "-game-over.json", playedToTheEnd());
    }

    /** A new game as a player's session starts it: field battles on (the session's default). */
    private static GameState newGame(long seed) {
        GameState state = new MapGenerator(seed).generate();
        state.setFieldBattles(true);
        return state;
    }

    /**
     * Some turns in, with archers and cavalry trained by the player and the AI, paused on a field
     * battle the player has not fought yet.
     */
    private static GameState midGameWithAFieldBattlePending(long seed) {
        GameState state = newGame(seed);
        GameService service = new GameService();
        service.setGameState(state);
        trainEverywhere(service, state, "CAVALRY");
        for (int turn = 0; turn < 12; turn++) {
            service.tick();
            resolveAll(service, state);
        }
        trainEverywhere(service, state, "ARCHERS");
        for (int turn = 0; turn < 400 && !state.isGameOver(); turn++) {
            LegacyGameHarness.sendAtEnemy(service, state);
            service.tick();
            if (!state.getPendingBattlesInternal().isEmpty() && hasTrainedUnits(state)) {
                return state;
            }
            resolveAll(service, state);
        }
        throw new IllegalStateException("no field battle with unit types came up on seed " + seed);
    }

    /** A game played to its end, with its run recorded: the first seed from 3 on whose game ends. */
    private static GameState playedToTheEnd() {
        for (long seed = 3; seed < 40; seed++) {
            GameState state = newGame(seed);
            GameService service = new GameService();
            service.setGameState(state);
            for (int turn = 0; turn < 1500 && !state.isGameOver(); turn++) {
                LegacyGameHarness.sendAtEnemy(service, state);
                service.tick();
                resolveAll(service, state);
            }
            if (state.isGameOver()) {
                state.setRunRecorded(true);
                return state;
            }
        }
        throw new IllegalStateException("no game ended");
    }

    private static void trainEverywhere(GameService service, GameState state, String type) {
        for (Army army : new ArrayList<>(state.getArmiesInternal())) {
            if (army.getPlayerId() != 1 || army.getInfantry() < 2) continue;
            Command train = new Command();
            train.setType("TRAIN");
            train.setArmyId(army.getId());
            train.setUnitType(type);
            train.setCount(army.getInfantry() / 2);
            try {
                service.executeCommand(train);
            } catch (IllegalArgumentException | IllegalStateException notHere) {
                // not on a holding that trains this type
            }
        }
    }

    private static boolean hasTrainedUnits(GameState state) {
        return state.getArmiesInternal().stream().anyMatch(a -> a.getArchers() + a.getCavalry() > 0);
    }

    private static void resolveAll(GameService service, GameState state) {
        for (PendingBattle battle : new ArrayList<>(state.getPendingBattlesInternal())) {
            BattleResult auto = new BattleResult();
            auto.setBattleId(battle.getId());
            auto.setResult("AUTO");
            service.resolveBattle(auto);
        }
    }

    private void write(String name, GameState state) throws Exception {
        Path file = DIR.resolve(name);
        assertFalse(Files.exists(file), name + " exists; a released fixture is never rewritten");
        Files.writeString(file, mapper.writeValueAsString(state), StandardCharsets.UTF_8);
    }
}
