package com.barony.backend.model;

import com.barony.backend.service.MapGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The persistence layer stores a game as JSON, so a generated GameState must survive a
 * serialize -> deserialize round trip. The backend models were previously only ever serialized
 * (for API responses), never read back, so this guards the @NoArgsConstructor additions and the
 * lenient mapper config that make them deserializable.
 */
class GameStateSerializationTest {

    // Mirrors the mapper SessionService uses: tolerate derived getters like getWidth()/isMoving().
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void generatedGameStateSurvivesAJsonRoundTrip() throws Exception {
        GameState original = new MapGenerator().generate();
        original.setTickCount(7);
        original.setEconomicPolicy("HEAVY_TAXATION");

        String json = mapper.writeValueAsString(original);
        GameState restored = mapper.readValue(json, GameState.class);

        assertEquals(original.getWidth(), restored.getWidth());
        assertEquals(original.getHeight(), restored.getHeight());
        assertEquals(7, restored.getTickCount());
        assertEquals("HEAVY_TAXATION", restored.getEconomicPolicy());
        assertEquals(original.getArmies().size(), restored.getArmies().size());
        assertFalse(restored.getArmies().isEmpty(), "a generated game should have armies");

        // Army fields round-trip (id, position, soldiers, owner).
        Army oa = original.getArmies().get(0);
        Army ra = restored.getArmies().get(0);
        assertEquals(oa.getId(), ra.getId());
        assertEquals(oa.getSoldiers(), ra.getSoldiers());
        assertEquals(oa.getPlayerId(), ra.getPlayerId());

        // Grid tiles round-trip (type + ownership).
        Tile ot = original.getGrid()[0][0];
        Tile rt = restored.getGrid()[0][0];
        assertEquals(ot.getType(), rt.getType());
        assertEquals(ot.getOwnerId(), rt.getOwnerId());
    }

    @Test
    void ensureIdsAboveAdvancesTheArmyIdCounter() {
        Army.ensureIdsAbove(5000);
        Army next = new Army(0, 0, 10, 1);
        assertTrue(next.getId() > 5000, "new army id should be past the restored max, was " + next.getId());
    }
}
