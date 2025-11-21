# Changelog

All notable changes to PokéAlert will be documented in this file.

## [2.0.0] - 2024-11-20

### Overview

Complete overhaul of the Realm Manager system with improved reliability, accurate state detection, and optimized timing.

### ✨ Major Improvements

#### **6-Step Automation Flow**
- **[Step 1/6] Spawn Detection**: Detects arrival at spawn realm with 6-second initialization
- **[Step 2/6] Anti-AFK Check**: Verifies and disables Anti-AFK
- **[Step 3/6] Server Buffer**: 30-second wait with single clean message
- **[Step 4/6] Realm Change**: Executes teleport command
- **[Step 5/6] Anti-AFK Enable**: Re-enables Anti-AFK at overworld
- **[Step 6/6] Completion**: Clean automation finish

Benefits:
- Clear separation of concerns (detection vs action)
- Explicit verification step
- Easier debugging with descriptive step names
- Better user feedback at each stage

#### **Accurate Spawn Detection**
- 6-second warm-up period for reliable initial state detection
- Accounts for Anti-AFK script initialization time
- Prevents false negatives on server join

#### **Anti-AFK Toggle at Overworld**
- 17-second smart delay accounts for teleport and warm-up
- Position-based teleport detection disabled during automation
- Prevents double teleport reset issue

#### **Safety Monitor Improvements**
- Safety monitor paused during Step 5/6 toggle operation
- Prevents race condition between toggle execution and state check
- Cleaner logs

### 🔧 Technical Improvements

#### **Movement-Based State Detection**
- Continuous background monitoring every 200ms
- 6-second warm-up period for accurate initial readings
- Global `currentAntiAfkState` variable updated by background thread
- Never performs blind toggles - always verifies state first
- Waits up to 5 seconds for state initialization before toggling

#### **Smart Teleport Handling**
- World change detection with automatic tracking reset
- Position-based teleport detection (>50 blocks)
- Teleport detection disabled during automation to prevent double reset
- Prevents warm-up timer from being reset mid-automation

#### **Enhanced Safety Monitor**
- Runs every 3 seconds throughout entire process
- Location-aware: expects OFF at spawn, ON at overworld
- Paused during Step 5/6 to prevent race conditions
- Graceful restart on anomaly detection
- All scheduled tasks cancelled on restart (prevents stale execution)

#### **Timing Optimizations**
- `WARM_UP_PERIOD`: 6000ms for accurate state detection
- `TELEPORT_WAIT_TIME`: 17000ms to account for teleport and warm-up
- `ANTIAFK_TOGGLE_DELAY`: 100ms for responsive toggling
- Total automation time: ~65 seconds

### Performance Improvements

- Reliable spawn detection
- Consistent Anti-AFK toggling at both spawn and overworld
- Eliminated false safety alerts
- Optimized timing for ~65 second automation cycle

### Migration Notes
- Config is fully backward compatible
- Existing configs will work without changes

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
