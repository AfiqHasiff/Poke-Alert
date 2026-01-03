package com.afiqhasiff.pokealert.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.afiqhasiff.pokealert.client.PokeAlertClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages configuration files for PokéAlert.
 * Handles loading, saving, and migration of config files.
 * 
 * v4.0.0: Added migration from flat config to nested config structure.
 */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();

    // File paths
    private static final File CONFIG_DIR = new File("config");
    
    // Current main config file
    private static final File SETTINGS_FILE = new File(CONFIG_DIR, "pokealert-settings.json");
    
    // Old telegram file (for migration into main config)
    private static final File TELEGRAM_FILE = new File(CONFIG_DIR, "pokealert-telegram.json");
    
    private static PokeAlertConfig currentConfig;

    /**
     * Initialize and load all configurations
     */
    public static void initialize() {
        CONFIG_DIR.mkdirs();
        
        // Load main configuration (pokealert-settings.json)
        currentConfig = loadSettings();
        
        // v4.0.0: Migrate from legacy flat config to nested config
        if (currentConfig.migrateFromLegacy()) {
            PokeAlertClient.LOGGER.info("✓ Migrated config from flat format to nested format");
            saveSettings(currentConfig);
        }
        
        // Smart telegram config migration (legacy pokealert-telegram.json):
        // - If old telegram file exists AND main config telegram is empty: migrate from old file
        // - If old telegram file exists AND main config telegram is populated: skip migration (manual config)
        // - If old telegram file doesn't exist: use main config values (even if empty)
        if (TELEGRAM_FILE.exists()) {
            // Check if main config telegram values are empty (need migration)
            boolean telegramIsEmpty = (currentConfig.telegram.botToken == null || currentConfig.telegram.botToken.trim().isEmpty()) &&
                                     (currentConfig.telegram.chatId == null || currentConfig.telegram.chatId.trim().isEmpty());
            
            if (telegramIsEmpty) {
                // Main config telegram is empty, migrate from old file
                migrateTelegramConfig();
            } else {
                // Main config telegram is already populated (manual configuration), skip migration
                PokeAlertClient.LOGGER.info("Skipping telegram migration - main config already has telegram values (manual configuration detected)");
                
                // Optionally backup the old telegram file without migrating
                try {
                    File backupFile = new File(CONFIG_DIR, "pokealert-telegram.json.backup");
                    Files.move(TELEGRAM_FILE.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    PokeAlertClient.LOGGER.info("Old telegram file backed up as pokealert-telegram.json.backup");
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.warn("Could not backup old telegram file: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Load main settings from file
     */
    public static PokeAlertConfig loadSettings() {
        PokeAlertConfig config = new PokeAlertConfig();
        
        if (SETTINGS_FILE.exists()) {
            try {
                // Read file content as string to check for missing fields
                String fileContent = new String(Files.readAllBytes(SETTINGS_FILE.toPath()), StandardCharsets.UTF_8);
                
                // Parse JSON (Gson will use default values for missing fields)
                config = GSON.fromJson(fileContent, PokeAlertConfig.class);
                
                // Ensure nested objects are not null (can happen if config was partially saved)
                ensureNestedObjectsNotNull(config);
                
                PokeAlertClient.LOGGER.info("Loaded settings from {}", SETTINGS_FILE.getName());
                
                // Check if config file is missing any fields (general migration for all new fields)
                // Compare the loaded JSON with a fresh default config to detect missing fields
                com.google.gson.JsonObject fileJson = GSON.fromJson(fileContent, com.google.gson.JsonObject.class);
                String defaultJson = GSON.toJson(new PokeAlertConfig());
                com.google.gson.JsonObject defaultJsonObj = GSON.fromJson(defaultJson, com.google.gson.JsonObject.class);
                
                // Check if any fields from default config are missing in the file
                boolean hasMissingFields = false;
                for (String fieldName : defaultJsonObj.keySet()) {
                    if (!fileJson.has(fieldName)) {
                        hasMissingFields = true;
                        PokeAlertClient.LOGGER.info("Config file missing field: {}", fieldName);
                        break;
                    }
                }
                
                // If fields are missing, save config to add them (preserves existing values, adds missing ones)
                if (hasMissingFields) {
                    saveSettings(config);
                    PokeAlertClient.LOGGER.info("Config file updated: Added missing fields with default values");
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("Failed to read settings file, using defaults", e);
            }
        } else {
            // Create default config file
            saveSettings(config);
            PokeAlertClient.LOGGER.info("Created default settings file at {}", SETTINGS_FILE.getName());
        }
        
        return config;
    }
    
    /**
     * Ensure all nested config objects are not null.
     * This handles cases where the config file was partially saved or corrupted.
     */
    private static void ensureNestedObjectsNotNull(PokeAlertConfig config) {
        if (config.detection == null) config.detection = new PokeAlertConfig.PokemonDetectionConfig();
        if (config.notifications == null) config.notifications = new PokeAlertConfig.NotificationConfig();
        if (config.telegram == null) config.telegram = new PokeAlertConfig.TelegramConfig();
        if (config.telegram.commands == null) config.telegram.commands = new PokeAlertConfig.TelegramConfig.CommandExecutionConfig();
        if (config.eggTimer == null) config.eggTimer = new PokeAlertConfig.EggTimerConfig();
        if (config.eggHatcher == null) config.eggHatcher = new PokeAlertConfig.EggHatcherConfig();
        if (config.eggHatcher.dmDetection == null) config.eggHatcher.dmDetection = new PokeAlertConfig.EggHatcherConfig.DmDetectionConfig();
        if (config.eggManager == null) config.eggManager = new PokeAlertConfig.EggManagerConfig();
        if (config.eggManager.phase2 == null) config.eggManager.phase2 = new PokeAlertConfig.EggManagerConfig.Phase2Config();
        if (config.eggManager.daycare == null) config.eggManager.daycare = new PokeAlertConfig.EggManagerConfig.DaycareConfig();
        if (config.antiAfk == null) config.antiAfk = new PokeAlertConfig.AntiAfkConfig();
        if (config.antiAfk.thresholds == null) config.antiAfk.thresholds = new PokeAlertConfig.AntiAfkConfig.ThresholdsConfig();
        if (config.antiAfk.timing == null) config.antiAfk.timing = new PokeAlertConfig.AntiAfkConfig.TimingConfig();
        if (config.antiAfk.playerSafety == null) config.antiAfk.playerSafety = new PokeAlertConfig.AntiAfkConfig.PlayerSafetyConfig();
        if (config.antiAfk.queue == null) config.antiAfk.queue = new PokeAlertConfig.AntiAfkConfig.QueueConfig();
        if (config.antiAfk.humanBehavior == null) config.antiAfk.humanBehavior = new PokeAlertConfig.AntiAfkConfig.HumanBehaviorConfig();
        if (config.mappingLines == null) config.mappingLines = new PokeAlertConfig.MappingLinesConfig();
        if (config.mappingLines.slotMapping == null) config.mappingLines.slotMapping = new SlotCoordinateMapping();
    }

    /**
     * Save main settings to file
     */
    public static void saveSettings(PokeAlertConfig config) {
        try (FileWriter writer = new FileWriter(SETTINGS_FILE)) {
            GSON.toJson(config, writer);
            currentConfig = config;
            PokeAlertClient.LOGGER.info("Saved settings to {}", SETTINGS_FILE.getName());
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to save settings file", e);
        }
    }

    /**
     * Migrate Telegram configuration from separate file (pokealert-telegram.json) into main config.
     * This is only called when main config telegram values are empty.
     */
    private static void migrateTelegramConfig() {
        try {
            if (!TELEGRAM_FILE.exists()) {
                return;
            }
            
            PokeAlertClient.LOGGER.info("Migrating telegram config from pokealert-telegram.json (main config telegram is empty)...");
            
            // Read telegram config as JsonObject
            FileReader reader = new FileReader(TELEGRAM_FILE);
            com.google.gson.JsonObject telegramJson = GSON.fromJson(reader, com.google.gson.JsonObject.class);
            reader.close();
            
            // Merge into main config (only overwrites empty values) - using new nested paths
            if (telegramJson.has("enabled")) {
                currentConfig.telegram.enabled = telegramJson.get("enabled").getAsBoolean();
            }
            if (telegramJson.has("botToken") && !telegramJson.get("botToken").getAsString().trim().isEmpty()) {
                currentConfig.telegram.botToken = telegramJson.get("botToken").getAsString();
                PokeAlertClient.LOGGER.info("✓ Migrated telegram bot token");
            }
            if (telegramJson.has("chatId") && !telegramJson.get("chatId").getAsString().trim().isEmpty()) {
                currentConfig.telegram.chatId = telegramJson.get("chatId").getAsString();
                PokeAlertClient.LOGGER.info("✓ Migrated telegram chat ID");
            }
            if (telegramJson.has("apiUrl")) {
                currentConfig.telegram.apiUrl = telegramJson.get("apiUrl").getAsString();
            }
            if (telegramJson.has("maxNotificationsPerMinute")) {
                currentConfig.telegram.maxNotificationsPerMinute = telegramJson.get("maxNotificationsPerMinute").getAsInt();
            }
            if (telegramJson.has("cooldownSeconds")) {
                currentConfig.telegram.cooldownSeconds = telegramJson.get("cooldownSeconds").getAsInt();
            }
            
            // Save merged config to pokealert-settings.json
            saveSettings(currentConfig);
            
            // Backup old telegram file
            File backupFile = new File(CONFIG_DIR, "pokealert-telegram.json.backup");
            Files.move(TELEGRAM_FILE.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            PokeAlertClient.LOGGER.info("✓ Telegram config migration complete! Old file backed up as pokealert-telegram.json.backup");
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to migrate Telegram config", e);
        }
    }

    /**
     * Get current configuration
     */
    public static PokeAlertConfig getConfig() {
        if (currentConfig == null) {
            currentConfig = loadSettings();
        }
        return currentConfig;
    }

    /**
     * Update and save configuration
     */
    public static void updateConfig(PokeAlertConfig newConfig) {
        // Update currentConfig reference BEFORE saving to ensure getConfig() returns updated config
        currentConfig = newConfig;
        saveSettings(newConfig);
    }

    /**
     * Reload all configurations from disk
     */
    public static void reload() {
        currentConfig = loadSettings();
        
        // v4.0.0: Migrate from legacy flat config to nested config
        if (currentConfig.migrateFromLegacy()) {
            PokeAlertClient.LOGGER.info("✓ Migrated config from flat format to nested format");
            saveSettings(currentConfig);
        }
        
        PokeAlertClient.LOGGER.info("Reloaded configuration");
    }
}
