package com.afiqhasiff.pokealert.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.afiqhasiff.pokealert.client.PokeAlertClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages configuration files for PokéAlert.
 * Handles loading, saving, and migration of config files.
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
        
        // Smart telegram config migration:
        // - If old telegram file exists AND main config telegram is empty: migrate from old file
        // - If old telegram file exists AND main config telegram is populated: skip migration (manual config)
        // - If old telegram file doesn't exist: use main config values (even if empty)
        if (TELEGRAM_FILE.exists()) {
            // Check if main config telegram values are empty (need migration)
            boolean telegramIsEmpty = (currentConfig.telegramBotToken == null || currentConfig.telegramBotToken.trim().isEmpty()) &&
                                     (currentConfig.telegramChatId == null || currentConfig.telegramChatId.trim().isEmpty());
            
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
            try (FileReader reader = new FileReader(SETTINGS_FILE)) {
                config = GSON.fromJson(reader, PokeAlertConfig.class);
                PokeAlertClient.LOGGER.info("Loaded settings from {}", SETTINGS_FILE.getName());
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
            
            // Merge into main config (only overwrites empty values)
            if (telegramJson.has("enabled")) {
                currentConfig.telegramEnabled = telegramJson.get("enabled").getAsBoolean();
            }
            if (telegramJson.has("botToken") && !telegramJson.get("botToken").getAsString().trim().isEmpty()) {
                currentConfig.telegramBotToken = telegramJson.get("botToken").getAsString();
                PokeAlertClient.LOGGER.info("✓ Migrated telegram bot token");
            }
            if (telegramJson.has("chatId") && !telegramJson.get("chatId").getAsString().trim().isEmpty()) {
                currentConfig.telegramChatId = telegramJson.get("chatId").getAsString();
                PokeAlertClient.LOGGER.info("✓ Migrated telegram chat ID");
            }
            if (telegramJson.has("apiUrl")) {
                currentConfig.telegramApiUrl = telegramJson.get("apiUrl").getAsString();
            }
            if (telegramJson.has("maxNotificationsPerMinute")) {
                currentConfig.telegramMaxNotificationsPerMinute = telegramJson.get("maxNotificationsPerMinute").getAsInt();
            }
            if (telegramJson.has("cooldownSeconds")) {
                currentConfig.telegramCooldownSeconds = telegramJson.get("cooldownSeconds").getAsInt();
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
        saveSettings(newConfig);
    }

    /**
     * Reload all configurations from disk
     */
    public static void reload() {
        currentConfig = loadSettings();
        PokeAlertClient.LOGGER.info("Reloaded configuration");
    }
}

