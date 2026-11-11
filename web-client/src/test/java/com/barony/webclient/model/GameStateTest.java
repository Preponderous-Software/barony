package com.barony.webclient.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The web client relays the backend's game state to the browser through this model, and the
 * browser sizes and walks the map from its {@code width} and {@code height}. Those two are not
 * stored: they are derived from the grid, with {@code grid[x][y]} indexing, so width is the
 * outer dimension and height the inner one.
 */
class GameStateTest {

    /** Configured the way Spring Boot configures the mapper behind the proxy's RestTemplate. */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void dimensionsAreZeroWithoutAGrid() {
        GameState state = new GameState();

        assertEquals(0, state.getWidth());
        assertEquals(0, state.getHeight());
    }

    @Test
    void dimensionsAreZeroForAnEmptyGrid() {
        GameState state = new GameState();
        state.setGrid(new Tile[0][0]);

        assertEquals(0, state.getWidth());
        assertEquals(0, state.getHeight());
    }

    @Test
    void widthIsTheOuterDimensionAndHeightTheInner() {
        GameState state = new GameState();
        state.setGrid(new Tile[3][2]);

        assertEquals(3, state.getWidth());
        assertEquals(2, state.getHeight());
    }

    @Test
    void relayedStateCarriesDimensionsDerivedFromTheBackendGrid() throws Exception {
        String backendJson = "{\"grid\":["
                + "[{\"type\":\"CASTLE\",\"ownerId\":1},{\"type\":\"EMPTY\",\"ownerId\":0}],"
                + "[{\"type\":\"VILLAGE\",\"ownerId\":0},{\"type\":\"EMPTY\",\"ownerId\":0}],"
                + "[{\"type\":\"EMPTY\",\"ownerId\":0},{\"type\":\"CASTLE\",\"ownerId\":2}]],"
                + "\"armies\":[],\"tickCount\":4,\"width\":3,\"height\":2,"
                + "\"economicPolicy\":\"BALANCED\"}";

        GameState state = mapper.readValue(backendJson, GameState.class);
        JsonNode relayed = mapper.readTree(mapper.writeValueAsString(state));

        assertEquals(3, relayed.get("width").asInt());
        assertEquals(2, relayed.get("height").asInt());
        assertEquals("CASTLE", relayed.get("grid").get(2).get(1).get("type").asText());
        assertEquals(2, relayed.get("grid").get(2).get(1).get("ownerId").asInt());
        assertEquals(4, relayed.get("tickCount").asInt());
        assertEquals("BALANCED", relayed.get("economicPolicy").asText());
    }

    /**
     * The game page waits on {@code pendingBattles} before a turn goes on, and the battlefield
     * reads the engagement from it; a field this model drops is stripped by the proxy without
     * any error, which would silently let the map decide every battle (#143).
     */
    @Test
    void relayedStateKeepsThePendingBattlesAndTheFieldBattlesSetting() throws Exception {
        String backendJson = "{\"armies\":[],\"tickCount\":11,"
                + "\"pendingBattles\":[{\"id\":4,\"x\":2,\"y\":3,\"terrain\":\"VILLAGE\","
                + "\"seed\":987654,\"tick\":11,\"side0ArmyIds\":[1,5],\"side1ArmyIds\":[2],"
                + "\"side0Soldiers\":40,\"side1Soldiers\":35,\"side0Morale\":80}],"
                + "\"nextBattleId\":5,\"fieldBattles\":true}";

        GameState state = mapper.readValue(backendJson, GameState.class);

        assertEquals(1, state.getPendingBattles().size());
        PendingBattle battle = state.getPendingBattles().get(0);
        assertEquals(4, battle.getId());
        assertEquals(TileType.VILLAGE, battle.getTerrain());
        assertEquals(5, state.getNextBattleId());
        assertEquals(true, state.getFieldBattles());

        JsonNode relayed = mapper.readTree(mapper.writeValueAsString(state));

        JsonNode pending = relayed.get("pendingBattles");
        assertNotNull(pending, "pendingBattles was dropped from the relayed state");
        assertEquals(1, pending.size());
        JsonNode relayedBattle = pending.get(0);
        assertEquals(4, relayedBattle.get("id").asInt());
        assertEquals(2, relayedBattle.get("x").asInt());
        assertEquals(3, relayedBattle.get("y").asInt());
        assertEquals("VILLAGE", relayedBattle.get("terrain").asText());
        assertEquals(987654, relayedBattle.get("seed").asInt());
        assertEquals(11, relayedBattle.get("tick").asInt());
        assertEquals(mapper.readTree("[1,5]"), relayedBattle.get("side0ArmyIds"));
        assertEquals(mapper.readTree("[2]"), relayedBattle.get("side1ArmyIds"));
        assertEquals(40, relayedBattle.get("side0Soldiers").asInt());
        assertEquals(35, relayedBattle.get("side1Soldiers").asInt());
        assertEquals(80, relayedBattle.get("side0Morale").asInt());
        assertEquals(5, relayed.get("nextBattleId").asInt());
        assertTrue(relayed.get("fieldBattles").asBoolean(),
                "fieldBattles was dropped or changed in the relayed state: " + relayed.get("fieldBattles"));
        assertEquals(11, relayed.get("tickCount").asInt());
    }

    @Test
    void backendWidthAndHeightAreNotStoredWithoutAGrid() throws Exception {
        GameState state = mapper.readValue("{\"width\":5,\"height\":5}", GameState.class);
        JsonNode relayed = mapper.readTree(mapper.writeValueAsString(state));

        assertEquals(0, relayed.get("width").asInt());
        assertEquals(0, relayed.get("height").asInt());
    }
}
