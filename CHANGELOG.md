# Changelog

All notable changes to PokéAlert will be documented in this file.

## [2.0.0] - 2024-12-07

### Overview

Complete overhaul with the new **Egg Hatcher** automation system (formerly Realm Manager), mouse button support for Anti-AFK, and continuous safety monitoring. This release focuses on reliability, cross-platform compatibility, and intelligent state management.

### ✨ Major New Features

#### **🥚 Egg Hatcher Automation (Renamed from Realm Manager)**
Fully automated egg hatching system that manages spawn-to-overworld realm changes with Anti-AFK coordination.

**6-Step Automation Flow:**
- **[Step 1/6] Spawn Detection**: Universal 3-second grace period for reliable state detection
- **[Step 2/6] Anti-AFK Check**: Verifies and disables Anti-AFK (never blind toggles)
- **[Step 3/6] Server Buffer**: 30-second wait with Home key cancellation support
- **[Step 4/6] Realm Change**: Executes `/home new` command with 5s teleport delay
- **[Step 5/6] Anti-AFK Enable**: Re-enables at overworld (17s total: 5s delay + 3s load + 3s stabilization + 6s data)
- **[Step 6/6] Completion**: Clean finish with safety monitor restart

**Key Improvements:**
- Renamed to better reflect purpose (egg hatching automation)
- Simplified spawn detection - removed RECONNECT vs IN_SERVER complexity
- Universal 3-second grace period works reliably for all connection scenarios
- Clear in-game notifications for each step
- Keybind toggle (Home key) with Mod Menu sync
- Commands: `/pokealert realm toggle`, `/pokealert realm status`

#### **🖱️ Mouse Button Support for Anti-AFK**
Full support for binding Anti-AFK toggle to mouse buttons, perfect for touchscreen/controller setups.

**Features:**
- Supports all mouse buttons (left, right, middle, side buttons, etc.)
- Default binding: Middle Click (button 2)
- Configurable via JSON config and Mod Menu
- Also works with Minecraft's native Controls menu
- GLFW callback approach for reliable cross-version compatibility
- No reflection on obfuscated Minecraft code

**Supported Inputs:**
- Mouse buttons: 0-7 (0=left, 1=right, 2=middle, 3-7=additional)
- Keyboard keys: 32+ (GLFW key codes)

**Configuration Methods:**
1. **Mod Menu**: Visual keybind selector
2. **JSON Config**: Set `antiAfkKeybind` to desired code (e.g., `2` for middle click)
3. **Controls Menu**: Native Minecraft keybind interface

#### **🛡️ Continuous Safety Monitor**
Intelligent monitoring system that ensures Anti-AFK is always in the correct state.

**Behavior:**
- Runs continuously while Egg Hatcher is enabled (not just during automation)
- Location-aware expectations:
  - At spawn → Anti-AFK should be **OFF**
  - At overworld → Anti-AFK should be **ON**
- Auto-corrects mismatches within 3-5 seconds
- Restarts automatically after automation completion
- Handles manual toggles gracefully

**Example Scenario:**
```
[User manually toggles Anti-AFK OFF while at overworld]
→ Safety monitor detects mismatch after 3-5 seconds
→ Automatically turns Anti-AFK back ON
→ Logs: "Safety: Anti-AFK OFF at overworld - Fixing"
```

#### **⚡ Improved Stuck Detection**
Enhanced stuck detection with Telegram alerts and automatic game closure.

**Features:**
- Timeout reduced from 3 minutes to **2 minutes**
- Sends Telegram notification: "CRITICAL: Stuck at spawn for 2 minutes"
- Automatically force-closes game after alert (prevents endless waiting)
- Preserves stuck detector during safety monitor restarts
- More reliable triggering with improved task management

#### **🔗 Master Toggle Integration**
Enhanced master toggle now manages all running modules.

**Behavior:**
- Detects running Egg Hatcher and Egg Timer
- Requires confirmation if modules are active (3-second window)
- Shows which modules are running with specific details (e.g., timer remaining time)
- Force-stops all modules on second press
- Keybind disabled alerts when pressing module keys while mod is disabled

