package com.afiqhasiff.pokealert.client.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.afiqhasiff.pokealert.client.util.PokemonLists;

public class PokeAlertConfig {
    // Master toggle
    public boolean modEnabled = true;
    
    // Detection categories
    public boolean broadcastAllLegendaries = true;
    public boolean broadcastAllMythics = true;
    public boolean broadcastAllStarter = false;
    public boolean broadcastAllBabies = false;
    public boolean broadcastAllUltraBeasts = false;
    public boolean broadcastAllShinies = true;
    public boolean broadcastAllParadox = false;
    
    // Custom whitelist and blacklist
    public String[] broadcastWhitelist = {"Mew", "Mewtwo"};
    public String[] broadcastBlacklist = new String[0];
    
    // Name-based filtering (for player-owned Pokemon in multiplayer)
    public String blacklistCharacter = "-"; // If Pokemon name contains this character, skip notification
    
    // Notification toggles
    public boolean inGameTextEnabled = true;
    public boolean inGameSoundEnabled = true;
    public float inGameSoundVolume = 1.0f; // 0.0 to 1.0 (0% to 100%)
    public boolean telegramEnabled = true;
    
    // Telegram configuration (merged from separate file)
    public String telegramBotToken = "";
    public String telegramChatId = "";
    public String telegramApiUrl = "https://api.telegram.org";
    public int telegramMaxNotificationsPerMinute = 10;
    public int telegramCooldownSeconds = 30;
    
    // Telegram Command Execution Settings
    public boolean telegramCommandExecutionEnabled = false;  // Master toggle
    public long[] telegramAuthorizedUsers = {};  // Array of Telegram user IDs allowed to execute commands
    public int telegramPollingInterval = 5000;  // Milliseconds between polls (5 seconds default, 1-60 seconds)
    public int telegramCommandRateLimitSeconds = 5;  // Rate limit: 1 command per N seconds (default: 5)
    
    // World exclusion list (users can input "spawn" or "minecraft:spawn")
    public String[] excludedWorlds = {"spawn"};
    
    // Egg timer settings
    public int eggTimerDuration = 30; // Default 30 minutes
    public boolean eggTimerTextNotification = true;
    public boolean eggTimerTelegramNotification = true;
    
    // Egg Hatcher Automation Settings
    public boolean eggHatcherEnabled = true;
    public String realmReturnCommand = "/home new"; // Default command to return to main realm for egg hatching
    
    // Egg Hatcher DM Detection Settings
    public boolean dmDetectionEnabled = true; // Master toggle for DM detection (requires eggHatcherEnabled)
    public boolean dmTelegramNotification = true; // Send Telegram notifications for DMs
    public boolean dmInGameNotification = false; // Optional in-game notifications for DMs (default false to avoid spam)
    
    // ========== v3.0.0 Anti-AFK Settings ==========
    
    // Anti-AFK Region (X, Z only - Y handled by Baritone)
    public int antiAfkRegionX1 = 0;
    public int antiAfkRegionZ1 = 0;
    public int antiAfkRegionX2 = 100;
    public int antiAfkRegionZ2 = 100;
    
    // Coordinate Monitoring Thresholds
    public int arrivalThreshold = 3;           // Blocks to consider "arrived" (1-10)
    public int teleportDetectionOffset = 10;   // Blocks to trigger teleport detection (5-50)
    
    // Timing Configuration (all in milliseconds)
    public int coordinateCheckInterval = 500;        // Position check during movement (100-2000)
    public int realmCheckIntervalSpawn = 200;        // Realm check at spawn (100-5000)
    public int realmCheckIntervalOverworld = 30000;  // Realm check in overworld (5000-60000)
    public int playerMonitorInterval = 5000;         // Player list/nearby check (1000-30000)
    public int locationTimeout = 45000;              // Max time per Baritone destination (10000-120000)
    
    // Player Safety
    public String[] playersToAvoid = {};       // Usernames to watch for
    public boolean enablePlayerListMonitoring = true;
    public boolean enableNearbyPlayerDetection = true;
    public double nearbyPlayerDetectionRadius = 32.0; // (1-128)
    
    // Anti-AFK Queue Settings
    public int initialQueueSize = 5;           // Initial locations in queue (3-10)
    public int replenishCount = 3;             // Locations to add when queue runs low (1-5)
    public int locationsForStep5 = 3;          // Successful visits before Step 5/completion (1-10) - kept as Step6 for migration
    public int maxConsecutiveTimeouts = 3;     // Timeouts before safety stop (1-10)
    
    // Human-like Behavior Settings
    public boolean enableHumanLikeBehavior = true;
   
    public int minLongPauseMs = 5000;             // 5s
    public int maxLongPauseMs = 15000;            // 15s
    public int minBreakPauseMs = 30000;           // 30s
    public int maxBreakPauseMs = 60000;           // 60s
    public double longPauseChance = 0.05;         // 5% chance of 5-15s pause
    public double breakPauseChance = 0.01;        // 1% chance of 30-60s break
    public double backtrackChance = 0.04;         // 4% chance to backtrack
    public double walkChance = 0.15;              // 15% chance to walk instead of run
    public double hotbarSwitchChance = 0.05;      // 5% chance to switch hotbar slot
    public double jumpWhileMovingChance = 0.0005; // 0.05% chance to jump while moving
    public double lookAroundChance = 0.03;        // 3% chance to look around after arrival (with anti-afk skip)

