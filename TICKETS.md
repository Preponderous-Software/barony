# Barony Prototype - GitHub Issues for MVP Development

> **Note for Players:** This document is for developers and contains implementation tickets. If you want to learn how to play Barony, see [PLAYER_GUIDE.md](PLAYER_GUIDE.md) instead.

This document contains issue templates ready for GitHub issue creation. Each ticket is designed for implementation by GitHub Copilot Coding Agent and groups related functionality to minimize the number of agent sessions required.

---

## Ticket 1: Implement Army Movement System with Pathfinding

**Priority:** High  
**Estimate:** 2-3 agent sessions  
**Dependencies:** None

### Description

Transform army movement from instant teleportation to realistic tile-by-tile movement with pathfinding. Armies should move 1 tile per tick toward their destination.

### Acceptance Criteria

**Backend:**
- Add `destinationX` and `destinationY` fields to `Army` model
- Add `isMoving()` method to `Army` class
- Implement Manhattan distance pathfinding in `GameService`
- Update `tick()` method to process army movement each tick
- Modify `executeCommand()` to set destination instead of instant movement
- Add movement validation (bounds checking, pathfinding)
- Add 10+ unit tests for movement mechanics

**Frontend:**
- Add visual indicator for army destination (selection box or highlight)
- Update rendering to show armies at current positions during movement
- Test movement commands through UI

**Documentation:**
- Update README.md with movement mechanics explanation
- Update API documentation for movement commands

### Technical Notes

- Use simple Manhattan distance pathfinding for MVP (A* can be added later)
- Movement should be queued: setting new destination cancels previous movement
- Armies stop when they reach their destination
- Movement through friendly castles/villages is allowed
- Movement through enemy castles can be blocked (optional for this ticket)

### Files to Modify

- `backend/src/main/java/com/barony/backend/model/Army.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 2: Implement Territory Control and Village Ownership

**Priority:** High  
**Estimate:** 2 agent sessions  
**Dependencies:** None

### Description

Add ownership mechanics for villages and enable territory capture. Villages should generate soldiers only for their owner, and ownership should persist until captured by an enemy army.

### Acceptance Criteria

**Backend:**
- Add `ownerId` field to `Tile` model (0=neutral, 1=player1, 2=player2)
- Initialize starting castles with player ownership (P1 castle at (1,1), P2 castle at (8,8))
- Implement village capture: enemy army occupying a village changes its ownership
- Update soldier generation to check village ownership in `tick()`
- Add `getPlayerIncome(playerId)` method to calculate total soldier generation
- Add 8+ unit tests for ownership and capture mechanics

**Frontend:**
- Update tile rendering to show ownership colors:
  - Castles: gray base with blue (P1) or red (P2) outline
  - Villages: brown base with blue/red tint for owned villages
  - Empty: gray/green (no change)
- Add territory statistics display (castles owned, villages owned, income per tick)
- Test village capture through gameplay

**Documentation:**
- Update README.md with territory control explanation
- Document color scheme for ownership visualization

### Technical Notes

- Villages are captured instantly when enemy army occupies them (no capture timer)
- Multiple armies from same player at village doesn't affect ownership
- Neutral villages (ownerId=0) don't generate soldiers
- Castle ownership is initialized at game start (Ticket 4 handles castle capture)

### Files to Modify

- `backend/src/main/java/com/barony/backend/model/Tile.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/test/java/com/barony/backend/model/TileTest.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 3: Implement Army Management (Split, Merge, Display)

**Priority:** High  
**Estimate:** 2 agent sessions  
**Dependencies:** Ticket 2 (for proper army rendering)

### Description

Enable players to split armies into multiple units and automatically merge co-located friendly armies. Display soldier counts on the map.

### Acceptance Criteria

