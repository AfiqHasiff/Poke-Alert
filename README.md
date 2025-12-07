# PokéAlert

<div align="center">
  <img src="src/main/resources/icon.png" alt="PokéAlert Logo" width="128" height="128">
  
  **A Cobblemon addon that notifies you when desirable Pokémon spawn near you!**
  
  [![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://minecraft.net)
  [![Fabric](https://img.shields.io/badge/Fabric-0.116.6-blue.svg)](https://fabricmc.net)
  [![Cobblemon](https://img.shields.io/badge/Cobblemon-1.6.1-orange.svg)](https://cobblemon.com)
</div>

> ⚠️ **Important Note**: This mod detects ALL Pokémon within range, including your own! To prevent your own Pokémon from triggering notifications, consider renaming them with a prefix or suffix (e.g., "-Charizard" or "Pikachu-").

## 🎮 Features

### Real-time Detection
- **Automatic Scanning**: Continuously monitors for Pokémon spawns within a 64-block radius
- **Smart Filtering**: Only alerts for Pokémon you care about
- **World Exclusion**: Configure worlds to exclude from notifications (e.g., spawn, the_nether)
- **Master Toggle**: Enable/disable the entire mod with one click
- **Keybind Support**: Quick toggle mod on/off with a customizable hotkey (default: :)
- **Smart List Management**: Automatic conflict detection when adding to whitelist/blacklist

### Customizable Alerts
Configure detection for different Pokémon categories:
- **Legendary Pokémon** - Rare and powerful legendary spawns
- **Mythical Pokémon** - Ultra-rare mythical encounters  
- **Shiny Pokémon** - Any shiny variant
- **Starter Pokémon** - All starter Pokémon and their evolutions
- **Baby Pokémon** - Cute baby Pokémon
- **Ultra Beasts** - Mysterious Ultra Beasts from another dimension
- **Paradox Pokémon** - Ancient and future Paradox forms
- **Custom Whitelist** - Add specific Pokémon you want to track
- **Custom Blacklist** - Exclude specific Pokémon from notifications

### Multi-Channel Notifications

#### 🎵 In-Game Notifications
- Chat messages with rarity-based color coding
- Custom notification sound with adjustable volume
- Clean, informative format showing rarity and location
- Toggle individual notification types (text, sound)

#### ⏰ Egg Timer Feature
- Built-in egg hatching timer with customizable duration (15-120 minutes)
- Start timer with a single key press (default: ')
- Press ' again while running to see remaining time, press twice to cancel
- 5-minute reminders with remaining time and expected end time
- Completion notifications via in-game text, sound, and Telegram
- Shows timer start time when completed

#### 📱 Telegram Integration
- Real-time push notifications to your phone
- Rich formatted messages with:
  - Pokémon name with Bulbapedia link
  - Rarity information scraped from Bulbapedia
  - Exact coordinates
  - Detection timestamp
  - World/dimension info
- Rate limiting to prevent spam

## 📦 Installation

### Prerequisites
- Minecraft 1.21.1
- Fabric Loader 0.16.14 or higher
- Fabric API
- Cobblemon Mod 1.6.1

### Required Dependencies
1. **[Fabric API](https://modrinth.com/mod/fabric-api)** - Core Fabric library
2. **[Cobblemon](https://modrinth.com/mod/cobblemon)** - The Pokémon mod

### Optional Dependencies
- **[Mod Menu](https://modrinth.com/mod/modmenu)** - For in-game configuration GUI

### Installation Steps
1. Install Fabric Loader for Minecraft 1.21.1
2. Download and place the following in your `mods` folder:
   - Fabric API
   - Cobblemon
   - PokéAlert
   - Mod Menu (optional, for GUI config)
3. Launch Minecraft and configure the mod via Mod Menu or config file

## 🎯 Commands

PokéAlert provides a comprehensive command system for quick configuration:

### Basic Controls
- **Mod Toggle**: Press `:` (default) to toggle the mod on/off
- **Egg Timer**: Press `'` (default) to start egg timer
  - Both keybinds customizable in Mod Menu or Minecraft Controls

### Basic Commands
- `/pokealert` or `/pokealert help` - Show version info and available commands
- `/pokealert enable` - Enable the mod
- `/pokealert disable` - Disable the mod
- `/pokealert status` - Show current configuration status

### Category Management
- `/pokealert categories <category> <enable/disable>` - Toggle detection categories
  - Categories: `legendaries`, `mythics`, `shinies`, `starters`, `babies`, `ultrabeasts`, `paradox`
  - Example: `/pokealert categories legendaries enable`

### List Management
- `/pokealert whitelist <add/remove> <pokemonName>` - Manage custom whitelist
- `/pokealert blacklist <add/remove> <pokemonName>` - Manage blacklist
- `/pokealert excludedworlds <add/remove> <worldName>` - Manage world exclusions
  - Example: `/pokealert whitelist add Pikachu`
  - Example: `/pokealert excludedworlds add spawn`

### View Lists
- `/pokealert list <type>` - View Pokémon in specific lists
  - Types: `whitelist`, `blacklist`, `legendaries`, `mythics`, `shinies`, `starters`, `babies`, `ultrabeasts`, `paradox`
  - Example: `/pokealert list legendaries`

### Notification Control
- `/pokealert notifications <type> <enable/disable>` - Toggle notification types
  - Types: `text`, `sound`, `telegram`
  - Example: `/pokealert notifications sound disable`

### Egg Timer Commands
- `/pokealert eggtimer start [minutes]` - Start egg timer (default 30 min)
- `/pokealert eggtimer stop` - Stop current timer
- `/pokealert eggtimer status` - Check remaining time
- `/pokealert eggtimer duration <minutes>` - Set default duration

## ⚙️ Configuration

### Via Mod Menu (Recommended)
1. Open Mod Menu in-game
2. Find PokéAlert and click the config button
3. Configure:
   - Master toggle to enable/disable the mod
   - Keybind for quick toggle (click to set custom key)
   - Detection categories with descriptions
   - Custom whitelist and blacklist
   - World exclusions (simplified names like "spawn", "the_nether")
   - Notification toggles (text, sound, telegram)
   - Sound volume control (0-100%)
   - Egg timer duration and keybind settings
   - Egg Hatcher settings:
     - Master toggle for automation
     - Mode cycling keybind (default: Home)
     - Anti-AFK keybind configuration (default: Numpad 9)
     - Realm return command (default: /home new)
4. Save and apply changes

### Via Config File
Edit `.minecraft/config/pokealert.json`:
```json
{
  "modEnabled": true,
  "broadcastAllLegendaries": true,
  "broadcastAllMythics": true,
  "broadcastAllShinies": true,
  "broadcastAllStarter": false,
  "broadcastAllBabies": false,
  "broadcastAllUltraBeasts": false,
  "broadcastAllParadox": false,
  "broadcastWhitelist": ["Pikachu", "Charizard", "Mewtwo"],
  "broadcastBlacklist": [],
  "inGameTextEnabled": true,
  "inGameSoundEnabled": true,
  "inGameSoundVolume": 1.0,
  "telegramEnabled": true,
  "excludedWorlds": ["spawn"],
  "eggTimerDuration": 30,
  "eggTimerTextNotification": true,
  "eggTimerTelegramNotification": true,
  "eggHatcherEnabled": true,
  "antiAfkKeybind": 329,
  "realmReturnCommand": "/home new"
}
```

### Telegram Setup
1. Create a Telegram bot via [@BotFather](https://t.me/botfather)
2. Get your bot token
3. Get your chat ID (send a message to your bot and visit `https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates`)
4. Edit `.minecraft/config/pokealert_telegram.json`:
```json
{
  "enabled": true,
  "botToken": "YOUR_BOT_TOKEN",
  "chatId": "YOUR_CHAT_ID"
}
```

## 🎨 Notification Examples

### In-Game Chat
```
[PokéAlert] A Legendary Mewtwo spawned near you!
[PokéAlert] A Shiny Starter Charizard spawned near you!
[PokéAlert] An Ultra Beast Buzzwole spawned near you!
```

### Telegram Message
```
🚨 PokéAlert
• Pokémon: Shiny Mewtwo
• Rarity: Legendary
• Detected: 14:32
• Position: X:1024 Y:64 Z:-512
• Location: minecraft:overworld
```

## 🛠️ Building from Source

```bash
git clone https://github.com/yourusername/poke-alert.git
cd poke-alert
./gradlew build
```

The built jar will be in `build/libs/`

## 🥚 Egg Hatcher Automation

This feature automates returning to the main realm after disconnects or server restarts to keep your eggs hatching with Anti-AFK systems.

#### Setup Requirements:
1. **Anti-AFK System** Keybind or mouse button configured (default: Numpad 9, supports keyboard keys and mouse buttons 0-7, customizable in Mod Menu)
2. Server must have a realm return command (assuming you've sethome at another realm other than spawn) (default: `/home new`, customizable in Mod Menu)
3. Configure keybinds and commands in Mod Menu → Egg Hatcher section

#### Automation Modes:
The system has 3 modes that you can cycle through with the `Home` key:

1. **AUTO Mode** (Default): Smart detection for all scenarios
   - **Always**: Disables Anti-AFK immediately upon spawn detection
   - **Always**: Waits 30 seconds for server realm-switch buffer
   - Shows countdown timer during the wait period
   - Press `Home` to cancel anytime during the 30s wait
   - Handles both reconnects and realm crashes/transfers

2. **MANUAL Mode**: Only triggers via command
   - Use `/pokealert realm trigger`
   - Full control over when to return

3. **DISABLED**: Completely off

#### How It Works:

**All Spawn Scenarios (Reconnect/Realm Crash/Manual Visit):**
1. **[Step 1/6]** Detects arrival at spawn world
   - 6-second initialization period for accurate state detection
2. **[Step 2/6]** Anti-AFK Check
   - **Immediately disables Anti-AFK** to prevent unwanted movement
   - Verifies state before taking action
3. **[Step 3/6]** Server Buffer - Waits 30 seconds
   - Server-mandated cooldown to prevent rapid realm switching
   - Shows single waiting message (no spam)
   - Press `Home` to cancel anytime
4. **[Step 4/6]** Realm Change - Executes return command
   - Sends configured command (e.g., `/home new`)
5. **[Step 5/6]** Anti-AFK Enable - Re-enables at overworld
   - Waits for teleport and state initialization
   - 17-second smart delay ensures state is ready
6. **[Step 6/6]** Completion - Automation finished

**Why the 30-second wait?**
- Servers have built-in cooldowns for realm switching
- Attempting to switch too soon results in errors or blocks
- The wait ensures smooth realm transitions

**Anti-AFK Detection & Toggle**:
- **Movement-Based State Detection**:
  - Monitors player position, sneaking, and sprinting every 200ms
  - 6-second warm-up period after initialization for accurate state detection
  - Automatic reset on realm changes and teleportation (>50 blocks)
  - Position-based teleport detection disabled during automation (prevents double reset)
  - Prevents false positives from disconnect/reconnect
- **Toggle Method**:
  - GLFW-level key simulation using configured keybind (default: Numpad 9)
  - Post-verification to ensure toggle succeeded
  - Always verifies state before toggling
  - Waits up to 5 seconds for state initialization before toggling
- **Continuous Safety Monitor**:
  - Background monitoring every 3 seconds throughout automation
  - Verifies state matches expected (OFF at spawn, ON at overworld)
  - Automatically detects and corrects state mismatches
  - Paused during Step 5/6 to prevent race conditions
  - Restarts process gracefully if anomaly detected
- **Smart Safeguards**:
  - Refuses toggle if state unknown (no blind toggles)
  - 17-second delay after teleport for state re-initialization
  - World change detection with immediate tracking reset
  - Disconnect/reconnect position tracking reset
  - All scheduled tasks cancelled on restart to prevent stale execution

#### Usage:
- **Cycle Modes:** Press `Home` key
  - If you cycle to AUTO mode while at spawn → Automatically starts return sequence
  - If you cycle to AUTO mode at overworld → Shows "no return needed" message
- **Cancel Automation:** Press `Home` during countdown
- **Check Status:** `/pokealert realm`
- **Check Status:** `/pokealert realm status`
- **Toggle Mode:** `/pokealert realm toggle`

#### Notifications:
- **In-Game:** Clean, simplified step-by-step progress (6 total messages)
  - Step indicators [X/6] for easy tracking
  - Standardized yellow color scheme
  - Descriptive step names (Spawn Detection, Anti-AFK Check, Server Buffer, Realm Change, Anti-AFK Enable, Completion)
  - Safety alerts in red if anomaly detected (paused during Step 5/6)
- **Telegram:** Success/failure notifications with mode and realm transition
- **Stuck Alert:** Warning if stuck at spawn > 3 minutes

#### Important Notes:
- Disabled by default in config
- Requires proper Anti-AFK setup
- WILL NOT work on all servers
- Use at your own risk!

## 📝 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---
