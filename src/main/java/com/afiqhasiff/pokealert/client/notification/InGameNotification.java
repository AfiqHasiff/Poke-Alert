package com.afiqhasiff.pokealert.client.notification;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.util.RarityScraper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * In-game notification service.
 * Sends notifications via Minecraft chat and plays sound effects.
 */
public class InGameNotification extends NotificationService {
    private final MinecraftClient client;

    public InGameNotification() {
        this.client = MinecraftClient.getInstance();
    }

    @Override
    public void sendNotification(PokemonSpawnData data) {
        PlayerEntity player = client.player;
        if (player == null) {
            return;
        }
        
        // Check if in-game text notification is enabled
        if (!PokeAlertClient.getInstance().config.inGameTextEnabled) {
            return;
        }

        // Get rarity info from shared scraper (uses cached value from PokemonSpawnData)
        RarityScraper.RarityInfo rarity = data.getRarityInfo();
        
        // Handle shiny prefix
        String rarityText = data.isShiny() ? "Shiny " + rarity.getName() : rarity.getName();
        Formatting rarityColor = data.isShiny() ? Formatting.LIGHT_PURPLE : rarity.getColor();
        
        // Determine article (A or An)
        String article = getArticle(rarityText);
        
        // Build the message: [PokeAlert] A/An <Rarity> <PokemonName> spawned near you!
        Text message = Text.literal("[").formatted(Formatting.GRAY)
            .append(Text.literal("PokeAlert").formatted(Formatting.RED))
            .append(Text.literal("] ").formatted(Formatting.GRAY))
            .append(Text.literal(article + " ").formatted(Formatting.GRAY))
            .append(Text.literal(rarityText).formatted(rarityColor))
            .append(Text.literal(" "))
            .append(Text.literal(data.getPokemonName()).formatted(Formatting.WHITE))
            .append(Text.literal(" spawned near you!").formatted(Formatting.GRAY));

        // Send chat message
        player.sendMessage(message, false);

        // Play notification sound if enabled
        if (PokeAlertClient.getInstance().config.inGameSoundEnabled) {
            float volume = PokeAlertClient.getInstance().config.inGameSoundVolume;
            player.playSound(
                PokeAlertClient.NOTIFICATION_SOUND_EVENT,
                volume * 10f,  // volume (scaled to Minecraft's range)
                1f             // pitch
            );
        }
    }
    
    /**
     * Returns "A" or "An" based on the first letter of the word
     */
    private String getArticle(String word) {
        if (word == null || word.isEmpty()) {
            return "A";
        }
        char firstChar = Character.toLowerCase(word.charAt(0));
        return (firstChar == 'a' || firstChar == 'e' || firstChar == 'i' || 
                firstChar == 'o' || firstChar == 'u') ? "An" : "A";
    }

    @Override
    public boolean isEnabled() {
        // In-game notifications are always enabled
        return true;
    }

    @Override
    public void initialize() {
        // No initialization needed for in-game notifications
    }

    @Override
    public String getServiceName() {
        return "In-Game Notification";
    }
}

