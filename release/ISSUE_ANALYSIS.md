# Issue Analysis & Solutions

## Issue 1: Jump While Moving Not Working

### Root Cause
- **Problem**: `#set allowjump true/false` command does NOT exist in Baritone
- **Observation**: Manual spacebar presses while Baritone is pathfinding are not registered
- **Why**: Baritone controls player input during pathfinding, overriding manual keyboard input

### Technical Analysis
Baritone's pathfinding system takes control of player movement, including:
- Forward/backward movement
- Sprinting (via `#set allowsprint`)
- Looking direction
- **Jumping** - Baritone handles this internally based on path requirements

When Baritone is actively pathing, it processes movement on the game tick, and manual key presses are ignored because Baritone's movement processor takes priority.

### Solution Options

#### Option 1: Direct Input Injection (Recommended)
Use Minecraft's input system to inject jump key presses at a lower level than Baritone's input processing.

**Implementation**:
- Use `KeyBinding.setPressed()` to set jump key state
- Schedule periodic jump presses (every 100-200ms) while pathing
- Use `client.player.jump()` method directly if available
- This bypasses Baritone's input control by injecting at the game engine level

**Pros**:
- Works even when Baritone controls movement
- More reliable than command-based approach
- Can be fine-tuned with timing

**Cons**:
- May conflict with Baritone's pathfinding logic
- Requires careful timing to avoid double-jumps

#### Option 2: Baritone Settings Check
Check if there's a hidden Baritone setting for jump behavior.

**Implementation**:
- Research Baritone's internal settings API
- Look for settings like `allowJump`, `jumpWhileMoving`, etc.
- Use reflection to access Baritone's settings if available

**Pros**:
- Native Baritone support if it exists
- No input conflicts

**Cons**:
- May not exist
- Requires Baritone API access

#### Option 3: Mixin Approach
Create a mixin to intercept Baritone's movement processing and inject jumps.

**Implementation**:
- Mixin into Baritone's movement processor
- Inject jump commands during pathfinding
- More complex but most compatible

**Pros**:
- Works at Baritone's level
- No input conflicts

**Cons**:
- Complex implementation
- Requires mixin setup
- May break with Baritone updates

### Recommended Solution: Option 1 (Direct Input Injection)
Use `client.player.jump()` method or direct key press injection with proper timing.

---

## Issue 2: Telegram Notifications Not Sending

### Root Cause
Looking at code in `EggHatcher.java` line 1627:
```java
// Send Telegram notification ONLY if automation started from spawn
if (startedFromSpawn) {
    long duration = (System.currentTimeMillis() - automationStartTime) / 1000;
    sendTelegramNotification(true, duration);
}
```

**Problem**: The condition `if (startedFromSpawn)` is too restrictive. If the automation starts in overworld (not from spawn), no Telegram notification is sent.

### Analysis from Log
- Log shows: `[03:10:34] [Render thread/INFO]: 🎯 Step 5/5: Completion`
- But no Telegram notification was sent
- This suggests `startedFromSpawn` was `false`

### Solution
Send Telegram notification for ALL successful completions, regardless of where automation started:
- Remove or modify the `startedFromSpawn` condition
- Always send notification on Step 5 completion
- Include journey type in the message (already implemented)

---

## Issue 3: Default Pathing Notification Missing

### Root Cause
When no human-like action is selected (no backtrack, walk, hotbar, jump, pause, etc.), the code navigates silently without any in-game notification.

### Solution
Add notification when default pathing occurs (no modifiers):
```java
if (selectedAction == null) {
    sendNotification("Egg Hatcher", "Default pathing executed", Formatting.GRAY);
}
```

---

## Implementation Plan

### Fix 1: Jump While Moving
1. Remove `BaritoneController.setAllowJump()` method (command doesn't exist)
2. Implement direct jump injection using `client.player.jump()` or key press simulation
3. Use periodic task to trigger jumps every 100-150ms while pathing
4. Ensure jumps stop when pathing stops

### Fix 2: Telegram Notifications
1. Remove `if (startedFromSpawn)` condition
2. Always send Telegram notification on Step 5 completion
3. Keep journey type tracking (already works)

### Fix 3: Default Pathing Notification
1. Add notification in `navigateToNextLocation()` when `selectedAction == null`
2. Place after action selection logic, before starting navigation

---

## Code Changes Required

### File: `BaritoneController.java`
- Remove `setAllowJump()` method
- Remove `currentAllowJump` tracking

### File: `EggHatcher.java`
- Replace `startJumpingWhileMoving()` to use direct jump injection
- Remove `BaritoneController.setAllowJump()` calls
- Fix Telegram notification condition
- Add default pathing notification

