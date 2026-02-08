# Barony Prototype - Minimum Viable Product (MVP)

## Vision

Transform the current technical prototype into an engaging single-player strategy game where players command armies, capture territory, and compete against AI opponents for control of the map.

## Current State (Prototype v0.1)

The prototype currently supports:
- ✅ 10x10 grid with CASTLE, VILLAGE, and EMPTY tiles
- ✅ Two armies (Player 1 and Player 2)
- ✅ Instant army teleportation via REST command
- ✅ Villages generate 1 soldier per tick when occupied
- ✅ Combat via mutual soldier reduction when armies share location
- ✅ Basic LWJGL rendering (colored tiles + circles for armies)
- ✅ REST API (GET /state, POST /tick, POST /command)

## MVP Feature Set

### 1. Army Movement & Pathfinding

**Current:** Armies teleport instantly to any position via REST command  
**MVP:** Armies move 1 tile per tick toward a destination

#### Features
- **Pathfinding:** Implement A* or simple Manhattan distance pathfinding
- **Movement Queue:** Armies remember their destination and move each tick
- **Movement Visualization:** Players see armies traveling across the grid
- **Movement Blocking:** Armies cannot move through impassable terrain (optional for MVP)

#### Backend Changes
- Add `Army.destinationX` and `Army.destinationY` fields
- Add `Army.isMoving()` method
- Modify `GameService.tick()` to process movement for all armies
- Update `Command` to accept destination coordinates
- Add movement validation (can't move through enemy castles, optional)

#### Frontend Changes
- Smooth interpolation between grid positions (optional for v1, can defer to v2)
- Visual indicator showing army destination (selection box or path preview)

#### Tasks
- [ ] Backend: Add destination fields to Army model
- [ ] Backend: Implement pathfinding algorithm (Manhattan distance for MVP)
- [ ] Backend: Add movement processing in tick() method
- [ ] Backend: Update command validation for movement
- [ ] Backend: Add unit tests for movement mechanics (8-10 tests)
- [ ] Frontend: Add visual feedback for army destination
- [ ] Frontend: Test movement commands and rendering
- [ ] Documentation: Update README with movement mechanics

---

### 2. Army Management & Composition

**Current:** Army size is a single integer (soldier count)  
**MVP:** Armies show size and can be split into multiple armies

#### Features
- **Army Display:** Show soldier count directly on army visualization
- **Army Splitting:** Split an army into two armies at the same location
- **Army Merging:** Automatically merge friendly armies at the same location
- **Minimum Army Size:** Require at least 1 soldier per army (can't split to 0)

#### Backend Changes
- Add `POST /command` support for SPLIT command
- Add `Command.splitAmount` field (how many soldiers to split off)
- Implement `GameService.splitArmy(armyId, soldierCount)` 
- Implement automatic merging in `tick()` for co-located friendly armies
- Update combat to handle multiple armies per player per location

#### Frontend Changes
- Display soldier count as text label over army circles
- Add keyboard shortcut for split command (e.g., `S` key)
- Add UI prompt for split amount (console input for MVP, can be UI later)
- Show multiple armies at same location (offset circles or stack indicator)

#### Tasks
- [ ] Backend: Add SPLIT command type to Command enum
- [ ] Backend: Implement splitArmy() in GameService
- [ ] Backend: Implement automatic army merging
- [ ] Backend: Add unit tests for splitting/merging (6-8 tests)
- [ ] Frontend: Display soldier count on armies
- [ ] Frontend: Add split command input handling
- [ ] Frontend: Render multiple armies at same location
- [ ] Frontend: Add unit tests for army display
- [ ] Documentation: Update README with army management

---

### 3. Territory Control & Village Mechanics

**Current:** Villages generate soldiers only when occupied (any army present)  
**MVP:** Villages generate soldiers only for the owning player

#### Features
- **Village Ownership:** Villages belong to the player whose army occupies them
- **Persistent Ownership:** Villages retain ownership until captured by enemy
- **Visual Ownership:** Color-code villages (blue=player 1, red=player 2, brown=neutral)
- **Income Display:** Show total soldier production per tick in UI

#### Backend Changes
- Add `Tile.ownerId` field (0=neutral, 1=player1, 2=player2)
- Modify village soldier generation to check tile ownership
- Add village capture logic when enemy army occupies village
- Add `GameState.getPlayerIncome(playerId)` method

#### Frontend Changes
- Color villages based on ownership (blue/red tint on brown base)
- Display territory statistics (castles owned, villages owned, income/tick)
- Add visual feedback when capturing a village

#### Tasks
- [ ] Backend: Add ownerId to Tile model
- [ ] Backend: Implement village capture logic in tick()
- [ ] Backend: Update soldier generation to respect ownership
- [ ] Backend: Add getPlayerIncome() calculation
- [ ] Backend: Add unit tests for ownership and capture (6-8 tests)
- [ ] Frontend: Update tile rendering for ownership colors
- [ ] Frontend: Display territory statistics panel
- [ ] Frontend: Add unit tests for rendering
- [ ] Documentation: Update README with territory mechanics

---

### 4. Castle Capture & Win Conditions

**Current:** No win/loss conditions  
**MVP:** Win by capturing all enemy castles, lose by losing all castles

#### Features
- **Castle Ownership:** Castles belong to players (castles start owned)
- **Castle Capture:** Occupy enemy castle for N ticks to capture it (N=3 for MVP)
- **Win Condition:** Player wins when they own all castles
- **Loss Condition:** Player loses when they have no castles remaining
- **Game Over State:** Display win/loss message and prevent further actions

#### Backend Changes
- Add `Tile.ownerId` for castles (extend from village ownership)
- Add `Tile.occupationTicks` counter for capture progress
- Add `GameState.checkWinCondition()` method
- Add `GameState.gameOver` and `GameState.winnerId` fields
- Prevent commands when game is over

#### Frontend Changes
- Color castles based on ownership (gray with blue/red outline)
- Show capture progress bar over contested castles
- Display win/loss overlay with "Play Again" option (restart via new /reset endpoint)
- Add sound effects for capture/victory (optional)

#### Tasks
- [ ] Backend: Add ownerId to castle tiles (initialization in GameService)
- [ ] Backend: Add occupationTicks to Tile model
- [ ] Backend: Implement castle capture logic in tick()
- [ ] Backend: Add checkWinCondition() and game over state
- [ ] Backend: Add POST /reset endpoint to restart game
- [ ] Backend: Add unit tests for castle capture and win conditions (8-10 tests)
- [ ] Frontend: Render castle ownership colors
- [ ] Frontend: Display capture progress
- [ ] Frontend: Show win/loss overlay
- [ ] Frontend: Add reset functionality
- [ ] Frontend: Add unit tests
- [ ] Documentation: Update README with victory conditions

---

### 5. Basic AI Opponent

**Current:** Player 2 armies exist but require manual control  
**MVP:** Simple AI that controls Player 2 armies

#### Features
- **AI Strategy:** Simple rule-based AI (no machine learning)
- **AI Goals:**
  1. Capture neutral villages (priority: nearest)
  2. Attack enemy villages (if superior force)
  3. Defend owned villages (if under threat)
  4. Attack enemy castles (if overwhelming force)
- **AI Execution:** AI makes decisions during each tick
- **AI Difficulty:** Single difficulty level (balanced for MVP)

#### Backend Changes
- Add `GameService.executeAI()` method called during tick()
- Implement AI decision logic:
  - Scan for targets (villages, castles, enemy armies)
  - Calculate threat levels and opportunity scores
  - Issue movement commands for AI armies
  - Implement army splitting for multi-front strategy
- Add AI army spawning at player 2's castle

#### Frontend Changes
- Visual indicator that AI is "thinking" (optional)
- Distinguish AI actions in game log (optional, can defer)

#### Tasks
- [ ] Backend: Implement executeAI() in GameService
- [ ] Backend: Add target evaluation logic
- [ ] Backend: Add AI command generation
- [ ] Backend: Add AI army spawning/management
- [ ] Backend: Add unit tests for AI decision-making (8-10 tests)
- [ ] Backend: Balance AI difficulty through testing
- [ ] Documentation: Update README with AI description

---

### 6. Enhanced UI & User Experience

**Current:** Minimal LWJGL rendering with keyboard-only controls  
**MVP:** Improved visualization and mouse-based interaction

#### Features
- **Mouse Controls:**
  - Click army to select it
  - Click destination to move selected army
  - Right-click to deselect
- **Visual Feedback:**
  - Highlight selected army (glowing effect or border)
  - Show movement range/destination preview
  - Display hover tooltips (tile type, army size, ownership)
- **HUD Elements:**
  - Top bar: Tick count, player income, army count
  - Side panel: Selected army details
  - Bottom bar: Game status messages
- **Game Log:** Recent events (army moved, village captured, combat occurred)

#### Frontend Changes
- Implement mouse input handling (GLFW mouse callbacks)
- Add army selection state and rendering
- Implement tooltip system with position tracking
- Add HUD rendering using text rendering or simple shapes
- Add game log with scrolling message list

#### Backend Changes
- No changes required (all UI-side)

#### Tasks
- [ ] Frontend: Add mouse input handling
- [ ] Frontend: Implement army selection
- [ ] Frontend: Add tooltip system
- [ ] Frontend: Create HUD panel rendering
- [ ] Frontend: Implement game log
- [ ] Frontend: Add unit tests for UI interactions
- [ ] Documentation: Update README with controls and UI

---

## Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
1. Army movement & pathfinding
2. Territory control & ownership
3. Enhanced army visualization

**Milestone:** Armies move realistically and villages have ownership

### Phase 2: Strategy (Week 3-4)
4. Army management (split/merge)
5. Castle capture mechanics
6. Win/loss conditions

**Milestone:** Complete game loop with victory conditions

### Phase 3: Engagement (Week 5-6)
7. Basic AI opponent
8. Enhanced UI & mouse controls
9. Polish and balancing

**Milestone:** Full MVP with AI opponent and polished UI

---

## Technical Requirements

### Backend API Extensions

#### New Endpoints
```java
POST /command/move    - Move army to destination (queued movement)
POST /command/split   - Split army into two armies
POST /reset          - Reset game state to initial conditions
GET /stats           - Get player statistics (income, territories, etc.)
```

#### Enhanced Models
```java
// Army.java additions
private int destinationX;
private int destinationY;
private boolean isMoving;

// Tile.java additions
private int ownerId;           // 0=neutral, 1=player1, 2=player2
private int occupationTicks;   // For castle capture

// GameState.java additions
private boolean gameOver;
private int winnerId;
private int ticksPerMove;      // Config: tiles per tick

// New PlayerStats.java
class PlayerStats {
    int playerId;
    int castlesOwned;
    int villagesOwned;
    int totalIncome;
    int totalArmySize;
}
```

### Frontend Enhancements

#### Input System
```java
- Mouse event handling (click, hover)
- Keyboard shortcuts (S=split, R=reset, Space=tick)
- Input state management
```

#### Rendering System
```java
- Text rendering for labels and HUD
- Tooltip system with position tracking
- Multi-layer rendering (tiles, armies, UI)
- Selection highlighting effects
```

---

## Testing Requirements

### Unit Tests
- Backend: Target 80% code coverage for new features
- Frontend: Test all user input handling and state management

### Integration Tests
- End-to-end game scenarios (capture village, win game)
- AI behavior validation
- Multi-army interactions

### Playtesting
- Balance testing: AI difficulty, soldier generation rates
- UX testing: Controls feel responsive, UI is clear
- Performance testing: 60 FPS with 20+ armies

---

## Success Metrics

An MVP is successful when:

1. ✅ **Core Loop:** Player can play a complete game from start to victory/defeat
2. ✅ **Engagement:** Games last 5-15 minutes with meaningful decisions
3. ✅ **Polish:** UI is clear, controls are responsive, no major bugs
4. ✅ **AI:** AI opponent provides reasonable challenge (wins ~30-40% vs new players)
5. ✅ **Stability:** Zero crashes during 30-minute playtest session

---

## Out of Scope (Future Versions)

These features are explicitly **NOT** in MVP:
- ❌ Multiple unit types (knights, archers, etc.)
- ❌ Terrain effects (mountains, rivers, forests)
- ❌ Fog of war / limited visibility
- ❌ Resource system beyond soldier generation
- ❌ Technology/research tree
- ❌ Multiplayer or networked gameplay
- ❌ Save/load game functionality
- ❌ Advanced AI with multiple difficulty levels
- ❌ Sound effects and music
- ❌ Animations and particle effects
- ❌ Modding support
- ❌ Localization / multiple languages

---

## Estimated Effort

- **Backend Development:** ~60-80 hours
- **Frontend Development:** ~40-60 hours
- **Testing & Polish:** ~20-30 hours
- **Documentation:** ~10-15 hours

**Total:** ~130-185 hours (4-6 weeks for solo developer, 2-3 weeks for small team)

---

## Next Steps

1. Review and approve this MVP document
2. Create GitHub issues for each feature/task
3. Prioritize Phase 1 features
4. Begin implementation starting with army movement
5. Iterate based on playtesting feedback

---

## Notes

- This MVP focuses on **fun** over **scope** - better to have 6 polished features than 12 half-baked ones
- Each feature should be fully tested before moving to the next
- Regular playtesting (every 2-3 features) helps identify balance issues early
- Consider streaming development or sharing builds for community feedback
