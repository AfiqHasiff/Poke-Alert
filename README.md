# PokéAlert

<div align="center">
  <img src="src/main/resources/icon.png" alt="PokéAlert Logo" width="128" height="128">
  
  **Get notified when desirable Pokémon spawn near you!**
  
  [![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://minecraft.net)
  [![Fabric](https://img.shields.io/badge/Fabric-0.116.6-blue.svg)](https://fabricmc.net)
  [![Cobblemon](https://img.shields.io/badge/Cobblemon-1.6.1-orange.svg)](https://cobblemon.com)
</div>

## 🎮 Features

### Real-time Detection
- **Automatic Scanning**: Continuously monitors for Pokémon spawns within a 64-block radius
- **Smart Filtering**: Only alerts for Pokémon you care about
- **World Exclusion**: Configure worlds to exclude from notifications (e.g., spawn, the_nether)
- **Master Toggle**: Enable/disable the entire mod with one click
- **Keybind Support**: Quick toggle mod on/off with a customizable hotkey (default: :)

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
- **Keybind**: Press `:` (default) to toggle the mod on/off
  - Customizable in Mod Menu or Minecraft Controls

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
  "excludedWorlds": ["spawn"]
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

## 📝 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---
