package com.barony.backend.service;

import com.barony.backend.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MapGenerator {
    private final Random random;

    static final int MIN_SIZE = 10;
    static final int MAX_SIZE = 20;
    static final int MIN_VILLAGES = 2;
    static final int MIN_DISTANCE_BETWEEN_SPECIALS = 3;
    private static final int MAX_PLACEMENT_ATTEMPTS = 100;

    public MapGenerator() {
        this.random = new Random();
    }

    public MapGenerator(long seed) {
        this.random = new Random(seed);
    }

    public GameState generate() {
        int width = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);
        int height = MIN_SIZE + random.nextInt(MAX_SIZE - MIN_SIZE + 1);

        GameState state = new GameState(width, height);

        state.getGrid()[0][0].setType(TileType.CASTLE);
        state.getGrid()[0][0].setOwnerId(1);
        state.getGrid()[width - 1][height - 1].setType(TileType.CASTLE);
        state.getGrid()[width - 1][height - 1].setOwnerId(2);

        int area = width * height;
        int numVillages = Math.max(MIN_VILLAGES, area / 30);

        List<int[]> specialTiles = new ArrayList<>();
        specialTiles.add(new int[]{0, 0});
        specialTiles.add(new int[]{width - 1, height - 1});

        int placedVillages = 0;
        while (placedVillages < numVillages) {
            int[] pos = findVillagePosition(state, width, height, specialTiles);
            if (pos == null) {
                break;
            }
            state.getGrid()[pos[0]][pos[1]].setType(TileType.VILLAGE);
            specialTiles.add(pos);
            placedVillages++;
        }

        state.getArmiesInternal().add(new Army(0, 0, 10, 1));
        state.getArmiesInternal().add(new Army(width - 1, height - 1, 10, 2));

        return state;
    }

    private int[] findVillagePosition(GameState state, int width, int height, List<int[]> specialTiles) {
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);

            if (state.getGrid()[x][y].getType() != TileType.EMPTY) {
                continue;
            }

            if (hasMinDistance(x, y, specialTiles)) {
                return new int[]{x, y};
            }
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (state.getGrid()[x][y].getType() == TileType.EMPTY && hasMinDistance(x, y, specialTiles)) {
                    return new int[]{x, y};
                }
            }
        }

        return null;
    }

    private boolean hasMinDistance(int x, int y, List<int[]> specialTiles) {
        for (int[] tile : specialTiles) {
            int distance = Math.abs(x - tile[0]) + Math.abs(y - tile[1]);
            if (distance < MIN_DISTANCE_BETWEEN_SPECIALS) {
                return false;
            }
        }
        return true;
    }
}
