# Barony Prototype

A minimal client/server game prototype with a Java Spring Boot backend and Java LWJGL frontend. This is a single-player strategy game where you control your armies against AI-controlled enemy Lords.

## Project Structure

- `backend/` - Spring Boot REST API server (owns all game logic)
- `frontend/` - LWJGL client application (renders game state and sends commands)

## Backend

### Features
- 2D grid (10x10)
- Tile types: CASTLE, VILLAGE, EMPTY
- Villages generate 1 soldier per tick for armies on them
- Integer-based combat (armies reduce each other's soldiers equally)
- In-memory game state
- Thread-safe game state management with synchronized access
- Stable army identification using unique IDs (not list indices)

### REST Endpoints
- `GET /state` - Get current game state (returns JSON with grid, armies, tick count)
- `POST /tick` - Advance game by one tick (executes game loop, returns updated state)
- `POST /command` - Send a command (currently supports MOVE command with army ID and target coordinates)

### Command Structure
```json
{
  "type": "MOVE",
  "armyId": 1,
  "targetX": 5,
  "targetY": 5
}
```

### State Response Structure
```json
{
  "grid": [[{"type": "CASTLE"}, ...], ...],
  "armies": [
    {"id": 1, "x": 0, "y": 0, "soldiers": 10, "playerId": 1},
    {"id": 2, "x": 9, "y": 9, "soldiers": 10, "playerId": 2}
  ],
  "tickCount": 0,
  "width": 10,
  "height": 10
}
```

### Running the Backend

```bash
cd backend
mvn spring-boot:run
```

Server will start on http://localhost:8080

## Frontend

### Features
- Renders 10x10 grid using LWJGL (cross-platform support for Linux, macOS, Windows)
- Visual representation:
  - Gray tiles: CASTLE
  - Brown tiles: VILLAGE  
  - Green tiles: EMPTY
  - Blue circles: Player 1 armies
  - Red circles: Player 2 armies
- HTTP client with proper timeouts and error handling
- UTF-8 encoding for all network communication

### Controls
- `SPACE` - Send tick command to server
- `M` - Move first army to position (5,5) using its unique ID
- `ESC` - Close window

### Running the Frontend

**Important:** Start the backend first!

```bash
cd frontend
mvn compile exec:java
```

## Quick Start Scripts

### Start Backend
```bash
./start-backend.sh
```

### Start Frontend
```bash
./start-frontend.sh
```

### Run API Demo
```bash
./demo-api.sh
```

## Command Examples

### Move Command (via REST API)
```bash
# Move army by ID (use the army's unique ID from /state endpoint)
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"type":"MOVE","armyId":1,"targetX":5,"targetY":5}'
```

### Tick Command
```bash
curl -X POST http://localhost:8080/tick
```

### Get State
```bash
curl http://localhost:8080/state
```

## Development

Both projects use Maven and Java 17.

### Build Backend
```bash
cd backend
mvn clean package
```

### Build Frontend
```bash
cd frontend
mvn clean package
```

### Run Tests
```bash
# Backend tests (29 tests)
cd backend
mvn test

# Frontend tests (12 tests)
cd frontend
mvn test
```

### Continuous Integration
GitHub Actions workflow automatically runs on pull requests to `main` or `develop`:
- Builds both backend and frontend
- Runs all unit tests (41 total)
- Packages applications
- Uses JDK 17 with Maven caching for faster builds

Test coverage includes:
- **Backend**: Model tests (Army, Command, Tile, GameState), Service tests (GameService with game mechanics)
- **Frontend**: Model tests (Army, Command, Tile, GameState)

## Game Rules

1. **Initial Setup:** Two castles at (0,0) and (9,9), two villages at (3,3) and (6,6)
2. **Players:** Player 1 (human, blue armies) starts at (0,0); Player 2 (AI/enemy, red armies) starts at (9,9)
3. **Starting Forces:** Each player starts with an army of 10 soldiers at their castle
4. **Soldier Generation:** Each tick, armies positioned on villages gain 1 soldier
5. **Combat:** When armies of different players occupy the same tile, combat occurs
6. **Combat Resolution:** Each army's soldier count is reduced by the opponent's soldier count (simultaneous damage)
7. **Army Removal:** Armies with 0 or fewer soldiers are removed from the game

## Core Game Loop

The game operates on a tick-based system. Each tick represents one turn where the following sequence occurs:

### 1. Tick Increment
The global tick counter increments by 1.

### 2. Soldier Generation Phase
```
For each army on the board:
  - Check the tile type at army's current position
  - If tile is a VILLAGE:
    - army.soldiers += 1
```

### 3. Combat Resolution Phase
```
For each pair of armies:
  - If armies occupy the same tile AND have different player IDs:
    - Store initial soldier counts
    - army1.soldiers = max(0, army1.soldiers - army2.soldiers)
    - army2.soldiers = max(0, army2.soldiers - army1.soldiers)
```

### 4. Cleanup Phase
```
For each army:
  - If army.soldiers <= 0:
    - Remove army from game
```

### Player Actions (Between Ticks)
- Players can issue MOVE commands to reposition their armies
- Commands are executed immediately (not queued)
- Commands validate target coordinates are within bounds (0-9 for x and y)
- Invalid commands are silently ignored

### Example Turn Sequence
```
Initial State: Army A (10 soldiers) at (3,3) village, Army B (8 soldiers) at (5,5)

Player issues: MOVE Army A to (5,5)
Result: Army A moves to (5,5)

Player calls: POST /tick

Tick executes:
  1. Tick count: 0 -> 1
  2. Soldier generation: (Army A is no longer on village, no generation occurs)
  3. Combat: Army A and B are at (5,5), different players
     - Army A: 10 - 8 = 2 soldiers
     - Army B: 8 - 10 = 0 soldiers (will be removed)
  4. Cleanup: Army B removed

Final State: Army A (2 soldiers) at (5,5), tick count = 1
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
- **No AI Implementation**: Player 2 armies exist but are not controlled by AI (future enhancement)
- **No Persistence**: Game state is lost on server restart
- **Single Game Instance**: Server manages only one game state globally
- **No Networking Beyond HTTP**: Client-server communication is purely REST/HTTP
- **No Assets**: All rendering uses simple geometric shapes and colors