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
    
    // Legacy file paths (for migration)
    private static final File OLD_CONFIG_FILE = new File(CONFIG_DIR, "PokeAlert.json");
    private static final File LEGACY_SETTINGS_FILE = new File(CONFIG_DIR, "cobblemondetector-settings.json");
    private static final File LEGACY_TELEGRAM_FILE = new File(CONFIG_DIR, "cobblemondetector-telegram.json");
    
    // Current file path (unified config)
    private static final File SETTINGS_FILE = new File(CONFIG_DIR, "pokealert-settings.json");
    
    // Legacy telegram file (for migration)
    private static final File TELEGRAM_FILE = new File(CONFIG_DIR, "pokealert-telegram.json");
    
    private static PokeAlertConfig currentConfig;

    /**
     * Initialize and load all configurations
     */
    public static void initialize() {
        CONFIG_DIR.mkdirs();
        
        // Migrate from legacy config files if needed
        if (!SETTINGS_FILE.exists()) {
            if (LEGACY_SETTINGS_FILE.exists()) {
                // Migrate from cobblemondetector-settings.json
                migrateLegacySettings();
            } else if (OLD_CONFIG_FILE.exists()) {
                // Migrate from very old PokeAlert.json
                migrateOldConfig();
            }
        }
        
        // Load main configuration
        currentConfig = loadSettings();
        
        // Migrate telegram config if it exists (merge into main config)
        if (TELEGRAM_FILE.exists() || LEGACY_TELEGRAM_FILE.exists()) {
            migrateTelegramConfig();
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
     * Migrate Telegram configuration from separate file into main config
     */
    private static void migrateTelegramConfig() {
        try {
            File telegramFile = TELEGRAM_FILE.exists() ? TELEGRAM_FILE : LEGACY_TELEGRAM_FILE;
            
            if (!telegramFile.exists()) {
                return;
            }
            
            PokeAlertClient.LOGGER.info("Migrating Telegram config from {} into main config...", telegramFile.getName());
            
            // Read telegram config as JsonObject to avoid dependency on deleted TelegramConfig class
            FileReader reader = new FileReader(telegramFile);
            com.google.gson.JsonObject telegramJson = GSON.fromJson(reader, com.google.gson.JsonObject.class);
            reader.close();
            
            // Merge into main config
            if (telegramJson.has("enabled")) {
                currentConfig.telegramEnabled = telegramJson.get("enabled").getAsBoolean();
            }
            if (telegramJson.has("botToken")) {
                currentConfig.telegramBotToken = telegramJson.get("botToken").getAsString();
            }
            if (telegramJson.has("chatId")) {
                currentConfig.telegramChatId = telegramJson.get("chatId").getAsString();
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
            
            // Save merged config
            saveSettings(currentConfig);
            
            // Backup and delete old telegram file
            File backupFile = new File(CONFIG_DIR, telegramFile.getName() + ".backup");
            Files.move(telegramFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            PokeAlertClient.LOGGER.info("Telegram config migration complete! Backed up as {}", backupFile.getName());
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to migrate Telegram config", e);
        }
    }

    /**
     * Migrate from legacy cobblemondetector-settings.json to pokealert-settings.json
     */
    private static void migrateLegacySettings() {
        try {
            PokeAlertClient.LOGGER.info("Migrating cobblemondetector-settings.json to pokealert-settings.json...");
            
            // Read legacy config
            FileReader reader = new FileReader(LEGACY_SETTINGS_FILE);
            PokeAlertConfig legacyConfig = GSON.fromJson(reader, PokeAlertConfig.class);
            reader.close();
            
            // Save to new location
            saveSettings(legacyConfig);
            
            // Backup legacy file
            File backupFile = new File(CONFIG_DIR, "cobblemondetector-settings.json.backup");
            Files.move(LEGACY_SETTINGS_FILE.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            PokeAlertClient.LOGGER.info("Settings migration complete! Backed up as {}", backupFile.getName());
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to migrate legacy settings", e);
        }
    }
    
    
    /**
     * Migrate from very old PokeAlert.json format to new format
     */
    private static void migrateOldConfig() {
        try {
            PokeAlertClient.LOGGER.info("Migrating PokeAlert.json to new format...");
            
            // Read old config
            FileReader reader = new FileReader(OLD_CONFIG_FILE);
            PokeAlertConfig oldConfig = GSON.fromJson(reader, PokeAlertConfig.class);
            reader.close();
            
            // Save to new location
            saveSettings(oldConfig);
            
            // Backup old file
            File backupFile = new File(CONFIG_DIR, "PokeAlert.json.backup");
            Files.move(OLD_CONFIG_FILE.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            PokeAlertClient.LOGGER.info("Migration complete! Old config backed up as {}", backupFile.getName());
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to migrate old config", e);
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

