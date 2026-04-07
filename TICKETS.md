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

**Web Client:**
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
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
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

**Web Client:**
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
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
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

**Web Client:**
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
- Web client can show overlapping circles offset by a few pixels for multiple armies

### Files to Modify

- `backend/src/main/java/com/barony/backend/model/Command.java`
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/test/java/com/barony/backend/service/GameServiceTest.java`
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
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

**Web Client:**
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
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
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

**Web Client:**
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

**Web Client:**
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
- Add text rendering for labels
- Test all mouse interactions and UI displays

**Backend:**
- No backend changes required (all web client)

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

- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
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

**Web Client:**
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
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
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

**Web Client:**
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

## Implementation Order

**Recommended sequence:**
1. Ticket 1 (Movement) - Foundation for gameplay
2. Ticket 2 (Territory) - Core strategic mechanic
3. Ticket 3 (Army Management) - Tactical control
4. Ticket 4 (Win Conditions) - Complete game loop
5. Ticket 5 (AI Opponent) - Single-player experience
6. Ticket 6 (UI/UX) - Enhanced player experience
7. Ticket 7 (Ruler Decisions) - Strategic depth layer
8. Ticket 8 (Polish) - Final refinement

**Total estimated effort:** 18-23 agent sessions (adds ruler decision system to original scope)

---

## Notes for Copilot Coding Agent

- Each ticket is designed to be substantial but focused on a single feature area
- Tickets include specific file paths to modify for context
- Acceptance criteria are concrete and testable
- Technical notes provide implementation guidance
- Dependencies are clearly marked to ensure proper sequencing
- Tests should be written alongside implementation (TDD where possible)
- Documentation updates are included in each ticket to keep README.md current

---

## Quick Reference: Creating GitHub Issues

To create these as GitHub issues:

1. Copy the title (e.g., "Implement Army Movement System with Pathfinding")
2. Copy the Description section as the issue description
3. Add label: `enhancement`
4. Add label: `mvp`
5. Set milestone: `MVP v1.0`
6. Assign to: Copilot Coding Agent or developer
7. Set priority based on ticket priority field

Example labels to create:
- `enhancement`
- `mvp`
- `backend`
- `web-client`
- `documentation`
- `testing`

---

## Post-MVP Improvement Tickets

The following tickets document potential improvements to the UI, gameplay, and narrative elements of Barony. They are intended as post-MVP enhancements and do not need to be addressed during initial development.

---

## UI Improvement Tickets

---

## Ticket 9: Accessibility and Visual Customization

**Priority:** Medium  
**Estimate:** 2-3 agent sessions  
**Dependencies:** Ticket 6 (requires UI foundation)

### Description

Improve the game's accessibility and allow players to customize the visual experience. This includes a colorblind-friendly mode, adjustable font sizes, high-contrast options, and selectable color themes so the game is enjoyable for the widest possible audience.

### Acceptance Criteria

**Web Client:**
- Add settings menu accessible from the main screen (configurable keybinding; suggest `F9` or `Esc → Settings`; avoid `F11` as it is the system fullscreen shortcut on most platforms)
- Implement colorblind mode with three presets:
  - Deuteranopia (red-green, replaces red/green with orange/blue)
  - Protanopia (red-blind, replaces red tones with high-contrast yellow)
  - Tritanopia (blue-yellow, replaces blue/yellow with pink/green)
- Implement high-contrast UI theme (dark background, bright text borders)
- Add font size options: Small, Medium (default), Large
- Add color theme selector with at least two built-in themes (Classic, Dark)
- Persist settings across sessions (save to `.barony/settings.json` in the user's home directory)
- Show live preview of theme/color changes in settings panel
- All existing UI elements must respect theme and accessibility settings

**Backend:**
- No backend changes required

**Documentation:**
- Update README.md with a note on accessibility settings
- Document keybinding for settings panel

### Technical Notes

- Store color palette as named constants so theme switching replaces the palette map rather than individual calls
- Use a single `ThemeManager` class to centralize color lookups; each render call fetches colors from it
- Font rendering should scale uniformly from a base size constant
- Colorblind presets should only affect faction colors and map ownership indicators, not status icons

### Files to Create/Modify

- `web-client/src/main/java/com/barony/webclient/ui/SettingsPanel.java` (create)
- `web-client/src/main/java/com/barony/webclient/rendering/ThemeManager.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `web-client/src/main/resources/settings/` (new directory for default theme JSON files)
- `README.md`