### 🔧 Technical Improvements

#### **GLFW Callback-Based Mouse Button Simulation**
- Uses GLFW's callback system instead of reflection on obfuscated code
- Version-agnostic implementation (works across all Minecraft versions)
- Directly invokes registered mouse button handlers
- Eliminates `NoSuchMethodException` issues from method name changes
- Thread-safe execution on main client thread

**Technical Details:**
```java
// Get and restore callback, then invoke directly
GLFWMouseButtonCallback callback = GLFW.glfwSetMouseButtonCallback(windowHandle, null);
GLFW.glfwSetMouseButtonCallback(windowHandle, callback);
callback.invoke(windowHandle, button, GLFW.GLFW_PRESS, 0);
```

#### **Movement-Based State Detection**
- Continuous background monitoring every 200ms
- Automatic state initialization on server connect (10s data collection)
- Global `currentAntiAfkState` variable updated by background thread
- Never performs blind toggles - always verifies state first
- Retry mechanism: waits up to 900ms (3 attempts × 300ms) for state before toggling
- State monitoring automatically restarts when toggling Egg Hatcher ON

#### **Smart Teleport Handling**
- World change detection with automatic tracking reset
- 3-second stabilization period after teleport for movement data to settle
- Pre-teleport state saved and restored during stabilization
- Teleport detection disabled during automation to prevent double reset
- 17-second total delay: 5s server + 3s world load + 3s stabilization + 6s fresh data

#### **Resource Pack Loading Management**
- `isResourcePackLoading` flag properly managed across all scenarios
- Flag set on server connect, cleared after 10 seconds
- **Critical fix**: Flag now reset when manually toggling Egg Hatcher ON
- Prevents safety monitor from being blocked indefinitely
- Ensures checks can run immediately when needed

#### **Anti-AFK State Monitoring Lifecycle**
- Started on server connect (immediate data collection)
- Restarted when toggling Egg Hatcher to AUTO mode
- Continues running throughout automation and after completion
- Only stopped on server disconnect or automation cancellation
- Ensures state is always detectable for safety monitor

#### **Timing Optimizations**
- `SPAWN_DETECTION_GRACE_PERIOD`: 3000ms (universal for all connection types)
- `TELEPORT_WAIT_TIME`: 17000ms (5s delay + 3s load + 3s stabilization + 6s data)
- `ANTIAFK_TOGGLE_DELAY`: 2500ms verification wait (2 full data points + buffer)
- `STUCK_TIMEOUT`: 120000ms (2 minutes) with Telegram alert and force exit
- Total automation cycle: ~50 seconds

### 🐛 Bug Fixes

#### **Critical Fixes**
- Fixed `isResourcePackLoading` blocking safety monitor indefinitely when toggling mode
- Fixed safety monitor not restarting after automation completion
- Fixed Anti-AFK state monitoring not running when toggling Egg Hatcher ON
- Fixed safety monitor unable to detect state due to cleared tracking data
- Fixed Home key not cancelling active automation process
- Fixed Mod Menu not reflecting keybind toggle state changes

#### **State Detection Fixes**
- Fixed "State unknown" preventing automation restart
- Fixed duplicate automation instances on server connect
- Fixed false "Step 5 failed" alerts (increased verification wait to 2500ms)
- Fixed safety monitor checking during teleport transitions
- Fixed stuck detector not triggering Telegram notifications

#### **UI/UX Fixes**
- Removed redundant "[PokeAlert]" prefix from Egg Hatcher notifications
- Fixed keybind disabled alerts not showing when mod is off
- Improved in-game feedback for all automation steps
- Added coordinate position and world name to debug logs

### 📝 Command Changes

#### **New Commands**
- `/pokealert realm toggle` - Toggle Egg Hatcher between AUTO and DISABLED
- `/pokealert realm status` - Check current Egg Hatcher status
- `/pokealert timer` - Toggle Egg Timer (alias for `/pokealert eggtimer toggle`)

