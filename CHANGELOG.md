# Changelog

All notable changes to PokéAlert will be documented in this file.

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
