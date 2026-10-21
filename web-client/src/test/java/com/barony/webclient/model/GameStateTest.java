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

    @Test
    void backendWidthAndHeightAreNotStoredWithoutAGrid() throws Exception {
        GameState state = mapper.readValue("{\"width\":5,\"height\":5}", GameState.class);
        JsonNode relayed = mapper.readTree(mapper.writeValueAsString(state));

        assertEquals(0, relayed.get("width").asInt());
        assertEquals(0, relayed.get("height").asInt());
    }
}
