package com.afiqhasiff.pokealert.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.notification.InGameNotification;
import com.afiqhasiff.pokealert.client.notification.NotificationManager;
import com.afiqhasiff.pokealert.client.notification.PokemonSpawnData;
import com.afiqhasiff.pokealert.client.notification.TelegramNotification;

public class PokeAlertClient implements ClientModInitializer {
    public static final String MOD_ID = "pokealert";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Singleton instance for accessing from config screen
    private static PokeAlertClient instance;

    // cobblemonCache used to prevent duplicate messages after a pokemon is spawned
    // when a player recalls and redeploys a pokemon it get a new ID though :/
    public ArrayList<UUID> cobblemonCache = new ArrayList<>();
    public PokeAlertConfig config;
    public String[] allowList;
    public NotificationManager notificationManager;

    public static Identifier NOTIFICATION_SOUND_ID = Identifier.of(MOD_ID, "pla_notification");
    public final static SoundEvent NOTIFICATION_SOUND_EVENT = SoundEvent.of(NOTIFICATION_SOUND_ID);
    
    public static PokeAlertClient getInstance() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        // Set singleton instance
        instance = this;
        
        // Initialize configuration system
        ConfigManager.initialize();
        config = ConfigManager.getConfig();
        allowList = config.getCombinedAllowlist();
        
        // Initialize notification system
        notificationManager = new NotificationManager(30000); // 30 second cooldown
        notificationManager.registerService(new InGameNotification());
        notificationManager.registerService(new TelegramNotification());
        
        LOGGER.info("PokéAlert initialized with {} allowed Pokemon", allowList.length);
        
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PlayerEntity player = client.player;
            
            if (client.world == null) {
                return;
            }

            for (Entity entity : client.world.getEntities()) {
                if (
                    player == null
                    || !entity.getType().toString().equals("entity.cobblemon.pokemon")
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
                
                for (String allowedPokemon : this.allowList) {
                    if (
                        allowedPokemon.equals(pokemonName.toLowerCase())
                        || (
                            pokemonEntity.getPokemon().getShiny()
                            && config.broadcastAllShinies
                        )
                    ) {
                        // Create spawn data with clean Pokemon name (without "Shiny" prefix)
                        String worldName = client.world.getRegistryKey().getValue().toString();
                        PokemonSpawnData spawnData = PokemonSpawnData.fromEntity(
                            entity,
                            pokemonName,  // Now this is just "Floragato" without "Shiny"
                            pokemonEntity.getPokemon().getShiny(),
                            worldName
                        );

                        // Send notification through all services
                        notificationManager.notifyAll(spawnData);
                        break;
                    }
                }
                continue;
            }
        });
    }
    
    /**
     * Reload configuration without restarting the game.
     * Called from the config screen after saving changes.
     */
    public void reloadConfig() {
        config = ConfigManager.getConfig();
        allowList = config.getCombinedAllowlist();
        LOGGER.info("Configuration reloaded! Now tracking {} allowed Pokemon", allowList.length);
    }
}