#### **Removed Commands**
- `/pokealert realm trigger` - Removed (use Home key for AUTO mode)

### ⚙️ Configuration Changes

#### **New Settings**
- `eggHatcherEnabled` - Renamed from `realmManagerEnabled`
- `antiAfkKeybind` - Default changed to `2` (middle click) for better accessibility

#### **Keybind Updates**
- Egg Hatcher toggle: `key.pokealert.egghatcher` (renamed from `realmmanager`)
- Anti-AFK toggle: `key.pokealert.antiafk` (NEW - visible in Controls menu)

### 🚀 Performance Improvements

- Eliminated spawn detection complexity (single 3-second grace period for all scenarios)
- Continuous safety monitoring prevents state drift
- Automatic state correction within 3-5 seconds of manual changes
- Optimized task scheduling and cancellation
- Reduced automation cycle from ~65s to ~50s

### 📦 Migration Notes

#### **Automatic Migrations**
- `realmManagerEnabled` automatically migrated to `eggHatcherEnabled`
- Existing Anti-AFK keybind configurations preserved
- No action required for existing users

#### **Breaking Changes**
- Manual mode removed (simplified to AUTO and DISABLED only)
- `/pokealert realm trigger` command removed
- Realm Manager renamed to Egg Hatcher throughout UI

### 🎯 Compatibility

- **Minecraft**: 1.21.1
- **Fabric Loader**: 0.17.2+
- **Dependencies**: Cobblemon 1.6.1+
- **Platform**: Works on Windows, macOS, and Linux (including touchscreen devices)

---

## [1.2.0] - 2024-11-11

### Added
- **Smart Whitelist/Blacklist Conflict Detection**: 
  - Automatically detects when adding Pokémon that exists in opposite list
  - Provides helpful prompts to resolve conflicts
  - Requires confirmation when adding Pokémon already in predefined categories
  - Run command twice within 10 seconds to confirm redundant additions

- **Enhanced Egg Timer Feature**:
  - Built-in egg hatching timer system
  - Default 30-minute timer (configurable from 1-120 minutes)
  - Customizable keybind (default: apostrophe ')
  - Smart toggle: Press once to start, again to show remaining time, twice within 3 seconds to cancel
  - Only text notification on start (no sound/Telegram)
  - Full notifications on completion (text, sound, Telegram with start time)
  - 5-minute reminders with remaining time and expected end time
  - Commands: `/pokealert eggtimer start/stop/status/duration`
  - Mod Menu interface for duration and keybind configuration

### Changed
- Updated version to 1.2.0
- Enhanced list management logic with conflict resolution
- README now highlights Cobblemon dependency upfront
- Added note about own Pokémon detection with naming suggestions
- Egg timer duration options now include 1 and 5 minute settings

### Technical Improvements
- Added `EggTimerManager` for centralized timer management
- Improved command feedback with better formatting
- Enhanced Mod Menu with proper egg timer configuration section
- Implemented scheduled reminders and cancel confirmation system
- Added confirmation mechanism for redundant list additions

## [1.1.0] - Previous Release

### Added
- Master toggle for quick mod enable/disable
- Keybind support for toggle (default: semicolon)
- Custom blacklist for excluding specific Pokémon
- World exclusion system
- Comprehensive command system
- Notification type toggles (text, sound, telegram)
- Sound volume control
- Enhanced Mod Menu interface with descriptions

### Changed
- Renamed "allowlist" to "whitelist" throughout codebase
- Improved configuration screen layout
- Enhanced command feedback with color formatting

## [1.0.0] - Initial Release

### Features
- Real-time Pokémon detection within 64-block radius
- Category-based filtering (Legendaries, Mythics, Shinies, etc.)
- Custom whitelist support
- In-game chat notifications with rarity-based coloring
- Telegram integration for mobile notifications
- Bulbapedia integration for rarity information
- Mod Menu support for configuration
