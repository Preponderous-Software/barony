# Barony Prototype - GitHub Issues for MVP Development

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
- `frontend`
- `documentation`
- `testing`
