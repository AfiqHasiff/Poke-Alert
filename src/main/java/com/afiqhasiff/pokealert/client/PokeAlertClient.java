package com.afiqhasiff.pokealert.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.command.PokeAlertCommand;
import com.afiqhasiff.pokealert.client.notification.InGameNotification;
import com.afiqhasiff.pokealert.client.notification.NotificationManager;
import com.afiqhasiff.pokealert.client.notification.PokemonSpawnData;
import com.afiqhasiff.pokealert.client.notification.TelegramNotification;
import com.afiqhasiff.pokealert.client.notification.EggTimerManager;

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
    
    public static PokeAlertClient getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
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
        
        // Register commands
        PokeAlertCommand.register();
        
        LOGGER.info("PokéAlert initialized with {} whitelisted Pokemon (Mod Enabled: {})", whitelist.length, config.modEnabled);
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Process toggle mod keybinding
            while (toggleModKey.wasPressed()) {
                config.modEnabled = !config.modEnabled;
                ConfigManager.saveSettings(config);
                
                // Send feedback message only if in-game text is enabled
                if (client.player != null && config.inGameTextEnabled) {
                    Text message = Text.literal("[")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("Mod ").formatted(Formatting.WHITE))
                        .append(Text.literal(config.modEnabled ? "Enabled" : "Disabled")
                            .formatted(config.modEnabled ? Formatting.GREEN : Formatting.RED));
                    
                    client.player.sendMessage(message, false);
                }
            }
            
            // Process egg timer keybinding
            while (startEggTimerKey.wasPressed()) {
                EggTimerManager timerManager = EggTimerManager.getInstance();
                timerManager.handleTimerToggle();
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
                String fullName = pokemonEntity.getName().getString();
                
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
                boolean isShiny = pokemonEntity.getPokemon().getShiny();
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
    }
}
