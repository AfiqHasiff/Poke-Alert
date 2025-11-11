# Changelog

All notable changes to PokéAlert will be documented in this file.

## [1.2.0] - 2024-11-11

### Added
- **Smart Whitelist/Blacklist Conflict Detection**: 
  - Automatically detects when adding Pokémon that exists in opposite list
  - Provides helpful prompts to resolve conflicts
  - Shows warnings when adding Pokémon already in predefined categories

- **Egg Timer Feature**:
  - Built-in egg hatching timer system
  - Default 30-minute timer (configurable from 1-120 minutes)
  - Customizable keybind (default: apostrophe ')
  - Start timer with single keypress
  - In-game and Telegram notifications on completion
  - Commands: `/pokealert eggtimer start/stop/status/duration`
  - Mod Menu interface for duration and keybind configuration

### Changed
- Updated version to 1.2.0
- Enhanced list management logic with conflict resolution

### Technical Improvements
- Added `EggTimerManager` for centralized timer management
- Improved command feedback with better formatting
- Enhanced Mod Menu with egg timer configuration section

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
