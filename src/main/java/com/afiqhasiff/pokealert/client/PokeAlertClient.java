package com.afiqhasiff.pokealert.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.command.PokeAlertCommand;
import com.afiqhasiff.pokealert.client.notification.InGameNotification;
import com.afiqhasiff.pokealert.client.notification.NotificationManager;
import com.afiqhasiff.pokealert.client.notification.PokemonSpawnData;
import com.afiqhasiff.pokealert.client.notification.TelegramNotification;
import com.afiqhasiff.pokealert.client.notification.EggTimerManager;
import com.afiqhasiff.pokealert.client.automation.EggHatcher;
import com.afiqhasiff.pokealert.client.util.DmDetector;
import com.afiqhasiff.pokealert.client.telegram.TelegramCommandReceiver;

public class PokeAlertClient implements ClientModInitializer {
    public static final String MOD_ID = "pokealert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Singleton instance for accessing from config screen
    private static PokeAlertClient instance;

    // cobblemonCache used to prevent duplicate messages after a pokemon is spawned
    // when a player recalls and redeploys a pokemon it get a new ID though :/
    public ArrayList<UUID> cobblemonCache = new ArrayList<>();
    public PokeAlertConfig config;
    public String[] whitelist;
    public NotificationManager notificationManager;

    public static final Identifier NOTIFICATION_SOUND_ID = Identifier.of(MOD_ID, "pla_notification");
    public static SoundEvent NOTIFICATION_SOUND_EVENT;
    
    // Keybindings
    public static KeyBinding toggleModKey;
    public static KeyBinding startEggTimerKey;
    public static KeyBinding eggHatcherKey;
    // v3.0.0: antiAfkKeybind REMOVED - now using internal Baritone-based Anti-AFK
    
    // Confirmation tracking for disabling with running modules
    private long lastDisableAttemptTime = 0;
    private static final long DISABLE_CONFIRM_WINDOW = 3000; // 3 seconds in milliseconds
    
    public static PokeAlertClient getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("PokéAlert: Starting client initialization...");
        
        // Set singleton instance
        instance = this;
        
        // Register sound event
        NOTIFICATION_SOUND_EVENT = Registry.register(
            Registries.SOUND_EVENT,
            NOTIFICATION_SOUND_ID,
            SoundEvent.of(NOTIFICATION_SOUND_ID)
        );
        
        // Initialize configuration system
        ConfigManager.initialize();
        config = ConfigManager.getConfig();
        whitelist = config.getCombinedWhitelist();
        
        // Initialize notification system
        notificationManager = new NotificationManager(30000); // 30 second cooldown
        notificationManager.registerService(new InGameNotification());
        notificationManager.registerService(new TelegramNotification());
        