---

## Ticket 10: Interactive Tutorial and Onboarding

**Priority:** Medium  
**Estimate:** 2 agent sessions  
**Dependencies:** Tickets 4, 6, 7 (requires complete gameplay loop and UI)

### Description

Add a guided interactive tutorial that walks a new player through their first game. The tutorial should highlight UI elements, explain mechanics step by step, and complete with a short scripted scenario before handing control to the player.

### Acceptance Criteria

**Web Client:**
- Add "Tutorial" option on the main menu
- Implement a `TutorialManager` that tracks tutorial step progress
- Display contextual tutorial tooltips (highlighted panel with arrow pointer):
  - Step 1: Select an army (highlight army circle, explain click-to-select)
  - Step 2: Move the army (highlight destination tile, explain click-to-move)
  - Step 3: Capture a village (explain village ownership and soldier generation)
  - Step 4: Advance a turn (explain `SPACE` keybinding)
  - Step 5: Open policy menu (explain `P` keybinding and policy effects)
  - Step 6: Win condition (explain castle capture timer)
- Block player actions that are out of sequence during the tutorial
- Allow the player to skip the tutorial at any step
- Show a "Tutorial Complete" screen at the end with a "Start New Game" button
- Store tutorial-completed flag in settings so it is not auto-launched again

**Backend:**
- No backend changes required (tutorial uses existing endpoints)

**Documentation:**
- Update PLAYER_GUIDE.md with tutorial information
- Add "First Time?" note to README.md pointing to tutorial

### Technical Notes

- Tutorial steps are defined in a JSON config file (`tutorial-steps.json`) for easy editing
- Tutorial overlay renders on top of the game canvas without blocking the underlying render
- Tutorial state is web-client-only (no backend session impact)
- "Skip Tutorial" button must always be reachable via keyboard (suggest `Ctrl+T`)

### Files to Create/Modify

- `web-client/src/main/java/com/barony/webclient/tutorial/TutorialManager.java` (create)
- `web-client/src/main/java/com/barony/webclient/tutorial/TutorialStep.java` (create)
- `web-client/src/main/resources/tutorial/tutorial-steps.json` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `PLAYER_GUIDE.md`
- `README.md`

---

## Ticket 11: Minimap and Enhanced Map Navigation

**Priority:** Low  
**Estimate:** 2 agent sessions  
**Dependencies:** Ticket 6 (requires UI foundation)

### Description

Add a minimap to the HUD showing the full game map at reduced scale, and implement map zoom and scroll controls so players can navigate larger maps comfortably without losing situational awareness.

### Acceptance Criteria

**Web Client:**
- Render a minimap in the lower-right corner of the screen (configurable size, default 180×180 px)
- Minimap must show:
  - Tile ownership colors (player blue, enemy red, neutral gray)
  - Army positions as colored dots
  - Castle and village icons (single-pixel or small icon)
  - Current viewport rectangle as a white outline overlay
- Clicking on the minimap centers the main viewport on that map position
- Implement viewport scroll via:
  - Arrow keys (pan 1 tile per key press)
  - Click-and-drag on empty map area
  - WASD keys as alternative pan
- Implement zoom controls:
  - Mouse scroll wheel to zoom in/out
  - `+` / `-` keyboard keys
  - Zoom range: 0.5× to 2× (default 1×)
- Minimap toggles with `M` key
- Add zoom level indicator in HUD (e.g., "Zoom: 100%")

**Backend:**
- No backend changes required

**Documentation:**
- Update README.md with minimap controls
- Update PLAYER_GUIDE.md with navigation section

### Technical Notes

- Minimap renders a scaled-down copy of the current tile grid each frame
- Viewport rectangle on minimap reflects the actual visible tile range
- Zoom changes tile render size; army selection click coordinates must be recalculated accordingly
- Pan and zoom state lives in a `CameraState` value object

