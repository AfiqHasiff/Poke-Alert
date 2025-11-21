package com.afiqhasiff.pokealert.client.util;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Anti-AFK management using continuous background state monitoring
 * 
 * Architecture:
 * - Background thread continuously monitors Anti-AFK state every 200ms
 * - Global variable holds current state (always up-to-date during realm change)
 * - toggleAntiAfk() waits for state to be known (NEVER blind toggles)
 * - Uses GLFW key simulation to trigger Anti-AFK keybind
 * 
 * Movement Detection:
 * - Monitors player position (X, Y, Z) and state (sneaking, sprinting)
 * - Checks every 1 second for actual state determination
 * - Background thread polls every 200ms to keep state fresh
 */
public class AntiAfkManager {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    
    // Background state monitoring
    private static volatile Boolean currentAntiAfkState = null;
    private static ScheduledExecutorService stateMonitor;
    private static ScheduledFuture<?> monitorTask;
    
    // Movement tracking for Anti-AFK detection
    private static double lastX = 0;
    private static double lastY = 0;
    private static double lastZ = 0;
    private static boolean lastSneaking = false;
    private static boolean lastSprinting = false;
    private static long lastMovementCheck = 0;
    private static String lastWorld = null; // Track world/realm changes
    private static long worldChangeTime = 0; // Track when last world change occurred
    private static Boolean stateBeforeTeleport = null; // Remember state before teleport
    private static final long MOVEMENT_CHECK_INTERVAL = 1000; // Check every 1 second for accurate detection
    private static final double MOVEMENT_THRESHOLD = 0.01; // Minimum movement to consider active
    private static final long TELEPORT_STABILIZATION_TIME = 3000; // Wait 3s after world change for position to stabilize
    
    /**
     * Check if state monitoring is currently running
     */
    public static boolean isStateMonitoringActive() {
        return monitorTask != null && !monitorTask.isDone();
    }
    
