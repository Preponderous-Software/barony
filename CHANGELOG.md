# Changelog

All notable changes to the Barony Prototype MVP are documented in this file.

## [Unreleased]

### Authentication (UserAuth integration)

- ✅ Player accounts via the standalone [UserAuth](https://github.com/Preponderous-Software/UserAuth) service (registration, login, logout)
- ✅ Register and login screens in the web client; logout revokes the token server-side
- ✅ Login issues a signed JWT (stored client-side) sent as `Authorization: Bearer <token>` on game requests
- ✅ Backend proxies `/api/auth/register`, `/api/auth/login`, `/api/auth/logout` to UserAuth and validates the token on every authenticated request
- ✅ Per-player game endpoints (`/api/session/*`) reject missing, invalid, expired, or revoked tokens with `401`
- ✅ Game state is keyed by the authenticated username instead of an anonymous session id
- ✅ `docker-compose` now starts UserAuth and its Postgres alongside Barony (configurable via `JWT_SECRET`, `USERAUTH_PATH`, `ALLOWED_ORIGINS`)

## [MVP v1.0.0] - 2026-02-11

### Core Game Features

#### Army Movement & Pathfinding
- ✅ Armies move 1 tile per tick toward destinations using Manhattan distance pathfinding
- ✅ Horizontal movement prioritized first, then vertical movement
- ✅ Movement visualization with destination indicators (light blue for Player 1, light red for Player 2)
- ✅ Movement cancellation via new MOVE commands
- ✅ Stable army identification using unique IDs (AtomicInteger-based)

#### Army Management & Composition
- ✅ Army splitting: divide armies into multiple units with SPLIT command
- ✅ Automatic army merging: friendly armies at same location merge automatically
- ✅ Minimum army size validation (1 soldier minimum per army)
- ✅ Visual soldier count display with dot patterns (up to 10 dots shown)
- ✅ Multiple army rendering with circular offsets

#### Territory Control & Village Mechanics
- ✅ Village ownership system (neutral, Player 1, or Player 2 owned)
- ✅ Persistent ownership: villages retain ownership until captured
- ✅ Village capture occurs after combat resolution
- ✅ Contested villages become neutral if multiple players occupy after combat
- ✅ Color-coded villages: blue tint (Player 1), red tint (Player 2), brown (neutral)
- ✅ Income system: owned villages generate +1 soldier/tick for armies on village

#### Castle Capture & Win Conditions
- ✅ Castle ownership system with initial Player 1/Player 2 assignments
- ✅ Castle capture requires 3 consecutive ticks of enemy occupation
- ✅ Capture progress tracking with visual progress bars
- ✅ Victory condition: capture all enemy castles
- ✅ Defeat condition: lose all your castles
- ✅ Game over state with win/loss overlay
- ✅ Game reset functionality via POST /api/reset endpoint

#### AI Opponent System
- ✅ Rule-based AI controlling Player 2 armies
- ✅ Priority-based decision making:
  1. Defend owned villages from nearby threats (within 3 tiles)
  2. Capture neutral villages (nearest first, if safe)
  3. Attack weak enemy positions (1.5x force multiplier required)
  4. Attack enemy castle (2x force multiplier required)
  5. Build up forces if no better action available
- ✅ Strategic validation: AI never makes suicidal attacks
- ✅ Multi-army coordination for simultaneous objective captures
- ✅ AI enabled by default with configurable difficulty

#### Ruler Decision System (CK-Lite Layer)
- ✅ Three policy categories with three options each:
  - **Economic Policies**: Heavy Taxation (+20% income/-10% stability), Balanced Budget (neutral), Infrastructure Investment (-10% income/+10% stability)
  - **Military Policies**: Aggressive Training (+10% morale/-5% loyalty), Standard Service (neutral), Veteran Benefits (-10% morale/+10% loyalty)
  - **Population Policies**: Growth Focus (+15% growth/-5% stability), Stable Population (neutral), Quality Over Quantity (-10% growth/+10% stability)
- ✅ Village stats: stability (affects soldier generation), population (affects generation capacity)
- ✅ Army stats: morale (affects combat effectiveness), loyalty (affects desertion rate)
- ✅ Gradual stat recovery/decay: stability (±2/tick), morale (±1/tick), loyalty (±2/tick)
- ✅ Policy cooldown: 15-tick minimum between changes
- ✅ Player 1 exclusive: AI does not use ruler decisions
- ✅ Policy UI with keyboard controls (P to open, E/M/O for categories, 1-3 for choices)

### User Interface & Experience

#### Mouse Controls
- ✅ Left-click army selection with glowing highlight effect
- ✅ Left-click tile (with army selected) to move army
- ✅ Right-click to deselect army
- ✅ Hover tooltips with 500ms delay showing tile/army information

#### Visual Feedback
- ✅ Pulsing selection highlights (blue for Player 1, orange for contested)
- ✅ Movement preview: faint ghost circle at destination
- ✅ Capture progress bars on contested castles (red fill)
- ✅ Color-coded ownership indicators for all tiles

#### HUD Elements
- ✅ Top bar: tick count, armies, castles, villages, income per tick (with proportional bars)
- ✅ Side panel: selected army details (ID, player, soldiers, position, destination)
- ✅ Bottom bar: game event log showing last 10 actions
- ✅ Ruler stats panel: displays current policies, cooldown, stability, morale, loyalty, population

#### Keyboard Controls
- ✅ SPACE - Advance game by one tick
- ✅ M - Move first army to (5,5)
- ✅ 1-4 - Move first army to preset locations
- ✅ S - Split army (interactive console input)
- ✅ P - Open policy menu
- ✅ R - Reset game (when game over)
- ✅ ESC - Close window

### Backend API

#### REST Endpoints
- ✅ GET /state - Retrieve current game state
- ✅ POST /tick - Advance game by one tick
- ✅ POST /command - Send MOVE or SPLIT command
- ✅ POST /api/reset - Reset game to initial state
- ✅ POST /api/decision - Change ruler policy (with cooldown)
- ✅ GET /api/ruler-stats - Get realm statistics

#### Game Mechanics
- ✅ Thread-safe game state management with synchronized access
- ✅ Integer-based combat with simultaneous damage
- ✅ Village soldier generation based on population and stability
- ✅ Combat strength modified by morale (Player 1)
- ✅ Desertion mechanics based on loyalty (Player 1)
- ✅ Population growth system (1% base per tick, policy-modified)

### Technical Features

#### Web Client
- ✅ Spring Boot + Thymeleaf web application
- ✅ HTML5 Canvas rendering
- ✅ RESTful backend communication
- ✅ UTF-8 encoding for all network communication

#### Backend
- ✅ Spring Boot REST API (Java 17)
- ✅ In-memory game state (stateless server)
- ✅ Defensive copying for thread safety
- ✅ CORS configuration for localhost origins
- ✅ Comprehensive test suite with unit and integration tests

### Testing

#### Test Coverage
- ✅ Backend: Model tests, game service tests, and integration scenarios
- ✅ Web Client: Tests for all game entities
- ✅ All tests passing

#### Test Coverage
- ✅ Army movement and pathfinding
- ✅ Combat mechanics and army removal
- ✅ Village soldier generation with ownership
- ✅ Castle capture with 3-tick timer
- ✅ Win/loss conditions
- ✅ Army splitting and merging
- ✅ Ruler policy effects and stat recovery
- ✅ AI decision making

### Documentation
- ✅ Comprehensive README.md (745 lines)
- ✅ Detailed MVP.md planning document (568 lines)
- ✅ Policy UI guide (POLICY_UI_GUIDE.md)
- ✅ Quick start scripts (Unix and Windows)
- ✅ CI/CD pipeline with GitHub Actions

### Balance Parameters

#### Soldier Generation
- Base generation: population / 100 (villages with pop < 100 generate 0/tick)
- Player 1 modifier: (base * stability + 50) / 100
- Player 2 (AI): base generation only

#### Castle Capture
- Timer: 3 consecutive ticks of enemy occupation
- Progress resets if: multiple players present or friendly army occupies

#### AI Force Multipliers
- Castle attack threshold: 2x enemy force
- Village attack threshold: 1.5x enemy force
- Defensive range: 3 tiles

#### Stat Recovery/Decay Rates
- Stability: ±2 points/tick toward policy-modified target (max 110)
- Morale: ±1 point/tick toward policy-modified target
- Loyalty: ±2 points/tick toward policy-modified target (max 110)
- Population growth: 1% base/tick, modified by policy (±15%, -10%, 0%)

#### Desertion Rate
- Formula: max(0, (100 - loyalty) / 20)%
- Loyalty ≥ 100 results in 0% desertion
- Applied per tick to army soldier counts

#### Combat
- Base: 1 soldier kills 1 enemy soldier (simultaneous damage)
- Morale modifier (Player 1): strength = (soldiers * morale + 50) / 100

### Performance Characteristics
- 60 FPS rendering target
- Efficient O(n) army merging with grouped processing
- Cached HUD statistics to avoid per-frame recalculation
- Batched text rendering for optimal performance
- Optimized grid rendering with minimal state changes

### Known Limitations
- Single-player only (no multiplayer/networking)
- No save/load functionality (in-memory state only)
- No fog of war or limited visibility
- No terrain effects (mountains, rivers, etc.)
- No sound effects or music
- Console input for army splitting (blocks render loop temporarily)
- Headless environments require X11/Wayland for UI rendering
- No advanced AI difficulty levels (single balanced difficulty)

### Out of Scope (Future Versions)
This MVP explicitly excludes:
- ❌ Multiple unit types (knights, archers, etc.)
- ❌ Technology/research tree
- ❌ Dynasties, succession, or character development
- ❌ Diplomacy system (alliances, treaties)
- ❌ Narrative events or story elements
- ❌ Religion or culture systems
- ❌ Advanced graphics (animations, particles, 3D models)
- ❌ Modding support
- ❌ Localization

---

## Development Notes

### Repository
- GitHub: dmccoystephenson/barony-prototype
- License: Stephenson-NC (See LICENSE.md)
- Java Version: 17
- Build Tool: Maven 3.x with wrapper included

### Quick Start
```bash
# Backend
cd backend && ./mvnw spring-boot:run

# Web Client (separate terminal)
cd web-client && ./mvnw spring-boot:run
```

### Contributing
This is an MVP prototype. See TICKETS.md for planned enhancements.

---

**Last Updated:** 2026-02-11
