package com.barony.backend.service;

import com.barony.backend.model.GameState;

/**
 * Where a new game's map comes from. {@link MapGenerator} (a fresh random map each game) is the
 * default; {@link RealmsMap} builds every new game from a realms map file and a world package.
 * {@link MapSourceConfig} picks one from the {@code barony.map.realms} setting.
 */
public interface MapSource {
    /** A new game: its map, its two players' starting armies, and nothing played yet. */
    GameState generate();
}
