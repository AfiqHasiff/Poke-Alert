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
    
    // Current file paths
    private static final File SETTINGS_FILE = new File(CONFIG_DIR, "pokealert-settings.json");
    private static final File TELEGRAM_FILE = new File(CONFIG_DIR, "pokealert-telegram.json");
    
    private static PokeAlertConfig currentConfig;
    private static TelegramConfig telegramConfig;

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
        
        if (!TELEGRAM_FILE.exists() && LEGACY_TELEGRAM_FILE.exists()) {
            // Migrate from cobblemondetector-telegram.json
            migrateLegacyTelegram();
        }
        
        // Load configurations
        currentConfig = loadSettings();
        telegramConfig = loadTelegramConfig();
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
     * Load Telegram configuration from file
     */
    public static TelegramConfig loadTelegramConfig() {
        TelegramConfig config = new TelegramConfig();
        
        if (TELEGRAM_FILE.exists()) {
            try (FileReader reader = new FileReader(TELEGRAM_FILE)) {
                config = GSON.fromJson(reader, TelegramConfig.class);
                PokeAlertClient.LOGGER.info("Loaded Telegram config from {}", TELEGRAM_FILE.getName());
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("Failed to read Telegram config, using defaults", e);
            }
        } else {
            // Create default Telegram config file
            saveTelegramConfig(config);
            PokeAlertClient.LOGGER.info("Created default Telegram config at {}", TELEGRAM_FILE.getName());
        }
        
        return config;
    }

    /**
     * Save Telegram configuration to file
     */
    public static void saveTelegramConfig(TelegramConfig config) {
        try (FileWriter writer = new FileWriter(TELEGRAM_FILE)) {
            GSON.toJson(config, writer);
            telegramConfig = config;
            PokeAlertClient.LOGGER.info("Saved Telegram config to {}", TELEGRAM_FILE.getName());
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to save Telegram config", e);
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
     * Migrate from legacy cobblemondetector-telegram.json to pokealert-telegram.json
     */
    private static void migrateLegacyTelegram() {
        try {
            PokeAlertClient.LOGGER.info("Migrating cobblemondetector-telegram.json to pokealert-telegram.json...");
            
            // Read legacy Telegram config
            FileReader reader = new FileReader(LEGACY_TELEGRAM_FILE);
            TelegramConfig legacyConfig = GSON.fromJson(reader, TelegramConfig.class);
            reader.close();
            
            // Save to new location
            saveTelegramConfig(legacyConfig);
            
            // Backup legacy file
            File backupFile = new File(CONFIG_DIR, "cobblemondetector-telegram.json.backup");
            Files.move(LEGACY_TELEGRAM_FILE.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            PokeAlertClient.LOGGER.info("Telegram config migration complete! Backed up as {}", backupFile.getName());
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to migrate legacy Telegram config", e);
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
     * Get Telegram configuration
     */
    public static TelegramConfig getTelegramConfig() {
        if (telegramConfig == null) {
            telegramConfig = loadTelegramConfig();
        }
        return telegramConfig;
    }

    /**
     * Reload all configurations from disk
     */
    public static void reload() {
        currentConfig = loadSettings();
        telegramConfig = loadTelegramConfig();
        PokeAlertClient.LOGGER.info("Reloaded all configurations");
    }
}

