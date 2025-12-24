package com.afiqhasiff.pokealert.client.util;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Anti-AFK management utility class.
 * 
 * v3.0.0 DEPRECATION NOTICE:
 * -------------------------
 * The following methods are DEPRECATED and should NOT be used in v3.0.0:
 * - toggleAntiAfk() - External keybind simulation removed
 * - simulateAntiAfkKeyPress() - No longer needed
 * 
 * The following methods are STILL USED in v3.0.0:
 * - getPlayerLocationInfo() - Used for logging
 * - resetTracking() - Used on disconnect
 * - startStateMonitoring() / stopStateMonitoring() - Legacy, kept for compatibility
 * 
 * v3.0.0 uses internal Baritone-based Anti-AFK via:
 * - BaritoneController (sends #goto commands)
 * - CoordinateMonitor (tracks position/teleport)
 * - PlayerMonitor (detects avoided players)
 * - SafetyManager (centralized safety stop)
 * 
 * Legacy v2.0.0 Architecture (for reference):
 * - Background thread continuously monitors Anti-AFK state with dynamic intervals
 * - At spawn: checks every 200ms (aggressive monitoring)
 * - At overworld: checks every 30 seconds (reduced frequency)
 * - Uses GLFW key simulation to trigger Anti-AFK keybind
 */
public class AntiAfkManager {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    
    // Background state monitoring
    private static volatile Boolean currentAntiAfkState = null;
    private static ScheduledExecutorService stateMonitor;
    private static ScheduledFuture<?> monitorTask;
    private static ScheduledFuture<?> worldChangeMonitorTask; // Separate task for world change detection
    
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
    
    // Dynamic check intervals based on location
    private static final long CHECK_INTERVAL_SPAWN = 200; // 200ms at spawn (aggressive)
    private static final long CHECK_INTERVAL_OVERWORLD = 30000; // 30 seconds at overworld
    private static final long WORLD_CHANGE_CHECK_INTERVAL = 500; // Check for world changes every 500ms
    private static final long POST_WORLD_CHANGE_INTERVAL = 1000; // 1 second after world change to quickly establish state
    private static volatile long worldChangeDetectedTime = 0; // Track when world change was detected
    private static final long POST_WORLD_CHANGE_DURATION = 5000; // Use shorter interval for 5 seconds after world change
    
    /**
     * Check if state monitoring is currently running
     */
    public static boolean isStateMonitoringActive() {
        return monitorTask != null && !monitorTask.isDone();
    }
    
    /**
     * Check if player is currently at spawn world
     * @return true if at spawn, false if at overworld or unknown
     */
    private static boolean isAtSpawn() {
        if (client == null || client.world == null) {
            return false;
        }
        
        String worldName = client.world.getRegistryKey().getValue().toString();
        return ConfigManager.getConfig().isWorldExcluded(worldName);
    }
    
    /**
     * Get the appropriate check interval based on current location
     * Uses shorter interval immediately after world change to quickly establish state
     * @return interval in milliseconds
     */
    private static long getCheckInterval() {
        // If we recently detected a world change, use shorter interval to quickly establish state
        long currentTime = System.currentTimeMillis();
        if (worldChangeDetectedTime > 0 && (currentTime - worldChangeDetectedTime) < POST_WORLD_CHANGE_DURATION) {
            return POST_WORLD_CHANGE_INTERVAL;
        }
        
        // Normal intervals based on location
        return isAtSpawn() ? CHECK_INTERVAL_SPAWN : CHECK_INTERVAL_OVERWORLD;
    }
    
    /**
     * Schedule the next monitoring check with dynamic interval based on location
     */
    private static void scheduleNextCheck() {
        if (stateMonitor == null || stateMonitor.isShutdown()) {
            return;
        }
        
        // Cancel existing task if running
        if (monitorTask != null && !monitorTask.isDone()) {
            monitorTask.cancel(false);
        }
        
        long interval = getCheckInterval();
        String location = isAtSpawn() ? "spawn" : "overworld";
        
        monitorTask = stateMonitor.schedule(() -> {
            try {
                Boolean newState = detectAntiAfkState();
                if (newState != null && !newState.equals(currentAntiAfkState)) {
                    PokeAlertClient.LOGGER.info("📊 State monitor: Anti-AFK " + 
                        (currentAntiAfkState == null ? "initialized" : "changed") + " → " + 
                        (newState ? "ON" : "OFF"));
                }
                currentAntiAfkState = newState;
                
                // Schedule next check with dynamic interval
                scheduleNextCheck();
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("Error in state monitor", e);
                // Schedule next check even on error to keep monitoring alive
                scheduleNextCheck();
            }
        }, interval, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Start world change monitoring to detect realm switches and immediately reschedule intervals
     * This runs more frequently than the main monitor to catch world changes quickly
     */
    private static void startWorldChangeMonitoring() {
        if (stateMonitor == null || stateMonitor.isShutdown()) {
            return;
        }
        
        // Cancel existing world change monitor if running
        if (worldChangeMonitorTask != null && !worldChangeMonitorTask.isDone()) {
            worldChangeMonitorTask.cancel(false);
        }
        
        // Track last known location for comparison
        final boolean[] lastKnownAtSpawn = {isAtSpawn()};
        
        worldChangeMonitorTask = stateMonitor.scheduleAtFixedRate(() -> {
            try {
                if (client == null || client.world == null) {
                    return;
                }
                
                boolean currentlyAtSpawn = isAtSpawn();
                
                // Detect location change
                if (currentlyAtSpawn != lastKnownAtSpawn[0]) {
                    String oldLocation = lastKnownAtSpawn[0] ? "spawn" : "overworld";
                    String newLocation = currentlyAtSpawn ? "spawn" : "overworld";
                    
                    PokeAlertClient.LOGGER.info("🌍 Location change detected: " + oldLocation + " → " + newLocation + 
                        " - Using temporary 1s interval for 5s to quickly establish state");
                    
                    // Mark world change time to trigger temporary shorter interval
                    worldChangeDetectedTime = System.currentTimeMillis();
                    
                    // CRITICAL: Immediately run a state check to update currentAntiAfkState
                    // This ensures state is available when automation steps need it
                    Boolean immediateState = detectAntiAfkState();
                    if (immediateState != null && !immediateState.equals(currentAntiAfkState)) {
                        PokeAlertClient.LOGGER.info("📊 State monitor: Anti-AFK " + 
                            (currentAntiAfkState == null ? "initialized" : "changed") + " → " + 
                            (immediateState ? "ON" : "OFF") + " (immediate check after world change)");
                    }
                    currentAntiAfkState = immediateState;
                    
                    // Immediately reschedule the main monitor with temporary shorter interval
                    scheduleNextCheck();
                    
                    // Update tracked location
                    lastKnownAtSpawn[0] = currentlyAtSpawn;
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("Error in world change monitor", e);
            }
        }, 0, WORLD_CHANGE_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Stop world change monitoring
     */
    private static void stopWorldChangeMonitoring() {
        if (worldChangeMonitorTask != null && !worldChangeMonitorTask.isDone()) {
            worldChangeMonitorTask.cancel(false);
            worldChangeMonitorTask = null;
        }
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
        
        // Start monitoring with dynamic intervals
        long initialInterval = getCheckInterval();
        String initialLocation = isAtSpawn() ? "spawn" : "overworld";
        PokeAlertClient.LOGGER.info("✅ Anti-AFK state monitoring started (dynamic intervals: " + 
            initialInterval + "ms at " + initialLocation + ")");
        
        // Start both the main monitor and world change detector
        scheduleNextCheck();
        startWorldChangeMonitoring();
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
        stopWorldChangeMonitoring();
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
     * Force immediate state detection and update currentAntiAfkState
     * This is useful when state is unknown and we need to initialize it quickly
     * @return the detected state, or null if detection failed
     */
    public static Boolean forceStateDetection() {
        Boolean detectedState = detectAntiAfkState();
        if (detectedState != null && !detectedState.equals(currentAntiAfkState)) {
            PokeAlertClient.LOGGER.info("📊 Forced state detection: Anti-AFK " + 
                (currentAntiAfkState == null ? "initialized" : "changed") + " → " + 
                (detectedState ? "ON" : "OFF"));
        }
        currentAntiAfkState = detectedState;
        return detectedState;
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
            
            // Preserve saved state if it was already set (from saveStateForTeleport)
            // Otherwise, save current state before resetting
            if (stateBeforeTeleport == null) {
                stateBeforeTeleport = currentAntiAfkState;
            }
            worldChangeTime = currentTime;
            
            // Reset tracking for new world (but preserve stateBeforeTeleport and worldChangeTime)
            long savedWorldChangeTime = worldChangeTime;
            Boolean savedStateBeforeTeleport = stateBeforeTeleport;
            resetTracking();
            // Restore preserved values
            worldChangeTime = savedWorldChangeTime;
            stateBeforeTeleport = savedStateBeforeTeleport;
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
        Boolean postStabilizationState = null;
        if (worldChangeTime > 0 && (currentTime - worldChangeTime) >= TELEPORT_STABILIZATION_TIME) {
            PokeAlertClient.LOGGER.info("✅ Teleport stabilization complete, resuming normal detection");
            // Save the state we were returning during stabilization as a fallback
            postStabilizationState = stateBeforeTeleport;
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
            // If we just finished stabilization, return the post-stabilization state as fallback
            // If no saved state but we're in overworld, assume OFF (safe default for Step 5)
            // Otherwise return null (need at least 2 data points)
            if (postStabilizationState != null) {
                return postStabilizationState;
            } else if (!isAtSpawn()) {
                // In overworld without saved state, assume OFF (safe default)
                PokeAlertClient.LOGGER.info("📊 No saved state after stabilization, assuming OFF in overworld");
                return false;
            }
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
        worldChangeDetectedTime = 0;  // Reset world change detection time
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
        
        // v3.0.0: This method is DEPRECATED
        // The external keybind toggle has been replaced with internal Baritone-based Anti-AFK
        // This method now always returns false (no-op)
        PokeAlertClient.LOGGER.warn("⚠️ toggleAntiAfk() is DEPRECATED in v3.0.0 - use Baritone Anti-AFK instead");
        sendNotification("Anti-AFK", "External toggle deprecated in v3.0.0", Formatting.YELLOW);
        return false;
    }
    
    // v3.0.0: simulateAntiAfkKeyPress() REMOVED - no longer using external keybind simulation
    // v3.0.0: getMouseButtonName() REMOVED - no longer needed
    
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
