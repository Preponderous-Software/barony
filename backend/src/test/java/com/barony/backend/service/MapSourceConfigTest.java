package com.barony.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How the barony.map.realms / .package / .players settings choose where new games' maps come from
 * (see docs/REAL_MAPS.md). The default and plain {@code example} cases are also covered in
 * {@link RealmsMapTest#randomMapsStayTheDefault()}.
 */
class MapSourceConfigTest {

    private static final String EXAMPLE_DIR = "maps/example/";

    private final MapSourceConfig config = new MapSourceConfig();

    // ---------------------------------------------------------------- barony.map.realms

    @Test
    void aBlankRealmsSettingMeansRandomMaps() throws IOException {
        assertInstanceOf(MapGenerator.class, config.mapSource("   ", "", ""));
        assertInstanceOf(MapGenerator.class, config.mapSource(null, null, null));
    }

    @Test
    void exampleIsTrimmedAndPicksTheBundledIsland() throws IOException {
        RealmsMap map = assertInstanceOf(RealmsMap.class, config.mapSource("  example  ", "", ""));
        assertEquals(List.of("westmarch", "highcairn"), map.getPlayerRealms());
    }

    @Test
    void aClasspathResourceIsReadFromTheBackendsOwnFiles() throws IOException {
        RealmsMap map = assertInstanceOf(RealmsMap.class,
                config.mapSource("classpath:" + EXAMPLE_DIR + "realms.json", "", ""));
        assertEquals(List.of("westmarch", "highcairn"), map.getPlayerRealms());
    }

    @Test
    void theBundledMapsIgnoreThePackageSetting() throws IOException {
        // The package beside the bundled realms file is always used, even when one is configured.
        assertInstanceOf(RealmsMap.class, config.mapSource("example", "/no/such/package", ""));
        assertInstanceOf(RealmsMap.class,
                config.mapSource("classpath:" + EXAMPLE_DIR + "realms.json", "/no/such/package", ""));
    }

    @Test
    void aMissingMapStopsStartupInsteadOfFallingBackToRandomMaps(@TempDir Path dir) {
        assertThrows(FileNotFoundException.class,
                () -> config.mapSource("classpath:maps/nowhere/realms.json", "", ""));
        assertThrows(NoSuchFileException.class,
                () -> config.mapSource(dir.resolve("missing.json").toString(), "", ""));
    }

    // ---------------------------------------------------------------- barony.map.package

    @Test
    void aFilePathFindsThePackageTheRealmsFileNames(@TempDir Path dir) throws IOException {
        Path realms = copyExample(dir, dir.resolve("package"));
        RealmsMap map = assertInstanceOf(RealmsMap.class, config.mapSource(realms.toString(), "", ""));
        assertEquals(List.of("westmarch", "highcairn"), map.getPlayerRealms());
        assertNotNull(map.generate());
    }

    @Test
    void thePackageSettingOverridesThePackageTheFileNames(@TempDir Path dir) throws IOException {
        // The file names "package", which is not there; the configured folder is used instead.
        Path elsewhere = dir.resolve("elsewhere");
        Path realms = copyExample(dir, elsewhere);
        assertFalse(Files.exists(dir.resolve("package")));
        assertInstanceOf(RealmsMap.class, config.mapSource(realms.toString(), elsewhere.toString(), ""));
        assertThrows(NoSuchFileException.class, () -> config.mapSource(realms.toString(), "", ""));
    }

    @Test
    void aFileNamingNoPackageNeedsThePackageSetting(@TempDir Path dir) throws IOException {
        Path realms = copyExample(dir, dir.resolve("package"));
        String named = Files.readString(realms);
        String unnamed = named.replace("{\"package\": \"package\"}", "{}");
        assertNotEquals(named, unnamed, "the example's map.package entry was found and removed");
        Files.writeString(realms, unnamed);
        assertThrows(IllegalArgumentException.class, () -> config.mapSource(realms.toString(), "  ", ""));
        assertInstanceOf(RealmsMap.class, config.mapSource(realms.toString(), dir.resolve("package").toString(), ""));
    }

    // ---------------------------------------------------------------- barony.map.players

    @Test
    void playersAreSplitOnCommasWithSpacesAndEmptyEntriesIgnored() throws IOException {
        RealmsMap map = assertInstanceOf(RealmsMap.class,
                config.mapSource("example", "", " saltreach , ,southfold, "));
        assertEquals(List.of("saltreach", "southfold"), map.getPlayerRealms());
    }

    @Test
    void blankPlayersKeepTheFilesOwnChoice() throws IOException {
        RealmsMap map = assertInstanceOf(RealmsMap.class, config.mapSource("example", "", "  "));
        assertEquals(List.of("westmarch", "highcairn"), map.getPlayerRealms());
    }

    @Test
    void namingOnlyOnePlayerStopsStartup() {
        assertThrows(IllegalArgumentException.class, () -> config.mapSource("example", "", "saltreach"));
        assertThrows(IllegalArgumentException.class, () -> config.mapSource("example", "", "saltreach,nowhere"));
    }

    // ---------------------------------------------------------------- helpers

    /** Copies the bundled example's realms file into {@code dir} and its package into {@code packageDir}. */
    private static Path copyExample(Path dir, Path packageDir) throws IOException {
        Files.createDirectories(packageDir);
        for (String name : List.of("world.json", "ground.png", "height.png")) {
            copyResource(EXAMPLE_DIR + "package/" + name, packageDir.resolve(name));
        }
        Path realms = dir.resolve("realms.json");
        copyResource(EXAMPLE_DIR + "realms.json", realms);
        return realms;
    }

    private static void copyResource(String resource, Path to) throws IOException {
        try (InputStream in = MapSourceConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            Files.copy(in, to);
        }
    }
}
