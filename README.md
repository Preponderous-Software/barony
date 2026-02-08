# Barony Prototype

A minimal client/server game prototype with a Java Spring Boot backend and Java LWJGL frontend.

## Project Structure

- `backend/` - Spring Boot REST API server
- `frontend/` - LWJGL client application

## Backend

### Features
- 2D grid (10x10)
- Tile types: CASTLE, VILLAGE, EMPTY
- Villages generate 1 soldier per tick for armies on them
- Integer-based combat (armies reduce each other's soldiers equally)
- In-memory game state

### REST Endpoints
- `GET /state` - Get current game state
- `POST /tick` - Advance game by one tick
- `POST /command` - Send a command (e.g., move army)

### Running the Backend

```bash
cd backend
mvn spring-boot:run
```

Server will start on http://localhost:8080

## Frontend

### Features
- Renders 10x10 grid using LWJGL
- Visual representation:
  - Gray tiles: CASTLE
  - Brown tiles: VILLAGE  
  - Green tiles: EMPTY
  - Blue circles: Player 1 armies
  - Red circles: Player 2 armies

### Controls
- `SPACE` - Send tick command to server
- `M` - Move army 0 to position (5,5)
- `ESC` - Close window

### Running the Frontend

**Important:** Start the backend first!

```bash
cd frontend
mvn compile exec:java
```

## Command Examples

### Move Command (via REST API)
```bash
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"type":"MOVE","armyIndex":0,"targetX":5,"targetY":5}'
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

## Game Rules

1. Initial setup: Two castles at (0,0) and (9,9), two villages at (3,3) and (6,6)
2. Each player starts with an army of 10 soldiers at their castle
3. Each tick, armies on villages gain 1 soldier
4. When armies of different players occupy the same tile, combat occurs
5. Combat reduces each army's soldiers by the other army's count
6. Armies with 0 soldiers are removed