    /**
     * Start continuous Anti-AFK state monitoring
     * Call this when realm change process starts
     */
    public static void startStateMonitoring() {
        // Don't restart if already running (prevents infinite loop with warm-up period)
        if (isStateMonitoringActive()) {
            PokeAlertClient.LOGGER.debug("State monitoring already active, skipping restart");
            return;
        }
        
        if (stateMonitor == null) {
            stateMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "AntiAFK-State-Monitor");
                t.setDaemon(true);
                return t;
            });
        }
        
        // Stop existing monitor if running (shouldn't happen, but just in case)
        stopStateMonitoring();
        
        // Reset tracking for fresh start
        resetTracking();
        
        // Start continuous monitoring
        monitorTask = stateMonitor.scheduleAtFixedRate(() -> {
            try {
                Boolean newState = detectAntiAfkState();
                if (newState != null && !newState.equals(currentAntiAfkState)) {
                    PokeAlertClient.LOGGER.info("📊 State monitor: Anti-AFK " + 
                        (currentAntiAfkState == null ? "initialized" : "changed") + " → " + 
                        (newState ? "ON" : "OFF"));
                }
                currentAntiAfkState = newState;
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("Error in state monitor", e);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);  // Check every 200ms (aggressive!)
        
        PokeAlertClient.LOGGER.info("✅ Anti-AFK state monitoring started (200ms interval)");
    }
    
    /**
     * Stop state monitoring
     * Call this when realm change completes or is cancelled
     */
    public static void stopStateMonitoring() {
        if (monitorTask != null && !monitorTask.isCancelled()) {
            monitorTask.cancel(false);
            PokeAlertClient.LOGGER.info("🛑 Anti-AFK state monitoring stopped");
        }
        currentAntiAfkState = null;  // Reset state
    }
    
    /**
     * Get current Anti-AFK state (instant read from global variable)
     * This is continuously updated by the background monitor
     */
    public static Boolean getAntiAfkState() {
        return currentAntiAfkState;
    }
    
    /**
     * Detect Anti-AFK state via movement detection
     * This is called by the background monitor
     * 
     * @return true if Anti-AFK is ON (movement detected), false if OFF, null if can't determine yet
     */
    private static Boolean detectAntiAfkState() {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }
        
        ClientPlayerEntity player = client.player;
        long currentTime = System.currentTimeMillis();
        String currentWorld = client.world.getRegistryKey().getValue().toString();
        
        // Detect world changes (teleportation between dimensions)
        if (lastWorld != null && !lastWorld.equals(currentWorld)) {
            PokeAlertClient.LOGGER.info("🌍 World change detected (" + lastWorld + " → " + currentWorld + "), entering stabilization period");
            
            // Save current state before resetting
            stateBeforeTeleport = currentAntiAfkState;
            worldChangeTime = currentTime;
            
            // Reset tracking for new world
            resetTracking();
            lastWorld = currentWorld;
            
            // During stabilization, return the pre-teleport state
            PokeAlertClient.LOGGER.info("📊 Teleport stabilization: Returning pre-teleport state (" + 
                                       (stateBeforeTeleport != null ? (stateBeforeTeleport ? "ON" : "OFF") : "unknown") + 
                                       ") for " + TELEPORT_STABILIZATION_TIME + "ms");
            return stateBeforeTeleport;
        }
        
        // Check if we're still in stabilization period after world change
        if (worldChangeTime > 0 && (currentTime - worldChangeTime) < TELEPORT_STABILIZATION_TIME) {
            long remaining = TELEPORT_STABILIZATION_TIME - (currentTime - worldChangeTime);
            PokeAlertClient.LOGGER.debug("⏳ Teleport stabilization period active (" + remaining + "ms remaining), using pre-teleport state");
            return stateBeforeTeleport; // Continue returning pre-teleport state
        }
        
        // Stabilization period over, clear saved state
        if (worldChangeTime > 0 && (currentTime - worldChangeTime) >= TELEPORT_STABILIZATION_TIME) {
            PokeAlertClient.LOGGER.info("✅ Teleport stabilization complete, resuming normal detection");
            worldChangeTime = 0;
            stateBeforeTeleport = null;
        }
        
        // Initialize on first check
        if (lastMovementCheck == 0) {
            lastX = player.getX();
            lastY = player.getY();
            lastZ = player.getZ();
            lastSneaking = player.isSneaking();
            lastSprinting = player.isSprinting();
            lastMovementCheck = currentTime;
            lastWorld = currentWorld;
            PokeAlertClient.LOGGER.info("📊 Movement tracking initialized");
            return null;  // Need at least 2 data points
        }
        
        // Only check every 1 second minimum for accurate detection
        if (currentTime - lastMovementCheck < MOVEMENT_CHECK_INTERVAL) {
            return currentAntiAfkState;  // Return last known state
        }
        
        // Calculate movement
        double deltaX = Math.abs(player.getX() - lastX);
        double deltaY = Math.abs(player.getY() - lastY);
        double deltaZ = Math.abs(player.getZ() - lastZ);
        
        boolean hasMovement = (deltaX > MOVEMENT_THRESHOLD || deltaY > MOVEMENT_THRESHOLD || deltaZ > MOVEMENT_THRESHOLD);
        boolean hasStateChange = (player.isSneaking() != lastSneaking) || (player.isSprinting() != lastSprinting);
        
        // Log movement detection (verbose for debugging)
        if (hasMovement || hasStateChange) {
            PokeAlertClient.LOGGER.debug("Movement detected - ΔX:" + String.format("%.3f", deltaX) + 
                ", ΔY:" + String.format("%.3f", deltaY) + ", ΔZ:" + String.format("%.3f", deltaZ) +
                ", sneak:" + player.isSneaking() + ", sprint:" + player.isSprinting());
        }
        
        // Update tracking
        lastX = player.getX();
        lastY = player.getY();
        lastZ = player.getZ();
        lastSneaking = player.isSneaking();
        lastSprinting = player.isSprinting();
        lastMovementCheck = currentTime;
        
        // Anti-AFK is ON if movement or state changes detected
        return hasMovement || hasStateChange;
    }
    
    /**
     * Reset movement tracking - call on disconnect/reconnect to prevent false positives
     * This ensures position deltas from before disconnect don't affect after reconnect
     */
    public static void resetTracking() {
        lastMovementCheck = 0;
        lastWorld = null;
        lastX = 0;
        lastY = 0;
        lastZ = 0;
        lastSneaking = false;
        lastSprinting = false;
        currentAntiAfkState = null;  // Also reset global state
        worldChangeTime = 0;  // Reset teleport tracking
        stateBeforeTeleport = null;  // Clear saved state
        PokeAlertClient.LOGGER.info("AntiAfkManager: Movement tracking reset");
    }
    
    /**
     * Get current player position and world for logging
     * @return String with format "world @ (x, y, z)" or "unknown" if unavailable
     */
    public static String getPlayerLocationInfo() {
        if (client == null || client.player == null || client.world == null) {
            return "unknown";
        }
        
        String world = client.world.getRegistryKey().getValue().toString();
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        
        return String.format("%s @ (%.1f, %.1f, %.1f)", world, x, y, z);
    }
    
    /**
     * Save current Anti-AFK state for teleport stabilization
     * Should be called BEFORE initiating a teleport
     */
    public static void saveStateForTeleport() {
        stateBeforeTeleport = currentAntiAfkState;
        PokeAlertClient.LOGGER.info("💾 Saved Anti-AFK state for teleport: " + 
            (stateBeforeTeleport != null ? (stateBeforeTeleport ? "ON" : "OFF") : "unknown"));
    }
    
    /**
     * Toggle Anti-AFK - Tries to get state with 3 retries (900ms max), NEVER blind toggles
     * 
     * This method will try 3 times with 300ms between attempts to get the current state.
     * If state cannot be determined after 900ms, it ABORTS the toggle for safety.
     *
     * @param enable true to enable Anti-AFK, false to disable
     * @return true if toggle succeeded or was already in desired state, false if failed/aborted
     */
    public static boolean toggleAntiAfk(boolean enable) {
        PokeAlertClient.LOGGER.info("🔄 toggleAntiAfk called: target=" + (enable ? "ENABLE" : "DISABLE"));
        
        // Try to get state with 3 retries (300ms each = 900ms max)
        int maxRetries = 3;
        int attempt = 0;
        
        while (currentAntiAfkState == null && attempt < maxRetries) {
            attempt++;
            PokeAlertClient.LOGGER.info("⏳ Waiting for Anti-AFK state... (attempt " + attempt + "/" + maxRetries + ")");
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                PokeAlertClient.LOGGER.error("❌ Interrupted while waiting for state");
                Thread.currentThread().interrupt();
                return false;  // Failed due to interruption
            }
        }
        
        // Check if we got the state
        if (currentAntiAfkState == null) {
            PokeAlertClient.LOGGER.error("❌ Anti-AFK state unknown after " + (maxRetries * 300) + "ms");
            PokeAlertClient.LOGGER.error("❌ ABORTING toggle - safety monitor will handle");
            sendNotification("Anti-AFK", "State detection failed - toggle aborted", Formatting.RED);
            return false;  // ABORT - DO NOT TOGGLE
        }
        
        // State is known - verify if toggle is needed
        PokeAlertClient.LOGGER.info("✅ Current Anti-AFK state: " + (currentAntiAfkState ? "ON" : "OFF") + 
                   ", target state: " + (enable ? "ON" : "OFF"));
        
        if ((enable && currentAntiAfkState) || (!enable && !currentAntiAfkState)) {
            PokeAlertClient.LOGGER.info("✓ Anti-AFK already in desired state, skipping toggle");
            sendNotification("Anti-AFK", "Already " + (enable ? "enabled" : "disabled"), Formatting.GRAY);
            return true;  // Already in desired state = success
        }
        
        // Perform toggle
        PokeAlertClient.LOGGER.info("🔧 Toggling Anti-AFK: " + (currentAntiAfkState ? "ON" : "OFF") + " → " + (enable ? "ON" : "OFF"));
        
        boolean keyPressSent = simulateAntiAfkKeyPress();
        if (!keyPressSent) {
            PokeAlertClient.LOGGER.error("❌ Failed to simulate key press");
            sendNotification("Anti-AFK", "Toggle failed", Formatting.RED);
            return false;
        }
        
        // Verification: Wait 2500ms for movement detection to update
        // Movement detection needs 2 data points that are 1 second apart (MOVEMENT_CHECK_INTERVAL = 1000ms)
        // Timeline: Key press → Player starts moving (0ms) → First data point (~1000ms) → Second data point (~2000ms)
        // This is especially important after world changes (teleports) when player position is resetting
        // 2500ms = 2 full data points (2000ms) + 500ms buffer for state monitor to process
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Boolean newState = currentAntiAfkState;
        boolean success = (newState != null && newState == enable);
        
        if (success) {
            PokeAlertClient.LOGGER.info("✅ Toggle verified successfully: State is now " + (enable ? "ON" : "OFF"));
        } else {
            PokeAlertClient.LOGGER.warn("⚠️ Toggle verification failed after 2500ms - expected " + 
                (enable ? "ON" : "OFF") + ", got " + (newState != null ? (newState ? "ON" : "OFF") : "unknown"));
            PokeAlertClient.LOGGER.warn("⚠️ This may be a transient issue - safety monitor will verify");
        }
        
        return success;
    }
    
    /**
     * Simulate key press using Minecraft's input system
     * This directly calls Minecraft's key handler
     */
    private static boolean simulateAntiAfkKeyPress() {
        try {
            if (client.getWindow() == null) {
                PokeAlertClient.LOGGER.warn("Cannot simulate key - window is null");
                return false;
            }
            
            if (client.currentScreen != null) {
                PokeAlertClient.LOGGER.warn("Cannot simulate key - screen is open");
                return false;
            }
            
            int keyCode = ConfigManager.getConfig().antiAfkKeybind;
            long windowHandle = client.getWindow().getHandle();
            
            PokeAlertClient.LOGGER.info("Simulating key press with key code: " + keyCode);
            
            // Execute on main client thread to ensure proper handling
            client.execute(() -> {
                try {
                    // Simulate key press (action = 1 for PRESS)
                    client.keyboard.onKey(windowHandle, keyCode, 0, GLFW.GLFW_PRESS, 0);
                    
                    PokeAlertClient.LOGGER.info("✓ Key press sent: " + keyCode);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("Error during key press", e);
                }
            });
            
            // Wait for key press to be processed
            Thread.sleep(50);
            
            // Simulate key release (action = 0 for RELEASE)
            client.execute(() -> {
                try {
                    client.keyboard.onKey(windowHandle, keyCode, 0, GLFW.GLFW_RELEASE, 0);
                    
                    PokeAlertClient.LOGGER.info("✓ Key release sent: " + keyCode);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("Error during key release", e);
                }
            });
            
            PokeAlertClient.LOGGER.info("✅ Key simulation completed for keyCode: " + keyCode);
            return true;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to simulate key press", e);
            return false;
        }
    }
    
    /**
     * Send notification to player
     * Matches standard notification format used throughout PokeAlert
     */
    private static void sendNotification(String title, String message, Formatting color) {
        if (client.player != null) {
            Text notificationText = Text.literal("[")
                .formatted(Formatting.GRAY)
                .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                .append(Text.literal("] ").formatted(Formatting.GRAY))
                .append(Text.literal(title + ": ").formatted(Formatting.WHITE))
                .append(Text.literal(message).formatted(color));
            
            client.player.sendMessage(notificationText, false);
        }
    }
    
    /**
     * Shutdown the state monitor gracefully
     * Call this when the mod is unloading
     */
    public static void shutdown() {
        stopStateMonitoring();
        if (stateMonitor != null && !stateMonitor.isShutdown()) {
            stateMonitor.shutdown();
            try {
                if (!stateMonitor.awaitTermination(2, TimeUnit.SECONDS)) {
                    stateMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                stateMonitor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
