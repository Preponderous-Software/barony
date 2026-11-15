package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.Tile;
import com.barony.backend.model.TileType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RealmsMapTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CLASSES = "[\"void\",\"water\",\"plain\",\"forest\",\"snow\",\"sand\"]";

    // ---------------------------------------------------------------- the bundled example

    @Test
    void exampleIslandLoadsAsATwoPlayerMapWithinTheRandomMapsSize() throws IOException {
        RealmsMap map = RealmsMap.fromClasspath("maps/example/realms.json", List.of());
        GameState state = map.generate();

        assertTrue(state.getWidth() >= MapGenerator.MIN_SIZE && state.getWidth() <= MapGenerator.MAX_SIZE);
        assertTrue(state.getHeight() >= MapGenerator.MIN_SIZE && state.getHeight() <= MapGenerator.MAX_SIZE);
        // The file's default realm plays, against the largest other realm.
        assertEquals(List.of("westmarch", "highcairn"), map.getPlayerRealms());

        int[] castles = new int[3];
        int neutralVillages = 0;
        for (Tile[] col : state.getGrid()) {
            for (Tile t : col) {
                if (t.getType() == TileType.CASTLE) castles[t.getOwnerId()]++;
                if (t.getType() == TileType.VILLAGE && t.getOwnerId() == 0) neutralVillages++;
                assertTrue(t.getOwnerId() >= 0 && t.getOwnerId() <= 2);
            }
        }
        assertEquals(0, castles[0], "only the two players hold castles");
        assertEquals(3, castles[1], "westmarch's town and two castles");
        assertEquals(3, castles[2], "highcairn's town and two castles");
        assertTrue(neutralVillages > 0, "the other realms' seats are neutral villages");
        assertTrue(state.getUnitTypes());

        // One army each, standing on its own capital.
        assertEquals(2, state.getArmies().size());
        for (Army army : state.getArmies()) {
            Tile under = state.getGrid()[army.getX()][army.getY()];
            assertEquals(TileType.CASTLE, under.getType());
            assertEquals(army.getPlayerId(), under.getOwnerId());
            assertEquals(RealmsMap.START_SOLDIERS, army.getSoldiers());
        }
    }

    @Test
    void exampleIslandCarriesItsGroundOntoTheTiles() throws IOException {
        GameState state = RealmsMap.fromClasspath("maps/example/realms.json", List.of()).generate();
        boolean water = false, plain = false;
        for (Tile[] col : state.getGrid()) {
            for (Tile t : col) {
                assertNotNull(t.getGround(), "the island's survey covers the whole map");
                water |= t.getGround().equals("water");
                plain |= t.getGround().equals("plain");
                if (t.getType() != TileType.EMPTY) {
                    assertNotEquals("water", t.getGround(), "a seat is fought on its land");
                }
            }
        }
        assertTrue(water && plain);
    }

    @Test
    void eachGameIsAFreshCopyOfTheSameMap() throws IOException {
        RealmsMap map = RealmsMap.fromClasspath("maps/example/realms.json", List.of());
        GameState a = map.generate();
        a.getGrid()[0][0].setOwnerId(2);
        GameState b = map.generate();
        for (int x = 0; x < b.getWidth(); x++) {
            for (int y = 0; y < b.getHeight(); y++) {
                if (x == 0 && y == 0) continue;
                assertEquals(a.getGrid()[x][y].getType(), b.getGrid()[x][y].getType());
                assertEquals(a.getGrid()[x][y].getGround(), b.getGrid()[x][y].getGround());
            }
        }
        assertEquals(0, b.getGrid()[0][0].getOwnerId());
        assertNotEquals(a.getArmies().get(0).getId(), b.getArmies().get(0).getId());
    }

    @Test
    void namedPlayersOverrideTheDefaultChoice() throws IOException {
        RealmsMap map = RealmsMap.fromClasspath("maps/example/realms.json", List.of("saltreach", "southfold"));
        assertEquals(List.of("saltreach", "southfold"), map.getPlayerRealms());
        assertThrows(IllegalArgumentException.class,
                () -> RealmsMap.fromClasspath("maps/example/realms.json", List.of("saltreach", "nowhere")));
        assertThrows(IllegalArgumentException.class,
                () -> RealmsMap.fromClasspath("maps/example/realms.json", List.of("saltreach")));
        assertThrows(IllegalArgumentException.class,
                () -> RealmsMap.fromClasspath("maps/example/realms.json", List.of("saltreach", "saltreach")));
    }

    @Test
    void aRealMapGameIsPlayedByTheEngineLikeAnyOther() throws IOException {
        GameService game = new GameService();
        game.setMapSource(RealmsMap.fromClasspath("maps/example/realms.json", List.of()));
        GameState start = game.getState();
        assertEquals(2, start.getArmies().size());
        for (int turn = 0; turn < 300 && !game.getState().isGameOver(); turn++) {
            game.tick();
        }
        // The neutral villages the other realms left behind get taken.
        assertTrue(countNeutralVillages(game.getState()) < countNeutralVillages(start));
        game.resetGame();
        assertEquals(start.getWidth(), game.getState().getWidth(), "New Game starts on the real map again");
        assertEquals(start.getGrid()[2][9].getType(), game.getState().getGrid()[2][9].getType());
    }

    // ---------------------------------------------------------------- small hand-made maps

    @Test
    void seatsScaleOntoTheGridWithNorthAtTheTopAndClaimsSkipped() throws IOException {
        // A 40x20-block world (one block per pixel), sea on the left half, snow on the right.
        Map<String, byte[]> pkg = pkg(40, 20, (x, z) -> x < 20 ? 1 : 4);
        JsonNode spec = JSON.readTree("""
            {"realms": [
              {"slot": 1, "key": "a", "seats": [
                {"name": "A", "kind": "town", "x": 2, "z": 2},
                {"name": "claim", "kind": "claim", "x": 0, "z": 0}]},
              {"slot": 2, "key": "b", "seats": [
                {"name": "B", "kind": "castle", "x": 38, "z": 18},
                {"name": "Bv", "kind": "village", "x": 30, "z": 10}]}]}
            """);
        RealmsMap map = new RealmsMap(spec, open(pkg), List.of());
        GameState state = map.generate();

        // Wider than tall: 20 across, and the shorter side never below a random map's minimum.
        assertEquals(RealmsMap.MAX_SIDE, state.getWidth());
        assertEquals(RealmsMap.MIN_SIDE, state.getHeight());
        // b is larger, so it is player 1 (no default realm named).
        assertEquals(List.of("b", "a"), map.getPlayerRealms());
        // a's town is at the north-west corner, b's castle at the south-east (a margin is kept).
        Army aArmy = state.getArmies().stream().filter(x -> x.getPlayerId() == 2).findFirst().orElseThrow();
        Army bArmy = state.getArmies().stream().filter(x -> x.getPlayerId() == 1).findFirst().orElseThrow();
        assertTrue(aArmy.getX() <= 1 && aArmy.getY() <= 1, "a's town at the top left");
        assertTrue(bArmy.getX() >= 18 && bArmy.getY() >= 8, "b's castle at the bottom right");
        assertEquals("snow", state.getGrid()[bArmy.getX()][bArmy.getY()].getGround());
        assertEquals("water", state.getGrid()[3][5].getGround());
        long villages = countType(state, TileType.VILLAGE);
        assertEquals(1, villages);
    }

    @Test
    void crowdedCastlesMoveAsideAndCrowdedVillagesAreLeftOff() throws IOException {
        Map<String, byte[]> pkg = pkg(100, 100, (x, z) -> 2);
        JsonNode spec = JSON.readTree("""
            {"realms": [
              {"slot": 1, "key": "a", "seats": [
                {"name": "A", "kind": "town", "x": 0, "z": 0},
                {"name": "A2", "kind": "castle", "x": 1, "z": 1},
                {"name": "Av", "kind": "village", "x": 2, "z": 0}]},
              {"slot": 2, "key": "b", "seats": [
                {"name": "B", "kind": "town", "x": 100, "z": 100}]}]}
            """);
        RealmsMap map = new RealmsMap(spec, open(pkg), List.of());
        GameState state = map.generate();
        assertEquals(3, countType(state, TileType.CASTLE), "the second castle moved to a free tile");
        assertEquals(0, countType(state, TileType.VILLAGE), "the village beside two castles was left off");
        assertEquals(1, map.getDropped());
    }

    @Test
    void aRealmWithoutATownOrCastleCannotPlay() throws IOException {
        Map<String, byte[]> pkg = pkg(10, 10, (x, z) -> 2);
        JsonNode spec = JSON.readTree("""
            {"realms": [
              {"slot": 1, "key": "a", "seats": [{"name": "A", "kind": "town", "x": 0, "z": 0}]},
              {"slot": 2, "key": "b", "seats": [{"name": "Bv", "kind": "village", "x": 9, "z": 9}]}]}
            """);
        assertThrows(IllegalArgumentException.class, () -> new RealmsMap(spec, open(pkg), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RealmsMap(spec, open(pkg), List.of("a", "b")));
    }

    @Test
    void aGroundRasterOfTheWrongSizeIsRefused() throws IOException {
        Map<String, byte[]> pkg = pkg(10, 10, (x, z) -> 2);
        pkg.put("ground.png", png(5, 5, (x, z) -> 2));
        JsonNode spec = JSON.readTree("""
            {"realms": [
              {"slot": 1, "key": "a", "seats": [{"name": "A", "kind": "town", "x": 0, "z": 0}]},
              {"slot": 2, "key": "b", "seats": [{"name": "B", "kind": "town", "x": 9, "z": 9}]}]}
            """);
        assertThrows(IOException.class, () -> new RealmsMap(spec, open(pkg), List.of()));
    }

    @Test
    void loadsFromDiskWithThePackageNamedInTheFile(@TempDir Path dir) throws IOException {
        Path pkgDir = Files.createDirectories(dir.resolve("island"));
        for (Map.Entry<String, byte[]> e : pkg(10, 10, (x, z) -> 5).entrySet()) {
            Files.write(pkgDir.resolve(e.getKey()), e.getValue());
        }
        Path realms = dir.resolve("realms.json");
        Files.writeString(realms, """
            {"map": {"package": "island"}, // where the rasters are
             "realms": [
              {"slot": 1, "key": "a", "seats": [{"name": "A", "kind": "town", "x": 0, "z": 0}]},
              {"slot": 2, "key": "b", "seats": [{"name": "B", "kind": "town", "x": 9, "z": 9}]}]}
            """);
        GameState state = RealmsMap.load(realms, null, List.of()).generate();
        assertEquals("sand", state.getGrid()[10][10].getGround());
        assertEquals(2, countType(state, TileType.CASTLE));
    }

    @Test
    void randomMapsStayTheDefault() throws IOException {
        MapSourceConfig config = new MapSourceConfig();
        assertInstanceOf(MapGenerator.class, config.mapSource("", "", ""));
        assertInstanceOf(RealmsMap.class, config.mapSource("example", "", ""));
        GameState random = config.mapSource("", "", "").generate();
        for (Tile[] col : random.getGrid()) {
            for (Tile t : col) {
                assertNull(t.getGround(), "a random map has no ground");
            }
        }
    }

    @Test
    void groundRoundTripsThroughASaveAndIsLeftOutWhenAbsent() throws IOException {
        GameState real = RealmsMap.fromClasspath("maps/example/realms.json", List.of()).generate();
        String json = JSON.writeValueAsString(real);
        assertTrue(json.contains("\"ground\":\"plain\""));
        GameState back = JSON.copy()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(json, GameState.class);
        assertEquals(real.getGrid()[3][4].getGround(), back.getGrid()[3][4].getGround());
        assertEquals(real.getGrid()[3][4].getGround(), real.createSnapshot().getGrid()[3][4].getGround());

        String random = JSON.writeValueAsString(new MapGenerator(7).generate());
        assertFalse(random.contains("ground"), "a random map's save is unchanged");
    }

    // ---------------------------------------------------------------- helpers

    private interface Classes { int at(int x, int z); }

    private static long countNeutralVillages(GameState state) {
        long n = 0;
        for (Tile[] col : state.getGrid()) for (Tile t : col) if (t.getType() == TileType.VILLAGE && t.getOwnerId() == 0) n++;
        return n;
    }

    private static long countType(GameState state, TileType type) {
        long n = 0;
        for (Tile[] col : state.getGrid()) for (Tile t : col) if (t.getType() == type) n++;
        return n;
    }

    /** A world package of w x h pixels at one block per pixel, origin (0, 0). */
    private static Map<String, byte[]> pkg(int w, int h, Classes classes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        files.put("world.json", ("{\"grid\": {\"origin_x\": 0, \"origin_z\": 0, "
                + "\"blocks_per_px\": 1, \"width\": " + w + ", \"height\": " + h + "}, \"classes\": " + CLASSES
                + ", \"files\": {\"ground\": \"ground.png\"}}").getBytes());
        files.put("ground.png", png(w, h, classes));
        return files;
    }

    private static byte[] png(int w, int h, Classes classes) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int x = 0; x < w; x++) for (int z = 0; z < h; z++) img.getRaster().setSample(x, z, 0, classes.at(x, z));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static RealmsMap.PackageFiles open(Map<String, byte[]> files) {
        return name -> {
            byte[] bytes = files.get(name);
            if (bytes == null) throw new FileNotFoundException(name);
            return new ByteArrayInputStream(bytes);
        };
    }
}
