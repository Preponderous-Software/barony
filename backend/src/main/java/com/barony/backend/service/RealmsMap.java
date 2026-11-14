package com.barony.backend.service;

import com.barony.backend.model.Army;
import com.barony.backend.model.GameState;
import com.barony.backend.model.TileType;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A map built from a real place: a realms map file (who rules which towns, castles and villages,
 * at which coordinates) and a world package (rasters of the land those coordinates sit on). The
 * formats are described in docs/REAL_MAPS.md.
 *
 * Every seat is scaled down onto a grid no bigger than a random map's ({@link MapGenerator#MAX_SIZE}
 * on its longer side). Two realms play: the player's (the file's {@code default_realm}, else the
 * largest) and the largest other one, unless {@code players} names both. Their towns and castles
 * become their castles, their villages their villages, and each starts with one army at its first
 * town. Every other realm's seats become neutral villages, so the game stays a two-player game.
 * Each tile also records the ground most of its land is (snow, sand, forest...), which the
 * battlefield draws.
 *
 * The file and package are read once; each {@link #generate()} is a fresh copy of the same map.
 */
public final class RealmsMap implements MapSource {

    static final int MAX_SIDE = MapGenerator.MAX_SIZE;
    static final int MIN_SIDE = MapGenerator.MIN_SIZE;
    static final int START_SOLDIERS = 10;
    /** A castle that lands on a taken tile moves to the nearest free one at most this far away. */
    static final int CASTLE_NUDGE = 2;
    /** A village is left off when another castle or village is closer than this (in steps). */
    static final int VILLAGE_SPACING = 2;
    /** Space kept around the outermost seats, as a share of the larger span. */
    private static final double MARGIN = 0.06;

    /** Opens a file of the world package by its name (world.json, ground.png). */
    interface PackageFiles {
        InputStream open(String name) throws IOException;
    }

    private record Seat(String name, String kind, double x, double z) {
        boolean fortified() { return kind.equals("town") || kind.equals("castle"); }
    }

    private record Realm(String key, int slot, List<Seat> seats) { }

    private final int width;
    private final int height;
    private final TileType[][] types;
    private final int[][] owners;
    private final String[][] grounds;
    private final int[][] capitals = new int[2][];
    private final List<String> playerRealms = new ArrayList<>();
    private int dropped;

    /**
     * Read a realms map file and its world package from disk. With {@code packageDir} null the
     * package is the file's {@code map.package}, taken relative to the realms file when relative.
     */
    public static RealmsMap load(Path realmsFile, Path packageDir, List<String> players) throws IOException {
        JsonNode realms = readJson(Files.newInputStream(realmsFile));
        Path dir = packageDir;
        if (dir == null) {
            String named = realms.path("map").path("package").asText("");
            if (named.isEmpty()) {
                throw new IllegalArgumentException("The realms map names no world package (map.package); set barony.map.package");
            }
            Path parent = realmsFile.toAbsolutePath().getParent();
            dir = parent.resolve(named);
        }
        Path base = dir;
        return new RealmsMap(realms, name -> Files.newInputStream(base.resolve(name)), players);
    }

    /** Read a realms map file bundled with the backend, its package relative to it. */
    public static RealmsMap fromClasspath(String realmsResource, List<String> players) throws IOException {
        ClassLoader loader = RealmsMap.class.getClassLoader();
        JsonNode realms = readJson(openResource(loader, realmsResource));
        int slash = realmsResource.lastIndexOf('/');
        String parent = slash < 0 ? "" : realmsResource.substring(0, slash + 1);
        String base = parent + realms.path("map").path("package").asText("package") + "/";
        return new RealmsMap(realms, name -> openResource(loader, base + name), players);
    }

    private static InputStream openResource(ClassLoader loader, String name) throws IOException {
        InputStream in = loader.getResourceAsStream(name);
        if (in == null) {
            throw new FileNotFoundException("No bundled map file " + name);
        }
        return in;
    }

    private static JsonNode readJson(InputStream in) throws IOException {
        // The format's own examples carry // notes, so comments are allowed.
        JsonMapper mapper = JsonMapper.builder().enable(JsonReadFeature.ALLOW_JAVA_COMMENTS).build();
        try (in) {
            return mapper.readTree(in);
        }
    }

    RealmsMap(JsonNode realmsSpec, PackageFiles files, List<String> players) throws IOException {
        List<Realm> realms = readRealms(realmsSpec);
        List<Realm> playing = choosePlayers(realms, realmsSpec.path("default_realm").asText(""), players);
        for (Realm realm : playing) {
            playerRealms.add(realm.key());
        }

        JsonNode world = readJson(files.open("world.json"));
        JsonNode grid = world.path("grid");
        double originX = grid.path("origin_x").asDouble();
        double originZ = grid.path("origin_z").asDouble();
        double bpp = grid.path("blocks_per_px").asDouble(1);
        List<String> classes = new ArrayList<>();
        world.path("classes").forEach(c -> classes.add(c.asText()));
        Raster ground = readGround(files, world.path("files").path("ground").asText("ground.png"), grid);

        // The grid covers the seats' extent plus a margin, its longer side MAX_SIDE tiles.
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Realm realm : realms) {
            for (Seat s : realm.seats()) {
                minX = Math.min(minX, s.x()); maxX = Math.max(maxX, s.x());
                minZ = Math.min(minZ, s.z()); maxZ = Math.max(maxZ, s.z());
            }
        }
        double span = Math.max(Math.max(maxX - minX, maxZ - minZ), 1);
        double pad = span * MARGIN;
        minX -= pad; maxX += pad; minZ -= pad; maxZ += pad;
        double spanX = maxX - minX, spanZ = maxZ - minZ;
        if (spanX >= spanZ) {
            width = MAX_SIDE;
            height = clamp((int) Math.round(MAX_SIDE * spanZ / spanX), MIN_SIDE, MAX_SIDE);
        } else {
            height = MAX_SIDE;
            width = clamp((int) Math.round(MAX_SIDE * spanX / spanZ), MIN_SIDE, MAX_SIDE);
        }
        double cellX = spanX / width, cellZ = spanZ / height;

        types = new TileType[width][height];
        owners = new int[width][height];
        grounds = new String[width][height];
        String[][] landGrounds = new String[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                types[x][y] = TileType.EMPTY;
                int px0 = (int) Math.floor((minX + x * cellX - originX) / bpp);
                int px1 = (int) Math.ceil((minX + (x + 1) * cellX - originX) / bpp);
                int pz0 = (int) Math.floor((minZ + y * cellZ - originZ) / bpp);
                int pz1 = (int) Math.ceil((minZ + (y + 1) * cellZ - originZ) / bpp);
                grounds[x][y] = majorityGround(ground, classes, px0, px1, pz0, pz1, false);
                landGrounds[x][y] = majorityGround(ground, classes, px0, px1, pz0, pz1, true);
            }
        }

        // Placement order decides who keeps a crowded tile: the capitals, the players' other
        // castles, the players' villages, then everyone else's seats.
        for (int p = 0; p < 2; p++) {
            Seat capital = capitalOf(playing.get(p));
            capitals[p] = placeCastle(cellOf(capital.x(), minX, cellX, width), cellOf(capital.z(), minZ, cellZ, height), p + 1);
        }
        for (int p = 0; p < 2; p++) {
            Seat capital = capitalOf(playing.get(p));
            for (Seat s : playing.get(p).seats()) {
                if (s.fortified() && s != capital) {
                    int[] at = placeCastle(cellOf(s.x(), minX, cellX, width), cellOf(s.z(), minZ, cellZ, height), p + 1);
                    if (at == null) dropped++;
                }
            }
        }
        for (int p = 0; p < 2; p++) {
            for (Seat s : playing.get(p).seats()) {
                if (!s.fortified()) {
                    placeVillage(cellOf(s.x(), minX, cellX, width), cellOf(s.z(), minZ, cellZ, height), p + 1);
                }
            }
        }
        for (Realm realm : realms) {
            if (playing.contains(realm)) continue;
            for (Seat s : realm.seats()) {
                placeVillage(cellOf(s.x(), minX, cellX, width), cellOf(s.z(), minZ, cellZ, height), 0);
            }
        }

        // A coastal seat's tile may be mostly sea; it is fought on its land.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (types[x][y] != TileType.EMPTY && landGrounds[x][y] != null) {
                    grounds[x][y] = landGrounds[x][y];
                }
            }
        }
    }

    @Override
    public GameState generate() {
        GameState state = new GameState(width, height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                state.getGrid()[x][y].setType(types[x][y]);
                state.getGrid()[x][y].setOwnerId(owners[x][y]);
                state.getGrid()[x][y].setGround(grounds[x][y]);
            }
        }
        for (int p = 0; p < 2; p++) {
            state.getArmiesInternal().add(new Army(capitals[p][0], capitals[p][1], START_SOLDIERS, p + 1));
        }
        state.setUnitTypes(true);
        return state;
    }

    /** The keys of the two realms that play: the player's, then the opponent's. */
    public List<String> getPlayerRealms() {
        return List.copyOf(playerRealms);
    }

    public int getWidth() { return width; }

    public int getHeight() { return height; }

    /** Seats left off the map because their tile, or every tile near it, was already taken. */
    public int getDropped() { return dropped; }

    // ------------------------------------------------------------------ reading

    private static List<Realm> readRealms(JsonNode spec) {
        List<Realm> realms = new ArrayList<>();
        for (JsonNode r : spec.path("realms")) {
            List<Seat> seats = new ArrayList<>();
            for (JsonNode s : r.path("seats")) {
                String kind = s.path("kind").asText("").toLowerCase(Locale.ROOT);
                // Claims only shape a realm's borders; Barony has no borders, so they are skipped.
                if (!kind.equals("town") && !kind.equals("castle") && !kind.equals("village")) continue;
                if (!s.path("x").isNumber() || !s.path("z").isNumber()) {
                    throw new IllegalArgumentException("Seat " + s.path("name").asText("?") + " has no x and z");
                }
                seats.add(new Seat(s.path("name").asText(""), kind, s.path("x").asDouble(), s.path("z").asDouble()));
            }
            String key = r.path("key").asText("");
            if (key.isEmpty()) {
                throw new IllegalArgumentException("A realm has no key");
            }
            realms.add(new Realm(key, r.path("slot").asInt(realms.size() + 1), seats));
        }
        return realms;
    }

    private static List<Realm> choosePlayers(List<Realm> realms, String defaultRealm, List<String> players) {
        List<Realm> chosen = new ArrayList<>();
        if (players != null && !players.isEmpty()) {
            if (players.size() != 2) {
                throw new IllegalArgumentException("Name exactly two realms to play, not " + players);
            }
            for (String key : players) {
                chosen.add(realms.stream().filter(r -> r.key().equals(key)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No realm with key " + key)));
            }
            if (chosen.get(0) == chosen.get(1)) {
                throw new IllegalArgumentException("The two players must be different realms");
            }
        } else {
            // Largest first; a tie goes to the lower slot.
            List<Realm> bySize = new ArrayList<>(realms);
            bySize.removeIf(r -> r.seats().stream().noneMatch(Seat::fortified));
            bySize.sort(Comparator.comparingInt((Realm r) -> -r.seats().size()).thenComparingInt(Realm::slot));
            bySize.stream().filter(r -> r.key().equals(defaultRealm)).findFirst().ifPresent(chosen::add);
            for (Realm r : bySize) {
                if (chosen.size() == 2) break;
                if (!chosen.contains(r)) chosen.add(r);
            }
            if (chosen.size() < 2) {
                throw new IllegalArgumentException("A real map needs two realms that each hold a town or castle");
            }
        }
        for (Realm r : chosen) {
            capitalOf(r);
        }
        return chosen;
    }

    /** A realm's first town, else its first castle. */
    private static Seat capitalOf(Realm realm) {
        return realm.seats().stream().filter(s -> s.kind().equals("town")).findFirst()
                .or(() -> realm.seats().stream().filter(s -> s.kind().equals("castle")).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("Realm " + realm.key() + " has no town or castle"));
    }

    private static Raster readGround(PackageFiles files, String name, JsonNode grid) throws IOException {
        BufferedImage image;
        try (InputStream in = files.open(name)) {
            image = ImageIO.read(in);
        }
        if (image == null) {
            throw new IOException("The world package's " + name + " is not a readable PNG");
        }
        int w = grid.path("width").asInt(image.getWidth()), h = grid.path("height").asInt(image.getHeight());
        if (image.getWidth() != w || image.getHeight() != h) {
            throw new IOException(name + " is " + image.getWidth() + "x" + image.getHeight()
                    + " but world.json's grid is " + w + "x" + h);
        }
        return image.getRaster();
    }

    /** The commonest ground class among the pixels, ignoring unsurveyed (class 0) and, when asked, water. */
    private static String majorityGround(Raster ground, List<String> classes, int px0, int px1, int pz0, int pz1, boolean landOnly) {
        int[] counts = new int[classes.size()];
        int x0 = Math.max(0, px0), x1 = Math.min(ground.getWidth(), Math.max(px1, px0 + 1));
        int z0 = Math.max(0, pz0), z1 = Math.min(ground.getHeight(), Math.max(pz1, pz0 + 1));
        for (int j = x0; j < x1; j++) {
            for (int i = z0; i < z1; i++) {
                int c = ground.getSample(j, i, 0);
                if (c <= 0 || c >= counts.length) continue;
                if (landOnly && "water".equals(classes.get(c))) continue;
                counts[c]++;
            }
        }
        int best = 0;
        for (int c = 1; c < counts.length; c++) {
            if (counts[c] > counts[best]) best = c;
        }
        return counts[best] > 0 ? classes.get(best) : null;
    }

    // ------------------------------------------------------------------ placing

    private static int cellOf(double v, double min, double cell, int n) {
        return clamp((int) Math.floor((v - min) / cell), 0, n - 1);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int[] placeCastle(int x, int y, int owner) {
        for (int d = 0; d <= CASTLE_NUDGE; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int sign : new int[]{-1, 1}) {
                    int dy = sign * (d - Math.abs(dx));
                    int cx = x + dx, cy = y + dy;
                    if (cx >= 0 && cx < width && cy >= 0 && cy < height && types[cx][cy] == TileType.EMPTY) {
                        types[cx][cy] = TileType.CASTLE;
                        owners[cx][cy] = owner;
                        return new int[]{cx, cy};
                    }
                    if (dy == 0) break;
                }
            }
        }
        if (owner != 0 && capitals[owner - 1] == null) {
            throw new IllegalArgumentException("No room on the map for realm " + playerRealms.get(owner - 1) + "'s capital");
        }
        return null;
    }

    private void placeVillage(int x, int y, int owner) {
        for (int dx = -(VILLAGE_SPACING - 1); dx <= VILLAGE_SPACING - 1; dx++) {
            for (int dy = -(VILLAGE_SPACING - 1 - Math.abs(dx)); dy <= VILLAGE_SPACING - 1 - Math.abs(dx); dy++) {
                int cx = x + dx, cy = y + dy;
                if (cx >= 0 && cx < width && cy >= 0 && cy < height && types[cx][cy] != TileType.EMPTY) {
                    dropped++;
                    return;
                }
            }
        }
        types[x][y] = TileType.VILLAGE;
        owners[x][y] = owner;
    }
}
