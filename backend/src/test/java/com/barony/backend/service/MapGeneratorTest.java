package com.barony.backend.service;

import com.barony.backend.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapGeneratorTest {

    @Test
    void generateProducesMapWithinSizeRange() {
        MapGenerator generator = new MapGenerator();
        for (int i = 0; i < 20; i++) {
            GameState state = generator.generate();
            assertTrue(state.getWidth() >= MapGenerator.MIN_SIZE && state.getWidth() <= MapGenerator.MAX_SIZE,
                "Width " + state.getWidth() + " should be between " + MapGenerator.MIN_SIZE + " and " + MapGenerator.MAX_SIZE);
            assertTrue(state.getHeight() >= MapGenerator.MIN_SIZE && state.getHeight() <= MapGenerator.MAX_SIZE,
                "Height " + state.getHeight() + " should be between " + MapGenerator.MIN_SIZE + " and " + MapGenerator.MAX_SIZE);
        }
    }

    @Test
    void generatePlacesPlayer1CastleAtOrigin() {
        MapGenerator generator = new MapGenerator();
        GameState state = generator.generate();
        assertEquals(TileType.CASTLE, state.getGrid()[0][0].getType());
        assertEquals(1, state.getGrid()[0][0].getOwnerId());
    }

    @Test
    void generatePlacesPlayer2CastleAtOppositeCorner() {
        MapGenerator generator = new MapGenerator();
        GameState state = generator.generate();
        int w = state.getWidth();
        int h = state.getHeight();
        assertEquals(TileType.CASTLE, state.getGrid()[w - 1][h - 1].getType());
        assertEquals(2, state.getGrid()[w - 1][h - 1].getOwnerId());
    }

    @Test
    void generatePlacesAtLeastMinVillages() {
        MapGenerator generator = new MapGenerator();
        for (int i = 0; i < 20; i++) {
            GameState state = generator.generate();
            int villageCount = countTiles(state, TileType.VILLAGE);
            assertTrue(villageCount >= MapGenerator.MIN_VILLAGES,
                "Should have at least " + MapGenerator.MIN_VILLAGES + " villages, got " + villageCount);
        }
    }

    @Test
    void generateVillagesAreNeutral() {
        MapGenerator generator = new MapGenerator();
        GameState state = generator.generate();
        for (int x = 0; x < state.getWidth(); x++) {
            for (int y = 0; y < state.getHeight(); y++) {
                if (state.getGrid()[x][y].getType() == TileType.VILLAGE) {
                    assertEquals(0, state.getGrid()[x][y].getOwnerId(),
                        "Village at (" + x + "," + y + ") should be neutral");
                }
            }
        }
    }

    @Test
    void generatePlacesTwoArmies() {
        MapGenerator generator = new MapGenerator();
        GameState state = generator.generate();
        assertEquals(2, state.getArmiesInternal().size());
    }

    @Test
    void generatePlacesArmiesAtCastles() {
        MapGenerator generator = new MapGenerator();
        GameState state = generator.generate();
        Army p1Army = state.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 1).findFirst().orElse(null);
        Army p2Army = state.getArmiesInternal().stream()
            .filter(a -> a.getPlayerId() == 2).findFirst().orElse(null);
        assertNotNull(p1Army);
        assertNotNull(p2Army);
        assertEquals(0, p1Army.getX());
        assertEquals(0, p1Army.getY());
        assertEquals(state.getWidth() - 1, p2Army.getX());
        assertEquals(state.getHeight() - 1, p2Army.getY());
    }

    @Test
    void generateWithSeedProducesDeterministicOutput() {
        MapGenerator gen1 = new MapGenerator(42L);
        MapGenerator gen2 = new MapGenerator(42L);
        GameState state1 = gen1.generate();
        GameState state2 = gen2.generate();
        assertEquals(state1.getWidth(), state2.getWidth());
        assertEquals(state1.getHeight(), state2.getHeight());
        for (int x = 0; x < state1.getWidth(); x++) {
            for (int y = 0; y < state1.getHeight(); y++) {
                assertEquals(state1.getGrid()[x][y].getType(), state2.getGrid()[x][y].getType(),
                    "Tile type at (" + x + "," + y + ") should match with same seed");
            }
        }
    }

    @Test
    void generateVillageCountScalesWithMapArea() {
        // Use seeded generators to control map sizes
        // Try many seeds and check that larger maps get more villages on average
        final int smallMapAreaThreshold = 150;
        final int largeMapAreaThreshold = 300;
        List<Integer> smallMapVillages = new ArrayList<>();
        List<Integer> largeMapVillages = new ArrayList<>();
        
        for (long seed = 0; seed < 100; seed++) {
            MapGenerator gen = new MapGenerator(seed);
            GameState state = gen.generate();
            int villages = countTiles(state, TileType.VILLAGE);
            int area = state.getWidth() * state.getHeight();
            if (area <= smallMapAreaThreshold) {
                smallMapVillages.add(villages);
            } else if (area >= largeMapAreaThreshold) {
                largeMapVillages.add(villages);
            }
        }
        
        if (!smallMapVillages.isEmpty() && !largeMapVillages.isEmpty()) {
            double smallAvg = smallMapVillages.stream().mapToInt(Integer::intValue).average().orElse(0);
            double largeAvg = largeMapVillages.stream().mapToInt(Integer::intValue).average().orElse(0);
            assertTrue(largeAvg >= smallAvg,
                "Larger maps should have at least as many villages on average. Small: " + smallAvg + ", Large: " + largeAvg);
        }
    }

    @Test
    void generateVillagesHaveMinimumDistance() {
        MapGenerator generator = new MapGenerator();
        for (int i = 0; i < 10; i++) {
            GameState state = generator.generate();
            List<int[]> specialTiles = new ArrayList<>();
            for (int x = 0; x < state.getWidth(); x++) {
                for (int y = 0; y < state.getHeight(); y++) {
                    TileType type = state.getGrid()[x][y].getType();
                    if (type == TileType.CASTLE || type == TileType.VILLAGE) {
                        specialTiles.add(new int[]{x, y});
                    }
                }
            }
            // Check minimum distance between all pairs
            for (int a = 0; a < specialTiles.size(); a++) {
                for (int b = a + 1; b < specialTiles.size(); b++) {
                    int dist = Math.abs(specialTiles.get(a)[0] - specialTiles.get(b)[0]) +
                               Math.abs(specialTiles.get(a)[1] - specialTiles.get(b)[1]);
                    assertTrue(dist >= MapGenerator.MIN_DISTANCE_BETWEEN_SPECIALS,
                        "Special tiles at (" + specialTiles.get(a)[0] + "," + specialTiles.get(a)[1] + ") and (" +
                        specialTiles.get(b)[0] + "," + specialTiles.get(b)[1] + ") are too close: distance " + dist);
                }
            }
        }
    }

    @Test
    void generateProducesVariedMapSizes() {
        boolean foundNon10Width = false;
        boolean foundNon10Height = false;
        for (int i = 0; i < 100; i++) {
            MapGenerator gen = new MapGenerator();
            GameState state = gen.generate();
            if (state.getWidth() != 10) foundNon10Width = true;
            if (state.getHeight() != 10) foundNon10Height = true;
        }
        assertTrue(foundNon10Width, "Should produce varying widths over 100 runs");
        assertTrue(foundNon10Height, "Should produce varying heights over 100 runs");
    }

    private int countTiles(GameState state, TileType type) {
        int count = 0;
        for (int x = 0; x < state.getWidth(); x++) {
            for (int y = 0; y < state.getHeight(); y++) {
                if (state.getGrid()[x][y].getType() == type) {
                    count++;
                }
            }
        }
        return count;
    }
}
