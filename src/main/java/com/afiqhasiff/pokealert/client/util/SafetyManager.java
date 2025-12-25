package com.afiqhasiff.pokealert.client.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.notification.TelegramNotification;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Centralized safety stop management for v3.0.0 Anti-AFK system.
 * Uses AtomicBoolean to ensure thread-safe, single-trigger behavior.
 * 
 * When safety is triggered:
 * 1. Stops Baritone (#stop)
 * 2. Stops CoordinateMonitor
 * 3. Stops PlayerMonitor
 * 4. Sends in-game notification
 * 5. Sends Telegram notification (if enabled and requested)
 * 
 * @since v3.0.0
 */
public class SafetyManager {
    private static final AtomicBoolean safetyLock = new AtomicBoolean(false);
    private static volatile String lastSafetyReason = null;
    private static volatile long lastSafetyTime = 0;
    
    // Callback for EggHatcher to handle safety stops
    private static Consumer<String> onSafetyStopCallback;
    
    // Safety reason constants
    public static final String REASON_TELEPORT = "Teleport detected";
    public static final String REASON_PLAYER_LIST = "Avoided player detected in server";
    public static final String REASON_PLAYER_NEARBY = "Avoided player detected nearby";
    public static final String REASON_WORLD_CHANGE = "Unexpected world change";
    public static final String REASON_TIMEOUT = "Too many consecutive timeouts";
    public static final String REASON_USER_TOGGLE = "User toggled off";
    public static final String REASON_DISCONNECT = "Player disconnected";
    public static final String REASON_REGION_INVALID = "Anti-AFK region not configured";
    
    /**
     * Attempt to trigger safety stop (thread-safe, first caller wins)
     * Stops all monitors and Baritone, sends notifications
     * 
     * @param reason Description of why safety was triggered (use REASON_* constants)
     * @param notifyTelegram Whether to send Telegram notification
     * @return true if this call triggered the stop, false if already stopped
     */
    public static boolean triggerSafetyStop(String reason, boolean notifyTelegram) {
        // Atomic check-and-set - only first caller succeeds
        if (safetyLock.compareAndSet(false, true)) {
            lastSafetyReason = reason;
            lastSafetyTime = System.currentTimeMillis();
            
            PokeAlertClient.LOGGER.error("SafetyManager: SAFETY STOP TRIGGERED - {}", reason);
            
            // Stop all systems (order matters - stop movement first)
            BaritoneController.stop();
            CoordinateMonitor.stopMonitoring();
            PlayerMonitor.stopMonitoring();
            
            // Send in-game notification (always)
            sendInGameNotification(reason);
            
            // Send Telegram notification (if requested)
            if (notifyTelegram) {
                sendTelegramNotification(reason);
            }
            
            // Trigger callback for EggHatcher
            if (onSafetyStopCallback != null) {
                MinecraftClient.getInstance().execute(() -> {
                    onSafetyStopCallback.accept(reason);
                });
            }
            
            return true;
        }
        
        // Already stopped - log but don't duplicate notifications
        PokeAlertClient.LOGGER.debug("SafetyManager: Ignoring duplicate safety trigger - {}", reason);
        return false;
    }
    
    /**
     * Check if safety has been triggered
     * @return true if safety stop is currently active
     */
    public static boolean isSafetyTriggered() {
        return safetyLock.get();
    }
    
    /**
     * Get the reason for last safety stop
     * @return Reason string, or null if no safety stop has occurred
     */
    public static String getLastSafetyReason() {
        return lastSafetyReason;
    }
    
    /**
     * Get timestamp of last safety stop
     * @return Unix timestamp in milliseconds, or 0 if never triggered
     */
    public static long getLastSafetyTime() {
        return lastSafetyTime;
    }
    
    /**
     * Reset safety state (called when starting new automation cycle)
     * Must be called before starting Step 4 (Anti-AFK)
     */
    public static void reset() {
        safetyLock.set(false);
        // Don't clear lastSafetyReason - useful for debugging
        PokeAlertClient.LOGGER.info("SafetyManager: Reset for new cycle");
    }
    
    /**
     * Register callback for safety stop events
     * @param callback Consumer receiving the safety reason
     */
    public static void onSafetyStop(Consumer<String> callback) {
        onSafetyStopCallback = callback;
    }
    
    /**
     * Internal: Send in-game chat notification
     */
    private static void sendInGameNotification(String reason) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        PokeAlertConfig config = ConfigManager.getConfig();
        if (!config.inGameTextEnabled) return;
        
        client.execute(() -> {
            if (client.player != null) {
                Text message = Text.literal("[")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("⚠ SAFETY STOP: ").formatted(Formatting.RED, Formatting.BOLD))
                    .append(Text.literal(reason).formatted(Formatting.YELLOW));
                client.player.sendMessage(message, false);
            }
        });
    }
    
    /**
     * Internal: Send Telegram notification
     */
    private static void sendTelegramNotification(String reason) {
        PokeAlertConfig config = ConfigManager.getConfig();
        if (!config.telegramEnabled || !config.isTelegramValid()) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                TelegramNotification telegram = new TelegramNotification();
                telegram.initialize();
                
                String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date());
                
                StringBuilder message = new StringBuilder();
                message.append("🚨 <b>Egg Hatcher Safety Stop</b>\n");
                message.append("• <b>Reason:</b> <i>").append(escapeHtml(reason)).append("</i>\n");
                message.append("⚠️ <i>Manual restart required</i>");
                
                telegram.sendEggTimerNotification(message.toString());
                
                PokeAlertClient.LOGGER.info("SafetyManager: Telegram notification sent");
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("SafetyManager: Failed to send Telegram notification", e);
            }
        });
    }
    
    /**
     * Escape HTML special characters for Telegram
     */
    private static String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
    
    /**
     * Get status string for debugging
     */
    public static String getStatus() {
        if (!safetyLock.get()) {
            return "OK";
        }
        return "STOPPED: " + (lastSafetyReason != null ? lastSafetyReason : "Unknown");
    }
}

