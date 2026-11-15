# Real maps

By default every new game gets a fresh random map. A server can instead start every new game on a
real map, built from two inputs:

- a **realms map file** (JSON): the realms, and the towns, castles and villages each one holds, at
  their coordinates in the world;
- a **world package**: a folder describing the land itself as rasters (heightmap, ground classes)
  over the same coordinates.

The backend reads both once at startup and scales the realms' seats down onto a grid no larger than a
random map's (at most 20 tiles on its longer side, at least 10 on its shorter). An example island
ships with the backend.

![The bundled example island as a new game: sea at the corners, a sandy rim, a lake in the west and a stony, snow-capped hill in the east; the player's three castles and three villages on the west side, the AI's on the east around the hill, and the two other realms' seats as neutral villages to the north and south](images/real-map-example.png)

<sub>Captured from a guest game on the backend and web client jars, run locally with `BARONY_MAP_REALMS=example`, using headless Chromium, on 2026-11-14.</sub>

## Turning it on

| Setting (environment variable) | Meaning |
|---|---|
| `barony.map.realms` (`BARONY_MAP_REALMS`) | Unset or empty: random maps (the default). `example`: the bundled example island. A file path, or `classpath:<resource>`: that realms map file. |
| `barony.map.package` (`BARONY_MAP_PACKAGE`) | The world package folder. Optional: by default it is the realms file's `map.package`, resolved against the realms file's folder when relative. |
| `barony.map.players` (`BARONY_MAP_PLAYERS`) | Optional: two realm keys, `player,opponent`. By default the player is the file's `default_realm` (or the largest realm), and the opponent is the largest other realm. |

```bash
BARONY_MAP_REALMS=example ./start-backend.sh
BARONY_MAP_REALMS=/maps/isle/realms.json BARONY_MAP_PLAYERS=westmarch,highcairn ./start-backend.sh
```

A map that cannot be read, or names a realm that cannot play, stops the backend at startup with the
reason. It never falls back to random maps without saying so. Games already saved keep the map they
were started on. The setting only decides the map of the next new game (a new account or guest, or
**New Game**).

## How the map is built

- **Players.** Barony is a two-player game, and it stays one on a real map. Two realms play: the
  player (player 1) and the AI (player 2). Each needs at least one town or castle. Its first town
  (else its first castle) is its capital, where its one starting army of 10 soldiers stands.
- **Seats.** A playing realm's towns and castles become its castles, and its villages become its
  villages. Every other realm's towns, castles and villages become **neutral villages**, there to be
  taken. Claims (`kind: "claim"`) and any other kinds are skipped.
- **Scaling.** The grid covers the box around every seat, plus a 6% margin, and keeps its proportions.
  Grid `x` runs east and grid `y` runs south, so north is at the top of the map.
- **Crowding.** When two seats fall on the same tile, the capitals go first, then the players' other
  castles, then the players' villages, then the neutral seats. A castle whose tile is taken moves to
  the nearest free tile within 2 steps. A village is left off if another castle or village is on its
  tile or next to it. The startup log reports how many seats were left off.
- **Ground.** Each tile records the commonest ground class (`plain`, `forest`, `snow`, `sand`,
  `stone`, `badland`, `ice`, `water`, `built`, `farm`) among the pixels it covers. Castles and
  villages use the commonest *land* class, so a coastal seat is not fought at sea. The ground
  appears in the game state as `tile.ground`, tints the map faintly, and sets how the 3D battlefield
  looks: the colour of the field, how many trees stand around it, and a word in the battle hint
  ("Snow."). It does not change any rule or any battle's outcome. Random maps
  have no ground, and their saves are unchanged.

## Realms map file

Plain JSON. `//` comments are allowed. Only these fields are read, and any others are ignored:

```json
{
  "map": {"package": "package"},
  "default_realm": "westmarch",
  "realms": [
    {
      "slot": 1, "key": "westmarch", "name": "March of the West",
      "seats": [
        {"name": "Greywater", "kind": "town", "x": 300, "z": 520},
        {"name": "Thornhold", "kind": "castle", "x": 340, "z": 330},
        {"name": "Reedby", "kind": "village", "x": 250, "z": 455},
        {"name": "the western shore", "kind": "claim", "x": 220, "z": 520}
      ]
    }
  ]
}
```

- `map.package`: the world package folder (see `barony.map.package`).
- `default_realm`: the key of the realm the player plays, if it can.
- `realms[].key` (required) and `realms[].slot` (breaks ties between equally large realms; lower wins).
- `realms[].seats[]`: `kind` is `town`, `castle`, `village` or `claim`. `x` (east) and `z` (south)
  are world coordinates in the package's units (blocks). A realm's size is its number of towns,
  castles and villages.

## World package

A folder holding at least `world.json` and the ground raster:

```json
{
  "grid": {"origin_x": 0, "origin_z": 0, "blocks_per_px": 4, "width": 256, "height": 256, "y_offset": 1024},
  "classes": ["void", "water", "plain", "forest", "snow", "sand", "stone", "badland", "ice", "lava", "built", "farm"],
  "files": {"height": "height.png", "ground": "ground.png"}
}
```

- Pixel (row `i`, column `j`) covers world `x` from `origin_x + j·blocks_per_px` and `z` from
  `origin_z + i·blocks_per_px`, each `blocks_per_px` wide.
- `ground.png` (8-bit greyscale, `width` × `height`): each pixel is an index into `classes`. Index 0
  means not surveyed and is ignored. The backend reads this file, and a raster of the wrong size is
  refused.
- `height.png` (16-bit greyscale): the surface height plus `y_offset`, with 0 meaning not surveyed.
  It is part of the format, but Barony does not read it yet.

The bundled example lives in `backend/src/main/resources/maps/example/`. It is a synthetic island
(a hill with a snowy peak, a lake and a sandy rim) with four invented realms. Two of them play, and
the other two give the map its neutral villages.

## Not yet

- **More than two players.** The other realms are neutral villages because the game has exactly two
  sides: one player and one AI. The win check, the AI and the respawns all assume two. Letting every
  realm play would need N-player turns and N AIs.
- **Height.** `height.png` could give the battlefield real rises, and hard going across mountains on
  the map.
- **Choosing a map per game.** The map is a server setting. A player cannot pick one when starting a
  game.