    public String[] getCombinedWhitelist(){
        List<String> combinedList = new ArrayList<String>();

        if (this.broadcastAllLegendaries) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.legendaries)));
        }
        if (this.broadcastAllMythics) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.mythics)));
        }
        if (this.broadcastAllStarter) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.starter)));
        }
        if (this.broadcastAllBabies) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.babies)));
        }
        if (this.broadcastAllUltraBeasts) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.ultra_beasts)));
        }
        if (this.broadcastAllParadox) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.paradox_mons)));
        }

        combinedList.addAll(new ArrayList<String>(Arrays.asList(this.broadcastWhitelist)));

        // turn everything lowercase just in case
        for (int i = 0; i < combinedList.size(); i++) {
            combinedList.set(i, combinedList.get(i).toLowerCase());
        }

        return combinedList.toArray(new String[combinedList.size()]);
    }
    
    /**
     * Check if a Pokemon should trigger notification
     * @param pokemonName The name of the Pokemon to check
     * @return true if Pokemon should trigger notification
     */
    public boolean shouldNotify(String pokemonName) {
        if (!modEnabled) {
            return false;
        }
        
        String lowerName = pokemonName.toLowerCase();
        
        // Check blacklist first
        for (String blacklisted : broadcastBlacklist) {
            if (blacklisted.toLowerCase().equals(lowerName)) {
                return false;
            }
        }
        
        // Check whitelist
        String[] whitelist = getCombinedWhitelist();
        for (String whitelisted : whitelist) {
            if (whitelisted.equals(lowerName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a world is excluded from notifications
     * @param worldName The name of the world to check
     * @return true if world is excluded
     */
    public boolean isWorldExcluded(String worldName) {
        for (String excluded : excludedWorlds) {
            // Handle both formats: "spawn" and "minecraft:spawn"
            String normalizedExcluded = excluded.contains(":") ? excluded : "minecraft:" + excluded;
            String normalizedWorld = worldName.contains(":") ? worldName : "minecraft:" + worldName;
            
            if (normalizedExcluded.equalsIgnoreCase(normalizedWorld)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if Telegram configuration is valid for sending notifications
     */
    public boolean isTelegramValid() {
        return telegramEnabled 
            && telegramBotToken != null && !telegramBotToken.trim().isEmpty() 
            && telegramChatId != null && !telegramChatId.trim().isEmpty();
    }

    /**
     * Get the full Telegram API URL for sending messages
     */
    public String getTelegramSendMessageUrl() {
        return String.format("%s/bot%s/sendMessage", telegramApiUrl, telegramBotToken);
    }
    
    // ========== v3.0.0 Helper Methods ==========
    
    /**
     * Check if Anti-AFK region has valid non-zero area
     * @return true if region is properly configured
     */
    public boolean isAntiAfkRegionValid() {
        return antiAfkRegionX1 != antiAfkRegionX2 && antiAfkRegionZ1 != antiAfkRegionZ2;
    }
    
    /**
     * Check if Anti-AFK region might be too small for effective movement
     * Recommended minimum is 20x20 blocks
     * @return true if region is smaller than recommended
     */
    public boolean isAntiAfkRegionTooSmall() {
        int width = Math.abs(antiAfkRegionX2 - antiAfkRegionX1);
        int depth = Math.abs(antiAfkRegionZ2 - antiAfkRegionZ1);
        return width < 20 || depth < 20;
    }
    
    /**
     * Get the Anti-AFK region width
     * @return Width in blocks
     */
    public int getAntiAfkRegionWidth() {
        return Math.abs(antiAfkRegionX2 - antiAfkRegionX1);
    }
    
    /**
     * Get the Anti-AFK region depth
     * @return Depth in blocks
     */
    public int getAntiAfkRegionDepth() {
        return Math.abs(antiAfkRegionZ2 - antiAfkRegionZ1);
    }
    
    /**
     * Validate all v3.0.0 timing configuration values
     * Clamps values to valid ranges
     */
    public void validateTimingConfig() {
        arrivalThreshold = clamp(arrivalThreshold, 1, 10);
        teleportDetectionOffset = clamp(teleportDetectionOffset, 5, 50);
        coordinateCheckInterval = clamp(coordinateCheckInterval, 100, 2000);
        realmCheckIntervalSpawn = clamp(realmCheckIntervalSpawn, 100, 5000);
        realmCheckIntervalOverworld = clamp(realmCheckIntervalOverworld, 5000, 60000);
        playerMonitorInterval = clamp(playerMonitorInterval, 1000, 30000);
        locationTimeout = clamp(locationTimeout, 10000, 120000);
        nearbyPlayerDetectionRadius = clamp(nearbyPlayerDetectionRadius, 1.0, 128.0);
        initialQueueSize = clamp(initialQueueSize, 3, 10);
        replenishCount = clamp(replenishCount, 1, 5);
        locationsForStep5 = clamp(locationsForStep5, 1, 10);
        maxConsecutiveTimeouts = clamp(maxConsecutiveTimeouts, 1, 10);
        
        // Ensure teleport detection is at least 2x arrival threshold
        if (teleportDetectionOffset < arrivalThreshold * 2) {
            teleportDetectionOffset = arrivalThreshold * 2;
        }
    }
    
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
