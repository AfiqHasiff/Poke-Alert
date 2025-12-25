package com.afiqhasiff.pokealert.client.util;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import net.minecraft.client.MinecraftClient;

/**
 * Controller for Baritone pathfinding commands.
 * Sends chat commands (#goto, #stop) to control player movement.
 * 
 * v3.0.0: Core component of the new internal Anti-AFK system.
 * 
 * Requirements:
 * - Baritone mod must be installed
 * - Chat prefix must be configured to '#' (default)
 */
public class BaritoneController {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    
    // Tracking state
    private static volatile boolean isPathing = false;
    private static int targetX = 0;
    private static int targetZ = 0;
    private static long lastCommandTime = 0;
    
    // Track allowSprint state to prevent redundant commands
    private static Boolean currentAllowSprint = null; // null = unknown, true/false = known state
    
    // Synchronization lock for command sending
    private static final Object commandLock = new Object();
    
    // Minimum delay between commands to prevent spam
    private static final long COMMAND_COOLDOWN_MS = 200;
    
    /**
     * Send #goto command to navigate to specified X, Z coordinates.
     * Baritone will handle Y coordinate automatically.
     * Thread-safe to prevent race conditions.
     * 
     * @param x Target X coordinate
     * @param z Target Z coordinate
     */
    public static void gotoLocation(int x, int z) {
        synchronized (commandLock) {
            // Check cooldown AND pathing state atomically
            if (isPathing) {
                PokeAlertClient.LOGGER.warn("BaritoneController: Already pathing, skipping duplicate goto");
                return;
            }
            
            if (!canSendCommand()) {
                PokeAlertClient.LOGGER.warn("BaritoneController: Command cooldown active, skipping goto");
                return;
            }
            
            if (client.player == null) {
                PokeAlertClient.LOGGER.error("BaritoneController: Cannot send goto - no player");
                return;
            }
            
            targetX = x;
            targetZ = z;
            isPathing = true;
            lastCommandTime = System.currentTimeMillis();
        }
        
        // Send the goto command (Baritone uses # prefix in CHAT, not as a command)
        String chatMessage = "#goto " + x + " " + z;
        client.execute(() -> {
            if (client.player != null && client.player.networkHandler != null) {
                client.player.networkHandler.sendChatMessage(chatMessage);
                PokeAlertClient.LOGGER.info("BaritoneController: Sent {}", chatMessage);
            }
        });
    }
    
    /**
     * Send #stop command to halt all Baritone pathfinding.
     * Thread-safe and resets state tracking.
     */
    public static void stop() {
        synchronized (commandLock) {
            if (client.player == null) {
                PokeAlertClient.LOGGER.debug("BaritoneController: Cannot send stop - no player");
                return;
            }
            
            isPathing = false;
            lastCommandTime = System.currentTimeMillis();
            // Reset allowSprint state when stopping
            currentAllowSprint = null;
        }
        
        client.execute(() -> {
            if (client.player != null && client.player.networkHandler != null) {
                client.player.networkHandler.sendChatMessage("#stop");
                PokeAlertClient.LOGGER.info("BaritoneController: Sent #stop");
            }
        });
    }
    
    /**
     * Set Baritone sprinting behavior
     * @param allowSprint true to allow sprinting, false to force walking
     */
    public static void setAllowSprint(boolean allowSprint) {
        if (client.player == null) {
            PokeAlertClient.LOGGER.debug("BaritoneController: Cannot set allowsprint - no player");
            return;
        }
        
        // Prevent redundant commands - only send if value is different from current state
        if (currentAllowSprint != null && currentAllowSprint == allowSprint) {
            PokeAlertClient.LOGGER.debug("BaritoneController: Skipping redundant allowsprint {} command", allowSprint);
            return;
        }
        
        // Update tracked state
        currentAllowSprint = allowSprint;
        
        client.execute(() -> {
            if (client.player != null && client.player.networkHandler != null) {
                String command = "#set allowsprint " + (allowSprint ? "true" : "false");
                client.player.networkHandler.sendChatMessage(command);
                PokeAlertClient.LOGGER.debug("BaritoneController: Sent {}", command);
            }
        });
    }
    
    /**
     * Check if Baritone is currently pathing (based on our tracking).
     * Note: This is our internal tracking, not actual Baritone state.
     * 
     * @return true if we believe Baritone is actively pathing
     */
    public static boolean isPathing() {
        return isPathing;
    }
    
    /**
     * Mark the current path as complete.
     * Called when CoordinateMonitor detects arrival at destination.
     * Also resets allowSprint state tracking.
     */
    public static void markPathComplete() {
        synchronized (commandLock) {
            isPathing = false;
            // Reset allowSprint state when pathing stops (Baritone state may have changed)
            currentAllowSprint = null;
        }
        PokeAlertClient.LOGGER.debug("BaritoneController: Path marked complete");
    }
    
    /**
     * Get the current target X coordinate.
     */
    public static int getTargetX() {
        return targetX;
    }
    
    /**
     * Get the current target Z coordinate.
     */
    public static int getTargetZ() {
        return targetZ;
    }
    
    /**
     * Check if enough time has passed since the last command.
     */
    private static boolean canSendCommand() {
        return (System.currentTimeMillis() - lastCommandTime) >= COMMAND_COOLDOWN_MS;
    }
    
    /**
     * Get status string for debugging.
     */
    public static String getStatus() {
        if (isPathing) {
            return String.format("Pathing to (%d, %d)", targetX, targetZ);
        }
        return "Idle";
    }
}
