# Barony - Player Guide

**Welcome to Barony!** This guide will teach you everything you need to know to play and win.

## What is Barony?

Barony is a single-player online strategy game where you command armies to capture territory and defeat an AI opponent. Expand your realm by capturing villages, build up your forces, and ultimately capture your enemy's castle to win.

## Quick Start

### Starting the Game

**Using Docker (Recommended):**

Barony uses the [UserAuth](https://github.com/Preponderous-Software/UserAuth) service for
player accounts, which `docker-compose` builds from a sibling checkout. Clone it next to this
repo, set a `JWT_SECRET`, then start everything together:

```bash
git clone https://github.com/Preponderous-Software/UserAuth.git   # next to barony/
cd barony
export JWT_SECRET="please-change-this-to-a-32-byte-minimum-secret"
docker-compose up --build
```
Then open http://localhost:3000 in your browser.

**Manual Start:** (also requires UserAuth running on port 9998 — see its README)
```bash
# Backend (Terminal 1)
cd backend && ./mvnw spring-boot:run

# Web Client (Terminal 2)
cd web-client && ./mvnw spring-boot:run
```
Then open http://localhost:3000 in your browser.

### Creating an Account & Logging In

The first screen asks you to log in. New players click **Create one** to register a username
(3–50 characters) and password (at least 8 characters), then log in.

- Your game progress is saved to your account, so you can return to it later.
- Click **Logout** in the game to sign out — this revokes your session immediately.

### Game Overview

- **Your Goal:** Capture all enemy castles to win
- **You Control:** Player 1 (Blue armies)
- **Enemy:** Player 2 AI (Red armies)
- **Turn-Based:** Click **Advance Turn** to advance one turn (tick)

## How to Play

### Understanding the Map

The game board is a 10x10 grid with different tile types:

- **Castles (Gray)**: 
  - Start at corners: Player 1 at (0,0), Player 2 at (9,9)
  - Blue outline = Player 1 owned
  - Red outline = Player 2 owned
  - Must capture enemy castle to win!

- **Villages (Brown)**:
  - Generate +1 soldier per turn for armies stationed there
  - Can be captured by occupying with your army
  - Blue tint = Player 1 owned
  - Red tint = Player 2 owned
  - No tint = Neutral (not generating soldiers)

- **Empty Tiles (Green)**:
  - Safe to move through
  - No special effects

### Controlling Your Armies

**Mouse Controls:**
1. **Left-click an army** (blue circle) to select it
   - Selected army will have a glowing highlight
2. **Left-click any tile** to move your selected army there
   - Army will move 1 tile per turn toward the destination
   - Light blue square shows where army will go
3. **Right-click** to deselect the army

**Buttons (above the map):**
- **Advance Turn** - Advance the game by one turn
- **Reset Game** - Start a fresh game (also how you play again after a game ends)
- **Auto Play** - Advance a turn automatically every second; click again (**Stop Auto**) to pause.
  Auto Play stops on its own when the game ends.

**Splitting armies:** select an army on the map and use the **Split off** control in the panel
below the map, or use the split control next to any of your armies in the **Armies** panel.

**Keyboard shortcuts** (ignored while typing in a text field, or with Ctrl/Cmd/Alt held):
- **Space** — Advance Turn
- **R** — Reset Game
- **S** — jump to the **Split off** amount box for the selected army
- **A** — toggle Auto Play
- **Escape** — deselect the current army (same as right-click)

**Tip:** Hover over any tile or army to see detailed information. In-game tooltips now cover most of what previously required consulting this guide mid-session.

### Basic Strategy

**1. Capture Villages Early**
- Villages generate soldiers for armies stationed there
- More villages = faster army growth
- Capture neutral villages first (brown with no tint)

**2. Build Up Your Forces**
- Park armies on villages you control
- Each turn, armies gain +1 soldier per village
- Don't attack with weak armies!

**3. Split Your Armies**
- Click one of your armies on the map, then enter an amount in **Split off** and click **Split Army**
- Or use the split control beside that army in the **Armies** panel
- You can split off at most one fewer soldier than the army has (both armies keep at least 1)
- The new army inherits the parent's morale and loyalty — splitting won't reset a disloyal force
- Useful for capturing multiple villages at once
- Can garrison villages while your main force attacks

**4. Friendly Armies Merge**
- Armies at the same location automatically combine
- Use this to consolidate forces before a big battle

**5. Capture the Enemy Castle**
- Must occupy enemy castle for **3 consecutive turns**
- Progress bar shows capture status
- Capture all enemy castles to win!

### Combat

When armies of different players occupy the same tile:
- **Simultaneous Damage**: Each army loses soldiers equal to the enemy's count
- **Example**: 10 soldiers vs 7 soldiers → 3 vs 0 (you win with 3 remaining)
- **Armies with 0 soldiers are destroyed**

**Combat Tips:**
- Attack with superior numbers (at least 1.5x enemy strength)
- Don't waste small armies against fortified positions
- Use split armies to flank and overwhelm

### Village Capture

- **After combat**, if only your army remains on a village, you capture it
- **Contested villages** (multiple players present) become neutral
- Villages retain ownership when abandoned (no army present)
- Captured villages immediately start generating soldiers for you

### Castle Capture

Castles are harder to capture:
1. **Occupy** the enemy castle with your army
2. **Hold** for 3 consecutive turns
3. **Progress resets** if enemy army arrives or you leave
4. **Capture complete** after 3 turns → castle is yours!

Red progress bar shows capture status (0/3 to 3/3).

### Winning and Losing

- **Victory:** Capture all enemy castles
- **Defeat:** Lose all your castles
- A banner appears over the map (**Victory!** or **Defeat**); click **Reset Game** to play again

## Advanced Strategy: Ruler Policies

As a ruler, you can enact policies that affect your realm. These provide strategic bonuses but also have trade-offs.

### How to Change Policies

1. Open the **Change Policy** panel in the sidebar
2. Pick an option from the **Economic**, **Military**, or **Population** dropdown
3. Click that category's **Apply** button
4. Wait 15 turns before changing policies again (cooldown — the **Next Decision In** bar in
   **Game Status & Stats** counts it down)

### Economic Policies

Affect village income and stability:

- **Heavy Taxation**: +20% income, -10% stability
  - More soldiers generated, but villages less stable
  - Good for aggressive expansion

- **Balanced Budget**: No modifiers
  - Default, safe option

- **Infrastructure Investment**: -10% income, +10% stability
  - Slower growth, but villages more stable
  - Good for defensive play

### Military Policies

Affect army morale and loyalty:

- **Aggressive Training**: +10% morale, -25% loyalty
  - Armies fight better, but loyalty settles at 75% and soldiers steadily desert
  - The bigger the army, the more soldiers per turn it loses
  - Good for offensive campaigns — win before the attrition adds up

- **Standard Service**: No modifiers
  - Default, balanced option

- **Veteran Benefits**: -10% morale, +10% loyalty
  - Armies less aggressive, but very loyal (no desertion)
  - Good for long games

### Population Policies

Affect village growth and stability:

- **Growth Focus**: +15% population growth, -5% stability
  - Villages grow faster, slightly less stable
  - Good for early game expansion

- **Stable Population**: No modifiers
  - Default option

- **Quality Over Quantity**: -10% growth, +10% stability
  - Slower growth, more stable villages
  - Good for defensive consolidation

### Understanding Stats

Check the **Ruler Stats** panel (right side) to monitor:

- **Stability** (villages): Affects soldier generation efficiency
  - 100% = normal generation
  - Below 70% = yellow warning (reduced generation)
  
- **Morale** (armies): Affects combat effectiveness
  - 100% = normal combat strength
  - Above 100% = bonus combat strength
  - Below 80% = warning (weaker in combat)
  
- **Loyalty** (armies): Affects desertion rate
  - 100% = no desertion, and restoring it to 100% clears any desertion still pending
  - Each turn an army loses `(100 - loyalty) / 20`% of its soldiers
  - Below 80% = warning (Aggressive Training's 75% target lands here)
  - Losses under one whole soldier carry over between turns rather than being ignored, so
    even a small army eventually feels a fractional rate
  
- **Population**: Total population across all villages
  - Higher population = more soldier generation

### Policy Strategy Tips

**Aggressive Strategy:**
- Use Heavy Taxation + Aggressive Training
- Rapid expansion with strong combat bonus
- Switch back to Standard Service to stop desertion once you've taken what you need
- Best for short, decisive games

**Defensive Strategy:**
- Use Infrastructure Investment + Veteran Benefits
- Stable, loyal armies and villages
- Slower growth but very resilient
- Best for longer games

**Balanced Strategy:**
- Keep default policies (Balanced/Standard/Stable)
- No bonuses or penalties
- Safe, predictable gameplay
- Good for learning the game

**Note:** Policy effects take several turns to manifest. Don't expect instant results!

## Reading the Interface

### Controls (above the map)
**Advance Turn**, **Reset Game**, and the **Auto Play** toggle.

### Selected Army Panel (below the map)
Shows the army you clicked on the map — its ID, soldier count, and position — plus the
**Split off** control. Until you select one of your armies it just prompts you to click one.

### Game Status & Stats (sidebar)
- **Game Status**: current turn, whether the game is over and the winner once it is, and a
  castle count (yours, the enemy's, and neutral, out of the total on the map)
- **Ruler Stats**: average stability, morale, and loyalty, plus total population
  (color-coded: green ≥ 90, amber 70–89, red < 70)
- **Policies**: the policy in force in each category and a **Next Decision In** cooldown bar

### Change Policy, Settings, and Armies (sidebar)
Three more collapsible panels: the policy dropdowns, the display settings, and the **Armies**
list. Each army in the list shows its ID, owner, position, soldiers, morale, and loyalty, and
your armies with 2+ soldiers get a split control.

## Accessibility & Visual Settings

### Settings Panel
Open the **Settings** panel in the sidebar. Changes apply immediately:

- **Colorblind Mode:** None (default), Deuteranopia, Protanopia, Tritanopia
  - Applies to faction colors and map ownership indicators
- **Theme:** Dark (default), Classic, High Contrast
- **Font Size:** Small, Medium (default), Large

Settings are saved to your browser's `localStorage`.

### Notifications
Non-blocking toast notifications keep you informed without interrupting gameplay:
- **Info** (gray): Turn advances, army selection
- **Success** (green): Village captured, policy applied
- **Warning** (amber): Policy cooldown, game reset errors
- **Danger** (red): Army destroyed, castle under attack

Toasts auto-dismiss after 4 seconds. Critical events persist until dismissed.

### Canvas Tooltips
Hover over any tile on the canvas to see:
- Tile type and ownership
- Army stats (soldiers, morale, loyalty, destination)
- Castle capture progress
- Village generation info

### Selection Feedback
- Click an army to select it — a gold highlight ring appears
- Right-click the canvas to deselect
- Clicking another army switches selection

## Tips for New Players

1. **Start slow** - Take time to learn the controls
2. **Capture villages first** - They're your economy
3. **Build up before attacking** - Don't rush with weak armies
4. **Use split strategically** - Garrison villages, then push forward
5. **Watch the AI** - Learn from enemy movements
6. **Protect your castle** - Losing it means instant defeat
7. **Try different policies** - Experiment to find your strategy

## Common Questions

**Q: How do I make my army move?**
A: Left-click the army to select it, then left-click where you want it to go. The army moves 1 tile per turn.

**Q: Why isn't my village generating soldiers?**
A: Villages only generate soldiers for armies of the owning player stationed on the village. Neutral villages don't generate anything.

**Q: How do I split an army?**
A: Click the army on the map, enter how many soldiers to split off in the **Split off** box
below the map, and click **Split Army** — or use the same control beside that army in the
**Armies** panel. Both armies must have at least 1 soldier after splitting, so an army needs
at least 2 soldiers to split.

**Q: Can I undo a move?**
A: No, all commands are final. Plan carefully!

**Q: The AI seems too hard/easy. Can I adjust difficulty?**
A: Currently there's only one AI difficulty level. Try different policy strategies to make it easier or harder on yourself.

**Q: What happens if I close the game?**
A: Your game is saved to your account automatically. Log back in and you'll pick up
where you left off — it survives closing the browser and server restarts. Use **Reset
Game** if you'd rather start over.

**Q: Can I play with friends?**
A: Not yet - it's single-player only right now. Multiplayer is planned for future versions.

**Q: Why does the policy menu show a cooldown?**
A: You can only change policies every 15 turns to prevent rapid switching exploits. This encourages strategic planning.

## Troubleshooting

**Game won't load in browser:**
- Make sure the backend is running first
- Check that port 8080 and 3000 aren't already in use
- Try clearing your browser cache

**Armies won't move:**
- Make sure you selected the army first (left-click)
- Verify the destination is within the 10x10 grid
- Check the backend is running (armies move when you click **Advance Turn**)

**Can't split armies:**
- You need at least 2 soldiers to split — the control is disabled below that
- You can't split off more than one fewer soldier than the army has
- Split from the panel below the map (after selecting the army) or from the **Armies** panel

## Next Steps

Now that you know the basics, try these challenges:

1. **Win your first game** - Beat the AI!
2. **Win without losing any armies** - Perfect execution
3. **Win in under 50 turns** - Speed run
4. **Win with Heavy Taxation policy** - High risk, high reward
5. **Capture all villages before the castle** - Total domination

Have fun conquering the realm!

---

**Need more details?** Check out [README.md](README.md) for technical documentation and [CHANGELOG.md](CHANGELOG.md) for version history.