**Backend:**
- Add `SPLIT` command type to `Command` model
- Add `splitAmount` field to `Command` (number of soldiers to split off)
- Implement `splitArmy(armyId, soldierCount)` in `GameService`
- Validate split commands (minimum 1 soldier per army, can't split to 0)
- Implement automatic merging of co-located friendly armies in `tick()`
- Add 8+ unit tests for split/merge mechanics

**Frontend:**
- Display soldier count as text overlay on army circles
- Add keyboard shortcut for split command (S key)
- Add console prompt or simple UI for entering split amount
- Render multiple armies at same location (offset circles or stack indicator)
- Show visual feedback when armies merge
- Test split/merge through gameplay

**Documentation:**
- Update README.md with army management controls (S key)
- Document split/merge mechanics

### Technical Notes

- Split creates a new army at the same location with the specified soldier count
- Merging happens automatically each tick for armies with same playerId at same location
- Merged armies combine soldier counts and use the lowest army ID
- Frontend can show overlapping circles offset by a few pixels for multiple armies

### Files to Modify

- `backend/src/main/java/com/barony/backend/model/Command.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 4: Implement Castle Capture and Win/Loss Conditions

**Priority:** High  
**Estimate:** 2 agent sessions  
**Dependencies:** Ticket 2 (requires ownership mechanics)

### Description

Add castle capture mechanics with occupation timer and implement win/loss conditions. Players win by capturing all enemy castles and lose when they have no castles remaining.

### Acceptance Criteria

**Backend:**
- Add `occupationTicks` field to `Tile` model (for capture progress)
- Implement castle capture logic: enemy army must occupy castle for 3 consecutive ticks
- Reset `occupationTicks` if no enemy army present or if friendly army present
- Add `checkWinCondition()` method to `GameService`
- Add `gameOver` and `winnerId` fields to `GameState`
- Call `checkWinCondition()` at end of each `tick()`
- Prevent commands when `gameOver` is true
- Add `POST /api/reset` endpoint to restart the game
- Add 10+ unit tests for castle capture and win conditions

**Frontend:**
- Show castle ownership with colored outlines (blue/red)
- Display capture progress bar above contested castles (0-3 ticks)
- Show win/loss overlay when game ends ("Player 1 Wins!" or "You Lose!")
- Add "Play Again" button that calls `/api/reset`
- Disable input when game is over
- Test complete game scenarios (win and loss)

**Documentation:**
- Update README.md with castle capture mechanics (3 tick timer)
- Document win/loss conditions
- Document `/api/reset` endpoint

### Technical Notes

- Castles require 3 ticks of continuous enemy occupation to capture
- If occupation is interrupted, timer resets to 0
- Game ends immediately when win condition is detected
- Both castles start owned by players (P1 at (1,1), P2 at (8,8))

### Files to Modify

- `backend/src/main/java/com/barony/backend/model/Tile.java`
- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/controller/GameController.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 5: Implement Basic Rule-Based AI Opponent

**Priority:** Medium  
**Estimate:** 3 agent sessions  
**Dependencies:** Tickets 1, 2, 3, 4 (requires all core mechanics)

### Description

Create a rule-based AI to control Player 2 armies. AI should make intelligent decisions about capturing villages, defending territory, and attacking enemy positions.

### Acceptance Criteria

**Backend:**
- Add `executeAI()` method in `GameService`, called during `tick()`
- Implement AI decision logic with priorities:
  1. Defend owned villages under threat (if enemy army within 3 tiles)
  2. Capture nearest neutral village (if safe to do so)
  3. Attack weakly-defended enemy villages (if superior force)
  4. Attack enemy castle (if overwhelming force, e.g., 2x enemy strength)
  5. Build up forces in villages (if no better action)
- Implement target evaluation (calculate threat levels, opportunity scores)
- Implement AI command generation (movement, splitting)
- Add AI army spawning: spawn 10 soldiers at P2 castle every 5 ticks
- Add configuration for AI difficulty (simple boolean: enabled/disabled for MVP)
- Add 10+ unit tests for AI decision-making logic

**Frontend:**
- Visual indicator for AI actions (optional, can be deferred)
- No UI changes required for MVP (AI runs automatically)

**Documentation:**
- Update README.md with AI description and behavior
- Document AI strategy and difficulty

### Technical Notes

- AI runs during each `tick()` after village generation and before combat
- AI should never make invalid moves (check bounds, pathfinding)
- AI should consider army strength before attacking (don't suicide small armies)
- AI can split armies to capture multiple villages simultaneously
- AI spawning provides catch-up mechanism if player dominates early

### Files to Modify

- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `README.md`

---

## Ticket 6: Implement Enhanced UI with Mouse Controls and HUD

**Priority:** Medium  
**Estimate:** 3 agent sessions  
**Dependencies:** Ticket 3 (for army display)

### Description

Add mouse-based controls for army selection and movement, plus informative HUD elements showing game state and statistics.

### Acceptance Criteria

**Frontend:**
- Implement mouse input handling (GLFW mouse callbacks)
- Click army circle to select it (store selected army ID)
- Click destination tile to move selected army
- Right-click to deselect army
- Highlight selected army (glowing border or color change)
- Show movement destination preview (faint circle or outline)
- Display hover tooltips:
  - Tiles: type, ownership, income (for villages)
  - Armies: soldier count, player ID, movement status
- Implement HUD panels:
  - Top bar: Tick count, P1 income, P1 army count, P1 territory count
  - Side panel: Selected army details (soldier count, destination)
  - Bottom bar: Status messages ("Village captured!", "Army moved", etc.)
- Implement scrolling game log (last 10 events)
- Add text rendering for labels (use simple OpenGL text or bitmap font)
- Test all mouse interactions and UI displays

**Backend:**
- No backend changes required (all frontend)

**Documentation:**
- Update README.md with mouse control instructions
- Document UI elements and their meanings
- Add screenshots showing new UI

### Technical Notes

- Use GLFW cursor position callbacks for mouse tracking
- Use GLFW mouse button callbacks for clicks
- Implement simple tooltip system with 0.5s hover delay
- HUD can use simple colored rectangles with text overlay
- Game log can be circular buffer of last 10 message strings

### Files to Modify

- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 7: Implement Ruler Decision System (CK-Lite Layer)

**Priority:** Medium  
**Estimate:** 3 agent sessions  
**Dependencies:** Tickets 2, 3, 6 (requires territory control, army management, and UI foundation)

### Description

Add a lightweight policy-based ruler decision system that allows players to make strategic choices affecting their realm through mechanical modifiers. This system provides a "CK-lite" layer focused on system-driven outcomes without dynasties, diplomacy, or narrative elements.

### Acceptance Criteria

**Backend:**
- Create `RulerDecision` model with policy categories and types:
  - Economic policies: Heavy Taxation, Balanced Budget, Infrastructure Investment
  - Military policies: Aggressive Training, Standard Service, Veteran Benefits
  - Population policies: Growth Focus, Stable Population, Quality Over Quantity
- Add policy state tracking to `GameState` (current policy in each category)
- Add new fields to models:
  - `Village.stability` (0-100, affects soldier generation efficiency)
  - `Village.population` (current population, affects generation capacity)
  - `Army.morale` (0-200, affects combat effectiveness)
  - `Army.loyalty` (0-100, affects desertion rate)
- Implement policy effect calculation system:
  - Economic policies modify village stability and income
  - Military policies modify army morale and loyalty
  - Population policies modify village population growth and stability
- Implement gradual stat recovery/decay in `tick()`:
  - Stability recovers toward 100% at 2% per tick
  - Morale decays toward 100% at 1% per tick
  - Loyalty recovers toward 100% at 2% per tick
- Implement modified game mechanics:
  - Soldier generation: `base * (stability / 100)`
  - Combat effectiveness: `strength * (morale / 100)`
  - Army desertion: `(100 - loyalty) / 20`% per tick
- Add `POST /api/decision` endpoint (change policy, requires category and choice)
- Add `GET /api/ruler-stats` endpoint (returns realm statistics)
- Add policy decision cooldown (15 ticks between policy changes)
- Add 12+ unit tests for policy effects and stat calculations

**Frontend:**
- Create policy selection UI:
  - Radio buttons or dropdown for each policy category
  - Display current policy in each category
  - Show policy effects preview (+10% morale, -5% loyalty, etc.)
  - Add confirmation dialog for policy changes
- Add realm statistics panel in HUD:
  - Average village stability (%)
  - Average army morale (%)
  - Average army loyalty (%)
  - Total population across owned villages
  - Current policies in each category
  - Ticks until next policy decision available
- Add visual indicators for affected entities:
  - Unstable villages (<70% stability): yellow tint
  - Low morale armies (<80%): dimmed color
  - Disloyal armies (<80%): orange outline
- Display policy change cooldown timer
- Test policy changes and verify stat changes over time

**Documentation:**
- Update README.md with ruler decision system explanation
- Document each policy type and its effects
- Add section explaining stat mechanics (stability, morale, loyalty, population)
- Include examples of effective policy strategies
- Clarify CK-lite scope (what's included and excluded)

### Technical Notes

- Policy effects are percentage modifiers applied to base mechanics
- Effects are continuous (not one-time bonuses) and last until policy changes
- Stats gradually return to baseline (100%) to prevent extreme scenarios
- Policy cooldown prevents rapid policy switching exploits
- All calculations use integer math with rounding for soldier generation
- AI should not use ruler decisions in MVP (Player 1 only feature)
- Balance policy effects during playtesting to ensure meaningful choices

### Policy Effect Specifications

**Economic Policies:**
- Heavy Taxation: +20% income, -10% stability
- Balanced Budget: No modifiers (baseline)
- Infrastructure Investment: -10% income, +10% stability

**Military Policies:**
- Aggressive Training: +10% morale, -5% loyalty
- Standard Service: No modifiers (baseline)
- Veteran Benefits: -10% morale (less aggressive), +10% loyalty

**Population Policies:**
- Growth Focus: +15% population growth, -5% stability
- Stable Population: No modifiers (baseline)
- Quality Over Quantity: -10% population growth, +10% stability

### Files to Modify

- `backend/src/main/java/com/barony/backend/model/RulerDecision.java` (create)
- `backend/src/main/java/com/barony/backend/model/Army.java`
- `backend/src/main/java/com/barony/backend/model/Tile.java`
- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/main/java/com/barony/backend/model/RulerStats.java` (create)
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/controller/GameController.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `backend/src/test/java/com/barony/backend/model/RulerDecisionTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 8: Polish, Balance, and Integration Testing

**Priority:** Low  
**Estimate:** 2 agent sessions  
**Dependencies:** All previous tickets

### Description

Final polish pass including balance adjustments, comprehensive integration testing, performance optimization, and documentation updates.

### Acceptance Criteria

**Backend:**
- Balance soldier generation rates (ensure games last 5-15 minutes)
- Balance castle capture timer (adjust from 3 ticks if needed)
- Balance AI difficulty (win rate around 30-40% vs new players)
- Balance ruler policy effects (ensure no dominant strategy)
- Verify stat recovery/decay rates feel appropriate
- Add integration tests for complete game scenarios
- Performance testing: verify 60 FPS with 20+ armies
- Code review and cleanup (remove debug code, add comments)

**Frontend:**
- Optimize rendering performance (target 60 FPS)
- Add visual polish (smooth color transitions, better army rendering)
- Improve responsiveness of mouse controls
- Add loading screen or startup message
- Test on all platforms (Windows, macOS, Linux)

**Documentation:**
- Update README.md with final screenshots
- Add troubleshooting section
- Document known limitations
- Update MVP.md with completion status
- Create CHANGELOG.md documenting MVP features

**Testing:**
- Complete end-to-end playthrough (human vs AI)
- Test all edge cases (empty armies, simultaneous captures, etc.)
- Test policy changes and verify gradual stat effects
- Test extreme scenarios (all policies at max modifiers)
- Verify no memory leaks during long play sessions
- Test all keyboard and mouse controls
- Verify CI pipeline passes all tests

### Technical Notes

- This ticket is for cleanup and polish, not new features
- Focus on making existing features feel smooth and professional
- Identify and fix any bugs discovered during integration testing

### Files to Modify

- All files (as needed for polish)
- `README.md`
- `MVP.md`
- `CHANGELOG.md` (create)

---

## Post-MVP Tickets

> The following tickets are derived from the [ROADMAP.md](ROADMAP.md) and cover planned features for versions 1.1 through 3.0.

---

## Ticket 9: Implement Audio System

**Priority:** High  
**Estimate:** 3-4 agent sessions  
**Dependencies:** Ticket 8 (MVP complete)  
**Target Version:** v1.1

### Description

Implement basic sound effects and background music to enhance game atmosphere. The audio system should support background music, multiple sound effects for gameplay events, and player-configurable volume controls.

### Acceptance Criteria

**Backend:**
- No backend changes required (all frontend)

**Frontend:**
- Add audio playback system using OpenAL or Java Sound API
- Implement background music track (medieval/strategic theme)
- Add sound effects for:
  - Army movement commands
  - Village capture
  - Castle capture progress
  - Combat resolution
  - Victory/defeat
  - Policy changes
- Implement volume controls in settings
- Add mute toggle (configurable keybinding; default `F10` on desktop builds)
- Implement audio asset loading system
- Support OGG/WAV format audio files
- Implement audio pooling for repeated sounds
- Keep total audio file sizes small (< 5MB)
- Test all audio triggers during gameplay

**Documentation:**
- Update README.md with audio settings and controls
- Document supported audio formats and configuration

### Technical Notes

- Use OpenAL or Java Sound API for audio playback
- Support OGG/WAV format audio files
- Implement audio pooling for repeated sounds
- Keep file sizes small (< 5MB total)

### Files to Create/Modify

- `frontend/src/main/java/com/barony/frontend/audio/AudioManager.java` (create)
- `frontend/src/main/resources/audio/` (new directory)
- `frontend/pom.xml` (add audio library dependency)
- `README.md`

---

## Ticket 10: Implement Save/Load Game Functionality

**Priority:** High  
**Estimate:** 3-4 agent sessions  
**Dependencies:** Ticket 8 (MVP complete)  
**Target Version:** v1.1

### Description

Allow players to save game progress and resume later. Support multiple save slots, auto-save functionality, and a save/load UI in the menu.

### Acceptance Criteria

**Backend:**
- Add session-aware `POST /api/session/save` endpoint with slot parameter
- Add session-aware `GET /api/session/saves` endpoint (list available saves)
- Add session-aware `POST /api/session/load` endpoint with slot parameter
- All endpoints require `X-Session-Id` header to identify the active session
- Serialize complete GameState to JSON (armies, tiles, policies, tick count)
- Support multiple save slots (3 slots minimum)
- Implement auto-save every 50 ticks (configurable)
- Include save file metadata (timestamp, tick count, player status)
- Include version number in save file for compatibility
- Validate save file integrity on load
- Add 8+ unit tests for save/load mechanics

**Frontend:**
- Add save/load menu (configurable keybinding; suggest `F5` key or dedicated menu button)
- Display save slots with metadata
- Implement confirm overwrite existing save dialog
- Add loading screen during load operation
- Test save/load through gameplay

**Documentation:**
- Update README.md with save/load instructions
- Document save file format and location

### Technical Notes

- Save files stored in user's home directory (`.barony/saves/`)
- File format: `save_slot_N.json`
- Include version number in save file for forward compatibility
- Validate save file integrity on load

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/service/SaveGameService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/SaveGameController.java` (create)
- `backend/src/test/java/com/barony/backend/service/SaveGameServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/ui/SaveLoadMenu.java` (create)
- `README.md`

---

## Ticket 11: Implement Enhanced Visual Feedback

**Priority:** Medium  
**Estimate:** 2-3 agent sessions  
**Dependencies:** Ticket 8 (MVP complete)  
**Target Version:** v1.1

### Description

Add visual polish and feedback including smooth animations, particle effects, and improved rendering to make gameplay more engaging.

### Acceptance Criteria

**Frontend:**
- Implement smooth animations:
  - Army movement interpolation (lerp between tiles)
  - Fade effects for army merging
  - Pulse effect for village capture
  - Progress bar fill animation for castle capture
- Implement particle effects:
  - Combat sparkles when armies clash
  - Capture particles when village/castle changes ownership
  - Victory confetti on game win
- Improve rendering:
  - Shadows under armies
  - Gradient backgrounds for tiles
  - Highlight effects for selected armies
  - Glow effects for villages generating soldiers
- Add settings toggle to disable animations
- Maintain 60 FPS with all effects enabled
- Test all visual effects during gameplay

**Backend:**
- No backend changes required

**Documentation:**
- Update README.md with visual settings options

### Technical Notes

- Keep animations subtle (< 0.5s duration)
- Optimize particle systems (max 100 particles)
- Use OpenGL alpha blending for effects
- Add settings toggle to disable animations for performance

### Files to Create/Modify

- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `frontend/src/main/java/com/barony/frontend/rendering/` (new package)
- `README.md`

---

## Ticket 12: Implement Game Statistics & History

**Priority:** Medium  
**Estimate:** 2-3 agent sessions  
**Dependencies:** Ticket 8 (MVP complete)  
**Target Version:** v1.1

### Description

Track and display game statistics and historical events. Provide players with a statistics panel, game history log, and end-game summary.

### Acceptance Criteria

**Backend:**
- Create `GameStatistics` model tracking key metrics:
  - Total soldiers trained
  - Villages captured
  - Castles captured
  - Battles won/lost
  - Policies enacted
  - Time played
- Update statistics during tick processing
- Add `GET /api/statistics` endpoint
- Add 6+ unit tests for statistics tracking

**Frontend:**
- Add statistics panel in UI (toggleable with Tab key)
- Implement game history log (scrollable event feed)
- Display detailed statistics on victory/defeat screen
- Show notable events in history log
- Add export statistics to JSON/CSV
- Test statistics display during gameplay

**Documentation:**
- Update README.md with statistics panel controls
- Document available statistics and export format

### Technical Notes

- Statistics tracking should have minimal performance impact
- History log can use circular buffer of recent events
- Export format should be human-readable

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/GameStatistics.java` (create)
- `backend/src/main/java/com/barony/backend/service/StatisticsService.java` (create)
- `backend/src/test/java/com/barony/backend/service/StatisticsServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/ui/StatisticsPanel.java` (create)
- `README.md`

---

## Ticket 13: Implement Multiple Unit Types

**Priority:** High  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Tickets 1-8 (MVP complete)  
**Target Version:** v1.2

### Description

Introduce different unit types with unique characteristics and tactical roles. Add Infantry (default), Cavalry, Archers, and Siege Weapons with distinct costs, speeds, attack/defense values, and special abilities.

### Acceptance Criteria

**Backend:**
- Create `UnitType` enum with values: INFANTRY, CAVALRY, ARCHER, SIEGE
- Add unit composition tracking to `Army` model
- Implement unit stats:
  - Infantry: Cost 1, Speed 1, Attack 1, Defense 1
  - Cavalry: Cost 3 infantry (upgraded at castles), Speed 2, Attack 2, Defense 1, bonus vs archers
  - Archers: Cost 2 infantry (trained at villages), Speed 1, Attack 1.5 (ranged), Defense 0.5, can attack from 1 tile away
  - Siege Weapons: Cost 5 infantry (built at castles), Speed 0.5, Attack 5 vs castles/villages, Defense 0.5
- Implement unit training system at villages and castles
- Update combat calculations for unit type interactions (rock-paper-scissors: cavalry > archers > infantry)
- Add unit type costs and conversion mechanics
- Update pathfinding for different movement speeds
- Update AI to understand unit type advantages
- Add 12+ unit tests for unit types and combat interactions

**Frontend:**
- Add different visual representations for each unit type
- Display unit composition (e.g., "10 infantry, 5 cavalry")
- Add unit training UI at villages/castles
- Implement tactical combat preview showing unit matchups
- Test unit training and combat through gameplay

**Documentation:**
- Update README.md with unit type descriptions and stats
- Document unit training mechanics and costs
- Document combat interactions (rock-paper-scissors)

### Technical Notes

- Combat uses rock-paper-scissors dynamics (cavalry > archers > infantry)
- Unit composition affects army appearance (size, color, icons)
- AI must understand unit type advantages
- Siege weapons are required for efficient castle capture

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/UnitType.java` (create)
- `backend/src/main/java/com/barony/backend/model/Army.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/CombatService.java` (create)
- `backend/src/test/java/com/barony/backend/service/CombatServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 14: Implement Terrain Effects & Elevation

**Priority:** High  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Ticket 1 (Movement), Ticket 13 (Unit Types)  
**Target Version:** v1.2

### Description

Add terrain variety that affects movement and combat. Implement Plains, Forest, Mountains, Rivers, and Roads terrain types with unique movement costs, combat modifiers, and vision effects.

### Acceptance Criteria

**Backend:**
- Add terrain type field to `Tile` model with values: PLAINS, FOREST, MOUNTAINS, RIVER, ROAD
- Implement terrain movement costs:
  - Plains: Normal (1 tile/tick)
  - Forest: Slow (0.5 tiles/tick), -20% cavalry effectiveness
  - Mountains: Very slow (0.33 tiles/tick), impassable to siege weapons
  - Rivers: Must use bridges/fords (1 extra tick), -30% attack when crossing
  - Roads: Fast (1.5 tiles/tick)
- Implement terrain combat modifiers:
  - Forest: +20% defense
  - Mountains: +50% defense
  - Rivers: -30% attack when crossing
- Add terrain generation algorithm (procedural or hand-crafted)
- Update pathfinding to consider terrain movement costs
- Update AI to consider terrain in decision-making
- Add 10+ unit tests for terrain effects

**Frontend:**
- Implement unique textures/colors for each terrain type
- Add terrain overlay toggle to show movement costs
- Display visual indicators for terrain effects (icons, borders)
- Test terrain effects on movement and combat

**Documentation:**
- Update README.md with terrain types and effects
- Document movement cost table
- Document combat modifier table

### Technical Notes

- Terrain is static (doesn't change during game)
- Rivers require strategic bridge positions
- Mountains create natural defensive positions
- Vision effects can be deferred to Fog of War ticket

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/Tile.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/PathfindingService.java` (create)
- `backend/src/test/java/com/barony/backend/service/PathfindingServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 15: Implement Advanced AI with Difficulty Levels

**Priority:** Medium  
**Estimate:** 3-4 agent sessions  
**Dependencies:** Ticket 5 (Basic AI), Ticket 13 (Unit Types)  
**Target Version:** v1.2

### Description

Improve AI sophistication and add multiple difficulty levels (Easy, Normal, Hard, Expert) with distinct play styles and win rates.

### Acceptance Criteria

**Backend:**
- Implement difficulty levels with distinct behaviors:
  - Easy: AI makes occasional mistakes, reacts slowly, limited coordination (20-30% win rate)
  - Normal: Balanced decisions, reasonable reaction time (30-40% win rate, current AI)
  - Hard: Optimal decisions, immediate threat response, understands unit counters (50-60% win rate)
  - Expert: Near-optimal play, predictive analysis, multi-front coordination (70-80% win rate)
- Refactor AI into difficulty-based strategies
- Create `AISettings` configuration with difficulty parameter
- Implement advanced decision-making:
  - Multi-objective planning (attack, defend, expand simultaneously)
  - Threat assessment with lookahead (predict player moves)
  - Resource allocation optimization
  - Unit composition optimization
  - Tactical retreats when disadvantaged
- Add AI planning horizon (2-5 moves ahead)
- Add 10+ unit tests for AI difficulty levels

**Frontend:**
- Add difficulty selection in game setup menu
- Display AI difficulty indicator in HUD
- Test AI behavior at each difficulty level

**Documentation:**
- Update README.md with difficulty level descriptions
- Document AI strategy differences per difficulty

### Technical Notes

- Higher difficulty AI should feel challenging but fair
- AI should not cheat (no vision or resource advantages)
- AI planning horizon increases with difficulty

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/ai/` (new package)
- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/test/java/com/barony/backend/ai/` (new test package)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 16: Implement Fog of War

**Priority:** Medium  
**Estimate:** 3-4 agent sessions  
**Dependencies:** Ticket 2 (Territory), Ticket 13 (Unit Types)  
**Target Version:** v1.2

### Description

Add limited visibility to increase strategic uncertainty. Implement vision ranges per army, explored/unexplored tile tracking, and fog of war rendering.

### Acceptance Criteria

**Backend:**
- Implement vision calculation system:
  - Vision range per army (3-5 tiles based on unit composition)
  - Scouts/cavalry have extended vision range
  - Villages and castles provide vision range
- Track explored tiles per player
- Add visibility parameter to `GET /api/state` (returns only visible info)
- Vision calculation runs each tick
- Update AI to work with limited information (no cheating)
- Add fog of war toggle for debugging
- Add 8+ unit tests for vision mechanics

**Frontend:**
- Render fog of war overlay (darkened unexplored tiles)
- Gray out non-visible areas
- Show last-known information for explored but non-visible tiles
- Test fog of war during gameplay

**Documentation:**
- Update README.md with fog of war explanation
- Document vision ranges for unit types

### Technical Notes

- Vision calculation runs each tick
- AI also respects fog of war (no cheating)
- Performance optimization important (vision is expensive)
- Explored tiles remain visible but show outdated information
- Unexplored tiles are completely hidden

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/service/VisionService.java` (create)
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/test/java/com/barony/backend/service/VisionServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 17: Implement Technology/Research Tree

**Priority:** High  
**Estimate:** 5-6 agent sessions  
**Dependencies:** Tickets 1-8 (MVP complete)  
**Target Version:** v2.0

### Description

Implement a research system for long-term strategic progression. Add Military, Economic, and Civic research categories with permanent passive bonuses, research point generation, and a research queue system.

### Acceptance Criteria

**Backend:**
- Create `ResearchTree` and `ResearchNode` models
- Implement research categories:
  - Military: Advanced Training (+10% combat), Cavalry Tactics (cavalry speed +1), Fortifications (castle capture timer +2), Siege Engineering (siege weapons more effective)
  - Economic: Improved Agriculture (village income +1), Road Network (movement speed +25%), Taxation Systems (income from territory), Trade Routes (bonus income between connected villages)
  - Civic: Population Growth (village population cap +20), Loyalty Programs (army loyalty +10), Efficient Bureaucracy (policy cooldown -5 ticks), Espionage (reveal enemy army positions)
- Implement research point generation from castles (1 per tick)
- Add research progress tracking to `GameState`
- Implement research queue system with prerequisites for advanced research
- Apply research effects as permanent passive bonuses
- Add `POST /api/research` endpoint
- Add 10+ unit tests for research mechanics

**Frontend:**
- Add research tree UI panel (F1 key to open)
- Display visual tree showing dependencies
- Show research progress bars
- Implement tooltips showing research benefits
- Test research through gameplay

**Documentation:**
- Update README.md with research system explanation
- Document all research nodes, costs, and effects
- Document research prerequisites

### Technical Notes

- Research provides permanent passive bonuses
- Research queue allows planning ahead
- Prerequisites create meaningful progression paths
- Balance research costs to ensure meaningful choices

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/ResearchTree.java` (create)
- `backend/src/main/java/com/barony/backend/model/ResearchNode.java` (create)
- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/controller/GameController.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 18: Implement Expanded Resource System

**Priority:** High  
**Estimate:** 5-6 agent sessions  
**Dependencies:** Ticket 2 (Territory), Ticket 17 (Research Tree)  
**Target Version:** v2.0

### Description

Add additional resources beyond soldier generation. Implement Gold, Food, and Materials resource types with generation, consumption, storage, and trade mechanics.

### Acceptance Criteria

**Backend:**
- Add resource tracking to `GameState`:
  - Gold: Generated by owned villages (10 gold/tick), used for unit training, research, buildings, unlimited storage
  - Food: Generated by villages (5 food/tick), required to maintain armies (1 food/soldier/tick), shortage causes desertion, storage limit based on village count
  - Materials: Generated by special resource tiles, required for siege weapons and buildings, used for research and upgrades, storage limit based on castles
- Implement resource generation in tick processing
- Add resource costs to units and actions
- Implement resource storage and caps
- Add resource trade system (convert between resource types)
- Implement resource shortage warnings and effects
- Update AI to manage resources
- Add 10+ unit tests for resource mechanics

**Frontend:**
- Add resource display in HUD
- Add resource management panel (F2 key)
- Display visual indicators for resource-generating tiles
- Show warnings for resource shortages
- Test resource management through gameplay

**Documentation:**
- Update README.md with resource system explanation
- Document resource types, generation rates, and costs
- Document resource storage and trade mechanics

### Technical Notes

- Resource generation per tick scales with owned territory
- Food shortage causes gradual army desertion
- Material shortage blocks construction and training
- Trade system allows converting resources at a rate

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/ResourceService.java` (create)
- `backend/src/test/java/com/barony/backend/service/ResourceServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 19: Implement Larger Maps & Scenarios

**Priority:** Medium  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Ticket 14 (Terrain Effects)  
**Target Version:** v2.0

### Description

Support larger map sizes and predefined scenarios. Add map size selection, procedural map generation, and a scenario system with custom starting conditions and victory variants.

### Acceptance Criteria

**Backend:**
- Support dynamic map sizes in `GameState`:
  - Small: 10x10 (current MVP size)
  - Medium: 20x20
  - Large: 30x30
  - Huge: 40x40
- Implement procedural map generation algorithm with configurable parameters
- Add scenario loading system from JSON files
- Create predefined scenarios with custom starting conditions and victory condition variants
- Add `POST /api/game/start` endpoint with map/scenario options
- Add 8+ unit tests for map generation and scenario loading

**Frontend:**
- Add game setup menu with map size and scenario options
- Implement map preview rendering before game start
- Add zoom controls for larger maps
- Implement minimap for navigation on large maps
- Test different map sizes and scenarios

**Documentation:**
- Update README.md with map sizes and scenario descriptions
- Document scenario JSON format for custom scenarios
- Document map generation parameters

### Technical Notes

- Map generation should produce balanced maps with fair starting positions
- Larger maps require performance optimization
- Scenarios allow custom victory conditions beyond "capture all castles"
- Minimap is essential for maps larger than 20x20

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/MapGeneratorService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/GameController.java`
- `backend/src/test/java/com/barony/backend/service/MapGeneratorServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 20: Implement Building Construction System

**Priority:** Medium  
**Estimate:** 5-6 agent sessions  
**Dependencies:** Ticket 2 (Territory), Ticket 18 (Resources)  
**Target Version:** v2.0

### Description

Allow players to construct buildings in villages and castles. Implement village buildings (Barracks, Archery Range, Market, Farm, Walls) and castle buildings (Throne Room, Workshop, Stables, Library, Treasury) with construction costs, build queues, and passive bonuses.

### Acceptance Criteria

**Backend:**
- Create `Building` model with type, level, and location
- Implement village buildings:
  - Barracks: Train infantry faster (+1/tick)
  - Archery Range: Train archers (converts infantry)
  - Market: Generate gold (+20/tick)
  - Farm: Generate food (+10/tick)
  - Walls: Increase village defense (+30%)
- Implement castle buildings:
  - Throne Room: Unlock additional policies
  - Workshop: Train siege weapons
  - Stables: Train cavalry
  - Library: Increase research speed (+50%)
  - Treasury: Increase gold storage (+500)
- Implement building construction queue (one at a time per location)
- Add building construction costs (resources and time)
- Support building upgrades (3 levels)
- Implement building destruction in combat
- Add `POST /api/building/construct` endpoint
- Add 10+ unit tests for building mechanics

**Frontend:**
- Add building construction UI at villages/castles
- Display building icons on tiles
- Show construction progress indicators
- Add building upgrade UI
- Test building construction and effects through gameplay

**Documentation:**
- Update README.md with building types and effects
- Document building costs, construction times, and upgrade paths

### Technical Notes

- Building effects are passive bonuses active while building exists
- Buildings can be destroyed when village/castle is captured
- Building queue prevents spam construction
- Building upgrades multiply base effects

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/Building.java` (create)
- `backend/src/main/java/com/barony/backend/model/Tile.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/BuildingService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/GameController.java`
- `backend/src/test/java/com/barony/backend/service/BuildingServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 21: Implement Dynamic Events System

**Priority:** Low  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Ticket 18 (Resources), Ticket 2 (Territory)  
**Target Version:** v2.0

### Description

Add random events that affect gameplay across Weather, Social, Military, and Economic categories. Events occur periodically and introduce strategic uncertainty while respecting game balance.

### Acceptance Criteria

**Backend:**
- Create `GameEvent` model with effects and duration
- Implement event categories:
  - Weather: Harsh Winter (-50% movement for 10 ticks), Bountiful Harvest (+50% food for 20 ticks), Storm (blocks movement in area for 5 ticks)
  - Social: Peasant Revolt (village switches to neutral), Plague (-20% population for 15 ticks), Festival (+20% loyalty for 10 ticks)
  - Military: Mercenaries Available (hire army for gold), Desertion Wave (random army loses 20% soldiers), Reinforcements (free soldiers at castle)
  - Economic: Trade Boom (+50% gold for 15 ticks), Famine (food halved for 10 ticks), Gold Discovery (instant +200 gold)
- Implement random event generation (1 event per 30-50 ticks)
- Add event effects to tick processing
- Implement temporary effect system with duration tracking
- Support player choice events (accept/decline)
- Add event history log
- Add `GET /api/events` endpoint
- Add 10+ unit tests for event mechanics

**Frontend:**
- Add event notification popup
- Implement event choice dialog for accept/decline events
- Display event effects in HUD
- Show event history in game log
- Test events during gameplay

**Documentation:**
- Update README.md with event system explanation
- Document all event types and effects

### Technical Notes

- Events should respect game balance (not too punishing)
- Random event generation should be seeded for reproducibility
- Temporary effects tracked with tick-based duration
- Player choice events pause until responded to

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/GameEvent.java` (create)
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/EventService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/GameController.java`
- `backend/src/test/java/com/barony/backend/service/EventServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 22: Implement Campaign Mode

**Priority:** High  
**Estimate:** 6-8 agent sessions  
**Dependencies:** Ticket 19 (Maps & Scenarios), Ticket 15 (Advanced AI)  
**Target Version:** v2.1

### Description

Create a structured campaign with progressive scenarios. Implement 10-15 campaign missions with increasing difficulty, story context, varied mission objectives, and persistent progress.

### Acceptance Criteria

**Backend:**
- Implement campaign system with 10-15 missions
- Support mission types:
  - Conquest: Capture all enemy castles (standard)
  - Defense: Hold castle for N ticks against waves
  - Assassination: Eliminate specific enemy army
  - Economic: Reach gold/resource threshold
  - Time Trial: Win within tick limit
- Implement mission objectives beyond "capture all castles"
- Add persistent progress tracking between missions
- Implement campaign victory bonuses
- Support optional side objectives per mission
- Add 8+ unit tests for campaign mechanics

**Frontend:**
- Add campaign selection menu
- Display mission briefing with story context
- Show mission objectives during gameplay
- Display campaign progress screen
- Test complete campaign playthrough

**Documentation:**
- Update README.md with campaign mode description
- Document mission types and objectives

### Technical Notes

- Campaign missions should progressively introduce game mechanics
- Story context should be minimal (text-based, not cutscenes)
- Campaign progress should persist between game sessions
- Side objectives provide bonus rewards but are not required

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/Campaign.java` (create)
- `backend/src/main/java/com/barony/backend/model/Mission.java` (create)
- `backend/src/main/java/com/barony/backend/service/CampaignService.java` (create)
- `backend/src/test/java/com/barony/backend/service/CampaignServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 23: Implement Challenge Modes

**Priority:** Medium  
**Estimate:** 3-4 agent sessions  
**Dependencies:** Ticket 22 (Campaign Mode)  
**Target Version:** v2.1

### Description

Add special challenge modes with unique rules and leaderboards. Implement Survival, Economic Victory, One Army Challenge, Pacifist Run, and Speed Run challenges.

### Acceptance Criteria

**Backend:**
- Implement challenge types:
  - Survival: Endless waves of enemies, track survival duration
  - Economic Victory: Win without military conquest
  - One Army Challenge: Can only control 1 army entire game
  - Pacifist Run: Win without initiating combat
  - Speed Run: Win in minimum ticks
- Add leaderboards for each challenge (local storage)
- Track special unlocks/achievements for challenges
- Add 6+ unit tests for challenge mechanics

**Frontend:**
- Add challenge selection menu
- Display challenge rules and objectives during gameplay
- Show leaderboard with best scores
- Test each challenge mode through gameplay

**Documentation:**
- Update README.md with challenge mode descriptions
- Document challenge rules and scoring

### Technical Notes

- Challenges use modified victory conditions
- Leaderboards stored locally initially (online leaderboards deferred)
- Weekly rotating challenges can be added later
- Challenge validation must be tamper-resistant

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/Challenge.java` (create)
- `backend/src/main/java/com/barony/backend/service/ChallengeService.java` (create)
- `backend/src/test/java/com/barony/backend/service/ChallengeServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 24: Implement Advanced Ruler Mechanics

**Priority:** Medium  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Ticket 7 (Ruler Decisions)  
**Target Version:** v2.1

### Description

Expand the ruler decision system with deeper mechanics including a Reputation System, Noble Houses, Court Decisions, Legacy System, and Ruler Traits. Maintains CK-lite philosophy (no dynasties, no characters, system-driven).

### Acceptance Criteria

**Backend:**
- Implement Reputation System: Actions affect realm reputation (modifies loyalty/stability)
- Implement Noble Houses: Manage relationships with 3-5 noble houses (faction system)
- Implement Court Decisions: Regular policy choices with trade-offs
- Implement Legacy System: Previous game outcomes affect next game
- Implement Ruler Traits: Permanent modifiers based on playstyle
- Add reputation, faction, and trait tracking to `GameState`
- Add 10+ unit tests for advanced ruler mechanics

**Frontend:**
- Add ruler management panel with reputation display
- Display noble house relationships and faction standings
- Add court decision prompts
- Show ruler traits and their effects
- Test ruler mechanics through gameplay

**Documentation:**
- Update README.md with advanced ruler mechanics
- Document reputation, factions, court decisions, legacy, and traits
- Clarify CK-lite scope (what's included and excluded)

### Technical Notes

- Still maintains CK-lite philosophy (no dynasties, no characters, system-driven)
- Reputation affects diplomatic interactions with noble houses
- Noble houses have independent goals and can rebel
- Legacy system provides cross-game progression

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/RulerService.java` (create)
- `backend/src/test/java/com/barony/backend/service/RulerServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 25: Implement Achievements & Unlockables

**Priority:** Low  
**Estimate:** 2-3 agent sessions  
**Dependencies:** Ticket 22 (Campaign Mode), Ticket 23 (Challenge Modes)  
**Target Version:** v2.1

### Description

Add progression and rewards for players through an achievements system with Victory, Challenge, Exploration, Mastery, and Secret categories. Implement unlockable content including custom map themes, unit skins, starting bonuses, and scenario editor features.

### Acceptance Criteria

**Backend:**
- Implement achievement categories:
  - Victory: Win games, complete campaigns
  - Challenge: Complete specific challenge conditions
  - Exploration: Discover map features
  - Mastery: Perfect execution achievements
  - Secret: Hidden condition achievements
- Implement unlockable content system:
  - Custom map themes
  - Unit skins/colors
  - Starting bonuses
  - Scenario editor features
- Track achievement progress persistently
- Add 6+ unit tests for achievement tracking

**Frontend:**
- Add achievements panel showing progress
- Display achievement unlock notifications
- Show unlockable content in customization menu
- Test achievement unlocking through gameplay

**Documentation:**
- Update README.md with achievements system
- Document achievement categories (without spoiling secrets)

### Technical Notes

- Achievement progress persists across game sessions
- Secret achievements should not be revealed until unlocked
- Unlockables are cosmetic or convenience (not pay-to-win)

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/Achievement.java` (create)
- `backend/src/main/java/com/barony/backend/service/AchievementService.java` (create)
- `backend/src/test/java/com/barony/backend/service/AchievementServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 26: Implement Hotseat Multiplayer

**Priority:** High  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Ticket 16 (Fog of War)  
**Target Version:** v3.0

### Description

Enable local multiplayer with turn-based play. Support 2-4 players with hotseat mode (pass device between players), turn timers, and hidden information via fog of war.

### Acceptance Criteria

**Backend:**
- Implement turn-based gameplay (each player takes turn)
- Add configurable turn timer
- Implement hidden information (fog of war per player)
- Support 2-4 player games
- Add player turn management and validation
- Add 8+ unit tests for multiplayer mechanics

**Frontend:**
- Add multiplayer game setup menu (player count, turn timer)
- Display current player turn indicator
- Implement screen transition between player turns (hide previous player's view)
- Show turn timer countdown
- Test hotseat gameplay with 2-4 players

**Documentation:**
- Update README.md with hotseat multiplayer instructions
- Document turn-based gameplay rules
- Document player count and timer configuration

### Technical Notes

- Hotseat mode requires screen clearing between turns to hide information
- Turn timer prevents stalling
- Fog of war is essential for fair hotseat play
- 2-4 player support requires flexible map generation

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/GameState.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/service/MultiplayerService.java` (create)
- `backend/src/test/java/com/barony/backend/service/MultiplayerServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 27: Implement Online Multiplayer

**Priority:** Medium  
**Estimate:** 10-12 agent sessions  
**Dependencies:** Ticket 26 (Hotseat Multiplayer)  
**Target Version:** v3.0

### Description

Enable networked multiplayer gameplay with client-server architecture, lobby system, real-time or turn-based modes, and anti-cheat measures.

### Acceptance Criteria

**Backend:**
- Implement client-server networking architecture
- Add lobby system for game creation and joining
- Support real-time and turn-based multiplayer modes
- Implement game synchronization protocol
- Add player authentication system
- Implement reconnection handling for dropped connections
- Add anti-cheat measures (server-authoritative game state)
- Support save/resume multiplayer games
- Add 12+ unit tests for networking and synchronization

**Frontend:**
- Add lobby browser and game creation UI
- Display player connection status
- Implement real-time game state updates
- Show latency indicator
- Add chat system for player communication
- Test online multiplayer with multiple clients

**Documentation:**
- Update README.md with online multiplayer setup instructions
- Document networking requirements and port configuration
- Document matchmaking and lobby system

### Technical Notes

- WebSocket or TCP for real-time communication
- Server-authoritative architecture prevents cheating
- Reconnection should restore full game state
- Matchmaking is optional for MVP multiplayer

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/service/NetworkService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/MultiplayerController.java` (create)
- `backend/src/test/java/com/barony/backend/service/NetworkServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`

---

## Ticket 28: Implement Modding Support

**Priority:** Medium  
**Estimate:** 8-10 agent sessions  
**Dependencies:** Tickets 13, 14, 17, 20 (Unit Types, Terrain, Research, Buildings)  
**Target Version:** v3.0

### Description

Enable community-created content through a modding system. Support custom maps, unit types, buildings, technologies, events, policies, AI behaviors, and texture replacements.

### Acceptance Criteria

**Backend:**
- Implement mod loading system from JSON/files
- Support moddable content:
  - Custom maps and scenarios
  - Custom unit types and stats
  - Custom buildings and technologies
  - Custom events and policies
  - Custom AI behaviors
- Add mod validation and safety checks
- Implement mod ordering and conflict resolution
- Add 8+ unit tests for mod loading and validation

**Frontend:**
- Add mod browser/manager in-game
- Support texture/sprite replacements
- Display active mods and load order
- Show mod descriptions and compatibility information
- Test modded gameplay

**Documentation:**
- Update README.md with modding overview
- Create modding documentation for mod creators
- Document mod file format and structure
- Provide example mods

### Technical Notes

- Mods loaded from JSON/configuration files
- Mod validation prevents crashes and exploits
- Mod ordering determines override priority
- Safety checks prevent malicious mod content

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/service/ModService.java` (create)
- `backend/src/main/java/com/barony/backend/model/Mod.java` (create)
- `backend/src/test/java/com/barony/backend/service/ModServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `README.md`
- `MODDING.md` (create)

---

## Ticket 29: Implement Localization

**Priority:** Low  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Tickets 1-8 (MVP complete)  
**Target Version:** v3.0

### Description

Support multiple languages in the game. Implement string externalization, language selection, and initial translations for English, Spanish, French, German, and Chinese (Simplified).

### Acceptance Criteria

**Backend:**
- Implement string externalization system for all user-facing text
- Support localized number/date formats
- Add `GET /api/settings/language` and `POST /api/settings/language` endpoints
- Add 4+ unit tests for localization loading

**Frontend:**
- Add language selection in settings menu
- Implement string lookup from language files
- Support RTL text layout (for future languages)
- Translate all UI text for initial languages:
  - English (default)
  - Spanish
  - French
  - German
  - Chinese (Simplified)
- Test all languages in UI

**Documentation:**
- Update README.md with language support information
- Document how to add new translations
- Create translation guide for contributors

### Technical Notes

- All user-facing strings should be externalized from code
- Language files in standard format (properties, JSON, or YAML)
- RTL support enables future Arabic/Hebrew translations
- Community contributions welcome for additional languages

### Files to Create/Modify

- `backend/src/main/resources/i18n/` (new directory)
- `backend/src/main/java/com/barony/backend/service/LocalizationService.java` (create)
- `backend/src/test/java/com/barony/backend/service/LocalizationServiceTest.java` (create)
- `frontend/src/main/java/com/barony/frontend/FrontendApplication.java`
- `frontend/src/main/resources/i18n/` (new directory)
- `README.md`

---

## Implementation Order

**Recommended sequence:**

**MVP (v1.0):**
1. Ticket 1 (Movement) - Foundation for gameplay
2. Ticket 2 (Territory) - Core strategic mechanic
3. Ticket 3 (Army Management) - Tactical control
4. Ticket 4 (Win Conditions) - Complete game loop
5. Ticket 5 (AI Opponent) - Single-player experience
6. Ticket 6 (UI/UX) - Enhanced player experience
7. Ticket 7 (Ruler Decisions) - Strategic depth layer
8. Ticket 8 (Polish) - Final refinement

**Post-MVP v1.1 (Polish & QoL):**
9. Ticket 9 (Audio System) - Game atmosphere
10. Ticket 10 (Save/Load) - Game persistence
11. Ticket 11 (Visual Feedback) - Visual polish
12. Ticket 12 (Statistics & History) - Player information

**Post-MVP v1.2 (Enhanced Gameplay):**
13. Ticket 13 (Unit Types) - Tactical variety
14. Ticket 14 (Terrain Effects) - Strategic depth
15. Ticket 15 (Advanced AI) - Better opponent
16. Ticket 16 (Fog of War) - Strategic uncertainty

**Post-MVP v2.0 (Major Features):**
17. Ticket 17 (Technology Tree) - Long-term progression
18. Ticket 18 (Resource System) - Economic depth
19. Ticket 19 (Larger Maps) - Content variety
20. Ticket 20 (Building System) - Territorial investment
21. Ticket 21 (Dynamic Events) - Gameplay variety

**Post-MVP v2.1 (Content Expansion):**
22. Ticket 22 (Campaign Mode) - Structured gameplay
23. Ticket 23 (Challenge Modes) - Replayability
24. Ticket 24 (Advanced Ruler) - Deeper strategy
25. Ticket 25 (Achievements) - Player progression

**Post-MVP v3.0 (Multiplayer & Modding):**
26. Ticket 26 (Hotseat Multiplayer) - Local multiplayer
27. Ticket 27 (Online Multiplayer) - Networked play
28. Ticket 28 (Modding Support) - Community content
29. Ticket 29 (Localization) - Language support

**Total estimated effort:** 18-23 agent sessions (MVP) + 88-116 agent sessions (post-MVP)

---

## Notes for Copilot Coding Agent

- Each ticket is designed to be substantial but focused on a single feature area
- Tickets include specific file paths to modify for context
- Acceptance criteria are concrete and testable
- Technical notes provide implementation guidance
- Dependencies are clearly marked to ensure proper sequencing
- Tests should be written alongside implementation (TDD where possible)
- Documentation updates are included in each ticket to keep README.md current
- Post-MVP tickets reference the [ROADMAP.md](ROADMAP.md) for additional context

---

## Quick Reference: Creating GitHub Issues

To create these as GitHub issues:

1. Copy the title (e.g., "Implement Army Movement System with Pathfinding")
2. Copy the Description section as the issue description
3. Add label: `enhancement`
4. Add label: `mvp` (Tickets 1-8) or `post-mvp` (Tickets 9-29)
5. Set milestone: `MVP v1.0` (Tickets 1-8), `v1.1` (Tickets 9-12), `v1.2` (Tickets 13-16), `v2.0` (Tickets 17-21), `v2.1` (Tickets 22-25), or `v3.0` (Tickets 26-29)
6. Assign to: Copilot Coding Agent or developer
7. Set priority based on ticket priority field

Example labels to create:
- `enhancement`
- `mvp`
- `post-mvp`
- `backend`
- `frontend`
- `documentation`
- `testing`
