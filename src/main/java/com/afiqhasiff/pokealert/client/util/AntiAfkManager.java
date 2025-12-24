package com.afiqhasiff.pokealert.client.util;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Anti-AFK management using continuous background state monitoring
 * 
 * Architecture:
 * - Background thread continuously monitors Anti-AFK state with dynamic intervals
 * - At spawn: checks every 200ms (aggressive monitoring)
 * - At overworld: checks every 30 seconds (reduced frequency)
 * - Separate world change monitor runs every 500ms to detect realm switches immediately
 * - When world change detected, monitoring interval is immediately rescheduled
 * - Global variable holds current state (always up-to-date during realm change)
 * - toggleAntiAfk() waits for state to be known (NEVER blind toggles)
 * - Uses GLFW key simulation to trigger Anti-AFK keybind
 * 
 * Movement Detection:
 * - Monitors player position (X, Y, Z) and state (sneaking, sprinting)
 * - Checks every 1 second for actual state determination
 * - Background thread polls at dynamic intervals based on location
 * 
 * World Change Detection:
 * - Dedicated monitor checks location every 500ms
 * - Immediately reschedules main monitor when spawn ↔ overworld transition detected
 * - Ensures interval adjustment happens within 500ms of world change (manual or automatic)
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
        
        // Perform toggle
        PokeAlertClient.LOGGER.info("🔧 Toggling Anti-AFK: " + (currentAntiAfkState ? "ON" : "OFF") + " → " + (enable ? "ON" : "OFF"));
        
        boolean keyPressSent = simulateAntiAfkKeyPress();
        if (!keyPressSent) {
            PokeAlertClient.LOGGER.error("❌ Failed to simulate key press");
            sendNotification("Anti-AFK", "Toggle failed", Formatting.RED);
            return false;
        }
        
        // Verification: Wait appropriately for movement detection to update
        // After world changes, we need to account for stabilization period + detection time
        long waitTime = 2500; // Default wait time
        long currentTime = System.currentTimeMillis();
        long toggleStartTime = currentTime;
        
        // Check if we're in stabilization period
        if (worldChangeTime > 0 && (currentTime - worldChangeTime) < TELEPORT_STABILIZATION_TIME) {
            // We're still in stabilization - wait until it ends + detection time
            // During stabilization, detectAntiAfkState() returns pre-teleport state, so we must wait
            long remainingStabilization = TELEPORT_STABILIZATION_TIME - (currentTime - worldChangeTime);
            waitTime = remainingStabilization + 4000; // Stabilization + 4 seconds for detection
            PokeAlertClient.LOGGER.info("⏳ In stabilization period - waiting " + waitTime + "ms for verification " +
                "(stabilization: " + remainingStabilization + "ms + detection: 4000ms)");
        } else if (worldChangeTime > 0 && (currentTime - worldChangeTime) < TELEPORT_STABILIZATION_TIME + 4000) {
            // Just finished stabilization - need extra time for detection
            long timeSinceStabilizationEnd = (currentTime - worldChangeTime) - TELEPORT_STABILIZATION_TIME;
            waitTime = Math.max(4000 - timeSinceStabilizationEnd, 2000); // Ensure at least 2s for detection
            PokeAlertClient.LOGGER.info("⏳ Just finished stabilization - waiting " + waitTime + "ms for verification");
        }
        
        // Wait for state to update (stabilization must end first, then detection needs time)
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check if tracking needs to be initialized
        // If it does, detectAntiAfkState() will initialize it, but then we need to wait for movement detection
        boolean trackingNeedsInit = (lastMovementCheck == 0);
        
        // Force an initial state check to initialize tracking if needed
        // This will return a fallback state if tracking was just initialized
        PokeAlertClient.LOGGER.info("🔍 Initial state check (to initialize tracking if needed)");
        Boolean tempState = detectAntiAfkState();
        long trackingInitTime = lastMovementCheck;
        
        // If tracking was just initialized, we need to wait for movement detection to work
        // Movement detection needs at least MOVEMENT_CHECK_INTERVAL (1000ms) after initialization
        if (trackingNeedsInit && trackingInitTime > 0) {
            // Tracking was just initialized, need to wait for movement detection
            long additionalWait = MOVEMENT_CHECK_INTERVAL + 1500; // 1s for detection + 1.5s buffer
            PokeAlertClient.LOGGER.info("⏳ Tracking just initialized at " + trackingInitTime + ", waiting additional " + additionalWait + "ms for movement detection");
            try {
                Thread.sleep(additionalWait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (lastMovementCheck > 0 && (System.currentTimeMillis() - lastMovementCheck) < MOVEMENT_CHECK_INTERVAL + 500) {
            // Tracking was recently initialized, need to wait for movement detection
            long timeSinceInit = System.currentTimeMillis() - lastMovementCheck;
            long additionalWait = MOVEMENT_CHECK_INTERVAL + 500 - timeSinceInit;
            if (additionalWait > 0) {
                PokeAlertClient.LOGGER.info("⏳ Tracking initialized " + timeSinceInit + "ms ago, waiting additional " + additionalWait + "ms for movement detection");
                try {
                    Thread.sleep(additionalWait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Force a final state check after waiting (helps when monitor interval is long)
        // This is safe now because stabilization should have ended and tracking has had time to detect
        PokeAlertClient.LOGGER.info("🔍 Final state check after " + (System.currentTimeMillis() - toggleStartTime) + "ms total wait");
        Boolean newState = detectAntiAfkState();
        boolean success = (newState != null && newState == enable);
        
        if (success) {
            // CRITICAL: Update currentAntiAfkState immediately so safety monitor sees correct state
            // Without this, safety monitor might check getAntiAfkState() and see stale state
            if (newState != null && !newState.equals(currentAntiAfkState)) {
                PokeAlertClient.LOGGER.info("📊 Updating currentAntiAfkState: " + 
                    (currentAntiAfkState == null ? "null" : (currentAntiAfkState ? "ON" : "OFF")) + 
                    " → " + (newState ? "ON" : "OFF"));
            }
            currentAntiAfkState = newState;
            PokeAlertClient.LOGGER.info("✅ Toggle verified successfully after " + (System.currentTimeMillis() - toggleStartTime) + "ms: State is now " + (enable ? "ON" : "OFF"));
        } else {
            // Even if verification failed, update state if we got a valid reading
            // This prevents safety monitor from seeing stale state
            if (newState != null) {
                currentAntiAfkState = newState;
            }
            PokeAlertClient.LOGGER.warn("⚠️ Toggle verification failed after " + (System.currentTimeMillis() - toggleStartTime) + "ms - expected " + 
                (enable ? "ON" : "OFF") + ", got " + (newState != null ? (newState ? "ON" : "OFF") : "unknown"));
            PokeAlertClient.LOGGER.warn("⚠️ This may be a transient issue - safety monitor will verify");
        }
        
        return success;
    }
    
    /**
     * Simulate key/button press using Minecraft's input system
     * Supports both keyboard keys and mouse buttons
     * Mouse buttons: 0-7 (0=left, 1=right, 2=middle, etc.)
     * Keyboard keys: 32+ (GLFW key codes)
     */
    private static boolean simulateAntiAfkKeyPress() {
        try {
            if (client.getWindow() == null) {
                PokeAlertClient.LOGGER.warn("Cannot simulate input - window is null");
                return false;
            }
            
            if (client.currentScreen != null) {
                PokeAlertClient.LOGGER.warn("Cannot simulate input - screen is open");
                return false;
            }
            
            // Get the input code and type from config
            // The config is synchronized with the KeyBinding via bidirectional sync
            int inputCode = ConfigManager.getConfig().antiAfkKeybind;
            InputUtil.Type inputType;
            
            // Detect mouse button (0-7) vs keyboard key
            if (inputCode >= 0 && inputCode <= 7) {
                inputType = InputUtil.Type.MOUSE;
            } else {
                inputType = InputUtil.Type.KEYSYM;
            }
            long windowHandle = client.getWindow().getHandle();
            
            // Detect if this is a mouse button or keyboard key based on InputUtil.Type
            boolean isMouseButton = (inputType == InputUtil.Type.MOUSE);
            
            if (isMouseButton) {
                PokeAlertClient.LOGGER.info("Simulating mouse button press: " + inputCode + " (" + getMouseButtonName(inputCode) + ")");
                
                try {
                    // Get GLFW's current mouse button callback and invoke it directly
                    // This bypasses Minecraft's private Mouse class and works reliably
                    org.lwjgl.glfw.GLFWMouseButtonCallback callback = GLFW.glfwSetMouseButtonCallback(windowHandle, null);
                    
                    if (callback == null) {
                        PokeAlertClient.LOGGER.error("No mouse button callback registered - cannot simulate");
                        return false;
                    }
                    
                    // Restore the callback immediately
                    GLFW.glfwSetMouseButtonCallback(windowHandle, callback);
                    
                    // Execute on main client thread to ensure proper handling
                    client.execute(() -> {
                        try {
                            // Simulate mouse button press by invoking the callback directly
                            callback.invoke(windowHandle, inputCode, GLFW.GLFW_PRESS, 0);
                            
                            PokeAlertClient.LOGGER.info("✓ Mouse button press sent: " + inputCode);
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.error("Error during mouse button press", e);
                        }
                    });
                    
                    // Wait for button press to be processed
                    Thread.sleep(50);
                    
                    // Simulate mouse button release
                    client.execute(() -> {
                        try {
                            callback.invoke(windowHandle, inputCode, GLFW.GLFW_RELEASE, 0);
                            
                            PokeAlertClient.LOGGER.info("✓ Mouse button release sent: " + inputCode);
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.error("Error during mouse button release", e);
                        }
                    });
                    
                    PokeAlertClient.LOGGER.info("✅ Mouse button simulation completed for button: " + inputCode);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("Failed to simulate mouse button", e);
                    return false;
                }
            } else {
                PokeAlertClient.LOGGER.info("Simulating keyboard key press: " + inputCode);
                
                // Execute on main client thread to ensure proper handling
                client.execute(() -> {
                    try {
                        // Simulate key press (action = 1 for PRESS)
                        client.keyboard.onKey(windowHandle, inputCode, 0, GLFW.GLFW_PRESS, 0);
                        
                        PokeAlertClient.LOGGER.info("✓ Key press sent: " + inputCode);
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.error("Error during key press", e);
                    }
                });
                
                // Wait for key press to be processed
                Thread.sleep(50);
                
                // Simulate key release (action = 0 for RELEASE)
                client.execute(() -> {
                    try {
                        client.keyboard.onKey(windowHandle, inputCode, 0, GLFW.GLFW_RELEASE, 0);
                        
                        PokeAlertClient.LOGGER.info("✓ Key release sent: " + inputCode);
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.error("Error during key release", e);
                    }
                });
                
                PokeAlertClient.LOGGER.info("✅ Keyboard key simulation completed for keyCode: " + inputCode);
            }
            
            return true;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("Failed to simulate input", e);
            return false;
        }
    }
    
    /**
     * Get human-readable name for mouse button code
     */
    private static String getMouseButtonName(int button) {
        switch (button) {
            case 0: return "Left Click";
            case 1: return "Right Click";
            case 2: return "Middle Click";
            case 3: return "Mouse Button 4";
            case 4: return "Mouse Button 5";
            case 5: return "Mouse Button 6";
            case 6: return "Mouse Button 7";
            case 7: return "Mouse Button 8";
            default: return "Unknown Mouse Button";
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