        // Initialize DM detector
        try {
            DmDetector.initialize();
            
            // Register chat message listener for DM detection
            // Note: DMs might come through as CHAT or GAME messages depending on server implementation
            ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
                try {
                    LOGGER.debug("DmDetector: CHAT event received");
                    // Process message on client thread
                    DmDetector.onChatMessage(message);
                } catch (Exception e) {
                    LOGGER.error("DmDetector: Error processing chat message", e);
                }
            });
            
            // Also listen to GAME messages (some servers send DMs as game messages)
            try {
                ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                    try {
                        LOGGER.debug("DmDetector: GAME event received");
                        // Process message on client thread
                        DmDetector.onChatMessage(message);
                    } catch (Exception e) {
                        LOGGER.error("DmDetector: Error processing game message", e);
                    }
                });
            } catch (Exception e) {
                LOGGER.warn("DmDetector: Failed to register GAME event listener (this is OK if Fabric API version doesn't support it): {}", e.getMessage());
            }
        } catch (Exception e) {
            LOGGER.error("DmDetector: Failed to initialize DM detection system", e);
        }
        
        // Register keybindings
        toggleModKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pokealert.toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_SEMICOLON, // Default to ':' key
            "key.categories.pokealert"
        ));
        
        startEggTimerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pokealert.eggtimer",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_APOSTROPHE, // Default to '\'' key
            "key.categories.pokealert"
        ));
        
        eggHatcherKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pokealert.egghatcher",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_HOME, // Home key for egg hatcher automation
            "key.categories.pokealert"
        ));
        
        // v3.0.0: Anti-AFK keybind registration REMOVED
        // Anti-AFK is now handled internally via Baritone #goto commands
        // No external keybind needed - controlled by EggHatcher automation
        LOGGER.info("v3.0.0: Anti-AFK now uses internal Baritone control (no external keybind)");
        
        // Register commands
        PokeAlertCommand.register();
        
        // Register connection event handlers for realm manager automation
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // Update DM detector player name
            DmDetector.updatePlayerName();
            
            // Only start realm manager if PokeAlert is enabled
            if (ConfigManager.getConfig().modEnabled) {
                EggHatcher.getInstance().markConnected();
                LOGGER.info("PokéAlert: Player connected to server - Egg Hatcher initialized");
            } else {
                LOGGER.info("PokéAlert: Player connected to server - Egg Hatcher skipped (mod disabled)");
            }
        });
        
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            EggHatcher.getInstance().markDisconnected();
            // v3.0.0: Stop Baritone on disconnect
            com.afiqhasiff.pokealert.client.util.BaritoneController.stop();
            LOGGER.info("PokéAlert: Player disconnected from server");
        });
        
        LOGGER.info("PokéAlert initialized with {} whitelisted Pokemon (Mod Enabled: {})", whitelist.length, config.modEnabled);
        
        // Initialize Telegram Command Receiver
        LOGGER.info("Telegram Command Execution - Enabled: {}, Valid: {}", 
            config.telegramCommandExecutionEnabled, config.isTelegramValid());
        if (config.telegramCommandExecutionEnabled && config.isTelegramValid()) {
            LOGGER.info("Starting Telegram Command Receiver polling...");
            TelegramCommandReceiver.getInstance().startPolling();
        } else {
            LOGGER.warn("Telegram Command Receiver NOT starting - CommandExecutionEnabled: {}, IsTelegramValid: {}", 
                config.telegramCommandExecutionEnabled, config.isTelegramValid());
        }
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Process toggle mod keybinding
            while (toggleModKey.wasPressed()) {
                // Check if we're trying to disable the mod
                if (config.modEnabled) {
                    // Check if any modules are running
                    boolean eggTimerWasRunning = EggTimerManager.getInstance().isTimerRunning();
                    boolean eggHatcherWasRunning = EggHatcher.getInstance().isRunning();
                    boolean hasRunningModules = eggTimerWasRunning || eggHatcherWasRunning;
                    
                    if (hasRunningModules) {
                        long currentTime = System.currentTimeMillis();
                        boolean withinConfirmWindow = (currentTime - lastDisableAttemptTime) < DISABLE_CONFIRM_WINDOW;
                        
                        if (!withinConfirmWindow) {
                            // First attempt - show warning with details
                            lastDisableAttemptTime = currentTime;
                            
                            if (client.player != null && config.inGameTextEnabled) {
                                MutableText message = Text.literal("[")
                                    .formatted(Formatting.GRAY)
                                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                                    .append(Text.literal("Warning: ").formatted(Formatting.YELLOW));
                                
                                if (eggTimerWasRunning && eggHatcherWasRunning) {
                                    int remainingMins = EggTimerManager.getInstance().getRemainingMinutes();
                                    message.append(Text.literal("Egg Timer (").formatted(Formatting.WHITE))
                                        .append(Text.literal(remainingMins + " min").formatted(Formatting.AQUA))
                                        .append(Text.literal(") & Egg Hatcher running").formatted(Formatting.WHITE));
                                } else if (eggTimerWasRunning) {
                                    int remainingMins = EggTimerManager.getInstance().getRemainingMinutes();
                                    message.append(Text.literal("Egg Timer running (").formatted(Formatting.WHITE))
                                        .append(Text.literal(remainingMins + " min remaining").formatted(Formatting.AQUA))
                                        .append(Text.literal(")").formatted(Formatting.WHITE));
                                } else {
                                    message.append(Text.literal("Egg Hatcher active").formatted(Formatting.WHITE));
                                }
                                
                                message.append(Text.literal(" - Press again within 3s to force stop").formatted(Formatting.GRAY));
                                client.player.sendMessage(message, false);
                            }
                            continue; // Don't disable yet
                        }
                        
                        // Second attempt within window - proceed with forced disable
                        lastDisableAttemptTime = 0; // Reset confirmation
                        
                        // Cancel egg timer if running
                        if (eggTimerWasRunning) {
                            EggTimerManager.getInstance().stopTimer(true); // true = silent stop
                        }
                        
                        // Cancel egg hatcher if running
                        if (eggHatcherWasRunning) {
                            EggHatcher.getInstance().stopAutomation();
                        }
                        
                        // Disable mod
                        config.modEnabled = false;
                        ConfigManager.saveSettings(config);
                        
                        // Send feedback message with what was cancelled
                        if (client.player != null && config.inGameTextEnabled) {
                            MutableText message = Text.literal("[")
                                .formatted(Formatting.GRAY)
                                .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                                .append(Text.literal("] ").formatted(Formatting.GRAY))
                                .append(Text.literal("Mod ").formatted(Formatting.WHITE))
                                .append(Text.literal("Disabled").formatted(Formatting.RED))
                                .append(Text.literal(" - ").formatted(Formatting.GRAY));
                            
                            if (eggTimerWasRunning && eggHatcherWasRunning) {
                                message.append(Text.literal("Egg Timer & Egg Hatcher force stopped").formatted(Formatting.YELLOW));
                            } else if (eggTimerWasRunning) {
                                message.append(Text.literal("Egg Timer force stopped").formatted(Formatting.YELLOW));
                            } else {
                                message.append(Text.literal("Egg Hatcher force stopped").formatted(Formatting.YELLOW));
                            }
                            
                            client.player.sendMessage(message, false);
                        }
                    } else {
                        // No running modules - disable normally
                        config.modEnabled = false;
                        ConfigManager.saveSettings(config);
                        lastDisableAttemptTime = 0; // Reset confirmation
                        
                        if (client.player != null && config.inGameTextEnabled) {
                            MutableText message = Text.literal("[")
                                .formatted(Formatting.GRAY)
                                .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                                .append(Text.literal("] ").formatted(Formatting.GRAY))
                                .append(Text.literal("Mod ").formatted(Formatting.WHITE))
                                .append(Text.literal("Disabled").formatted(Formatting.RED));
                            
                            client.player.sendMessage(message, false);
                        }
                    }
                } else {
                    // Enabling mod - always allowed
                    config.modEnabled = true;
                    ConfigManager.saveSettings(config);
                    lastDisableAttemptTime = 0; // Reset confirmation
                    
                    if (client.player != null && config.inGameTextEnabled) {
                        MutableText message = Text.literal("[")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                            .append(Text.literal("] ").formatted(Formatting.GRAY))
                            .append(Text.literal("Mod ").formatted(Formatting.WHITE))
                            .append(Text.literal("Enabled").formatted(Formatting.GREEN));
                        
                        client.player.sendMessage(message, false);
                    }
                }
            }
            
            // Process egg timer keybinding
            while (startEggTimerKey.wasPressed()) {
                // Check if mod is disabled
                if (!config.modEnabled) {
                    if (client.player != null && config.inGameTextEnabled) {
                        Text message = Text.literal("[")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                            .append(Text.literal("] ").formatted(Formatting.GRAY))
                            .append(Text.literal("Egg Timer disabled - Enable PokeAlert first").formatted(Formatting.YELLOW));
                        client.player.sendMessage(message, false);
                    }
                    continue;
                }
                
                EggTimerManager timerManager = EggTimerManager.getInstance();
                timerManager.handleTimerToggle();
            }
            
            // Process egg hatcher automation keybinding
            while (eggHatcherKey.wasPressed()) {
                // Check if mod is disabled
                if (!config.modEnabled) {
                    if (client.player != null && config.inGameTextEnabled) {
                        Text message = Text.literal("[")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                            .append(Text.literal("] ").formatted(Formatting.GRAY))
                            .append(Text.literal("Egg Hatcher disabled - Enable PokeAlert first").formatted(Formatting.YELLOW));
                        client.player.sendMessage(message, false);
                    }
                    continue;
                }
                
                EggHatcher eggHatcher = EggHatcher.getInstance();
                String status = eggHatcher.getStatus();
                // Check if automation is running (from spawn) OR Anti-AFK is active (from overworld) OR countdown active
                boolean isRunning = eggHatcher.isRunning() || eggHatcher.isAntiAfkActive() || 
                                   status.contains("Server Buffer") || status.contains("Starting in");
                
                // If automation is running OR Anti-AFK is active OR there's an active countdown, stop and disable completely
                if (isRunning) {
                    eggHatcher.stopAutomation();
                    eggHatcher.disableCompletely();
                } else {
                    // Not running - just toggle enable/disabled normally
                    eggHatcher.toggleAutomation();
                }
            }
            
            PlayerEntity player = client.player;
            
            // Check if mod is enabled and world exists
            if (!config.modEnabled || client.world == null || player == null) {
                return;
            }
            
            // Check if current world is excluded
            String worldName = client.world.getRegistryKey().getValue().toString();
            if (config.isWorldExcluded(worldName)) {
                return;
            }

            for (Entity entity : client.world.getEntities()) {
                if (
                    !entity.getType().toString().equals("entity.cobblemon.pokemon")
                    || cobblemonCache.contains(entity.getUuid())
                ) {
                    continue;
                }
                cobblemonCache.add(entity.getUuid());
                
                PokemonEntity pokemonEntity = (PokemonEntity) entity;
                Pokemon pokemon = pokemonEntity.getPokemon();
                
                String fullName = pokemonEntity.getName().getString();
                
                // Primary filter: Check for blacklist character in name (works everywhere)
                // Users rename their Pokémon with this character (e.g., "Charizard-") to prevent notifications
                if (config.blacklistCharacter != null && 
                    !config.blacklistCharacter.isEmpty() && 
                    fullName.contains(config.blacklistCharacter)) {
                    LOGGER.debug("Skipping Pokemon with blacklist character '{}': {}", 
                        config.blacklistCharacter, fullName);
                    continue;
                }
                
                // Backup filter: Check if Pokemon is owned using Cobblemon's isWild() method
                // Note: In multiplayer, this may not work reliably for all player-owned Pokémon
                if (!pokemon.isWild()) {
                    LOGGER.debug("Skipping player-owned Pokemon: {} (UUID: {})", 
                        fullName, entity.getUuid());
                    continue;
                }
                
                // Skip boss Pokemon (they contain formatting codes § and "Boss" text)
                if (fullName.contains("§") || fullName.toLowerCase().contains("boss")) {
                    continue;
                }
                
                // Strip "Shiny " prefix to get the base Pokemon name
                String pokemonName = fullName;
                if (fullName.startsWith("Shiny ")) {
                    pokemonName = fullName.substring(6); // Remove "Shiny " (6 characters)
                }
                
                // Use the new shouldNotify method which checks both whitelist and blacklist
                boolean isShiny = pokemon.getShiny();
                if (config.shouldNotify(pokemonName) || (isShiny && config.broadcastAllShinies)) {
                    // Create spawn data with clean Pokemon name (without "Shiny" prefix)
                    PokemonSpawnData spawnData = PokemonSpawnData.fromEntity(
                        entity,
                        pokemonName,  // Now this is just "Floragato" without "Shiny"
                        isShiny,
                        worldName
                    );

                    // Send notification through all services
                    notificationManager.notifyAll(spawnData);
                }
            }
        });
    }
    
    /**
     * Reload configuration without restarting the game.
     * Called from the config screen after saving changes.
     */
    public void reloadConfig() {
        config = ConfigManager.getConfig();
        whitelist = config.getCombinedWhitelist();
        LOGGER.info("Configuration reloaded! Now tracking {} whitelisted Pokemon", whitelist.length);
        
        // Reload Telegram Command Receiver config
        TelegramCommandReceiver.getInstance().reloadConfig();
        
        // Start/stop polling based on new config
        if (config.telegramCommandExecutionEnabled && config.isTelegramValid()) {
            if (!TelegramCommandReceiver.getInstance().isPolling()) {
                TelegramCommandReceiver.getInstance().startPolling();
            }
        } else {
            if (TelegramCommandReceiver.getInstance().isPolling()) {
                TelegramCommandReceiver.getInstance().stopPolling();
            }
        }
    }
}
