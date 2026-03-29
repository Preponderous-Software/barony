# Barony - Player Guide

**Welcome to Barony!** This guide will teach you everything you need to know to play and win.

## What is Barony?

Barony is a single-player strategy game where you command armies to capture territory and defeat an AI opponent. Expand your realm by capturing villages, build up your forces, and ultimately capture your enemy's castle to win.

## Quick Start

### Starting the Game

**On Linux/macOS:**
```bash
./start-backend.sh    # Terminal 1
./start-frontend.sh   # Terminal 2
```

**On Windows:**
```batch
start-backend.bat     # Terminal 1
start-frontend.bat    # Terminal 2
```

**Using Docker:**
```bash
docker-compose up --build
```
Then open http://localhost:3000 in your browser.

### Game Overview

- **Your Goal:** Capture all enemy castles to win
- **You Control:** Player 1 (Blue armies)
- **Enemy:** Player 2 AI (Red armies)
- **Turn-Based:** Press SPACE to advance one turn (tick)

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

**Keyboard Shortcuts:**
- **SPACE** - Advance one turn
- **S** - Enter split mode for first army (press 1-9 to choose split amount, S/ESC to cancel)
- **R** - Play again (when game ends)
- **F9** - Open Settings panel (colorblind mode, theme, font size)
- **F10** - Open Notification Log
- **ESC** - Quit game / Close panel

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
- Press **S** to enter split mode (targets first army in list)
- Press number keys **1-9** to choose how many soldiers to split off
- Press **S** or **ESC** to cancel split mode
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
- **Press R** to play again

## Advanced Strategy: Ruler Policies

As a ruler, you can enact policies that affect your realm. These provide strategic bonuses but also have trade-offs.

### How to Change Policies

1. **Press P** to open the policy menu
2. **Press E, M, or O** to select a category:
   - **E** = Economic policies
   - **M** = Military policies  
   - **O** = pOpulation policies
3. **Press 1, 2, or 3** to choose a policy
4. Wait 15 turns before changing policies again (cooldown)

### Economic Policies

Affect village income and stability:

- **[1] Heavy Taxation**: +20% income, -10% stability
  - More soldiers generated, but villages less stable
  - Good for aggressive expansion

- **[2] Balanced Budget**: No modifiers
  - Default, safe option

- **[3] Infrastructure Investment**: -10% income, +10% stability
  - Slower growth, but villages more stable
  - Good for defensive play

### Military Policies

Affect army morale and loyalty:

- **[1] Aggressive Training**: +10% morale, -5% loyalty
  - Armies fight better, but may desert over time
  - Good for offensive campaigns

- **[2] Standard Service**: No modifiers
  - Default, balanced option

- **[3] Veteran Benefits**: -10% morale, +10% loyalty
  - Armies less aggressive, but very loyal (no desertion)
  - Good for long games

### Population Policies

Affect village growth and stability:

- **[1] Growth Focus**: +15% population growth, -5% stability
  - Villages grow faster, slightly less stable
  - Good for early game expansion

- **[2] Stable Population**: No modifiers
  - Default option

- **[3] Quality Over Quantity**: -10% growth, +10% stability
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
  - 100% = no desertion
  - Below 80% = warning (armies may lose soldiers over time)
  
- **Population**: Total population across all villages
  - Higher population = more soldier generation

### Policy Strategy Tips

**Aggressive Strategy:**
- Use Heavy Taxation + Aggressive Training
- Rapid expansion with strong combat bonus
- Monitor loyalty to prevent desertion
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

### Top Bar
Shows game statistics for both players:
- **Tick count**: Current turn number
- **Armies**: Number of armies each player has
- **Castles**: Castles owned (need to protect yours!)
- **Villages**: Villages owned (more = better income)
- **Income**: Soldiers generated per turn (+X/turn)

### Side Panel (Right)
Shows selected army details:
- Army ID and player
- Current soldier count
- Current position (X, Y)
- Destination (if moving)

### Bottom Panel
Game event log showing recent actions:
- Army movements
- Village captures
- Combat results
- Turn advances

### Ruler Stats Panel (Right)
Shows your realm statistics:
- Current policies in each category
- Policy change cooldown timer
- Average stability, morale, loyalty (color-coded: green ≥ 90, amber 70–89, red < 70)
- Total population

## Accessibility & Visual Settings

### Desktop Client (F9)
Press **F9** to open the Settings panel. Use arrow keys to navigate and change options:

- **Colorblind Mode:** None (default), Deuteranopia, Protanopia, Tritanopia
  - Applies to faction colors and map ownership indicators
- **Theme:** Dark (default), Classic, High Contrast
- **Font Size:** Small, Medium (default), Large

Settings are automatically saved to `~/.barony/settings.json`.

### Web Client (Settings Panel)
The Settings panel appears in the game info area. Changes apply immediately:

- **Colorblind Mode:** Same options as desktop
- **Theme:** Dark (default), Classic, High Contrast
- **Font Size:** Small, Medium (default), Large

Settings are saved to your browser's `localStorage`.

### Notifications
Both clients use non-blocking toast notifications instead of modal pop-ups:
- **Info** (gray): Turn advances, army selection
- **Success** (green): Village captured, policy applied
- **Warning** (amber): Policy cooldown, game reset errors
- **Danger** (red): Army destroyed, castle under attack

Toasts auto-dismiss after 4 seconds. Critical events persist until dismissed.

### Canvas Tooltips (Web Client)
Hover over any tile on the canvas to see:
- Tile type and ownership
- Army stats (soldiers, morale, loyalty, destination)
- Castle capture progress
- Village generation info

### Selection Feedback (Web Client)
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
A: Press **S** to enter split mode (targets first army). Press a number key (**1**-**9**) to choose how many soldiers to split off. Press **S** or **ESC** to cancel. Both armies must have at least 1 soldier after splitting.

**Q: Can I undo a move?**
A: No, all commands are final. Plan carefully!

**Q: The AI seems too hard/easy. Can I adjust difficulty?**
A: Currently there's only one AI difficulty level. Try different policy strategies to make it easier or harder on yourself.

**Q: What happens if I close the game?**
A: Game state is lost (no save feature yet). You'll start a new game next time.

**Q: Can I play with friends?**
A: Not yet - it's single-player only right now. Multiplayer is planned for future versions.

**Q: Why does the policy menu show a cooldown?**
A: You can only change policies every 15 turns to prevent rapid switching exploits. This encourages strategic planning.

## Troubleshooting

**Game window won't open:**
- Make sure the backend is running first
- Check that port 8080 isn't already in use
- On Linux, verify your DISPLAY variable is set

**Mouse clicks don't work:**
- Click the window to give it focus
- Make sure you're clicking on armies/tiles, not the UI panels

**Armies won't move:**
- Make sure you selected the army first (left-click)
- Verify the destination is within the 10x10 grid
- Check the backend is running (armies move when you press SPACE)

**Low frame rate:**
- Close other applications
- Try reducing window size
- Update graphics drivers

**Can't split armies:**
- You need at least 2 soldiers to split
- Press **S** to enter split mode (targets first army)
- Press number keys **1-9** to choose split amount
- Press **S** or **ESC** to cancel

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
