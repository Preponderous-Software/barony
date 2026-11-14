package com.barony.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Chooses where new games' maps come from. With {@code barony.map.realms} unset (the default)
 * every new game gets a fresh random map, exactly as before. Set it to a realms map file, to
 * {@code classpath:<resource>}, or to {@code example} for the bundled example island, and every
 * new game is that map instead (see docs/REAL_MAPS.md). A map that cannot be read stops the
 * backend from starting rather than quietly falling back to random maps.
 */
@Configuration
public class MapSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(MapSourceConfig.class);

    static final String EXAMPLE = "classpath:maps/example/realms.json";

    @Bean
    public MapSource mapSource(@Value("${barony.map.realms:}") String realms,
                               @Value("${barony.map.package:}") String packageDir,
                               @Value("${barony.map.players:}") String players) throws IOException {
        if (realms == null || realms.isBlank()) {
            return new MapGenerator();
        }
        String source = realms.trim().equals("example") ? EXAMPLE : realms.trim();
        List<String> chosen = players == null || players.isBlank() ? List.of()
                : Arrays.stream(players.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        RealmsMap map = source.startsWith("classpath:")
                ? RealmsMap.fromClasspath(source.substring("classpath:".length()), chosen)
                : RealmsMap.load(Path.of(source), packageDir == null || packageDir.isBlank() ? null : Path.of(packageDir), chosen);
        log.info("New games use the real map {}: {}x{}, {} against {}, {} seat(s) left off for room",
                source, map.getWidth(), map.getHeight(), map.getPlayerRealms().get(0), map.getPlayerRealms().get(1),
                map.getDropped());
        return map;
    }
}
