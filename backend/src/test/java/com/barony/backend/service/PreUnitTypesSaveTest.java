package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.PendingBattle;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Saves made before unit types must load and play exactly as they always did.
 *
 * <p>{@code saves/pre-unit-types-save.json} was written by the code before unit types (commit
 * 6466263): a game 22 turns in, under the desertion-heavy military policy, paused on a pending
 * field battle. The digests below were recorded by running {@link LegacyGameHarness} on that same
 * code; this build must reproduce them bit for bit, through movement, merging, recruiting, the AI
 * and its garrison splits, player splits, auto and field battles with retreats, and desertion.
 */
class PreUnitTypesSaveTest {

    private static final String FIXTURE_DIGEST = "16f93eeed634c416a7c54a4b9214cfd4c243a2af6f72aa64a1f34567db236822";

    /** Seeded new games with the unitTypes flag removed, as an old save has it; recorded on 6466263. */
    private static final String[] SEED_DIGESTS = {
        "dbec4b4e505de7ba768d0f084c6d75c03433f926c7332dad3600553c9d49814d",
        "f661394cf662e2c5714fbd8d9eaaaad491eb8d098bde3a70f848c574ebdba020",
        "5fc80e04773d8852af8f60cae4baa465463585f456bce5f19f9a272c63f2fd53",
        "43b09eb0f63d50e4e574a1ae0ab001c6707f965c3a51e32603c977a554149ae3",
        "d6ec761f1524ca221e96ab65a834650415e2e196c644108671498e591ac4b229",
        "8d3100c9bd1e3f67f999aeebcf583998c43f3e0303ca1e96a2994f75739876ed",
    };

    // The mapper SessionService and GuestSessionService load saves with.
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private String fixtureJson() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/saves/pre-unit-types-save.json")) {
            assertNotNull(in, "fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theOldSaveHasNoneOfTheNewFields() throws Exception {
        String json = fixtureJson();
        for (String field : new String[] {"archers", "cavalry", "infantry", "training", "unitTypes", "survivorsByType"}) {
            assertFalse(json.contains(field), "the fixture predates '" + field + "'");
        }
    }

    @Test
    void itLoadsAsAllInfantryWithNoTrainingAndNoAiTraining() throws Exception {
        GameState loaded = mapper.readValue(fixtureJson(), GameState.class);
        assertEquals(22, loaded.getTickCount());
        assertFalse(loaded.getArmiesInternal().isEmpty());
        for (Army army : loaded.getArmiesInternal()) {
            assertEquals(0, army.getArchers());
            assertEquals(0, army.getCavalry());
            assertEquals(army.getSoldiers(), army.getInfantry());
            assertNull(army.getTrainingType());
            assertEquals(0, army.getTrainingRemaining());
        }
        assertEquals(1, loaded.getPendingBattlesInternal().size());
        PendingBattle battle = loaded.getPendingBattlesInternal().get(0);
        assertEquals(0, battle.getSide0Archers() + battle.getSide0Cavalry() + battle.getSide1Archers() + battle.getSide1Cavalry());
        assertNull(loaded.getUnitTypes());
        assertFalse(loaded.isAiTrainingEnabled());
    }

    @Test
    void itRoundTripsThroughANewSave() throws Exception {
        GameState loaded = mapper.readValue(fixtureJson(), GameState.class);
        String saved = mapper.writeValueAsString(loaded);
        GameState again = mapper.readValue(saved, GameState.class);
        assertEquals(LegacyGameHarness.describe(loaded), LegacyGameHarness.describe(again));
        assertEquals(mapper.writeValueAsString(loaded), mapper.writeValueAsString(again));
        // the re-saved game plays the same as the original save
        assertEquals(FIXTURE_DIGEST, LegacyGameHarness.play(again, 60));
    }

    @Test
    void itPlaysExactlyAsBefore() throws Exception {
        GameState loaded = mapper.readValue(fixtureJson(), GameState.class);
        assertEquals(FIXTURE_DIGEST, LegacyGameHarness.play(loaded, 60));
        for (Army army : loaded.getArmiesInternal()) {
            assertEquals(0, army.getArchers() + army.getCavalry(), "nothing in an old game trains on its own");
        }
    }

    @Test
    void oldGamesFromEverySeedPlayExactlyAsBefore() throws Exception {
        for (long seed = 1; seed <= SEED_DIGESTS.length; seed++) {
            GameState state = new MapGenerator(seed).generate();
            state.setFieldBattles(seed % 3 != 0);
            if (seed % 2 == 0) {
                state.setMilitaryPolicy("AGGRESSIVE_TRAINING");
            }
            String json = mapper.writeValueAsString(state).replace(",\"unitTypes\":true", "");
            assertFalse(json.contains("unitTypes\":true"));
            GameState old = mapper.readValue(json, GameState.class);
            assertEquals(SEED_DIGESTS[(int) seed - 1], LegacyGameHarness.play(old, 80), "seed " + seed);
        }
    }

    @Test
    void aNewGameIsPlayedWithUnitTypes() {
        GameState state = new MapGenerator(1).generate();
        assertTrue(state.isAiTrainingEnabled());
        assertEquals(Boolean.TRUE, state.createSnapshot().getUnitTypes());
    }
}
