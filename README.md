# Barony Prototype

**Version:** 1.0.0 (MVP Complete)  
**Status:** ✅ Production Ready

A minimal client/server game prototype with a Java Spring Boot backend and Java LWJGL frontend. This is a single-player strategy game where you control your armies against AI-controlled enemy Lords.

**🎉 MVP Features Complete:**
- ✅ Army movement with Manhattan pathfinding
- ✅ Army splitting and automatic merging
- ✅ Territory control with village ownership
- ✅ Castle capture mechanics (3-tick timer)
- ✅ Win/loss conditions
- ✅ AI opponent with priority-based decision making
- ✅ Mouse and keyboard controls with HUD
- ✅ Ruler decision system (CK-lite policies)

## Project Structure

- `backend/` - Spring Boot REST API server (owns all game logic)
- `frontend/` - LWJGL client application (renders game state and sends commands)

## Backend

### Features
- 2D grid (10x10)
- Tile types: CASTLE, VILLAGE, EMPTY
- **Territory Control System**: Villages and castles can be owned by players
- Villages generate 1 soldier per tick for armies of the owning player
- Integer-based combat (armies reduce each other's soldiers equally)
- In-memory game state
- Thread-safe game state management with synchronized access
- Stable army identification using unique IDs (not list indices)
- **Army Movement System**: Armies move 1 tile per tick using Manhattan distance pathfinding
- **AI Opponent System**: Rule-based AI controls Player 2 armies with intelligent decision-making

### REST Endpoints
- `GET /state` - Get current game state (returns JSON with grid, armies, tick count, gameOver, winnerId)
- `POST /tick` - Advance game by one tick (executes game loop, returns updated state)
- `POST /command` - Send a command (currently supports MOVE command with army ID and target coordinates)
- `POST /api/reset` - Reset the game to initial state (useful after game over)

### Command Structure
```json
{
  "type": "MOVE",
  "armyId": 1,
  "targetX": 5,
  "targetY": 5
}
```

**Split Command:**
```json
{
  "type": "SPLIT",
  "armyId": 1,
  "splitAmount": 5
}
```

### State Response Structure
```json
{
  "grid": [[{"type": "CASTLE", "ownerId": 1, "occupationTicks": 0}, ...], ...],
  "armies": [
    {
      "id": 1, 
      "x": 0, 
      "y": 0, 
      "soldiers": 10, 
      "playerId": 1,
      "destinationX": 5,
      "destinationY": 5
    },
    {"id": 2, "x": 9, "y": 9, "soldiers": 10, "playerId": 2}
  ],
  "tickCount": 0,
  "width": 10,
  "height": 10,
  "gameOver": false,
  "winnerId": null
}
```

**Note**: 
- `destinationX` and `destinationY` are optional fields. When set, they indicate the army is moving toward that destination.
- `ownerId` indicates tile ownership: 0=neutral, 1=player1, 2=player2
- `occupationTicks` indicates castle capture progress (0-3, only relevant for castles)
- `gameOver` is true when the game has ended
- `winnerId` is null during play, set to winning player ID (1 or 2) when game ends
- `aiEnabled` is a boolean indicating whether the AI opponent is active (true by default)

### AI Opponent

The game features a rule-based AI that controls Player 2 armies with intelligent decision-making:

**AI Behavior:**
- **Priority-Based Decision Making**: The AI evaluates all idle armies each tick and assigns them actions based on the following priorities:
  1. **Defend Territory** (Priority 1): Moves armies to defend owned villages if enemy forces are within 3 tiles
  2. **Capture Neutral Villages** (Priority 2): Targets the nearest neutral village if it's safe to do so (no strong enemy forces nearby)
  3. **Attack Weak Positions** (Priority 3): Attacks enemy-owned villages when the AI has superior force (1.5x enemy strength)
  4. **Attack Enemy Castle** (Priority 4): Moves to attack the enemy castle only with overwhelming force (2x enemy strength)
  5. **Build Up Forces** (Priority 5): If no better action is available, armies stay put to accumulate soldiers from village generation

**AI Strategy:**
- Never makes suicidal attacks against superior enemy forces
- Considers both army strength and positioning when making decisions
- Can coordinate multiple armies to capture different objectives simultaneously
- Validates all moves to ensure they're within map bounds
- Only controls Player 2 armies (Player 1 is controlled by the player)
- **Note**: Army splitting functionality is not yet implemented in the AI decision engine (planned for future enhancement)

**Configuration:**
- AI is enabled by default but can be disabled via the `aiEnabled` flag in the game state
- AI difficulty can be adjusted by modifying force multipliers or decision priorities in `GameService.java`

**Technical Details:**
- AI execution occurs after village soldier generation and before combat resolution
- This ensures AI decisions account for current turn's village income
- Both players have equal army spawning constraints (initial army + village income only)

### Ruler Decision System (CK-Lite Layer)

The Ruler Decision System adds a lightweight policy-based strategic layer to the game, allowing Player 1 to make choices that affect their realm through mechanical modifiers. This "CK-lite" layer focuses on system-driven outcomes without dynasties, diplomacy, or narrative elements.

**Policy Categories:**

The system includes three policy categories, each with three options:

1. **Economic Policies** (affects village income and stability):
   - `HEAVY_TAXATION`: +20% income, -10% stability
   - `BALANCED_BUDGET`: No modifiers (baseline)
   - `INFRASTRUCTURE_INVESTMENT`: -10% income, +10% stability

2. **Military Policies** (affects army morale and loyalty):
   - `AGGRESSIVE_TRAINING`: +10% morale, -5% loyalty
   - `STANDARD_SERVICE`: No modifiers (baseline)
   - `VETERAN_BENEFITS`: -10% morale (less aggressive), +10% loyalty

3. **Population Policies** (affects village population growth and stability):
   - `GROWTH_FOCUS`: +15% population growth, -5% stability
   - `STABLE_POPULATION`: No modifiers (baseline)
   - `QUALITY_OVER_QUANTITY`: -10% population growth, +10% stability

**Stat Mechanics:**

The system tracks several new statistics for Player 1 entities:

- **Villages:**
  - `stability` (0-110, clamped): Affects soldier generation efficiency. Formula: `base generation * (stability / 100)`. Policies can raise stability above 100 (up to 110 cap).
  - `population` (current population): Sets the base soldier generation rate. Baseline formula: `base_generation = population / 100` (before stability and policy modifiers)
  
- **Armies:**
  - `morale` (0-200): Affects combat effectiveness. Formula: `strength * (morale / 100)`
  - `loyalty` (0-110, clamped): Affects desertion rate. Formula: `max(0, (100 - loyalty) / 20)`% per tick, so any loyalty ≥ 100 results in 0% desertion

**Stat Recovery/Decay:**

Stats gradually drift toward policy-modified baselines (not always 100%):
- Stability moves toward policy-modified target (100 + economic modifier + population modifier) at 2 points per tick, capped at 110
- Morale moves toward policy-modified target (100 + military modifier) at 1 point per tick
- Loyalty moves toward policy-modified target (100 + military modifier) at 2 points per tick, capped at 110

**Policy Mechanics:**

- Policy effects are **continuous** (not one-time bonuses) and last until the policy changes
- Policy effects start influencing stats on the **next game tick** after a policy is changed (stats are updated during `tick()`, not instantly)
- There is a **15-tick cooldown** between policy changes to prevent rapid switching exploits
- All calculations use integer math with rounding for soldier generation
- **AI (Player 2) does not use ruler decisions** - this is a Player 1 only feature

**API Endpoints:**

- `POST /api/decision` - Change a policy
  ```json
  {
    "category": "ECONOMIC",
    "choice": "HEAVY_TAXATION"
  }
  ```
  Returns updated game state. Fails if cooldown is active.

- `GET /api/ruler-stats` - Get realm statistics
  ```json
  {
    "averageStability": 95.0,
    "averageMorale": 110.0,
    "averageLoyalty": 95.0,
    "totalPopulation": 200,
    "economicPolicy": "HEAVY_TAXATION",
    "militaryPolicy": "AGGRESSIVE_TRAINING",
    "populationPolicy": "STABLE_POPULATION",
    "ticksUntilNextDecision": 5
  }
  ```

**Strategy Examples:**

- **Aggressive Expansion**: Use `AGGRESSIVE_TRAINING` for combat bonus, combine with `HEAVY_TAXATION` for rapid army growth. Monitor loyalty to prevent desertion.
- **Defensive Consolidation**: Use `VETERAN_BENEFITS` for high loyalty, `INFRASTRUCTURE_INVESTMENT` for stable villages. Slower growth but very stable.
- **Balanced Growth**: Keep default policies (`BALANCED_BUDGET`, `STANDARD_SERVICE`, `STABLE_POPULATION`) for steady, predictable gameplay.

**How to Change Policies (In-Game UI):**

1. **Press 'P'** to open the policy menu (displays in center of screen)
2. **Select a category**:
   - Press **'E'** for Economic policies
   - Press **'M'** for Military policies
   - Press **'O'** for pOpulation policies
3. **Select a policy**:
   - Press **'1'** for the first option (e.g., Heavy Taxation, Aggressive Training, Growth Focus)
   - Press **'2'** for the second option (e.g., Balanced Budget, Standard Service, Stable Population)
   - Press **'3'** for the third option (e.g., Infrastructure Investment, Veteran Benefits, Quality Over Quantity)
4. The menu closes automatically after selection and the policy takes effect on the next tick
5. Check the "Ruler Stats" panel on the right side to see current policies and cooldown status
6. Wait for the 15-tick cooldown before changing policies again

**Note:** The policy menu displays all available policies with their effects. Policies on cooldown cannot be changed until the timer expires.

**Scope Clarification:**

This is a "CK-lite" system focused on mechanical modifiers, **not** including:
- ❌ Dynasties or family trees
- ❌ Diplomacy or alliances
- ❌ Characters with traits or skills
- ❌ Events or narrative elements
- ❌ Religion or culture systems

It **does** include:
- ✅ Policy-based strategic choices
- ✅ Stat-driven mechanical effects
- ✅ Economic and military trade-offs
- ✅ Gradual stat changes and recovery
- ✅ Cooldown-based decision pacing

### Running the Backend

**Option 1: Using Maven Wrapper (no Maven installation required)**
```bash
cd backend
./mvnw spring-boot:run
```

**Option 2: Using installed Maven**
```bash
cd backend
mvn spring-boot:run
```

Server will start on http://localhost:8080

## Frontend

### Features
- Renders 10x10 grid using LWJGL (cross-platform support for Linux, macOS, Windows)
- Visual representation:
  - **Castles**: Gray base with colored outline (blue for Player 1, red for Player 2)
  - **Villages**: 
    - Neutral: Brown
    - Player 1 owned: Brown with blue tint
    - Player 2 owned: Brown with red tint
  - **Empty tiles**: Green
  - **Armies**:
    - Blue circles: Player 1 armies
    - Red circles: Player 2 armies
- **Mouse Controls**: Click to select and move armies with visual feedback
- **HUD Display**: Real-time game statistics, selected army info, and game event log
- **Tooltips**: Hover over tiles and armies to see detailed information
- **Territory Statistics Display**: Window title shows castles owned, villages owned, and income per tick for each player
- HTTP client with proper timeouts and error handling
- UTF-8 encoding for all network communication

### Controls

**Mouse Controls:**
- `Left Click` on army - Select Player 1 army (shows glowing highlight)
- `Left Click` on tile (with army selected) - Move selected army to that location
- `Right Click` - Deselect currently selected army
- `Hover` over tile/army - Show tooltip after 0.5s delay with:
  - Tile type and ownership (displayed with color-coded text labels)
  - Army information (player, soldier count, movement status with text labels)

**Keyboard Controls:**
- `SPACE` - Send tick command to server
- `M` - Move first army to position (5,5) using its unique ID
- `1` - Move first army to Player 1 castle (0,0)
- `2` - Move first army to Player 2 castle (9,9)
- `3` - Move first army to village (3,3)
- `4` - Move first army to village (6,6)
- `S` - Split first army (prompts for soldier count in console)
- `R` - Play again (reset game) - only available when game is over
- `ESC` - Close window

### UI Elements

**HUD Layout:**
- **Top Bar** (dark background, 15% screen height):
  - Displays tick count, armies, castles, and villages for both players
  - Text labels show exact counts for Player 1 and Player 2
  - Proportional colored bars visualize distribution between players
  - Income per tick displayed (based on village count)
  
- **Side Panel** (right side, 30% screen width):
  - Shows selected army details when an army is selected
  - Displays: Army ID, Player, Soldier count, Position
  - Shows destination coordinates if army is moving
  - Visual bars indicate soldier count
  
- **Bottom Bar** (30% screen height):
  - Game event log showing last 10 actions with text messages
  - Color-coded entry bars (alternating for visibility)
  - Events include: army selections, movement commands, tick updates
  - Real-time text updates show game actions
  
- **Game Area** (center):
  - Main grid display (70% screen width, 55% screen height)
  - Selection highlight: Pulsing colored border around selected army
  - Movement preview: Faint circle showing where army will move on click
  - Tooltips: Semi-transparent box with text labels showing:
    - Tile position, type, and ownership
    - Army ID, player, soldier count, and movement status
    - Village income information

> **Note:** UI screenshots require a running display server (X11/Wayland) for rendering. The application uses LWJGL for OpenGL rendering, which requires a graphical environment. In headless environments, the UI cannot be displayed or captured.

### Running the Frontend

**Important:** Start the backend first!

**Option 1: Using Maven Wrapper (no Maven installation required)**
```bash
cd frontend
./mvnw compile exec:java
```

**Option 2: Using installed Maven**
```bash
cd frontend
mvn compile exec:java
```

## Quick Start Scripts

### Unix/Linux/macOS

**Start Backend:**
```bash
./start-backend.sh
```

**Start Frontend:**
```bash
./start-frontend.sh
```

**Run API Demo:**
```bash
./demo-api.sh
```

### Windows

**Start Backend:**
```batch
start-backend.bat
```

**Start Frontend (requires X server like VcXsrv if using WSL):**
```batch
start-frontend.bat
```

**Note:** For WSL users, you'll need an X server running on Windows (e.g., VcXsrv, Xming) since WSL doesn't have native display support. The batch script provides a native Windows alternative.

## Command Examples

### Move Command (via REST API)
```bash
# Move army by ID (sets destination - army will move gradually over multiple ticks)
# Use the army's unique ID from /state endpoint
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"type":"MOVE","armyId":1,"targetX":5,"targetY":5}'
```

**Note:** The MOVE command sets the army's destination but doesn't move it instantly. The army will move 1 tile per tick toward the destination.

### Split Command (via REST API)
```bash
# Split army by ID (creates a new army at the same location)
# Use the army's unique ID from /state endpoint
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"type":"SPLIT","armyId":1,"splitAmount":5}'
```

**Note:** The SPLIT command creates a new army at the same location with the specified number of soldiers. The original army's soldier count is reduced accordingly. Both armies must have at least 1 soldier after the split.

### Tick Command
```bash
curl -X POST http://localhost:8080/tick
```

### Get State
```bash
curl http://localhost:8080/state
```

### Reset Game
```bash
curl -X POST http://localhost:8080/api/reset
```

**Note:** The reset endpoint reinitializes the entire game state, including:
- Resets tick count to 0
- Restores initial armies at starting positions
- Resets castle ownership (Player 1 at (0,0), Player 2 at (9,9))
- Clears game over status
- Useful after a game ends to start a new match

## Development

Both projects use Maven and Java 17. Maven Wrapper (mvnw) is included, so Maven installation is optional.

### Build Backend
```bash
cd backend
./mvnw clean package  # or: mvn clean package if Maven is installed
```

### Build Frontend
```bash
cd frontend
./mvnw clean package  # or: mvn clean package if Maven is installed
```

### Run Tests
```bash
# Backend tests
cd backend
./mvnw test  # or: mvn test

# Frontend tests
cd frontend
./mvnw test  # or: mvn test
```

### Continuous Integration
GitHub Actions workflow automatically runs on pull requests to `main` or `develop`:
- Builds both backend and frontend
- Runs all unit and integration tests
- Packages applications
- Uses JDK 17 with Maven caching for faster builds

Test coverage includes:
- **Backend**: Model tests, service tests (game mechanics, movement, territory control, castle capture, win conditions), and integration tests
- **Frontend**: Model tests for all game entities

## Game Rules

1. **Initial Setup:** Two castles at (0,0) and (9,9), two villages at (3,3) and (6,6)
2. **Players:** Player 1 (human, blue armies) starts at (0,0); Player 2 (AI/enemy, red armies) starts at (9,9)
3. **Starting Forces:** Each player starts with an army of 10 soldiers at their castle
4. **Movement:** Armies move 1 tile per tick toward their destination using Manhattan distance pathfinding
5. **Territory Control:** Villages can be captured by occupying them with an army
6. **Soldier Generation:** Each tick, armies positioned on villages owned by their player gain 1 soldier
7. **Combat:** When armies of different players occupy the same tile, combat occurs
8. **Combat Resolution:** Each army's soldier count is reduced by the opponent's soldier count (simultaneous damage)
9. **Army Removal:** Armies with 0 or fewer soldiers are removed from the game
10. **Castle Capture:** Enemy army must occupy a castle for 3 consecutive ticks to capture it
11. **Win Condition:** Player wins by capturing all enemy castles
12. **Loss Condition:** Player loses when they have no castles remaining

## Territory Control

The game features a territory control system where tiles can be owned by players:

### Ownership Mechanics
- **Initial Ownership:**
  - Player 1's castle at (0,0) starts owned by Player 1
  - Player 2's castle at (9,9) starts owned by Player 2
  - Villages start neutral (ownerId = 0)
  
- **Village Capture:**
  - Village capture occurs **after combat resolution** to ensure only surviving armies can claim territory
  - When a single player's army occupies a village after combat, it captures the village for that player
  - **Contested Villages**: If multiple armies from different players occupy the same village after combat, the village becomes neutral (ownerId = 0)
  - **Ownership Persistence**: Villages retain their ownership when abandoned (no army present)
  - No capture timer - ownership changes immediately based on post-combat occupation

- **Castle Capture:**
  - Castles require **3 consecutive ticks** of enemy occupation to capture
  - If enemy army leaves or friendly army arrives, capture progress resets to 0
  - Capture progress is visible via `occupationTicks` field on castle tiles
  - Once captured, ownership transfers to the occupying player
  - Capture progress bar is displayed above contested castles in the frontend
  
- **Soldier Generation:**
  - Only villages owned by a player generate soldiers
  - Neutral villages (ownerId = 0) do NOT generate soldiers
  - Each owned village generates +1 soldier per tick for armies of the owning player on that village
  
- **Income Calculation:**
  - A player's income per tick equals the number of villages they own
  - Use `getPlayerIncome(playerId)` to calculate total soldier generation potential

### Win/Loss Conditions
- **Victory:** Capture all enemy castles to win the game
- **Defeat:** Lose all your castles to lose the game
- When game ends:
  - `gameOver` flag is set to true in game state
  - `winnerId` indicates the winning player (1 or 2)
  - Commands are ignored (except for reset)
  - Win/loss overlay is displayed in the frontend
  - Press `R` to reset and play again

### Visual Indicators
- **Castles**: Gray base with colored outline (blue=Player 1, red=Player 2)
  - Capture progress bar shown above castles being contested (0-3 ticks)
- **Villages**:
  - Neutral: Brown
  - Player 1 owned: Brown with blue tint
  - Player 2 owned: Brown with red tint
- **Statistics**: Window title displays: `P1: XC YV +Z/tick | P2: XC YV +Z/tick`
  - XC = castles owned
  - YV = villages owned
  - +Z/tick = income (soldiers generated per tick)
- **Game Over**: Win/loss overlay displayed when game ends
  - Green overlay for Player 1 victory
  - Red overlay for Player 2 victory (player loss)
  - Press `R` to play again

### Strategic Implications
- Controlling more villages increases your army growth rate
- Capturing enemy villages reduces their income while increasing yours
- Villages are key strategic objectives for long-term advantage
- **Castles are critical:** Losing all castles results in immediate defeat
- Castle capture requires sustained occupation - defend your castles!

## Army Movement System

The game features a realistic movement system where armies move gradually toward their destinations:

### Movement Mechanics
- **Command**: When a MOVE command is issued, the army's destination is set but it doesn't teleport instantly
- **Movement Speed**: Armies move 1 tile per tick toward their destination
- **Pathfinding**: Uses Manhattan distance pathfinding (horizontal movement preferred first, then vertical)
- **Destination Indicator**: The frontend displays a colored square at the destination
  - Light blue for Player 1 armies
  - Light red for Player 2 armies
- **Movement Cancellation**: Issuing a new MOVE command cancels the previous movement and sets a new destination
- **Arrival**: When an army reaches its destination, the destination fields are cleared and movement stops

### Example Movement Sequence
```
Initial: Army at (0,0), commanded to move to (3,2)
Tick 1: Army moves to (1,0) - horizontal first
Tick 2: Army moves to (2,0) - horizontal continues
Tick 3: Army moves to (3,0) - reached horizontal destination
Tick 4: Army moves to (3,1) - vertical movement begins
Tick 5: Army moves to (3,2) - destination reached, stops moving
```

## Army Management System

The game features army splitting and automatic merging mechanics for tactical flexibility:

### Split Mechanics
- **Split Command**: Divides an army into two separate units at the same location
- **Validation**: 
  - Minimum 1 soldier must remain in both armies after split
  - Cannot split 0 or negative soldiers
  - Cannot split all soldiers (would leave 0 in original army)
- **New Army**: Created at the same location with a new unique ID
- **Original Army**: Soldier count reduced by the split amount
- **Frontend Controls**: Press `S` key and enter split amount in console

### Merge Mechanics
- **Automatic Merging**: Friendly armies at the same location automatically merge each tick
- **Merge Process**: 
  - Occurs during the movement phase of each tick
  - Only merges armies with same `playerId` at same location
  - Combines soldier counts
  - Keeps the army with the lowest ID
  - Removes the higher ID army
- **Multiple Armies**: If 3+ friendly armies are at the same location, they all merge into the lowest ID army
- **Enemy Armies**: Armies from different players do NOT merge - they fight instead

### Visual Display
- **Soldier Count**: Displayed on each army circle as a visual indicator (dots pattern)
- **Multiple Armies**: When multiple armies occupy the same tile, they are rendered with circular offsets
- **Army Circles**: 
  - Blue for Player 1
  - Red for Player 2
  - White center with colored dots indicating soldier count (up to 10 dots shown)

### Strategic Uses
- **Split Before Battle**: Divide forces to minimize losses if one army is defeated
- **Garrison Splitting**: Leave small garrison armies at captured villages while moving main force
- **Flanking Maneuvers**: Split army to attack from multiple directions
- **Auto-Merge**: Reinforcements automatically combine when armies meet at a location

## Core Game Loop

The game operates on a tick-based system. Each tick represents one turn where the following sequence occurs:

### 1. Tick Increment
The global tick counter increments by 1.

### 2. Army Movement Phase
```
For each army on the board:
  - If army has a destination set:
    - Calculate next position (1 step toward destination)
    - Move army to next position
    - If army reached destination:
      - Clear destination fields
```

### 3. Army Merge Phase
```
For each pair of armies:
  - If armies occupy the same tile AND have same playerId:
    - Combine soldier counts into army with lower ID
    - Remove army with higher ID
  - Repeat until no more merges possible
```

### 4. Village Capture Phase
```
For each army on the board:
  - Check the tile at army's current position
  - If tile is a VILLAGE and tile.ownerId != army.playerId:
    - tile.ownerId = army.playerId (capture the village)
```

### 5. Soldier Generation Phase
```
For each army on the board:
  - Check the tile at army's current position
  - If tile is a VILLAGE and tile.ownerId == army.playerId:
    - army.soldiers += 1 (owned villages generate soldiers)
```

### 6. Combat Resolution Phase
```
For each pair of armies:
  - If armies occupy the same tile AND have different player IDs:
    - Store initial soldier counts
    - army1.soldiers = max(0, army1.soldiers - army2.soldiers)
    - army2.soldiers = max(0, army2.soldiers - army1.soldiers)
```

### 7. Cleanup Phase
```
For each army:
  - If army.soldiers <= 0:
    - Remove army from game
```

### 8. Village Capture Phase
```
For each village tile:
  - Find all armies at this location (after combat)
  - If single player occupies the village:
    - tile.ownerId = occupyingPlayer (capture village)
  - If multiple players occupy the village:
    - tile.ownerId = 0 (contested - becomes neutral)
  - If no armies occupy the village:
    - Keep current ownership (villages persist when abandoned)
```

### Player Actions (Between Ticks)
- Players can issue MOVE commands to set army destinations
- Players can issue SPLIT commands to divide armies
- MOVE commands set the destination immediately but armies move gradually (1 tile per tick)
- SPLIT commands execute immediately, creating a new army at the same location
- Commands validate target coordinates are within bounds (0-9 for x and y)
- SPLIT commands validate soldier counts (minimum 1 soldier per army)
- Invalid commands are silently ignored
- Issuing a new MOVE command cancels any previous movement

### Example Turn Sequence
```
Initial State: Army A (10 soldiers) at (0,0) castle, Army B (8 soldiers) at (9,9)

Player issues: MOVE Army A to (3,3)
Result: Army A destination set to (3,3), but army stays at (0,0)

Player calls: POST /tick
Tick 1 executes:
  1. Tick count: 0 -> 1
  2. Movement: Army A moves from (0,0) to (1,0) - moving toward (3,3)
  3. Soldier generation: (No armies on villages)
  4. Combat: (No armies on same tile)
  5. Cleanup: (No defeated armies)

Player calls: POST /tick
Tick 2 executes:
  1. Tick count: 1 -> 2
  2. Movement: Army A moves from (1,0) to (2,0)
  3-5. (No changes)

Player calls: POST /tick
Tick 3 executes:
  1. Tick count: 2 -> 3
  2. Movement: Army A moves from (2,0) to (3,0)
  3-5. (No changes)

Player calls: POST /tick
Tick 4 executes:
  1. Tick count: 3 -> 4
  2. Movement: Army A moves from (3,0) to (3,1)
  3-5. (No changes)

Player calls: POST /tick
Tick 5 executes:
  1. Tick count: 4 -> 5
  2. Movement: Army A moves from (3,1) to (3,2)
  3-5. (No changes)

Player calls: POST /tick
Tick 6 executes:
  1. Tick count: 5 -> 6
  2. Movement: Army A moves from (3,2) to (3,3) - destination reached!
  3. Soldier generation: Army A at village gains 1 soldier (10 -> 11)
  4. Combat: (No armies on same tile)
  5. Cleanup: (No defeated armies)

Final State: Army A (11 soldiers) at (3,3), Army B (8 soldiers) at (9,9), tick count = 6
```

## Architecture

### Backend (Game Server)
- **GameService**: Singleton service managing in-memory game state
- **Thread Safety**: All public methods are synchronized to handle concurrent requests
- **State Management**: Returns defensive copies to prevent external modification
- **Army Identification**: Uses AtomicInteger-generated unique IDs (not list indices)
- **CORS**: Restricted to localhost origins (ports 8080 and 3000)
- **No Database**: All state is in-memory (resets on server restart)

### Frontend (Game Client)
- **Polling**: Client fetches game state periodically via GET /state
- **Commands**: User actions sent as HTTP POST requests
- **Rendering**: OpenGL-based grid rendering at 60 FPS
- **Connection Management**: 5s connect timeout, 10s read timeout, automatic disconnection
- **Cross-Platform**: Maven profiles auto-detect OS and load appropriate LWJGL natives (Linux, macOS, Windows)
- **UTF-8 Encoding**: All HTTP communication uses explicit UTF-8 charset

## Technical Constraints

- **No Authentication**: This is a prototype - no user authentication or authorization
- **No Persistence**: Game state is lost on server restart
- **Single Game Instance**: Server manages only one game state globally
- **No Networking Beyond HTTP**: Client-server communication is purely REST/HTTP
- **No Assets**: All rendering uses simple geometric shapes and colors

## Troubleshooting

### Backend Issues

**Problem: Backend fails to start**
- **Solution**: Ensure Java 17 is installed (`java -version`)
- **Solution**: Check if port 8080 is already in use (`lsof -i :8080` on Unix/macOS or `netstat -ano | findstr :8080` on Windows)
- **Solution**: Try running `./mvnw clean install` first to rebuild

**Problem: Tests fail during build**
- **Solution**: Verify Java 17 is being used (not Java 8 or 11)
- **Solution**: Run `./mvnw clean test` to see detailed error messages
- **Solution**: Check that MAVEN_OPTS doesn't override Java version

**Problem: Out of memory during build**
- **Solution**: Increase Maven heap size: `export MAVEN_OPTS="-Xmx1024m"` (Unix) or `set MAVEN_OPTS=-Xmx1024m` (Windows)

### Frontend Issues

**Problem: Frontend window doesn't open**
- **Solution**: Ensure backend is running first (frontend requires active server on localhost:8080)
- **Solution**: On WSL, install and start an X server (VcXsrv, Xming)
- **Solution**: On Linux, verify DISPLAY environment variable is set correctly
- **Solution**: Try setting `export LIBGL_ALWAYS_SOFTWARE=1` for software rendering fallback

**Problem: Black screen or rendering issues**
- **Solution**: Update graphics drivers
- **Solution**: Try software rendering: `export LIBGL_ALWAYS_SOFTWARE=1` (Linux)
- **Solution**: Check LWJGL native libraries are correctly loaded for your OS

**Problem: Mouse clicks not working**
- **Solution**: Ensure window has focus (click the title bar)
- **Solution**: Try maximizing the window if it's too small
- **Solution**: Restart the frontend application

**Problem: "Connection refused" errors**
- **Solution**: Verify backend is running on port 8080
- **Solution**: Check firewall settings aren't blocking localhost connections
- **Solution**: Try `curl http://localhost:8080/state` to test backend connectivity

**Problem: Army split command doesn't work**
- **Solution**: After pressing 'S', check the console/terminal for input prompt
- **Solution**: Enter a valid number between 1 and (total_soldiers - 1)
- **Solution**: Note: This is a known limitation (blocking console input in prototype)

### Performance Issues

**Problem: Low frame rate (< 60 FPS)**
- **Solution**: Reduce window size to improve performance
- **Solution**: Close other applications consuming GPU resources
- **Solution**: Enable VSync if experiencing tearing
- **Solution**: Try software rendering if GPU drivers are problematic

**Problem: High CPU usage**
- **Solution**: This is expected for the polling-based architecture
- **Solution**: Close other resource-intensive applications
- **Solution**: The frontend polls server every frame; this is intentional for MVP

### Gameplay Issues

**Problem: AI doesn't seem to be working**
- **Solution**: Verify `aiEnabled` is true in game state (check `/state` endpoint)
- **Solution**: AI only controls Player 2 (red armies)
- **Solution**: AI makes decisions during each tick - try advancing several ticks

**Problem: Policies don't seem to have effect**
- **Solution**: Policy effects are gradual (take several ticks to manifest)
- **Solution**: Check ruler stats panel to see current stat values
- **Solution**: Wait 15 ticks between policy changes (cooldown period)
- **Solution**: Only Player 1 armies/villages are affected by policies

**Problem: Castle won't capture**
- **Solution**: Enemy army must occupy castle for 3 consecutive ticks
- **Solution**: If friendly army arrives, capture progress resets to 0
- **Solution**: Check capture progress bar above castle (red fill shows progress)

### Platform-Specific Issues

**Windows:**
- If batch files don't work, run commands manually from command prompt
- Use `start-backend.bat` and `start-frontend.bat` from cmd.exe, not PowerShell
- For WSL users: install VcXsrv and set `export DISPLAY=:0`

**macOS:**
- If you get security warnings, go to System Preferences → Security & Privacy → Allow
- Ensure Java 17 is from a trusted source (Oracle, Adoptium, Homebrew)
- Some older Macs may require software rendering

**Linux:**
- If LWJGL fails to load natives, install `libglfw3` and `libglfw3-dev`
- For Wayland users, try `export GDK_BACKEND=x11` to force X11
- Ensure graphics drivers are up to date (especially for NVIDIA/AMD)

## Known Limitations

### Gameplay Limitations
- **Single Player Only**: No multiplayer support; one game instance per server
- **No Save/Load**: Game state is lost when backend restarts (in-memory only)
- **No Fog of War**: All units and tiles are always visible to both players
- **No Terrain Effects**: All tiles are equally traversable (except destination placement)
- **No Unit Types**: Only one army type; no knights, archers, siege weapons, etc.
- **Single AI Difficulty**: No adjustable difficulty levels
- **No Undo**: Commands are final; no undo/redo functionality

### Technical Limitations
- **Blocking Console Input**: Split command (S key) blocks rendering temporarily while awaiting console input
- **Headless Environments**: Frontend requires X11/Wayland display server; cannot run in true headless mode
- **No Persistent Storage**: No database; all state is volatile (in-memory)
- **Single Game Instance**: Server manages one game at a time globally
- **No Authentication**: No user accounts, passwords, or session management
- **Localhost Only**: CORS restricted to localhost; not designed for public internet deployment

### UI Limitations
- **No Sound**: No audio effects or background music
- **No Animations**: Movement is tile-by-tile; no smooth interpolation between positions
- **Basic Graphics**: Simple geometric shapes and colors; no sprites or textures
- **Console-Based Split Input**: Army splitting requires console input (not in-game UI)
- **Limited Text Rendering**: Custom text renderer; no font antialiasing or Unicode support
- **Fixed Window Size**: Window size is fixed at startup (not dynamically resizable)

### AI Limitations
- **Rule-Based Only**: AI uses simple heuristics; no machine learning or adaptive behavior
- **No Army Splitting**: AI does not use split command (planned for future)
- **Predictable Patterns**: AI follows priority-based decision tree; can be exploited by experienced players
- **No Communication**: AI doesn't provide feedback or messages about its intentions

### Policy System Limitations
- **Player 1 Only**: Ruler decisions and policies only affect Player 1 (not AI)
- **No Policy Mixing**: Can only have one active policy per category at a time
- **15-Tick Cooldown**: Cannot rapidly switch between policies; must wait between changes
- **Integer Math**: All stat calculations use integer arithmetic; some rounding occurs
- **No Policy History**: No tracking of past policy choices or their long-term effects

### Performance Limitations
- **Polling Architecture**: Frontend polls server every frame; not event-driven
- **Single-Threaded Backend**: Game logic runs on single thread; not optimized for massive army counts
- **No Culling**: All entities rendered every frame; performance degrades with 50+ armies
- **Memory Growth**: Long sessions may accumulate minor memory overhead (though no critical leaks)

### Documentation Limitations
- **No Tutorial**: Players must read README to understand controls and mechanics
- **Limited Tooltips**: Hover tooltips don't explain all game mechanics in detail
- **No In-Game Help**: No help menu or quick reference guide within the game

## Future Enhancements

See [MVP.md](MVP.md) and [TICKETS.md](TICKETS.md) for planned features beyond the MVP scope.

Potential improvements include:
- Multiple unit types and specialized armies
- Terrain effects (mountains, rivers, forests)
- Fog of war and limited visibility
- Multiple AI difficulty levels
- Sound effects and background music
- Smooth animations and visual effects
- Save/load functionality
- In-game tutorial and help system
- Advanced policy system with more categories
- Multiplayer support (networked gameplay)

## Architecture

### Backend (Game Server)
- **GameService**: Singleton service managing in-memory game state
- **Thread Safety**: All public methods are synchronized to handle concurrent requests
- **State Management**: Returns defensive copies to prevent external modification
- **Army Identification**: Uses AtomicInteger-generated unique IDs (not list indices)
- **CORS**: Restricted to localhost origins (ports 8080 and 3000)
- **No Database**: All state is in-memory (resets on server restart)

### Frontend (Game Client)
- **Polling**: Client fetches game state periodically via GET /state
- **Commands**: User actions sent as HTTP POST requests
- **Rendering**: OpenGL-based grid rendering at 60 FPS
- **Connection Management**: 5s connect timeout, 10s read timeout, automatic disconnection
- **Cross-Platform**: Maven profiles auto-detect OS and load appropriate LWJGL natives (Linux, macOS, Windows)
- **UTF-8 Encoding**: All HTTP communication uses explicit UTF-8 charset

## Technical Constraints

- **No Authentication**: This is a prototype - no user authentication or authorization
- **No AI Implementation**: Player 2 armies exist but are not controlled by AI (future enhancement)
- **No Persistence**: Game state is lost on server restart
- **Single Game Instance**: Server manages only one game state globally
- **No Networking Beyond HTTP**: Client-server communication is purely REST/HTTP
- **No Assets**: All rendering uses simple geometric shapes and colors

## License
This project is licensed under the **Stephenson Software Non-Commercial License (Stephenson-NC)**.  
© 2025 Daniel McCoy Stephenson. All rights reserved.

You may use, modify, and share this software for **non-commercial purposes only**.  
Commercial use is prohibited without explicit written permission from the copyright holder.

Full license text: [Stephenson-NC License](https://github.com/Stephenson-Software/stephenson-nc-license)  
SPDX Identifier: `Stephenson-NC`