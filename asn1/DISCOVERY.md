# ASN.1 Discovery Report

## 1. Wire Format

**Serialization mechanism:** Standard Spring Boot REST with Jackson JSON serialization.

All communication between the web-client and backend is over HTTP REST endpoints.
The web-client's `BackendService.java` uses Spring `RestTemplate` to make HTTP calls
to the backend. The frontend JavaScript (in `game.html`) uses `fetch()` API with
JSON bodies.

- **Unit of data:** JSON objects (Java POJOs serialized by Jackson)
- **Schema:** No machine-readable schema. Field names and types are enforced only by
  Java class definitions (Lombok `@Getter`/`@Setter` annotations on POJOs).
- **Hand-rolled parser:** None. Standard Jackson deserialization handles everything.
- **Transport:** HTTP with `Content-Type: application/json`
- **Session management:** `X-Session-Id` HTTP header (UUID string)

### Serialization files found

| File | Purpose |
|------|---------|
| `backend/src/main/java/com/barony/backend/model/GameState.java` | Uses `@JsonIgnore` on `getArmiesInternal()` |
| `backend/src/main/java/com/barony/backend/model/Session.java` | Contains `toString()` for logging |

No custom `fromString`, `deserialize`, `encode/decode`, `marshal/unmarshal`,
`toBytes/fromBytes`, `toJson/fromJson`, or `toXml/fromXml` methods were found.

### Network boundary files

| File | Purpose |
|------|---------|
| `web-client/.../service/BackendService.java` | `RestTemplate` HTTP client with JSON bodies |
| `web-client/.../controller/WebController.java` | Proxies frontend requests to backend |
| `backend/.../controller/AuthController.java` | Login endpoint |
| `backend/.../controller/GameController.java` | All game REST endpoints |

## 2. Message Vocabulary

### REST API Endpoints (the message types)

| Method | Path | Request Body Type | Response Type |
|--------|------|-------------------|---------------|
| POST | `/api/auth/login` | `{username: String}` | `Session` |
| GET | `/state` | — | `GameState` |
| POST | `/tick` | — | `GameState` |
| POST | `/command` | `Command` | `GameState` |
| POST | `/api/reset` | — | `GameState` |
| POST | `/api/decision` | `RulerDecision` | `GameState` |
| GET | `/api/ruler-stats` | — | `RulerStats` |
| GET | `/api/session/state` | — (header) | `GameState` |
| POST | `/api/session/tick` | — (header) | `GameState` |
| POST | `/api/session/command` | `Command` (header) | `GameState` |
| POST | `/api/session/reset` | — (header) | `GameState` |
| POST | `/api/session/decision` | `RulerDecision` (header) | `GameState` |
| GET | `/api/session/ruler-stats` | — (header) | `RulerStats` |

### Model types (the data structures)

| Type | File | Fields |
|------|------|--------|
| `Command` | `model/Command.java` | type (String), armyId (int), targetX (int), targetY (int), splitAmount (int) |
| `GameState` | `model/GameState.java` | grid (Tile[][]), armies (List\<Army\>), tickCount (int), gameOver (boolean), winnerId (Integer), aiEnabled (boolean), economicPolicy (String), militaryPolicy (String), populationPolicy (String), lastPolicyChangeTick (int) |
| `Army` | `model/Army.java` | id (int), x (int), y (int), soldiers (int), playerId (int), destinationX (Integer), destinationY (Integer), morale (int), loyalty (int) |
| `Tile` | `model/Tile.java` | type (TileType), ownerId (int), occupationTicks (int), stability (int), population (int) |
| `TileType` | `model/TileType.java` | CASTLE, VILLAGE, EMPTY |
| `Session` | `model/Session.java` | sessionId (UUID), username (String), gameState (GameState), lastAccessed (LocalDateTime) |
| `RulerDecision` | `model/RulerDecision.java` | category (PolicyCategory), choice (String) |
| `RulerStats` | `model/RulerStats.java` | averageStability (double), averageMorale (double), averageLoyalty (double), totalPopulation (int), economicPolicy (String), militaryPolicy (String), populationPolicy (String), ticksUntilNextDecision (int) |