### Files to Create/Modify

- `web-client/src/main/java/com/barony/webclient/rendering/MinimapRenderer.java` (create)
- `web-client/src/main/java/com/barony/webclient/rendering/CameraState.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `README.md`
- `PLAYER_GUIDE.md`

---

## Gameplay Improvement Tickets

---

## Ticket 12: Campaign Mode with Progressive Missions

**Priority:** Medium  
**Estimate:** 4-5 agent sessions  
**Dependencies:** Tickets 1-7 (requires complete MVP feature set)

### Description

Add a structured single-player campaign consisting of a series of scenarios with escalating difficulty, custom starting conditions, and varied victory objectives. The campaign gives players a directed path to learn the game while providing a meaningful challenge arc.

### Acceptance Criteria

**Backend:**
- Add `ScenarioConfig` model defining:
  - Map layout (tile types, starting armies, village/castle positions)
  - Player and AI starting conditions (armies, resources, policies)
  - Victory condition type: `CONQUEST`, `SURVIVAL`, `ECONOMIC`, `TIME_TRIAL`
  - Optional defeat condition (e.g., "do not lose your castle for 50 ticks")
  - Turn/tick limit (optional)
- Add `CampaignState` model tracking:
  - Current mission index
  - Missions completed
  - Overall campaign score
- Add `POST /api/campaign/start` endpoint (accepts mission index)
- Add `GET /api/campaign/state` endpoint
- Implement at least 5 campaign scenarios stored as JSON in `backend/src/main/resources/scenarios/`
- Add 8+ unit tests for scenario loading and victory condition checking

**Web Client:**
- Add "Campaign" button on the main menu
- Add mission selection screen listing available missions, locked/unlocked status, and completion stars
- Show mission briefing screen before each scenario (map preview + objectives text)
- Display mission objective progress in HUD during gameplay (e.g., "Castles captured: 1/2")
- Show mission completion screen with score and "Next Mission" / "Replay" buttons
- Lock subsequent missions until the preceding one is completed

**Documentation:**
- Add Campaign section to PLAYER_GUIDE.md
- Document `ScenarioConfig` JSON schema in DOCS.md

### Technical Notes

- Scenario JSON files are loaded at startup and cached in memory
- Campaign progress is stored in the session (not persisted to disk for MVP)
- Victory condition checking runs inside the existing `tick()` after `checkWinCondition()`
- Use the existing session API endpoints with scenario-aware initialization

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/ScenarioConfig.java` (create)
- `backend/src/main/java/com/barony/backend/model/CampaignState.java` (create)
- `backend/src/main/java/com/barony/backend/service/CampaignService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/CampaignController.java` (create)
- `backend/src/main/resources/scenarios/` (new directory with 5 mission JSON files)
- `backend/src/test/java/com/barony/backend/service/CampaignServiceTest.java` (create)
- `web-client/src/main/java/com/barony/webclient/ui/CampaignMenu.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `PLAYER_GUIDE.md`
- `DOCS.md`

---

## Ticket 13: Achievement System

**Priority:** Low  
**Estimate:** 2-3 agent sessions  
**Dependencies:** Tickets 1-7 (requires complete gameplay metrics)

### Description

Add an achievement system that tracks player accomplishments across play sessions and rewards engagement with unlockable cosmetic content. Achievements give players long-term goals and record notable gameplay moments.

### Acceptance Criteria

**Backend:**
- Define `Achievement` model with: id, name, description, category, unlock condition
- Define achievement categories: `VICTORY`, `MILITARY`, `ECONOMIC`, `CHALLENGE`, `EXPLORATION`
- Implement at least 20 achievements, for example:
  - "First Blood" – Win your first game
  - "Speed Conquest" – Win a game in under 50 ticks
  - "Iron Fist" – Capture 10 villages in a single game
  - "Pacifist" – Win without initiating any combat
  - "Policy Master" – Change policies in all three categories in one game
  - "Outnumbered" – Win a game where the AI had double your army size at any point
- Add `AchievementService` that evaluates unlock conditions after each tick
- Add `GET /api/achievements` endpoint (returns all achievements with unlock status)
- Persist unlocked achievements to a JSON file in the user's home directory (`.barony/achievements.json`)
- Add 6+ unit tests for achievement condition checking

**Web Client:**
- Add achievement panel accessible via `F2` key
- Display achievements grouped by category with lock/unlock icons
- Show achievement notification toast (bottom of screen, 3-second display) when an achievement unlocks
- Show achievement progress counters for multi-step achievements (e.g., "Villages captured: 7/10")

**Documentation:**
- List all achievements in PLAYER_GUIDE.md
- Add achievements section to DOCS.md

### Technical Notes

- Achievement conditions are evaluated as predicates against `GameState` and `GameStatistics`
- Unlock is idempotent (re-evaluating on a completed achievement does not fire the notification again)
- Achievements are read-only once unlocked; no revocation mechanic
- Cosmetic unlocks (map themes, army color palette) are toggled via `ThemeManager` from Ticket 9

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/Achievement.java` (create)
- `backend/src/main/java/com/barony/backend/service/AchievementService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/AchievementController.java` (create)
- `backend/src/test/java/com/barony/backend/service/AchievementServiceTest.java` (create)
- `web-client/src/main/java/com/barony/webclient/ui/AchievementPanel.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `PLAYER_GUIDE.md`
- `DOCS.md`

---

## Ticket 14: Difficulty Settings and Replayability Options

**Priority:** Medium  
**Estimate:** 2 agent sessions  
**Dependencies:** Ticket 5 (requires AI opponent)

### Description

Add configurable difficulty settings and new-game options so players can tailor the challenge level and game variety. Provide map size, AI difficulty, starting conditions, and optional handicap settings accessible from a pre-game setup menu.

### Acceptance Criteria

**Backend:**
- Add `GameConfig` model with fields:
  - `mapSize`: `SMALL` (10×10), `MEDIUM` (15×15), `LARGE` (20×20)
  - `aiDifficulty`: `EASY`, `NORMAL`, `HARD`
  - `playerStartSoldiers`: integer (default 20)
  - `aiStartSoldiers`: integer (default 20)
  - `handicap`: `NONE`, `PLAYER_ADVANTAGE` (+50% start soldiers for player), `AI_ADVANTAGE` (+50% for AI)
- Update `POST /api/session/reset` to accept a `GameConfig` JSON body
- Implement AI behavior variations per difficulty:
  - **Easy**: AI skips attack decisions 40% of the time and never uses army splitting (the `SPLIT` command from Ticket 3 that divides one army into two)
  - **Normal**: Current behavior (existing Ticket 5 implementation)
  - **Hard**: AI evaluates all moves optimally, uses army splitting to pursue multiple objectives simultaneously, and reacts to threats within 2 tiles
- Update map generation to support configurable sizes
- Add 6+ unit tests for difficulty-based AI behavior differences and map size generation

**Web Client:**
- Add "New Game" setup screen before starting a game (accessible from main menu and after game over)
- Provide UI controls for all `GameConfig` fields with descriptions of each option
- Show estimated game length based on settings (e.g., "~10 min on Normal/Medium")
- Persist last-used settings in a config file under the user's home directory (for example, `.barony/settings.json`)

**Documentation:**
- Add difficulty descriptions to PLAYER_GUIDE.md
- Document `GameConfig` in DOCS.md

### Technical Notes

- Difficulty AI variations are implemented as strategy subclasses of a common `AIStrategy` interface
- Map size only affects grid dimensions; all gameplay logic remains map-size-agnostic
- Handicap modifies only starting army size; economy and mechanics are unchanged

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/GameConfig.java` (create)
- `backend/src/main/java/com/barony/backend/service/GameService.java`
- `backend/src/main/java/com/barony/backend/ai/AIStrategy.java` (create)
- `backend/src/main/java/com/barony/backend/ai/EasyAIStrategy.java` (create)
- `backend/src/main/java/com/barony/backend/ai/NormalAIStrategy.java` (create)
- `backend/src/main/java/com/barony/backend/ai/HardAIStrategy.java` (create)
- `backend/src/test/java/com/barony/backend/ai/AIStrategyTest.java` (create)
- `web-client/src/main/java/com/barony/webclient/ui/NewGameMenu.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `PLAYER_GUIDE.md`
- `DOCS.md`

---

## Narrative Elements Tickets

---

## Ticket 15: World-Building Lore and Realm Flavor Text

**Priority:** Low  
**Estimate:** 1-2 agent sessions  
**Dependencies:** Ticket 6 (requires HUD and tooltip infrastructure)

### Description

Add lore and world-building text that gives the game world a sense of history and place. This includes named realms, named rulers, village and castle names displayed on the map, and a lore codex accessible from the menu. Flavor text enriches the game without adding mechanical complexity.

### Acceptance Criteria

**Backend:**
- Add `LoreService` that loads lore data from JSON at startup
- Define a `LoreData` model holding:
  - Realm names for each player faction (e.g., "Kingdom of Aldenmoor" for Player 1, "Duchy of Varnholt" for Player 2)
  - Ruler names and short epithets (e.g., "Queen Isolde the Unyielding")
  - A pool of 20+ village names (e.g., "Millhaven", "Thornford", "Crestwick")
  - A pool of 10+ castle names (e.g., "Stormwall Keep", "Redwall Fortress"); story-mission-specific castle names (e.g., "Irongate") should be excluded from this random pool to avoid naming conflicts with campaign scenarios
  - 30+ flavor text strings for game events (village capture, battle, policy change)
- Assign village/castle names at map initialization and include them in `Tile` model (`name` field)
- Add `GET /api/lore` endpoint returning realm and ruler names for the current session

**Web Client:**
- Display village and castle names as small text labels below/above their tile on the map
- Show realm name and ruler name in the HUD header (e.g., "Aldenmoor – Turn 12")
- Display a random flavor text string in the status bar for major events:
  - Village captured: "The villagers of {villageName} submit to {realm}."
  - Castle captured: "{ruler} raises their banner over {castleName}."
  - Policy change: "Word spreads of the new {policyName} decree across {realm}."
  - Victory: "Songs will be sung of {realm}'s conquest."
  - Defeat: "The chronicles record the fall of {realm}."
- Add "Lore Codex" screen accessible from the menu (`F3` key) with realm descriptions, ruler biography stubs, and a world-history paragraph
- Lore text is displayed only; it does not affect game mechanics

**Documentation:**
- Add a "Lore & Setting" section to PLAYER_GUIDE.md
- Store all lore strings in `backend/src/main/resources/lore/lore-data.json` for easy editing

### Technical Notes

- Lore JSON is the single source of truth; do not hardcode names in Java source files
- Village name assignment uses a shuffled pool; if the pool is exhausted, fall back to "Village #N"
- Flavor text selection uses a seeded random tied to the game session (repeatable for the same session)
- Realm and ruler names are fixed per session (not re-randomized mid-game)

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/LoreData.java` (create)
- `backend/src/main/java/com/barony/backend/service/LoreService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/LoreController.java` (create)
- `backend/src/main/java/com/barony/backend/model/Tile.java` (add `name` field)
- `backend/src/main/resources/lore/lore-data.json` (create)
- `web-client/src/main/java/com/barony/webclient/ui/LoreCodex.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `PLAYER_GUIDE.md`

---

## Ticket 16: Narrative Event System with Player Choices

**Priority:** Low  
**Estimate:** 3-4 agent sessions  
**Dependencies:** Tickets 7, 15 (requires ruler decision system and lore foundation)

### Description

Add a narrative event system that periodically presents the player with story-driven scenarios requiring a decision. Each event has flavor text, two or three choices, and mechanical consequences expressed through the existing ruler decision/modifier system. Events make the player feel like a ruler responding to their realm rather than just a game piece.

### Acceptance Criteria

**Backend:**
- Define `NarrativeEvent` model with:
  - `id`, `title`, `description` (flavor text, 2-4 sentences)
  - `triggerCondition`: `TICK_THRESHOLD`, `VILLAGE_CAPTURED`, `ARMY_DEFEATED`, `POLICY_CHANGED`, `RANDOM`
  - `triggerValue`: numeric or string parameter for the condition
  - `choices`: list of `EventChoice` (label string, short outcome description, list of `StatModifier`)
  - `cooldownTicks`: minimum ticks before this event can recur (default 30)
- Define `StatModifier` model matching existing stat system (stability, morale, loyalty, income multiplier)
- Define `EventChoice` model (label, outcome description, list of stat modifiers)
- Add `NarrativeEventService`:
  - Load events from `backend/src/main/resources/narrative/events.json` at startup
  - Evaluate trigger conditions each tick; queue up to 1 event at a time
  - Apply chosen modifiers via the existing `RulerDecision` stat pipeline
  - Track event history (last 10 events) on `GameState`
- Add `GET /api/narrative/event` endpoint (returns pending event, or 204 if none)
- Add `POST /api/narrative/event/choose` endpoint (accepts event id and choice index)
- Implement at least 15 narrative events covering:
  - Economic crises and windfalls
  - Military morale events
  - Population and stability events
  - Village-specific story hooks
  - Rival ruler taunts and diplomatic gestures (flavor-only; no multiplayer mechanic)
- Add 8+ unit tests for event trigger evaluation and modifier application

**Web Client:**
- Display a narrative event modal dialog when a pending event is available:
  - Title and flavor text (with realm/village/ruler names filled in from `LoreService`)
  - Choice buttons (2-3) each showing the choice label and a brief effect preview
  - "Dismiss" option (treated as neutral choice with no modifier)
- Animate modal slide-in from the top of the screen
- Add event history log accessible via `F4` key showing the last 10 events and choices
- Pause tick auto-advance while a modal is open (if auto-advance is implemented)

**Documentation:**
- Document the `NarrativeEvent` JSON schema in DOCS.md
- Add "Events" section to PLAYER_GUIDE.md explaining event system
- List example events in PLAYER_GUIDE.md so players know what to expect

### Technical Notes

- Events are purely additive to the existing stat system; they do not introduce new mechanics
- Event JSON is the single source of truth; do not hardcode event strings in Java
- Flavor text supports simple token substitution (`{realm}`, `{ruler}`, `{village}`, `{castle}`) resolved by `LoreService`
- The "Dismiss" fallback ensures the game is never blocked waiting for an event response
- Trigger condition evaluation is lightweight (no lookahead, no pathfinding)

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/NarrativeEvent.java` (create)
- `backend/src/main/java/com/barony/backend/model/EventChoice.java` (create)
- `backend/src/main/java/com/barony/backend/model/StatModifier.java` (create)
- `backend/src/main/java/com/barony/backend/service/NarrativeEventService.java` (create)
- `backend/src/main/java/com/barony/backend/controller/NarrativeEventController.java` (create)
- `backend/src/main/resources/narrative/events.json` (create)
- `backend/src/test/java/com/barony/backend/service/NarrativeEventServiceTest.java` (create)
- `web-client/src/main/java/com/barony/webclient/ui/NarrativeEventModal.java` (create)
- `web-client/src/main/java/com/barony/webclient/ui/EventHistoryLog.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `PLAYER_GUIDE.md`
- `DOCS.md`

---

## Ticket 17: Campaign Story Missions with Narrative Framing

**Priority:** Low  
**Estimate:** 2-3 agent sessions  
**Dependencies:** Tickets 12, 15, 16 (requires campaign mode, lore, and narrative event systems)

### Description

Expand the campaign mode (Ticket 12) with story-driven mission briefings, mid-mission narrative events, and post-mission epilogue text. Each mission feels like a chapter in a larger story of conquest, creating a narrative arc across the full campaign.

### Acceptance Criteria

**Backend:**
- Extend `ScenarioConfig` with narrative fields:
  - `storyTitle`: mission title shown on the selection screen
  - `briefingText`: multi-paragraph pre-mission briefing (2-5 paragraphs)
  - `epilogueVictoryText`: story text shown on mission victory (1-2 paragraphs)
  - `epilogueDefeatText`: story text shown on mission defeat (1-2 paragraphs)
  - `midMissionEvents`: list of `NarrativeEvent` IDs to inject at specific tick thresholds
- Implement mission-scoped narrative event injection in `CampaignService` that fires `midMissionEvents` at their configured tick thresholds
- Add `GET /api/campaign/mission/{index}/briefing` endpoint
- Implement 5 story missions forming a connected narrative:
  - Mission 1: "The Disputed Valley" – Introductory conquest (standard victory)
  - Mission 2: "Siege of Irongate" – Castle defense (survival for 60 ticks)
  - Mission 3: "The Merchant's Road" – Economic victory (reach 500 gold)
  - Mission 4: "Winter Advance" – Conquest with harsh winter event at tick 25
  - Mission 5: "The Final Stand" – AI starts with 2× advantage; player must overcome
- Write cohesive lore connecting all 5 missions in `backend/src/main/resources/scenarios/campaign-lore.md`

**Web Client:**
- Display mission briefing screen before each mission:
  - Full-screen or large modal with story text and map preview
  - "Begin Mission" and "Back to Campaign" buttons
  - Background uses map theme from `ThemeManager`
- Display epilogue screen after mission completion/failure:
  - Victory or defeat flavor illustration placeholder (simple colored banner)
  - Victory/defeat epilogue text
  - Campaign score summary
  - "Next Mission" or "Retry" button
- Inject mid-mission narrative events via existing `NarrativeEventModal` (Ticket 16)

**Documentation:**
- Add campaign story summary to PLAYER_GUIDE.md
- Document `storyTitle`, `briefingText`, and `epilogueVictoryText`/`epilogueDefeatText` fields in DOCS.md

### Technical Notes

- Briefing and epilogue text supports `{realm}`, `{ruler}`, `{castle}` token substitution from `LoreService`
- Mid-mission narrative events reuse the `NarrativeEventService` pipeline; no separate injection code needed beyond scheduling
- All story text lives in the scenario JSON files, not in Java source
- The 5-mission narrative arc should stand alone; it does not require external knowledge of other game modes

### Files to Create/Modify

- `backend/src/main/java/com/barony/backend/model/ScenarioConfig.java` (add narrative fields)
- `backend/src/main/java/com/barony/backend/service/CampaignService.java` (add mid-mission event injection)
- `backend/src/main/java/com/barony/backend/controller/CampaignController.java` (add briefing endpoint)
- `backend/src/main/resources/scenarios/mission-01-disputed-valley.json` (create)
- `backend/src/main/resources/scenarios/mission-02-siege-of-irongate.json` (create)
- `backend/src/main/resources/scenarios/mission-03-merchants-road.json` (create)
- `backend/src/main/resources/scenarios/mission-04-winter-advance.json` (create)
- `backend/src/main/resources/scenarios/mission-05-final-stand.json` (create)
- `backend/src/main/resources/scenarios/campaign-lore.md` (create)
- `web-client/src/main/java/com/barony/webclient/ui/MissionBriefingScreen.java` (create)
- `web-client/src/main/java/com/barony/webclient/ui/MissionEpilogueScreen.java` (create)
- `web-client/src/main/java/com/barony/webclient/WebClientApplication.java`
- `PLAYER_GUIDE.md`
- `DOCS.md`

---

## Post-MVP Implementation Order

**Recommended sequence for post-MVP tickets:**

1. Ticket 9 (Accessibility & Visual Customization) – foundational for all subsequent UI work
2. Ticket 14 (Difficulty Settings) – high player-impact, low complexity
3. Ticket 11 (Minimap & Navigation) – quality-of-life for growing map sizes
4. Ticket 10 (Tutorial) – helps new players; depends on stable UI
5. Ticket 13 (Achievement System) – extends engagement; depends on full game loop
6. Ticket 12 (Campaign Mode) – first major content addition
7. Ticket 15 (Lore & Flavor Text) – narrative foundation for Tickets 16 and 17
8. Ticket 16 (Narrative Event System) – interactive narrative layer
9. Ticket 17 (Story Missions) – full narrative campaign experience

**Total estimated effort:** 21-30 additional agent sessions

---

## Post-MVP Labels

When creating these as GitHub issues, use the following additional labels:

- `ui-improvement`
- `gameplay-improvement`
- `narrative`
- `post-mvp`
- `accessibility`
- `campaign`
- `lore`
