# Barony

**Version:** 1.0.0  
**A single-player online strategy game with army movement, territory control, and AI opponent**

Barony is a browser-based turn-based strategy game where you command armies to capture villages and castles against an AI opponent. Build your forces, control territory, and use strategic policies to defeat your enemy.

## Documentation

### Quick Links
- **[PLAYER_GUIDE.md](PLAYER_GUIDE.md)** - Complete guide to playing Barony (start here!)
- **[DOCS.md](DOCS.md)** - Documentation index (find what you need)
- **[CHANGELOG.md](CHANGELOG.md)** - Version history and features

### For Developers
- **[MVP.md](MVP.md)** - MVP specification and implementation details
- **[ROADMAP.md](ROADMAP.md)** - Future feature roadmap (v1.1+)
- **[TICKETS.md](TICKETS.md)** - Development tickets and tasks

## Quick Start

### Using Docker (Recommended)

Barony authenticates players through the standalone [**UserAuth**](https://github.com/Preponderous-Software/UserAuth)
service, which `docker-compose` builds from a sibling checkout. Clone it next to this repo first:

```bash
# from the directory that contains barony/
git clone https://github.com/Preponderous-Software/UserAuth.git
cd barony
# JWT_SECRET must be at least 32 bytes; override the dev default in production
export JWT_SECRET="please-change-this-to-a-32-byte-minimum-secret"
docker-compose up --build
```

This brings up four services together: `userauth-db` (Postgres), `userauth` (port 9998),
`backend` (8080), and `web-client` (3000). Then open http://localhost:3000 in your browser,
create an account, and log in.

> If UserAuth lives elsewhere, point `USERAUTH_PATH` at it (e.g. `USERAUTH_PATH=../sibling/UserAuth`).

### Manual Start
```bash
# Backend
cd backend && ./mvnw spring-boot:run

# Web Client (separate terminal)
cd web-client && ./mvnw spring-boot:run
```
Then open http://localhost:3000 in your browser.

## Project Structure

- `backend/` - Spring Boot REST API (game logic)
- `web-client/` - Browser-based client (HTML5 Canvas)

## Backend API

### REST Endpoints

**Single-Game Endpoints:**
- `GET /state` - Get current game state
- `POST /tick` - Advance game by one turn
- `POST /command` - Send MOVE or SPLIT command
- `POST /api/reset` - Reset game
- `POST /api/decision` - Change ruler policy
- `GET /api/ruler-stats` - Get realm statistics

**Authenticated, Per-Player Endpoints (require `Authorization: Bearer <token>` header):**
- `GET /api/session/state` - Get the authenticated player's game state
- `POST /api/session/tick` - Advance the player's game by one turn
- `POST /api/session/command` - Send a command for the player
- `POST /api/session/reset` - Reset the player's game
- `POST /api/session/decision` - Change ruler policy for the player
- `GET /api/session/ruler-stats` - Get realm statistics for the player

These endpoints validate the bearer token against UserAuth on every request, so missing,
invalid, expired, or revoked (logged-out) tokens are rejected with `401`. Game state is keyed
by the authenticated username.

**Authentication (proxied to the [UserAuth](https://github.com/Preponderous-Software/UserAuth) service):**
- `POST /api/auth/register` - Create an account (`{username, password}`)
- `POST /api/auth/login` - Log in; returns a signed JWT (`{token, tokenType, expiresAt}`)
- `POST /api/auth/logout` - Revoke the supplied bearer token

#### Authentication flow

Barony never stores credentials itself — it delegates to UserAuth. The web client posts
credentials to the backend, which proxies them to UserAuth; the backend then validates the
returned JWT on each authenticated game request:

```
browser → web-client (proxy) → backend → UserAuth (/register, /login, /session/validate, /logout)
```

```bash
# Register, then log in to obtain a token
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"a-strong-password"}'

TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"a-strong-password"}' | jq -r .token)

# Use the token on per-player endpoints
curl http://localhost:8080/api/session/state -H "Authorization: Bearer $TOKEN"

# Log out (revokes the token; it is refused afterward)
curl -X POST http://localhost:8080/api/auth/logout -H "Authorization: Bearer $TOKEN"
```

### Command Examples

**Move Army:**
```bash
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"type":"MOVE","armyId":1,"targetX":5,"targetY":5}'
```

**Split Army:**
```bash
curl -X POST http://localhost:8080/command \
  -H "Content-Type: application/json" \
  -d '{"type":"SPLIT","armyId":1,"splitAmount":5}'
```

**Change Policy:**
```bash
curl -X POST http://localhost:8080/api/decision \
  -H "Content-Type: application/json" \
  -d '{"category":"ECONOMIC","choice":"HEAVY_TAXATION"}'
```

For detailed API documentation, see technical sections below.


## Web Client

Browser-based interface with HTML5 Canvas rendering.

**Start:** 
```bash
docker-compose up
```
Then open http://localhost:3000

### Features
- Toast notifications replace all in-game blocking alerts
- Canvas hover tooltips (tile info, army stats, castle capture progress)
- Army selection highlight ring on canvas
- Right-click to deselect armies
- Auto Play toggle button (single button, active-state indication)
- Color-coded stats (green ≥ 90, amber 70–89, red < 70)
- Policy cooldown progress bar
- Game-over banner overlay on canvas
- Inline split validation (no alerts)
- Colorblind modes: Deuteranopia, Protanopia, Tritanopia
- Themes: Dark (default), Classic, High Contrast
- Adjustable font size: Small, Medium, Large
- Settings panel with live preview
- Settings persisted to `localStorage` under `barony_settings`

## Game Overview

### Basic Rules
1. **Goal:** Capture all enemy castles to win
2. **Players:** You (blue) vs AI (red)
3. **Movement:** Armies move 1 tile per turn
4. **Villages:** Generate +1 soldier/turn for stationed armies
5. **Combat:** Simultaneous damage (10 vs 7 → 3 vs 0)
6. **Castle Capture:** Occupy for 3 consecutive turns

See [PLAYER_GUIDE.md](PLAYER_GUIDE.md) for complete gameplay instructions.

## Development

**Requirements:** Java 17, Maven (wrapper included)

### Build & Test
```bash
# Backend
cd backend && ./mvnw clean package test

# Web Client
cd web-client && ./mvnw clean package test
```

### CI/CD
GitHub Actions runs on all PRs:
- Builds backend and web client
- Runs all unit and integration tests
- Uses JDK 17 with Maven caching

## Technical Architecture

### Backend
- Spring Boot REST API
- In-memory game state (thread-safe)
- Unique army IDs (not list indices)
- CORS: localhost only

### Web Client
- Spring Boot + Thymeleaf
- HTML5 Canvas rendering
- RESTful backend communication

## Troubleshooting

### Common Issues

**Backend won't start:**
- Check Java 17: `java -version`
- Port 8080 in use: `lsof -i :8080` (Unix) or `netstat -ano | findstr :8080` (Windows)

**Web client won't load:**
- Ensure backend is running first
- Check that port 3000 isn't already in use
- Try clearing browser cache

**Policy changes not working:**
- Wait for 15-turn cooldown
- Check Ruler Stats panel for timer
- Effects are gradual (several turns)

For detailed troubleshooting, see [PLAYER_GUIDE.md](PLAYER_GUIDE.md).

## Getting Help

- **Playing the game?** See [PLAYER_GUIDE.md](PLAYER_GUIDE.md)
- **Technical issues?** Check the Troubleshooting section above
- **Can't find what you need?** See [DOCS.md](DOCS.md) for documentation index
- **Found a bug?** Open an issue on GitHub

## License
This project is licensed under the **Stephenson Software Non-Commercial License (Stephenson-NC)**.  
© 2025 Daniel McCoy Stephenson. All rights reserved.

You may use, modify, and share this software for **non-commercial purposes only**.  
Commercial use is prohibited without explicit written permission from the copyright holder.

Full license text: [LICENSE.md](LICENSE.md) (canonical: [Stephenson-Software/stephenson-nc-license](https://github.com/Stephenson-Software/stephenson-nc-license))  
SPDX Identifier (custom): `LicenseRef-Stephenson-NC`