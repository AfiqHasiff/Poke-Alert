package com.afiqhasiff.pokealert.client.util;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.notification.TelegramNotification;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects direct messages (DMs) sent to the player and sends notifications.
 * 
 * DM Format: [sender -> recipient]: message
 * Example: [tishuu -> MarshalJim]: its 2k per stack if u buy alot im doing big discounts
 * 
 * Requirements:
 * - Egg Hatcher must be enabled (eggHatcherEnabled)
 * - DM Detection must be enabled (dmDetectionEnabled)
 * - Telegram must be enabled for notifications
 */
public class DmDetector {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static String currentPlayerName = null;
    
    // Pattern to match DM format: [sender -> recipient]: message
    // Handles variations: spaces, no spaces, etc.
    private static final Pattern DM_PATTERN = Pattern.compile(
        "\\[([^\\]]+?)\\s*->\\s*([^\\]]+?)\\]:\\s*(.+)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Initialize DM detector - get player name
     */
    public static void initialize() {
        updatePlayerName();
        PokeAlertClient.LOGGER.info("DmDetector: Initialized (player: {})", 
            currentPlayerName != null ? currentPlayerName : "unknown");
    }
    
    /**
     * Update cached player name from client
     */
    public static void updatePlayerName() {
        if (client != null && client.player != null) {
            String name = client.player.getName().getString();
            if (name != null && !name.isEmpty()) {
                currentPlayerName = name;
                PokeAlertClient.LOGGER.info("DmDetector: Player name updated to {}", currentPlayerName);
            } else {
                PokeAlertClient.LOGGER.warn("DmDetector: Failed to get player name (name is null or empty)");
            }
        } else {
            PokeAlertClient.LOGGER.warn("DmDetector: Cannot update player name (client or player is null)");
        }
    }
    
    /**
     * Process incoming chat message to detect DMs
     * Called from ClientReceiveMessageEvents.CHAT or GAME
     */
    public static void onChatMessage(Text message) {
        if (message == null) {
            return;
        }
        
        // Convert Text to string - try multiple methods to get the full text
        String messageText = message.getString();
        
        // If getString() doesn't capture everything, try getting the full content
        // Some Text objects have complex structures with formatting
        if (messageText == null || messageText.isEmpty()) {
            // Try getting the full content including formatting codes
            messageText = message.getContent().toString();
        }
        
        // Quick check: if message contains "->", it might be a DM
        if (messageText == null || !messageText.contains("->")) {
            return; // Not a DM format, skip early
        }
        
        // Log that we found a potential DM (this confirms handler is being called)
        PokeAlertClient.LOGGER.info("DmDetector: Handler invoked for message with '->': '{}'", messageText);
        
        // Get config
        PokeAlertConfig config = ConfigManager.getConfig();
        
        // Check if mod is enabled
        if (!config.modEnabled) {
            return;
        }
        
        // Check if Egg Hatcher is enabled (required for DM detection)
        if (!config.eggHatcher.enabled) {
            return;
        }
        
        // Check if DM detection is enabled
        if (!config.eggHatcher.dmDetection.enabled) {
            return;
        }
        
        // Always try to update player name (in case it wasn't set on JOIN)
        updatePlayerName();
        
        // Check if player name is available
        if (currentPlayerName == null || "unknown".equals(currentPlayerName)) {
            PokeAlertClient.LOGGER.warn("DmDetector: Cannot detect DMs - player name is not available (currentPlayerName: {})", 
                currentPlayerName);
            return;
        }
        
        // Log potential DM for debugging
        PokeAlertClient.LOGGER.info("DmDetector: Processing potential DM message: '{}'", messageText);
        
        // Try to match DM pattern
        Matcher matcher = DM_PATTERN.matcher(messageText);
        if (!matcher.find()) {
            PokeAlertClient.LOGGER.warn("DmDetector: Message contains '->' but does not match DM pattern: '{}'", messageText);
            return; // Not a DM format
        }
        
        // Extract components
        String sender = matcher.group(1).trim();
        String recipient = matcher.group(2).trim();
        String dmMessage = matcher.group(3).trim();
        
        // Debug logging
        PokeAlertClient.LOGGER.info("DmDetector: Pattern matched - sender: '{}', recipient: '{}', currentPlayerName: '{}'", 
            sender, recipient, currentPlayerName);
        
        // Validate recipient matches current player (case-insensitive)
        if (!recipient.equalsIgnoreCase(currentPlayerName)) {
            PokeAlertClient.LOGGER.debug("DmDetector: Recipient '{}' does not match current player '{}'", 
                recipient, currentPlayerName);
            return; // Not a DM to this player
        }
        
        // Validate message is not empty
        if (dmMessage.isEmpty()) {
            return;
        }
        
        // Check if sender is in avoided players list
        boolean isAvoided = isPlayerAvoided(sender, config);
        
        // Log detection
        PokeAlertClient.LOGGER.info("DmDetector: DM detected from {} to {}: {}", 
            sender, recipient, dmMessage);
        if (isAvoided) {
            PokeAlertClient.LOGGER.warn("DmDetector: ⚠️ DM from AVOIDED player: {}", sender);
        }
        
        // Send notifications
        sendNotifications(sender, dmMessage, isAvoided, config);
    }
    
    /**
     * Check if sender is in avoided players list
     */
    private static boolean isPlayerAvoided(String sender, PokeAlertConfig config) {
        if (config.antiAfk.playerSafety.playersToAvoid == null || config.antiAfk.playerSafety.playersToAvoid.length == 0) {
            return false;
        }
        
        String senderLower = sender.toLowerCase();
        return Arrays.stream(config.antiAfk.playerSafety.playersToAvoid)
            .anyMatch(avoided -> avoided != null && avoided.toLowerCase().equals(senderLower));
    }
    
    /**
     * Send notifications for DM
     */
    private static void sendNotifications(String sender, String message, boolean isAvoided, PokeAlertConfig config) {
        // In-game notification (optional)
        if (config.eggHatcher.dmDetection.inGameNotification && config.notifications.textEnabled) {
            sendInGameNotification(sender, message, isAvoided);
        }
        
        // Telegram notification
        if (config.eggHatcher.dmDetection.telegramNotification && config.telegram.enabled) {
            TelegramNotification telegram = new TelegramNotification();
            telegram.initialize(); // Initialize httpClient before use
            telegram.sendDmNotification(sender, message, isAvoided);
        }
    }
    
    /**
     * Send in-game notification
     */
    private static void sendInGameNotification(String sender, String message, boolean isAvoided) {
        if (client == null || client.player == null) return;
        
        client.execute(() -> {
            if (client.player != null) {
                MutableText notification = Text.literal("[")
                    .formatted(net.minecraft.util.Formatting.GRAY)
                    .append(Text.literal("DM").formatted(net.minecraft.util.Formatting.AQUA))
                    .append(Text.literal("] ").formatted(net.minecraft.util.Formatting.GRAY))
                    .append(Text.literal("From: ").formatted(net.minecraft.util.Formatting.WHITE))
                    .append(Text.literal(sender).formatted(net.minecraft.util.Formatting.YELLOW))
                    .append(Text.literal(": ").formatted(net.minecraft.util.Formatting.WHITE))
                    .append(Text.literal(message).formatted(net.minecraft.util.Formatting.GRAY));
                
                if (isAvoided) {
                    notification.append(Text.literal(" ⚠️ AVOIDED").formatted(
                        net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD));
                }
                
                client.player.sendMessage(notification, false);
            }
        });
    }
    
    /**
     * Get current player name (for debugging)
     */
    public static String getCurrentPlayerName() {
        return currentPlayerName;
    }
}

