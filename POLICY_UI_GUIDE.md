# Policy Selection UI Guide

## How to Change Policies During Gameplay

The Ruler Decision System allows you to make strategic policy choices that affect your realm. Here's how to use it:

### Step-by-Step Instructions

1. **Open the Policy Menu**
   - Press the **'P'** key
   - A large menu will appear in the center of the screen with a semi-transparent dark overlay
   - The menu shows "POLICY MENU" at the top

2. **Select a Policy Category**
   - Press **'E'** for Economic Policies
   - Press **'M'** for Military Policies  
   - Press **'O'** for pOpulation Policies
   
   The menu will update to show the three policy options for that category

3. **Select Your Policy**
   - Press **'1'** for the first policy option
   - Press **'2'** for the second policy option (baseline)
   - Press **'3'** for the third policy option
   
   The menu will close and your policy will be applied on the next game tick

4. **Monitor Your Choice**
   - Look at the "RULER STATS (P1)" panel on the right side of the screen
   - Your current policies are displayed under "POLICIES:"
   - A cooldown timer shows when you can change policies again (15 ticks)

### Policy Options

#### Economic Policies (Press 'E')
- **[1] Heavy Taxation**: +20% income, -10% stability
- **[2] Balanced Budget**: No modifiers (baseline)
- **[3] Infrastructure Investment**: -10% income, +10% stability

#### Military Policies (Press 'M')
- **[1] Aggressive Training**: +10% morale, -5% loyalty
- **[2] Standard Service**: No modifiers (baseline)
- **[3] Veteran Benefits**: -10% morale, +10% loyalty

#### Population Policies (Press 'O')
- **[1] Growth Focus**: +15% population growth, -5% stability
- **[2] Stable Population**: No modifiers (baseline)
- **[3] Quality Over Quantity**: -10% population growth, +10% stability

### Visual Feedback

- **Menu Colors**:
  - Blue border indicates the active menu
  - Green text shows available actions
  - Gray text shows policy descriptions
  
- **Ruler Stats Panel**:
  - Green stats (≥80%/100) indicate healthy values
  - Red stats (<80%/100) indicate concerning values
  - Current policies shown in gray text
  - Cooldown shown in red when active, green when ready

### Tips

- The menu can be closed at any time by pressing **'P'** again
- Console output shows success/failure messages for policy changes
- You cannot change policies during cooldown (shows remaining ticks)
- Policy effects are continuous - they last until you change to a different policy
- Stats gradually drift toward policy-modified targets at each tick

### Example Session

```
1. Press 'P' → Menu opens
2. Press 'E' → Economic category selected
3. Press '1' → Heavy Taxation applied
4. Menu closes automatically
5. Console shows: "Policy changed successfully!"
6. Ruler Stats panel updates to show "Econ: Heavy Taxation"
7. Cooldown timer shows "Cooldown: 15 ticks"
```

### Troubleshooting

**Menu doesn't respond:**
- Make sure the game window has focus
- Check that you're pressing the correct keys (not holding them)

**Policy change fails:**
- Wait for cooldown to expire (watch the timer in Ruler Stats panel)
- Check console for error messages (might be on cooldown or server error)

**Can't see menu:**
- The menu appears in the center of the screen with a dark overlay
- Make sure the game is running and not paused