### Command types (String values for Command.type)
- `"MOVE"` — Move army to target position
- `"SPLIT"` — Split army into two groups

### Policy categories (RulerDecision.PolicyCategory enum)
- `ECONOMIC` → choices: `HEAVY_TAXATION`, `BALANCED_BUDGET`, `INFRASTRUCTURE_INVESTMENT`
- `MILITARY` → choices: `AGGRESSIVE_TRAINING`, `STANDARD_SERVICE`, `VETERAN_BENEFITS`
- `POPULATION` → choices: `GROWTH_FOCUS`, `STABLE_POPULATION`, `QUALITY_OVER_QUANTITY`

### TileType enum values
- `CASTLE`, `VILLAGE`, `EMPTY`

## 3. Existing Documentation

Documentation files that mention protocol/API/messages:
- `README.md` — Backend API section with REST endpoint docs
- `DOCS.md` — Documentation index
- `MVP.md` — MVP specification with implementation details
- `PLAYER_GUIDE.md` — Player guide

**No existing schema files found:**
- No `.proto` files
- No `.thrift` files
- No `.avsc` (Avro) files
- No OpenAPI/Swagger specs
- No `.xsd` or `.wsdl` files

**Conclusion:** No machine-readable schema exists. ASN.1 is appropriate.

## 4. Call Sites

### Message construction sites

| File | Line | Pattern |
|------|------|---------|
| `web-client/.../service/BackendService.java` | 33 | `new HttpEntity<>(request, headers)` — constructs HTTP requests with Map bodies |
| `backend/.../controller/AuthController.java` | 26 | `new ResponseStatusException(...)` — error response |
| `backend/.../controller/GameController.java` | 52-193 | Multiple `new ResponseStatusException(...)` — error responses |

### Message parsing sites

No explicit parse/decode/deserialize calls found. All deserialization is handled
automatically by Spring/Jackson from `@RequestBody` annotations on controller methods.

### Frontend fetch calls (game.html)

| Action | Endpoint | Body |
|--------|----------|------|
| Login | `POST /api/auth/login` | `{username}` |
| Get state | `GET /api/session/state` | — |
| Advance turn | `POST /api/session/tick` | — |
| Reset game | `POST /api/session/reset` | — |
| Change policy | `POST /api/session/decision` | `{category, choice}` |
| Get ruler stats | `GET /api/session/ruler-stats` | — |
| Move army | `POST /api/session/command` | `{type:"MOVE", armyId, targetX, targetY}` |
| Split army | `POST /api/session/command` | `{type:"SPLIT", armyId, splitAmount}` |

## 5. Tier Selection

### Selected: Tier A — Schema Only

**Justification:**

1. **No hand-rolled parser exists.** The codebase uses standard Spring Boot with
   Jackson JSON serialization. There is no fragile, custom wire format to replace.

2. **The language is Java.** While Java has JNI/JNA for C FFI, the existing
   serialization (Jackson) is robust and well-tested. Replacing it with a C-based
   ASN.1 codec (Tier B) would add complexity without clear benefit.

3. **The primary gap is documentation.** There is no machine-readable schema
   documenting the data structures exchanged between client and server. The ASN.1
   schema fills this gap.

4. **The message objects are typed.** Each model (Command, GameState, Army, etc.) is
   a Java class with defined fields and types. They are not stringly-typed HashMaps.
   Tier C is therefore not applicable.

### What Tier B or C would require (TODO)

- **Tier B** would require building `asn1c`-generated C code into a shared library
  and calling it via JNI/JNA from the Spring Boot application. This would replace
  Jackson serialization with XER encoding, which is non-standard for REST APIs and
  would break the web-client JavaScript frontend.

- **Tier C** would require the existing models to be untyped (e.g., `Map<String,String>`).
  They are already typed Java classes, so Tier C is not applicable.
