package com.afiqhasiff.pokealert.client.automation;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.notification.TelegramNotification;
import com.afiqhasiff.pokealert.client.util.AntiAfkManager;
import com.afiqhasiff.pokealert.client.util.AntiAfkRegion;
import com.afiqhasiff.pokealert.client.util.BaritoneController;
import com.afiqhasiff.pokealert.client.util.CoordinateMonitor;
import com.afiqhasiff.pokealert.client.util.LocationQueue;
import com.afiqhasiff.pokealert.client.util.PlayerMonitor;
import com.afiqhasiff.pokealert.client.util.SafetyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Automation manager for handling realm transitions after disconnects.
 * v3.0.0: Now uses internal Baritone-based Anti-AFK instead of external keybind.
 * 
 * Flow (5 Steps):
 * Step 1: Spawn Detection
 * Step 2: Server Buffer (30s wait)
 * Step 3: Realm Change (/home)
 * Step 4: Anti-AFK Start (Baritone movement - perpetual)
 * Step 5: Completion (after 3 successful locations - Anti-AFK continues)
 */
public class EggHatcher {
    private static EggHatcher instance;
    private final MinecraftClient client;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentTask;
    private ScheduledFuture<?> stuckDetector;
    private ScheduledFuture<?> automationDelayTask;
    private ScheduledFuture<?> safetyMonitorTask;
    private ScheduledFuture<?> playerSuspicionMonitorTask;
    
    // Track all scheduled automation tasks for proper cancellation
    private ScheduledFuture<?> step1Task;
    private ScheduledFuture<?> step2Task;
    private ScheduledFuture<?> step3Task;
    private ScheduledFuture<?> step4Task;
    private ScheduledFuture<?> step5Task;
    
    private long automationStartTime;
    private boolean isAutomationRunning = false;
    private State currentState = State.IDLE;
    private long lastConnectionTime = 0;
    private long spawnDetectionTime = 0;
    // Removed wasDisconnected - no longer needed with simplified detection
    private boolean antiAfkDisabledOnReconnect = false;
    private boolean manuallyCancelled = false; // Track manual cancellations
    private AutomationMode mode = AutomationMode.AUTO;
    
    // Teleport tracking to prevent command spam
    private long lastTeleportCommandTime = 0;
    private static final long TELEPORT_COOLDOWN = 10000; // 10 seconds between /home new commands
    
    // Resource pack loading tracking (high CPU usage can cause false positives)
    private boolean isResourcePackLoading = false;
    private boolean isConnected = false;
    
    // Step 5 retry tracking for logging purposes
    private int step5RetryCount = 0;
    
    // v3.0.0: Anti-AFK components
    private final LocationQueue locationQueue = new LocationQueue();
    private AntiAfkRegion antiAfkRegion;
    private boolean antiAfkActive = false;
    private ScheduledFuture<?> locationTimeoutTask;
    
    // Movement progress monitoring (for stuck detection)
    private int[] lastProgressCheckPosition = null;
    private long lastProgressCheckTime = 0;
    private ScheduledFuture<?> progressCheckTask = null;
    private static final long PROGRESS_CHECK_INTERVAL = 15000; // 15 seconds
    private static final double MIN_PROGRESS_THRESHOLD = 1.0; // Must move at least 1 block toward destination
    
    // Human-like behavior state
    private volatile boolean isInBreakState = false; // Flag to prevent false anti-AFK detection during breaks
    private volatile boolean isCameraRotating = false; // Flag to prevent false anti-AFK detection during camera rotation
    private volatile boolean isJumpKeyHeld = false; // Track if jump key is currently held
    private ScheduledFuture<?> breakStateTask;
    private ScheduledFuture<?> jumpTask;
    private ScheduledFuture<?> jumpRepeatTask; // For periodic jump execution while moving
    private ScheduledFuture<?> cameraRotationTask;
    private final java.util.Random behaviorRandom = new java.util.Random();
    
    // Store selected action for current navigation to prevent re-rolling on timeout
    private String currentNavigationAction = null;
    
    // Track recent movement positions for jump detection (movement-based approach)
    private static class PositionSnapshot {
        final double x, z;
        final long timestamp;
        PositionSnapshot(double x, double z, long timestamp) {
            this.x = x;
            this.z = z;
            this.timestamp = timestamp;
        }
    }
    private final java.util.Queue<PositionSnapshot> recentPositions = new java.util.ArrayDeque<>();
    
    // Atomic flag to prevent arrival callback race condition
    private final java.util.concurrent.atomic.AtomicBoolean arrivalProcessed = new java.util.concurrent.atomic.AtomicBoolean(false);
    
    // Track if automation started from spawn (for Telegram notification)
    private boolean startedFromSpawn = false;
    
    // Journey tracking for Telegram notification
    private boolean hadDisconnect = false;  // Disconnect -> Reconnect during automation
    private boolean hadOverworldCrash = false;  // Started in overworld, forced to spawn, then back to overworld
    private boolean startedInOverworld = false;  // Track if automation started in overworld

    // Track if we've already sent the "taking a break" notification
    private boolean breakNotificationSent = false;
    
    // State machine for tracking automation progress
    private enum State {
        IDLE,
        DETECTED_AT_SPAWN,
        WAITING_CONFIRMATION,
        DISABLING_ANTIAFK,
        SENDING_HOME_COMMAND,
        WAITING_FOR_TELEPORT,
        ENABLING_ANTIAFK,
        COMPLETED,
        STUCK,
        CANCELLED
    }
    
    // Automation modes (simplified to Auto/Disabled only)
    public enum AutomationMode {
        AUTO,       // Automatically triggers at spawn with 3s grace period
        DISABLED    // Completely disabled
    }
    
    // Timing constants
    private static final long ANTIAFK_TOGGLE_DELAY = 100; // 100ms delay after toggling anti-afk (key press buffer)
    private static final long HOME_COMMAND_DELAY = 100; // 100ms delay before sending /home (command formatting buffer)
    private static final long TELEPORT_WAIT_TIME = 17000; // 17s wait: 5s server delay + 3s world load + 3s stabilization + 6s fresh data
    private static final long STUCK_TIMEOUT = 120000; // 2 minutes stuck detection (will force close game)
    private static final long REALM_SWITCH_BUFFER = 30000; // 30 seconds server buffer for realm switching
    private static final long AUTO_MODE_DELAY = 30000; // 30 seconds delay for normal spawn visits
    private static final long CANCEL_WINDOW = 5000; // 5 seconds to cancel automation
    
    private EggHatcher() {
        this.client = MinecraftClient.getInstance();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    public static EggHatcher getInstance() {
        if (instance == null) {
            instance = new EggHatcher();
        }
        return instance;
    }
    
    /**
     * Check if player is at spawn or in overworld
     */
    public boolean isInTargetRealm() {
        if (client.player == null || client.world == null) {
            return false;
        }
        
        String worldName = client.world.getRegistryKey().getValue().toString();
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        // Check if we're in spawn world
        if (config.isWorldExcluded(worldName)) {
            return true; // At spawn
        }
        
        // Check if we're in overworld
        if (worldName.contains("overworld") || worldName.equals("minecraft:overworld")) {
            return true; // In main realm
        }
        
        return false;
    }
    
    /**
     * Check if specifically at spawn
     */
    public boolean isAtSpawn() {
        if (client.player == null || client.world == null) {
            return false;
        }
        
        String worldName = client.world.getRegistryKey().getValue().toString();
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        return config.isWorldExcluded(worldName);
    }
    
    /**
     * Check if in a non-spawn world (overworld, nether, end, etc.)
     * Simply verifies we're NOT at spawn
     */
    public boolean isInOverworld() {
        if (client.player == null || client.world == null) {
            return false;
        }
        
        // Just check if we're NOT at spawn - this covers overworld, nether, end, etc.
        return !isAtSpawn();
    }
    
    /**
     * Check if automation is currently running
     * @return true if automation is active, false otherwise
     */
    public boolean isRunning() {
        return isAutomationRunning;
    }
    
    /**
     * Mark that a disconnect has occurred
     */
    public void markDisconnected() {
        lastConnectionTime = 0;
        antiAfkDisabledOnReconnect = false;
        
        // Track disconnect during automation for journey detection
        if (isAutomationRunning || antiAfkActive) {
            hadDisconnect = true;
            PokeAlertClient.LOGGER.info("Disconnect detected during automation - will mark as Disconnect → Reconnect journey");
        }
        
        // Reset Anti-AFK movement tracking to prevent false positives on reconnect
        AntiAfkManager.resetTracking();
        
        PokeAlertClient.LOGGER.info("PokéAlert: Player disconnected from server");
    }
    
    /**
     * Mark that a connection has occurred
     */
    public void markConnected() {
        lastConnectionTime = System.currentTimeMillis();
        isConnected = true;
        
        // Don't restart monitoring if automation is already running
        // This prevents interference during spawn→overworld teleport
        if (isAutomationRunning) {
            PokeAlertClient.LOGGER.info("⚠️ World change detected during automation - preserving current process");
            return;
        }
        
        // Mark resource pack as loading (typically happens on connect, causes high CPU usage)
        isResourcePackLoading = true;
        
        // Reset the Anti-AFK disabled flag for new connection
        antiAfkDisabledOnReconnect = false;
        
        // Reset Anti-AFK movement tracking on connect to ensure clean baseline
        AntiAfkManager.resetTracking();
        
        // START STATE MONITORING IMMEDIATELY!
        // Anti-AFK starts at 0s, so we need to collect movement data from the start
        // By the time resource pack finishes (10s), we'll have 10 seconds of reliable data
        AntiAfkManager.startStateMonitoring();
        PokeAlertClient.LOGGER.info("🔌 Player connected - starting Anti-AFK monitoring immediately");
        
        // Mark resource pack as done after 10 seconds
        scheduler.schedule(() -> {
            isResourcePackLoading = false;
            PokeAlertClient.LOGGER.info("Resource pack loading complete");
        
            // Start automation if in AUTO mode
            // By now, we have 10 seconds of movement data!
            if (mode == AutomationMode.AUTO) {
                startMonitoring();
                
                // Also start safety monitor if not already running
                if (safetyMonitorTask == null || safetyMonitorTask.isDone()) {
                    PokeAlertClient.LOGGER.info("Starting safety monitor after resource pack load (AUTO mode)");
                    startSafetyMonitor();
                }
                // Start player suspicion monitor if not already running
                if (playerSuspicionMonitorTask == null || playerSuspicionMonitorTask.isDone()) {
                    startPlayerSuspicionMonitor();
                }
            }
        }, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Toggle the egg hatcher automation
     */
    public void toggleAutomation() {
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        // Check if mod is enabled
        if (!config.modEnabled) {
            return;
        }
        
        // Check if automation was running before any changes
        boolean wasRunning = isAutomationRunning || antiAfkActive || currentState != State.IDLE || spawnDetectionTime > 0;
        
        // If automation is running, stop it first
        if (wasRunning) {
            stopAutomation();
        }
        
        // Toggle enable/disabled state regardless of whether automation was running
        if (mode == AutomationMode.DISABLED) {
            // Cycle to AUTO mode
            mode = AutomationMode.AUTO;
            config.eggHatcherEnabled = true;
            ConfigManager.saveSettings(config);
            
            // Reset ALL session flags for fresh start
            // Treat mode cycle to AUTO as "fresh server join" behavior
            manuallyCancelled = false;
            antiAfkDisabledOnReconnect = false;
            spawnDetectionTime = 0;
            
            // CRITICAL: Reset resource pack loading flag
            // When toggling ON manually, we're not loading a resource pack
            // This ensures safety monitor can run immediately
            isResourcePackLoading = false;
            
            // CRITICAL: Reset Anti-AFK tracking including stateBeforeTeleport
            // This prevents stale teleport state from previous automation runs
            AntiAfkManager.resetTracking();
            
            // CRITICAL: Ensure Anti-AFK state monitoring is running
            // Without this, state detector has no data and returns "unknown"
            AntiAfkManager.startStateMonitoring();
            PokeAlertClient.LOGGER.info("🔍 Anti-AFK state monitoring restarted for enable toggle");
            
            PokeAlertClient.LOGGER.info("Egg Hatcher enabled - all session flags reset (fresh start)");
            
            sendNotification("Egg Hatcher", "Enabled", Formatting.GREEN);
            startMonitoring();
            
            // Start safety monitor if not already running
            if (safetyMonitorTask == null || safetyMonitorTask.isDone()) {
                PokeAlertClient.LOGGER.info("Starting safety monitor for AUTO mode");
                startSafetyMonitor();
            }
            // Start player suspicion monitor if not already running
            if (playerSuspicionMonitorTask == null || playerSuspicionMonitorTask.isDone()) {
                startPlayerSuspicionMonitor();
            }
            
            // Check current location and trigger if at spawn
            if (isAtSpawn() && !isAutomationRunning) {
                startedFromSpawn = true; // Track that automation started from spawn
                startedInOverworld = false;
                hadDisconnect = false;
                hadOverworldCrash = false;
                startAutomationSequence(false, false);
            } else if (!isAtSpawn()) {
                // Enabled at overworld - start Step 4 (Anti-AFK) directly (NO Telegram notification)
                if (!antiAfkActive && !isAutomationRunning) {
                    PokeAlertClient.LOGGER.info("Enabled at overworld - Starting Step 4 (Anti-AFK) directly");
                    sendNotification("Egg Hatcher [4/5]", "Anti-AFK Start: Initializing Baritone", Formatting.YELLOW);
                    
                    // Track that automation started in overworld
                    startedFromSpawn = false;
                    startedInOverworld = true;
                    hadDisconnect = false;
                    hadOverworldCrash = false;

                    // Small delay to ensure world is stable
                    scheduler.schedule(() -> {
                        if (mode == AutomationMode.AUTO && !isAtSpawn() && !antiAfkActive) {
                            startBaritoneAntiAfk();
                        }
                    }, 1, TimeUnit.SECONDS);
                } else {
                    // Already active or running - just notify
                sendNotification("Egg Hatcher", "Enabled - safety monitor active", Formatting.GRAY);
                    PokeAlertClient.LOGGER.info("Enabled at overworld - Anti-AFK already active or automation running");
                }
            }
        } else {
            // AUTO -> DISABLED (simplified: removed MANUAL mode)
            // wasRunning already checked at the start of the method
            
            mode = AutomationMode.DISABLED;
            config.eggHatcherEnabled = false;
            ConfigManager.saveSettings(config);

            antiAfkDisabledOnReconnect = false;
            manuallyCancelled = false;
            spawnDetectionTime = 0;
            
            PokeAlertClient.LOGGER.info("Egg Hatcher disabled - all session flags reset");
            
            // Stop automation (will show "stopped and disabled" if was running)
            // stopAutomation() handles all notifications when mode is DISABLED
            stopAutomation();
            
            // No need to send separate "Disabled" notification - stopAutomation() handles it
            // It sends "Stopped and disabled" if wasRunning, or nothing if not running
            // (we don't send "Disabled" separately to avoid duplicate notifications)
            
            // Stop PlayerSuspicionMonitor when disabling automation
            stopPlayerSuspicionMonitor();
        }
    }
    
    /**
     * Disable Egg Hatcher completely (stop + set mode to DISABLED)
     */
    public void disableCompletely() {
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        // Set mode to DISABLED first so stopAutomation() knows it's being disabled
        mode = AutomationMode.DISABLED;
        
        // Stop all automation (will show "stopped and disabled" if was running)
        // stopAutomation() handles all notifications when mode is DISABLED
        stopAutomation();
        
        // Stop PlayerSuspicionMonitor when completely disabling
        stopPlayerSuspicionMonitor();
        
        // Save config
        config.eggHatcherEnabled = false;
        ConfigManager.saveSettings(config);
        
        // No need to send separate "Disabled" notification - stopAutomation() handles it
        // It sends "Stopped and disabled" if wasRunning, or nothing if not running
        // (we don't send "Disabled" separately to avoid duplicate notifications)
        
        PokeAlertClient.LOGGER.info("Egg Hatcher: Completely disabled");
    }
    
    // Removed manualTrigger() and startManualCountdown() methods - Manual mode removed (simplified to Auto/Disabled only)
    
    /**
     * Cancel the automation if it's waiting or monitoring
     */
    public void cancelAutomation() {
        // Legacy method - now just delegates to stopAutomation
        // Kept for compatibility but stopAutomation is preferred
        stopAutomation();
    }
    
    /**
     * Start monitoring for spawn detection
     */
    private void startMonitoring() {
        // Check every 2 seconds if we're at spawn
        if (currentTask != null) {
            currentTask.cancel(false);
        }
        
        currentTask = scheduler.scheduleAtFixedRate(() -> {
            if (client.player != null) {
                // FIRST: Clear flags if we're NOT at spawn (before the spawn check)
                if (!isAtSpawn()) {
                    // Player is at overworld, reset all spawn-related flags
                    if (spawnDetectionTime > 0) {
                        PokeAlertClient.LOGGER.info("Player left spawn - resetting all flags");
                        spawnDetectionTime = 0;
                        antiAfkDisabledOnReconnect = false;
                        manuallyCancelled = false; // Also reset manual cancel flag when leaving spawn
                    }
                    return; // Not at spawn, nothing to do
                }
                
                // NOW check if we should trigger automation at spawn
                if (!isAutomationRunning && !manuallyCancelled) {
                    long currentTime = System.currentTimeMillis();
                    
                    // DON'T set spawnDetectionTime yet - wait for state initialization
                    // This prevents the 30s buffer from starting too early
                
                boolean shouldTrigger = false;
                
                switch (mode) {
                    case AUTO:
                        // By this point, state monitoring has been running for 10+ seconds (started on connect)
                        // Universal 3-second grace period ensures reliable state detection for all scenarios
                        if (!antiAfkDisabledOnReconnect) {
                            PokeAlertClient.LOGGER.info("🔄 Spawn detected, waiting 3s grace period for reliable state detection");
                            
                            // CRITICAL: Set flag BEFORE scheduling to prevent duplicate triggers
                            antiAfkDisabledOnReconnect = true;
                            
                            // Schedule the automation steps after 3s grace period (universal for all scenarios)
                            scheduler.schedule(() -> {
                                proceedWithSpawnDetection();
                            }, 3, TimeUnit.SECONDS);
                            
                            return; // Exit early, scheduler will handle the rest
                        }
                        
                        // Check if we should trigger realm return (after 30s buffer)
                        if ((currentTime - spawnDetectionTime) >= REALM_SWITCH_BUFFER) {
                            shouldTrigger = true;
                        }
                        break;
                }
                
                if (shouldTrigger) {
                    // Use the flag to determine if Anti-AFK was already disabled
                    startAutomationSequence(false, antiAfkDisabledOnReconnect);
                }
            }
        }
        }, 1, 2, TimeUnit.SECONDS);
    }
    
    /**
     * Start the full automation sequence
     */
    private void startAutomationSequence(boolean isManual, boolean antiAfkAlreadyDisabled) {
        if (isAutomationRunning) {
            return; // Already running
        }
        
        isAutomationRunning = true;
        automationStartTime = System.currentTimeMillis();
        currentState = State.DETECTED_AT_SPAWN;
        
        // CRITICAL: Set journey tracking flags based on current location
        // This ensures correct journey type in Telegram notification
        if (isAtSpawn()) {
            startedFromSpawn = true;
            startedInOverworld = false;
            PokeAlertClient.LOGGER.info("Journey tracking: Started from spawn (startedFromSpawn=true)");
        } else {
            startedFromSpawn = false;
            startedInOverworld = true;
            PokeAlertClient.LOGGER.info("Journey tracking: Started in overworld (startedInOverworld=true)");
        }
        
        // Don't reset journey tracking flags here - they should persist until Telegram notification is sent
        // Flags are only reset in stopAutomation() after notification is sent
        
        // Reset spawn detection time and retry counter
        spawnDetectionTime = 0;
        step5RetryCount = 0;
        
        // Auto mode - execute directly without additional confirmation
        executeAutomationSteps(antiAfkAlreadyDisabled);
    }
    
    // Overload for backward compatibility
    private void startAutomationSequence(boolean isManual) {
        startAutomationSequence(isManual, false);
    }
    
    /**
     * v3.0.0: Execute the automation steps (simplified - no external Anti-AFK to disable)
     */
    private void executeAutomationSteps(boolean antiAfkAlreadyDisabled) {
        currentState = State.DETECTED_AT_SPAWN;
        
        // Start stuck detection timer (only if not already running)
        if (stuckDetector == null || stuckDetector.isDone()) {
            stuckDetector = scheduler.schedule(() -> {
                if (isAtSpawn() && mode == AutomationMode.AUTO) {
                    currentState = State.STUCK;
                    handleStuckAtSpawn();
                }
            }, STUCK_TIMEOUT, TimeUnit.MILLISECONDS);
            PokeAlertClient.LOGGER.info("🚨 Stuck detector started - 2 min timeout");
        }
        
        // v3.0.0: No external Anti-AFK to disable, proceed directly to Step 2 (Server Buffer)
            continueAutomationFromStep2();
    }
    
    // Overload for backward compatibility
    private void executeAutomationSteps() {
        executeAutomationSteps(false);
    }
    
    /**
     * v3.0.0: Proceed with spawn detection (Step 1)
     * Simplified - no longer needs to check external Anti-AFK state
     */
    private void proceedWithSpawnDetection() {
        long currentTime = System.currentTimeMillis();
        String location = AntiAfkManager.getPlayerLocationInfo();
        
        // Start stuck detection timer if not already running
        if (stuckDetector == null || stuckDetector.isDone()) {
            stuckDetector = scheduler.schedule(() -> {
                if (isAtSpawn() && mode == AutomationMode.AUTO) {
                    currentState = State.STUCK;
                    handleStuckAtSpawn();
                }
            }, STUCK_TIMEOUT, TimeUnit.MILLISECONDS);
            PokeAlertClient.LOGGER.info("🚨 Stuck detector started - 2 min timeout");
        }
        
        // v3.0.0: Stop any existing Baritone movement from previous session
        BaritoneController.stop();
        antiAfkActive = false;
        
        // Step 1: Spawn Detection (v3.0.0 - 5 steps total)
        sendNotification("Egg Hatcher [1/5]", "Spawn Detected", Formatting.YELLOW);
        PokeAlertClient.LOGGER.info("🎯 Step 1/5: Spawn Detection at " + location);
        
        // v3.0.0: Step 2 (Anti-AFK Check) removed - no external Anti-AFK to check
        // Proceed directly to server buffer
        
        antiAfkDisabledOnReconnect = true;
        spawnDetectionTime = currentTime;
        PokeAlertClient.LOGGER.info("Spawn detection time set, starting automation");
        
        // Step 2: Server Buffer (was Step 3 in v2.0.0)
        if (client.player != null) {
            PokeAlertConfig stepConfig = PokeAlertClient.getInstance().config;
            if (stepConfig.inGameTextEnabled) {
                Text notification = Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("Egg Hatcher [2/5]: ").formatted(Formatting.WHITE))
                    .append(Text.literal("Server Buffer: Waiting 30s").formatted(Formatting.YELLOW))
                    .append(Text.literal(" - Press Home to disable").formatted(Formatting.GRAY));
                
                client.player.sendMessage(notification, false);
            }
        }
        PokeAlertClient.LOGGER.info("🎯 Step 2/5: Server Buffer - Waiting 30 seconds before realm change at " + location);
    }
    
    /**
     * v3.0.0: Continue automation from Step 2 (Server Buffer)
     * Handles Step 3 (Realm Change) and Step 4 (Anti-AFK Start via Baritone)
     */
    private void continueAutomationFromStep2() {
        // Step 3: Send /home command after 30s buffer
        step2Task = scheduler.schedule(() -> {
            // Check teleport cooldown to prevent command spam during restarts
            long currentTime = System.currentTimeMillis();
            long timeSinceLastTeleport = currentTime - lastTeleportCommandTime;
            
            if (timeSinceLastTeleport < TELEPORT_COOLDOWN) {
                long remainingCooldown = (TELEPORT_COOLDOWN - timeSinceLastTeleport) / 1000;
                PokeAlertClient.LOGGER.warn("Teleport on cooldown, skipping command ({}s remaining)", remainingCooldown);
                sendNotification("Egg Hatcher", "Teleport cooldown active - Waiting", Formatting.DARK_GRAY);
                
                // Retry after cooldown expires
                scheduler.schedule(() -> continueAutomationFromStep2(), remainingCooldown + 1, TimeUnit.SECONDS);
                return;
            }
            
            currentState = State.SENDING_HOME_COMMAND;
            String location = AntiAfkManager.getPlayerLocationInfo();
            
            // Step 3: Realm Change (was Step 4 in v2.0.0)
            sendNotification("Egg Hatcher [3/5]", "Realm Change: Executing", Formatting.YELLOW);
            PokeAlertClient.LOGGER.info("🎯 Step 3/5: Realm Change - Sending teleport command at " + location);
            
            PokeAlertConfig config = PokeAlertClient.getInstance().config;
            String returnCmd = config.realmReturnCommand;
            sendChatCommand(returnCmd);
            
            // Update last teleport time
            lastTeleportCommandTime = currentTime;
            
            // Stop safety monitor during teleport
            stopSafetyMonitor();
            PokeAlertClient.LOGGER.info("🛡️ Safety monitor paused for teleport");
            
            // Wait for teleport then start Step 4 (Anti-AFK)
            step3Task = scheduler.schedule(() -> {
                currentState = State.WAITING_FOR_TELEPORT;
                
                // Step 4: Anti-AFK Start via Baritone (was Step 5 in v2.0.0)
                step4Task = scheduler.schedule(() -> {
                    // Verify we successfully teleported to overworld
                    if (!isInOverworld()) {
                        String currentWorld = client.world != null ? 
                                            client.world.getRegistryKey().getValue().toString() : "unknown";
                        PokeAlertClient.LOGGER.error("❌ Step 4/5 FAILED: Not in overworld after 17s (current: " + currentWorld + ")");
                        sendNotification("Egg Hatcher", "World verification failed - Restarting", Formatting.RED);
                        
                        cancelAllAutomationTasks();
                        scheduler.schedule(() -> {
                            stopAutomation();
                            manuallyCancelled = false;
                            antiAfkDisabledOnReconnect = false;
                            spawnDetectionTime = 0;
                            startMonitoring();
                        }, 3, TimeUnit.SECONDS);
                        return;
                    }
                    
                    // Confirmed in overworld - proceed with Step 4: Baritone Anti-AFK
                    String overworldLocation = AntiAfkManager.getPlayerLocationInfo();
                    PokeAlertClient.LOGGER.info("✅ World verification passed: In overworld at " + overworldLocation);
                    
                    // v3.0.0: Start Baritone-based Anti-AFK
                    currentState = State.ENABLING_ANTIAFK;
                    sendNotification("Egg Hatcher [4/5]", "Anti-AFK Start: Initializing Baritone", Formatting.YELLOW);
                    PokeAlertClient.LOGGER.info("🎯 Step 4/5: Anti-AFK Start - Initializing Baritone at " + overworldLocation);
                    
                    // Start the Baritone Anti-AFK system
                    startBaritoneAntiAfk();
                    
                }, TELEPORT_WAIT_TIME, TimeUnit.MILLISECONDS);
                
            }, HOME_COMMAND_DELAY, TimeUnit.MILLISECONDS);
            
        }, REALM_SWITCH_BUFFER, TimeUnit.MILLISECONDS);
    }
    
    /**
     * v3.0.0: Start Baritone-based Anti-AFK movement
     * Initializes region, queue, monitors, and starts navigation
     */
    private void startBaritoneAntiAfk() {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        // Validate region
        if (!config.isAntiAfkRegionValid()) {
            PokeAlertClient.LOGGER.error("❌ Anti-AFK region not configured!");
            SafetyManager.triggerSafetyStop(SafetyManager.REASON_REGION_INVALID, true);
            return;
        }
        
        // Warn if region is too small
        if (config.isAntiAfkRegionTooSmall()) {
            PokeAlertClient.LOGGER.warn("⚠️ Anti-AFK region is smaller than recommended (20x20)");
        }
        
        // Initialize region
        antiAfkRegion = new AntiAfkRegion(
            config.antiAfkRegionX1, config.antiAfkRegionZ1,
            config.antiAfkRegionX2, config.antiAfkRegionZ2
        );
        PokeAlertClient.LOGGER.info("📍 Anti-AFK Region: " + antiAfkRegion);
        
        // Reset safety manager for new cycle
        SafetyManager.reset();
        
        // Initialize location queue
        locationQueue.initialize(antiAfkRegion, config);
        
        // Start monitors
        CoordinateMonitor.startMonitoring(config);
        PlayerMonitor.startMonitoring(config);
        
        // Register callbacks
        setupMonitorCallbacks();
        
        // Start Anti-AFK
        antiAfkActive = true;
        
        // Navigate to first location
        navigateToNextLocation(null);
        
        PokeAlertClient.LOGGER.info("✅ Baritone Anti-AFK started with {} initial locations", 
            locationQueue.getQueueSize());
    }
    
    /**
     * v3.0.0: Setup callbacks for coordinate and player monitors
     */
    private void setupMonitorCallbacks() {
        // Arrival callback
        CoordinateMonitor.onArrival(() -> {
            if (!antiAfkActive) return;
            
            // Guard: Prevent multiple arrival callbacks from firing simultaneously using atomic flag
            // This is thread-safe and prevents race conditions
            if (!arrivalProcessed.compareAndSet(false, true)) {
                // Already processed by another arrival callback - skip
                PokeAlertClient.LOGGER.debug("Arrival callback: Already processed, skipping");
            return;
        }
        
            // Mark path complete to prevent duplicate processing
            BaritoneController.markPathComplete();
            
            // CRITICAL: Clear destination in CoordinateMonitor to prevent repeated arrival callbacks
            // This must be done BEFORE resetting the arrival processed flag
            CoordinateMonitor.clearDestination();
            
            // Reset arrival processed flag for next navigation
            arrivalProcessed.set(false);
            
            // Stop jumping when arriving
            stopJumping();
            
            // CRITICAL: Cancel timeout timer FIRST before any action processing
            // This prevents timeout from firing if arrival callback takes time to process
            if (locationTimeoutTask != null) {
                locationTimeoutTask.cancel(false);
                locationTimeoutTask = null;
                PokeAlertClient.LOGGER.debug("Arrival callback: Timeout cancelled");
            }
            
            // Cancel progress check task (arrival successful, no need to monitor progress)
            if (progressCheckTask != null) {
                progressCheckTask.cancel(false);
                progressCheckTask = null;
            }
            lastProgressCheckPosition = null;
            lastProgressCheckTime = 0;
            
            // Clear stored navigation action (arrival successful, action was applied)
            currentNavigationAction = null;
            
            // Check if Step 5 should trigger (3 successful visits)
            locationQueue.markVisitedAndAdvance();
            
            if (locationQueue.shouldTriggerStep5()) {
                locationQueue.markStep5Completed();
                completeAutomation();
            }
            
            // Human-like behavior: Roll for ALL actions (navigation + pause/look around), select LOWEST chance if multiple land
            // NOTE: This roll happens ONCE per #goto action (when arriving at a destination)
            // Each action has its own chance (0% = disabled), and if multiple actions land, the one with the lowest chance is selected
            PokeAlertConfig config = ConfigManager.getConfig();
            if (config.enableHumanLikeBehavior) {
                // Roll separate random value for each action
                double backtrackRandom = behaviorRandom.nextDouble();
                double walkRandom = behaviorRandom.nextDouble();
                double hotbarRandom = behaviorRandom.nextDouble();
                double jumpRandom = behaviorRandom.nextDouble();
                double longPauseRandom = behaviorRandom.nextDouble();
                double breakPauseRandom = behaviorRandom.nextDouble();
                double lookAroundRandom = behaviorRandom.nextDouble();
                
                // Track which actions "landed" (random < chance) and their chance values
                // IMPORTANT: We need to find the LOWEST chance among ALL successful rolls
                // Strategy: First collect all successful actions with their chances, then select the one with lowest chance
                double lowestChance = Double.MAX_VALUE;
                String selectedAction = null;
                
                // Collect all successful actions with their chance values
                // Navigation actions
                if (config.backtrackChance > 0 && backtrackRandom < config.backtrackChance) {
                    if (config.backtrackChance < lowestChance) {
                        lowestChance = config.backtrackChance;
                        selectedAction = "backtrack";
                    }
                }

                if (config.walkChance > 0 && walkRandom < config.walkChance) {
                    if (config.walkChance < lowestChance) {
                        lowestChance = config.walkChance;
                        selectedAction = "walk";
                    }
                }

                if (config.hotbarSwitchChance > 0 && hotbarRandom < config.hotbarSwitchChance) {
                    if (config.hotbarSwitchChance < lowestChance) {
                        lowestChance = config.hotbarSwitchChance;
                        selectedAction = "hotbar";
                    }
                }

                if (config.jumpWhileMovingChance > 0 && jumpRandom < config.jumpWhileMovingChance) {
                    if (config.jumpWhileMovingChance < lowestChance) {
                        lowestChance = config.jumpWhileMovingChance;
                        selectedAction = "jump";
                    }
                }

                // Pause/look around actions
                if (config.longPauseChance > 0 && longPauseRandom < config.longPauseChance) {
                    if (config.longPauseChance < lowestChance) {
                        lowestChance = config.longPauseChance;
                        selectedAction = "longPause";
                    }
                }

                if (config.breakPauseChance > 0 && breakPauseRandom < config.breakPauseChance) {
                    if (config.breakPauseChance < lowestChance) {
                        lowestChance = config.breakPauseChance;
                        selectedAction = "breakPause";
                    }
                }

                if (config.lookAroundChance > 0 && lookAroundRandom < config.lookAroundChance) {
                    if (config.lookAroundChance < lowestChance) {
                        lowestChance = config.lookAroundChance;
                        selectedAction = "lookAround";
                    }
                }
                
                // Log which action was selected for debugging
                if (selectedAction != null) {
                    PokeAlertClient.LOGGER.info("Human behavior: Selected action '{}' with chance {} (lowest among successful rolls)", 
                        selectedAction, lowestChance);
                } else {
                    // Log default behavior selection
                    double totalChance = config.backtrackChance + config.walkChance + config.hotbarSwitchChance + 
                                        config.jumpWhileMovingChance + config.longPauseChance + 
                                        config.breakPauseChance + config.lookAroundChance;
                    PokeAlertClient.LOGGER.info("Human behavior: No action selected (default behavior) - Total action chance: {:.2f}%, Default chance: {:.2f}%", 
                        String.format("%.2f", totalChance * 100.0), String.format("%.2f", (1.0 - totalChance) * 100.0));
                }

                // Execute only the selected action (lowest chance if multiple landed, or the single one that landed)
                if (selectedAction != null) {
                    // Handle pause/look around actions (blocking - happens immediately)
                    if (selectedAction.equals("longPause") || selectedAction.equals("breakPause") || selectedAction.equals("lookAround")) {
                        int pauseMs;
                        String notificationMessage;
                        
                        switch (selectedAction) {
                            case "longPause":
                                pauseMs = config.minLongPauseMs + behaviorRandom.nextInt(config.maxLongPauseMs - config.minLongPauseMs);
                                notificationMessage = "Taking a short break - Looking around";
                                PokeAlertClient.LOGGER.info("Human behavior: Taking {}ms pause", pauseMs);
                                break;
                            case "breakPause":
                                pauseMs = config.minBreakPauseMs + behaviorRandom.nextInt(config.maxBreakPauseMs - config.minBreakPauseMs);
                                notificationMessage = "Taking a longer break - Looking around";
                                PokeAlertClient.LOGGER.info("Human behavior: Taking {}ms break", pauseMs);
                                break;
                            case "lookAround":
                                pauseMs = 2000 + behaviorRandom.nextInt(3000); // 2-5 seconds for looking around
                                notificationMessage = "Looking around";
                                PokeAlertClient.LOGGER.info("Human behavior: Taking {}ms to look around", pauseMs);
                                break;
                            default:
                                pauseMs = 0;
                                notificationMessage = null;
                        }
                        
                        if (pauseMs > 0) {
                            // Only send notification once
                            if (!breakNotificationSent) {
                                sendNotification("Egg Hatcher", notificationMessage, Formatting.GRAY);
                                breakNotificationSent = true;
                            }

                            // Timeout was already cancelled at the start of arrival callback
                            // No need to cancel again - we're intentionally pausing, not navigating
                            // CRITICAL: Ensure timeout is cancelled (double-check for safety)
                            if (locationTimeoutTask != null) {
                                locationTimeoutTask.cancel(false);
                                locationTimeoutTask = null;
                            }

                            setBreakState(true); // Skip anti-AFK checking during break
                            simulateCameraRotation(pauseMs);

                            scheduler.schedule(() -> {
                                setBreakState(false);
                                breakNotificationSent = false; // Reset notification flag
                                if (antiAfkActive && !SafetyManager.isSafetyTriggered()) {
                                    // CRITICAL: Always navigate after pause completes
                                    // This ensures navigation continues even if pause was long
                                    navigateToNextLocation(null);
                                }
                            }, pauseMs, TimeUnit.MILLISECONDS);
                return;
            }
            } else {
                        // Handle navigation actions (non-blocking - applied to next navigation)
                        navigateToNextLocation(selectedAction);
                        return;
                    }
                }
            }
            
            // Continue to next location immediately (no blocking pause, no special action)
            navigateToNextLocation(null);
        });
        
        // Teleport detection callback
        CoordinateMonitor.onTeleportDetected(() -> {
            PokeAlertClient.LOGGER.warn("⚠️ Teleport/Manual movement detected!");
            SafetyManager.triggerSafetyStop(SafetyManager.REASON_TELEPORT, true);
            stopBaritoneAntiAfk();
        });
        
        // World change callback
        CoordinateMonitor.onWorldChange(newWorld -> {
            PokeAlertClient.LOGGER.warn("⚠️ World changed to: " + newWorld);
            
            // Stop Anti-AFK first
            stopBaritoneAntiAfk();
            
            // Check if we went to spawn (server kicked us back)
            // In this case, we want to auto-restart, not permanent stop
            if (isAtSpawn()) {
                PokeAlertClient.LOGGER.info("📍 Teleported to spawn - will auto-restart automation");
                
                // Track overworld crash: if we started in overworld and got forced to spawn
                if (startedInOverworld && (isAutomationRunning || antiAfkActive)) {
                    hadOverworldCrash = true;
                    PokeAlertClient.LOGGER.info("Overworld crash detected - will mark as Overworld crash → Spawn → Overworld journey");
                }
                
                // Trigger safety stop with notification but allow restart
                SafetyManager.triggerSafetyStop(SafetyManager.REASON_WORLD_CHANGE + " (to spawn)", false); // No telegram for spawn return
        } else {
                // Went to unexpected world (not spawn, not overworld where we started)
                PokeAlertClient.LOGGER.warn("📍 Teleported to unknown world - stopping");
                SafetyManager.triggerSafetyStop(SafetyManager.REASON_WORLD_CHANGE, true);
            }
        });
        
        // Player list change callback (every 5 minutes, in-game notification only)
        // Telegram notification will be included in Step 5 completion message
        PlayerMonitor.onPlayerListChanged((joined, left) -> {
            if (joined.isEmpty() && left.isEmpty()) return;
            
            StringBuilder inGameMessage = new StringBuilder();
            inGameMessage.append("Avoided Players: ");
            
            if (!joined.isEmpty()) {
                inGameMessage.append("Joined: ").append(String.join(", ", joined));
            }
            
            if (!left.isEmpty()) {
                if (!joined.isEmpty()) {
                    inGameMessage.append(" | Left: ").append(String.join(", ", left));
                } else {
                    inGameMessage.append("Left: ").append(String.join(", ", left));
                }
            }
            
            // Send in-game notification only (no Telegram spam)
            sendNotification("Egg Hatcher", inGameMessage.toString(), Formatting.YELLOW);
            PokeAlertClient.LOGGER.info("EggHatcher: Avoided players list updated (in-game notification only)");
        });
        
        // Nearby player detection callback (immediate safety stop)
        PlayerMonitor.onPlayerDetectedWithType((playerName, type) -> {
            if (type.equals("nearby") || type.equals("nearby_vanished")) {
                // Player nearby - Safety stop required
                String reason = type.equals("nearby_vanished") ? 
                    SafetyManager.REASON_PLAYER_NEARBY + " (VANISHED - likely admin!)" :
                    SafetyManager.REASON_PLAYER_NEARBY;
                PokeAlertClient.LOGGER.error("🚨 Avoided player nearby: " + playerName + 
                    (type.equals("nearby_vanished") ? " (VANISHED!)" : ""));
                
                // Send separate "Avoided Players Warning" Telegram notification
                PokeAlertConfig config = ConfigManager.getConfig();
                if (config.telegramEnabled && config.isTelegramValid()) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            TelegramNotification telegram = new TelegramNotification();
                            telegram.initialize();
                            
                            StringBuilder message = new StringBuilder();
                            message.append("⚠️ <b>Avoided Players Warning</b>\n");
                            message.append("• <b>Player:</b> <code>").append(playerName).append("</code>\n");
                            if (type.equals("nearby_vanished")) {
                                message.append("• <b>Status:</b> <i> Vanished nearby</i>\n");
        } else {
                                message.append("• <b>Status:</b> Nearby\n");
                            }
                            message.append("• <b>Action:</b> Anti-AFK stopped immediately\n");
                            message.append("🚨 <i>Manual restart required</i>");
                            
                            telegram.sendEggTimerNotification(message.toString());
                            PokeAlertClient.LOGGER.info("EggHatcher: Sent Avoided Players Warning notification");
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.error("EggHatcher: Failed to send Avoided Players Warning", e);
                        }
                    });
                }
                
                SafetyManager.triggerSafetyStop(reason + ": " + playerName, true);
                stopBaritoneAntiAfk();
            }
        });
        
        // Safety manager callback
        SafetyManager.onSafetyStop(reason -> {
            PokeAlertClient.LOGGER.error("🛑 Safety stop triggered: " + reason);
            stopBaritoneAntiAfk();
            
            // v3.0.0: Don't call stopAutomation() which sets manuallyCancelled=true
            // Instead, reset state to allow auto-restart if at spawn
            isAutomationRunning = false;
            currentState = State.IDLE;
                    spawnDetectionTime = 0;
            antiAfkDisabledOnReconnect = false;
            
            // Cancel automation tasks but NOT monitoring
            cancelAllAutomationTasks();
            
            // If we're at spawn in AUTO mode, restart monitoring to trigger re-automation
            if (mode == AutomationMode.AUTO && isAtSpawn()) {
                PokeAlertClient.LOGGER.info("🔄 Safety stop at spawn - restarting automation in 5s");
                sendNotification("Egg Hatcher", "Teleported to spawn - Restarting", Formatting.YELLOW);
                
                // Wait 5 seconds then restart monitoring (fresh start)
                scheduler.schedule(() -> {
                    if (mode == AutomationMode.AUTO && isAtSpawn()) {
                        SafetyManager.reset(); // Clear safety lock for new cycle
                        manuallyCancelled = false; // Allow monitoring to trigger
                        PokeAlertClient.LOGGER.info("🔄 Restarting monitoring after safety stop");
                    startMonitoring();
                }
                }, 5, TimeUnit.SECONDS);
            } else {
                // Not at spawn - just log
                PokeAlertClient.LOGGER.info("🛑 Safety stop at overworld/unknown - waiting for manual action or spawn detection");
            }
        });
    }
    
    /**
     * v3.0.0: Navigate to the next location in queue with human-like behaviors
     * @param preselectedAction Optional action to apply (from arrival callback selection), or null to roll for actions here
     */
    private void navigateToNextLocation(String preselectedAction) {
        if (!antiAfkActive || SafetyManager.isSafetyTriggered() || isInBreakState) {
            return;
        }
        
        // CRITICAL: Get destination FIRST before any checks that might cause early return
        // This ensures destination is set even if navigation is skipped, preventing timeout
        int[] destination = locationQueue.getCurrentDestination();
        if (destination == null) {
            PokeAlertClient.LOGGER.error("❌ No destination available!");
            return;
        }
        
        // Guard: Don't start new navigation if Baritone is already pathing
        // This prevents duplicate #goto commands from multiple callbacks (arrival, timeout, safety monitor)
        // NOTE: gotoLocation() also has this check, but we check here to avoid setting destination unnecessarily
        if (BaritoneController.isPathing()) {
            PokeAlertClient.LOGGER.debug("Baritone already pathing, skipping duplicate navigation");
            // CRITICAL FIX: Set destination even if navigation is skipped to prevent timeout
            // This ensures CoordinateMonitor has a destination to monitor, even if Baritone doesn't navigate yet
            CoordinateMonitor.setDestination(destination[0], destination[1]);
            PokeAlertClient.LOGGER.debug("Destination set to ({}, {}) despite Baritone already pathing - will navigate when pathing completes", 
                destination[0], destination[1]);
            return;
        }
        
        // Guard: Don't start navigation if camera is rotating (look around action in progress)
        // Queue navigation to execute after camera rotation completes instead of skipping
        // CRITICAL FIX: Set destination BEFORE queuing to prevent timeout
        if (isCameraRotating) {
            PokeAlertClient.LOGGER.debug("Camera rotating (look around in progress), queuing navigation");
            // CRITICAL: Set destination now so CoordinateMonitor has something to monitor during camera rotation
            CoordinateMonitor.setDestination(destination[0], destination[1]);
            // Schedule navigation to execute after a short delay (camera rotation should complete soon)
            scheduler.schedule(() -> {
                if (!isCameraRotating && antiAfkActive && !SafetyManager.isSafetyTriggered() && !BaritoneController.isPathing()) {
                    navigateToNextLocation(preselectedAction);
                }
            }, 100, TimeUnit.MILLISECONDS);
            return;
        }
        
        // Cancel any pending timeout task before starting new navigation
        if (locationTimeoutTask != null) {
            locationTimeoutTask.cancel(false);
            locationTimeoutTask = null;
        }
        
        // Don't navigate if we're in an actual break (long/break pause)
        // But allow navigation during normal camera rotation (which happens while moving)
        
        // Stop jumping from previous action (if any) before starting new navigation
        stopJumping();
        
        // Stop camera rotation when starting navigation - Baritone will handle camera with #freelook
        if (cameraRotationTask != null) {
            cameraRotationTask.cancel(false);
            cameraRotationTask = null;
            isCameraRotating = false;
            PokeAlertClient.LOGGER.debug("Stopped camera rotation for Baritone navigation");
        }
        
        PokeAlertConfig config = ConfigManager.getConfig();
        
        // Destination already retrieved above (moved before isPathing check to prevent timeout bug)

        // Apply preselected action (from arrival callback) or use stored action (from timeout)
        // IMPORTANT: Store action to prevent re-rolling on timeout
        boolean shouldWalk = false;
        boolean shouldJump = false;
        String selectedAction = preselectedAction != null ? preselectedAction : currentNavigationAction;
        
        // Clear stored action when starting new navigation (will be set if action is selected)
        currentNavigationAction = null;
        
        if (config.enableHumanLikeBehavior && selectedAction == null) {
            // No preselected action - roll for navigation actions only (pause actions handled in arrival callback)
            double backtrackRandom = behaviorRandom.nextDouble();
            double walkRandom = behaviorRandom.nextDouble();
            double hotbarRandom = behaviorRandom.nextDouble();
            double jumpRandom = behaviorRandom.nextDouble();
            
            // Track which actions "landed" (random < chance) and their chance values
            double lowestChance = Double.MAX_VALUE;

            // Check backtrack action (skip if chance is 0% - disabled)
            if (config.backtrackChance > 0 && backtrackRandom < config.backtrackChance) {
                if (config.backtrackChance < lowestChance) {
                    lowestChance = config.backtrackChance;
                    selectedAction = "backtrack";
                }
            }

            // Check walk action (skip if chance is 0% - disabled)
            if (config.walkChance > 0 && walkRandom < config.walkChance) {
                if (config.walkChance < lowestChance) {
                    lowestChance = config.walkChance;
                    selectedAction = "walk";
                }
            }

            // Check hotbar switch action (skip if chance is 0% - disabled)
            if (config.hotbarSwitchChance > 0 && hotbarRandom < config.hotbarSwitchChance) {
                if (config.hotbarSwitchChance < lowestChance) {
                    lowestChance = config.hotbarSwitchChance;
                    selectedAction = "hotbar";
                }
            }

            // Check jump while moving action (skip if chance is 0% - disabled)
            if (config.jumpWhileMovingChance > 0 && jumpRandom < config.jumpWhileMovingChance) {
                if (config.jumpWhileMovingChance < lowestChance) {
                    lowestChance = config.jumpWhileMovingChance;
                    selectedAction = "jump";
                }
            }
        }
        
        // Store selected action for this navigation (to reuse on timeout)
        if (selectedAction != null) {
            currentNavigationAction = selectedAction;
        }
        
        // Execute the selected navigation action
        if (selectedAction != null && config.enableHumanLikeBehavior) {
            switch (selectedAction) {
                case "backtrack":
                    if (locationQueue.hasPrevious()) {
                        // Get the previous destination before backtracking
                        int[] previousDestination = locationQueue.getPreviousDestination();
                        if (previousDestination != null) {
                            // Check if we're already at the previous location (prevent immediate arrival callback)
                            int currentX = (int) client.player.getX();
                            int currentZ = (int) client.player.getZ();
                            double distanceToPrevious = Math.sqrt(
                                Math.pow(currentX - previousDestination[0], 2) + 
                                Math.pow(currentZ - previousDestination[1], 2)
                            );
                            
                            // If we're already at the previous location, skip backtracking to prevent immediate arrival
                            if (distanceToPrevious <= 3.0) {
                                PokeAlertClient.LOGGER.debug("Human behavior: Skipping backtrack - already at previous location");
                                // Skip backtracking, continue with normal navigation
                                selectedAction = null;
                                currentNavigationAction = null;
                                break;
                            }
                        }
                        
                        PokeAlertClient.LOGGER.info("Human behavior: Backtracking to previous location");
                        sendNotification("Egg Hatcher", "Backtracking to previous location", Formatting.GRAY);
                        locationQueue.goToPrevious();
                        // Get new destination after backtracking
                        destination = locationQueue.getCurrentDestination();
                        if (destination == null) {
                            PokeAlertClient.LOGGER.error("❌ No destination available after backtrack!");
                            return;
                        }
                    }
                    break;
                case "walk":
                    shouldWalk = true;
                    PokeAlertClient.LOGGER.debug("Human behavior: Walking (no sprint)");
                    sendNotification("Egg Hatcher", "Walking to destination", Formatting.GRAY);
                    BaritoneController.setAllowSprint(false);
                    break;
                case "hotbar":
                    int slot = behaviorRandom.nextInt(9);
                    sendNotification("Egg Hatcher", "Switching to hotbar slot " + (slot + 1), Formatting.GRAY);
                    switchHotbarSlot(slot);
                    break;
                case "jump":
                    // Jump is only valid when sprinting (not walking)
                    // If jump is selected, we'll enable it after navigation starts
                    shouldJump = true;
                    PokeAlertClient.LOGGER.debug("Human behavior: Will jump while moving");
                    break;
            }
        }
        
        // Default to sprinting (if walking action was not selected)
        if (!shouldWalk) {
            BaritoneController.setAllowSprint(true);
        }
        
        // Set destination in monitor
        CoordinateMonitor.setDestination(destination[0], destination[1]);
        
        // Start navigation via Baritone
        BaritoneController.gotoLocation(destination[0], destination[1]);
        
        PokeAlertClient.LOGGER.info("🚶 Navigating to {} - {}", 
            locationQueue.getStatus(), 
            String.format("(%d, %d)", destination[0], destination[1]));
        
        // Start movement progress monitoring (for stuck detection)
        // Cancel any existing progress check task
        if (progressCheckTask != null) {
            progressCheckTask.cancel(false);
            progressCheckTask = null;
        }
        
        // Initialize progress tracking
        if (client.player != null) {
            lastProgressCheckPosition = new int[] { (int) client.player.getX(), (int) client.player.getZ() };
            lastProgressCheckTime = System.currentTimeMillis();
            
            // Start progress check task (check every 15 seconds)
            final int[] finalDestination = destination; // Final reference for lambda
            progressCheckTask = scheduler.scheduleAtFixedRate(() -> {
                checkMovementProgress(finalDestination);
            }, PROGRESS_CHECK_INTERVAL, PROGRESS_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
        }
        
        // Human-like behavior: Jumping while moving - ONLY when sprinting, not walking
        // IMPORTANT: Only execute jump if it was explicitly selected as the action
        // This ensures only ONE action executes per navigation (backtrack OR walk OR hotbar OR jump, not multiple)
        if (config.enableHumanLikeBehavior && "jump".equals(selectedAction) && !shouldWalk) {
            PokeAlertClient.LOGGER.debug("Human behavior: Jumping while moving");
            sendNotification("Egg Hatcher", "Jumping while moving", Formatting.GRAY);
            // Start periodic jump execution (every 100ms) for natural sprint+jump behavior
            startJumpingWhileMoving();
        } else if (selectedAction == null) {
            // Default pathing - no human-like action selected
            sendNotification("Egg Hatcher", "Default pathing executed", Formatting.GRAY);
        }
        
        // Start timeout timer
        locationTimeoutTask = scheduler.schedule(() -> {
            handleLocationTimeout();
        }, config.locationTimeout, TimeUnit.MILLISECONDS);
    }
    
    /**
     * v3.0.0: Handle path broken (Baritone stopped pathing or player stuck)
     */
    private void handlePathBroken() {
        if (!antiAfkActive) return;
        
        PokeAlertClient.LOGGER.warn("Baritone path broke or player stuck - moving to next location immediately");
        
        // Cancel timeout (no need to wait)
        if (locationTimeoutTask != null) {
            locationTimeoutTask.cancel(false);
            locationTimeoutTask = null;
        }
        
        // Cancel progress check task
        if (progressCheckTask != null) {
            progressCheckTask.cancel(false);
            progressCheckTask = null;
        }
        
        // Mark path complete (Baritone already stopped or player stuck)
        BaritoneController.markPathComplete();
        CoordinateMonitor.clearDestination();
        arrivalProcessed.set(false);
        stopJumping();
        
        // Mark timeout and advance (but this is acceptable - path broke or player stuck)
        boolean shouldContinue = locationQueue.markTimeoutAndAdvance();
        
        if (!shouldContinue) {
            // Too many consecutive timeouts (even from path breaks/stuck)
            SafetyManager.triggerSafetyStop(SafetyManager.REASON_TIMEOUT, true);
            stopBaritoneAntiAfk();
            return;
        }
        
        // Move to next location immediately (use stored action if available)
        final String storedAction = currentNavigationAction;
        scheduler.schedule(() -> {
            if (antiAfkActive && !SafetyManager.isSafetyTriggered() && !BaritoneController.isPathing()) {
                navigateToNextLocation(storedAction);
            }
        }, 100, TimeUnit.MILLISECONDS); // Small delay to ensure Baritone stopped
    }
    
    /**
     * v3.0.0: Check if player is making progress toward destination
     * Detects when player is stuck (Baritone pathing but player not moving)
     */
    private void checkMovementProgress(int[] destination) {
        if (!antiAfkActive || !BaritoneController.isPathing() || destination == null) {
            return;
        }
        
        if (client.player == null || lastProgressCheckPosition == null) {
            return;
        }
        
        int currentX = (int) client.player.getX();
        int currentZ = (int) client.player.getZ();
        
        // Calculate distance to destination from last check position
        double lastDistance = Math.sqrt(
            Math.pow(lastProgressCheckPosition[0] - destination[0], 2) +
            Math.pow(lastProgressCheckPosition[1] - destination[1], 2)
        );
        
        // Calculate current distance to destination
        double currentDistance = Math.sqrt(
            Math.pow(currentX - destination[0], 2) +
            Math.pow(currentZ - destination[1], 2)
        );
        
        // If no progress made (distance didn't decrease significantly)
        if (currentDistance >= lastDistance - MIN_PROGRESS_THRESHOLD) {
            // Player is stuck - Baritone is pathing but player isn't moving
            PokeAlertClient.LOGGER.warn("Player stuck - no progress toward destination (Baritone pathing but player not moving)");
            
            // Cancel progress check task
            if (progressCheckTask != null) {
                progressCheckTask.cancel(false);
                progressCheckTask = null;
            }
            
            // Handle as path broken (player stuck)
            handlePathBroken();
            return;
        }
        
        // Update last check position
        lastProgressCheckPosition[0] = currentX;
        lastProgressCheckPosition[1] = currentZ;
        lastProgressCheckTime = System.currentTimeMillis();
    }
    
    /**
     * v3.0.0: Handle location timeout
     */
    private void handleLocationTimeout() {
        if (!antiAfkActive) return;
        
        // CRITICAL: Check if arrival was already detected (race condition protection)
        // If arrival callback already processed, don't trigger timeout
        if (arrivalProcessed.get()) {
            PokeAlertClient.LOGGER.debug("Location timeout: Arrival already processed, skipping timeout");
            return;
        }
        
        // CRITICAL: Check if we're actually at the destination (calculate distance directly)
        // Don't rely on hasArrived() which requires hasDestination to be true
        int[] currentDest = locationQueue.getCurrentDestination();
        if (currentDest != null && client.player != null) {
            int currentX = (int) client.player.getX();
            int currentZ = (int) client.player.getZ();
            double distance = Math.sqrt(
                Math.pow(currentX - currentDest[0], 2) + 
                Math.pow(currentZ - currentDest[1], 2)
            );
            
            // Get arrival threshold from config
            PokeAlertConfig config = ConfigManager.getConfig();
            if (distance <= config.arrivalThreshold) {
                PokeAlertClient.LOGGER.warn("Location timeout: Actually at destination but arrival callback didn't fire - treating as arrival");
                // Mark as arrived and continue to next location
                BaritoneController.markPathComplete();
                CoordinateMonitor.clearDestination();
                arrivalProcessed.set(false);
                stopJumping();
                currentNavigationAction = null;
                
                // Cancel progress check task
                if (progressCheckTask != null) {
                    progressCheckTask.cancel(false);
                    progressCheckTask = null;
                }
                
                locationQueue.markVisitedAndAdvance();
                if (locationQueue.shouldTriggerStep5()) {
                    locationQueue.markStep5Completed();
                    completeAutomation();
                }
                // Continue to next location
                navigateToNextLocation(null);
                return;
            }
        }
        
        // Check if Baritone stopped pathing (path broke)
        Boolean antiAfkState = AntiAfkManager.getAntiAfkState();
        if (antiAfkState != null && !antiAfkState && BaritoneController.isPathing()) {
            PokeAlertClient.LOGGER.warn("Location timeout: Anti-AFK state shows Baritone stopped - path broke");
            handlePathBroken();
            return;
        }
        
        PokeAlertClient.LOGGER.warn("⏱️ Location timeout - skipping to next");
        sendNotification("Egg Hatcher", "Location timeout - Skipping", Formatting.YELLOW);
        
        // Mark timeout and check if we should stop
        boolean shouldContinue = locationQueue.markTimeoutAndAdvance();
        
        if (!shouldContinue) {
            // Too many consecutive timeouts
            SafetyManager.triggerSafetyStop(SafetyManager.REASON_TIMEOUT, true);
            stopBaritoneAntiAfk();
            return;
        }
        
        // Stop current path and try next location
        BaritoneController.stop();
        
        // Small delay before next navigation (check if Baritone is already pathing to prevent duplicate calls)
        // IMPORTANT: Use stored action instead of null to prevent re-rolling on timeout
        // If no stored action, it means this was a fresh navigation (not from timeout), so roll is OK
        final String storedAction = currentNavigationAction;
        scheduler.schedule(() -> {
            if (antiAfkActive && !SafetyManager.isSafetyTriggered() && !BaritoneController.isPathing()) {
                // Use stored action if available, otherwise roll for new action
                navigateToNextLocation(storedAction);
            } else {
                PokeAlertClient.LOGGER.debug("Skipping timeout navigation - already pathing");
            }
        }, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * v3.0.0: Stop Baritone Anti-AFK system
     */
    private void stopBaritoneAntiAfk() {
        antiAfkActive = false;
        
        // Clear break state
        setBreakState(false);
        
        // Stop jumping
        stopJumping();
        
        // Stop camera rotation if active
        if (cameraRotationTask != null) {
            cameraRotationTask.cancel(false);
            cameraRotationTask = null;
        }
        isCameraRotating = false;
        
        // Cancel all behavior tasks
        if (breakStateTask != null) {
            breakStateTask.cancel(false);
            breakStateTask = null;
        }
        if (cameraRotationTask != null) {
            cameraRotationTask.cancel(false);
            cameraRotationTask = null;
        }
        
        // Cancel timeout
        if (locationTimeoutTask != null) {
            locationTimeoutTask.cancel(false);
            locationTimeoutTask = null;
        }
        
        // Cancel progress check task
        if (progressCheckTask != null) {
            progressCheckTask.cancel(false);
            progressCheckTask = null;
        }
        lastProgressCheckPosition = null;
        lastProgressCheckTime = 0;
        
        // Stop all systems
        BaritoneController.stop();
        CoordinateMonitor.stopMonitoring();
        PlayerMonitor.stopMonitoring();
        
        PokeAlertClient.LOGGER.info("🛑 Baritone Anti-AFK stopped");
    }
    
    // v3.0.0: retryStep5() REMOVED - no longer using external Anti-AFK keybind
    // v3.0.0: toggleAntiAfk() REMOVED - now using Baritone #goto commands
    
    /**
     * v3.0.0: Human-like behavior helper methods
     */
    
    /**
     * Set break state flag to prevent false anti-AFK detection
     * @param inBreak true if entering break, false if exiting
     */
    private void setBreakState(boolean inBreak) {
        isInBreakState = inBreak;
        PokeAlertClient.LOGGER.debug("Break state: {}", inBreak ? "ACTIVE" : "INACTIVE");
    }
    
    /**
     * Simulate smooth camera rotation (looking around) during breaks
     * Uses interpolation for smooth mouse-like movement
     */
    private void simulateCameraRotation(int durationMs) {
        if (cameraRotationTask != null) {
            cameraRotationTask.cancel(false);
        }
        
        if (client.player == null) return;
        
        // Start from current position
        final float[] startYaw = {client.player.getYaw()};
        final float[] startPitch = {client.player.getPitch()};
        
        // Target rotation (random within reasonable range)
        final float[] targetYaw = {startYaw[0] + (behaviorRandom.nextFloat() - 0.5f) * 120f}; // ±60 degrees
        final float[] targetPitch = {Math.max(-90, Math.min(90, startPitch[0] + (behaviorRandom.nextFloat() - 0.5f) * 60f))}; // ±30 degrees
        
        // Current interpolation progress (0.0 to 1.0)
        final float[] progress = {0.0f};
        
        // Update interval (every 50ms for smooth movement)
        final long updateInterval = 50;
        final int totalSteps = (int)(durationMs / updateInterval);
        final float stepSize = 1.0f / totalSteps;
        
        // Mark camera rotation as active
        isCameraRotating = true;
        
        // Smooth interpolation using ease-in-out curve
        cameraRotationTask = scheduler.scheduleAtFixedRate(() -> {
            if (client.player == null) {
                if (cameraRotationTask != null) {
                    cameraRotationTask.cancel(false);
                    cameraRotationTask = null;
                }
                isCameraRotating = false;
                    return;
                }
                
            progress[0] += stepSize;
            if (progress[0] > 1.0f) progress[0] = 1.0f;
            
            // Ease-in-out curve for smooth acceleration/deceleration
            float eased = progress[0] < 0.5f 
                ? 2 * progress[0] * progress[0] 
                : 1 - (float)Math.pow(-2 * progress[0] + 2, 2) / 2;
            
            // Interpolate between start and target
            float currentYaw = startYaw[0] + (targetYaw[0] - startYaw[0]) * eased;
            float currentPitch = startPitch[0] + (targetPitch[0] - startPitch[0]) * eased;
            
            // Normalize yaw to -180 to 180 range
            while (currentYaw > 180) currentYaw -= 360;
            while (currentYaw < -180) currentYaw += 360;
            
            // Store in final variables for lambda
            final float finalYaw = currentYaw;
            final float finalPitch = currentPitch;
            
            client.execute(() -> {
                if (client.player != null) {
                    client.player.setYaw(finalYaw);
                    client.player.setPitch(finalPitch);
                }
            });
            
            // If we've reached the target, pick a new target
            if (progress[0] >= 1.0f) {
                startYaw[0] = currentYaw;
                startPitch[0] = currentPitch;
                targetYaw[0] = startYaw[0] + (behaviorRandom.nextFloat() - 0.5f) * 120f;
                targetPitch[0] = Math.max(-90, Math.min(90, startPitch[0] + (behaviorRandom.nextFloat() - 0.5f) * 60f));
                progress[0] = 0.0f;
            }
        }, 0, updateInterval, TimeUnit.MILLISECONDS);
        
        // Stop after duration
        scheduler.schedule(() -> {
            if (cameraRotationTask != null) {
                cameraRotationTask.cancel(false);
                cameraRotationTask = null;
            }
            isCameraRotating = false;
        }, durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Switch to a random hotbar slot (1-9)
     */
    private void switchHotbarSlot() {
        if (client.player == null) return;
        
        int slot = behaviorRandom.nextInt(9); // 0-8 (slots 1-9)
        client.execute(() -> {
            if (client.player != null) {
                client.player.getInventory().selectedSlot = slot;
                PokeAlertClient.LOGGER.debug("Switched to hotbar slot {}", slot + 1);
            }
        });
    }
    
    /**
     * Switch to a specific hotbar slot (for notification display)
     */
    private void switchHotbarSlot(int slot) {
        if (client.player == null) return;
        
        client.execute(() -> {
            if (client.player != null) {
                client.player.getInventory().selectedSlot = slot;
                PokeAlertClient.LOGGER.debug("Switched to hotbar slot {}", slot + 1);
            }
        });
    }
    
    /**
     * Enable jumping while Baritone is pathing with realistic sprint-jump timing
     * 
     * Strategy:
     * 1. Wait for Baritone to actually start moving (movement-based detection)
     * 2. Only jump when player is on ground (realistic sprint-jump pattern)
     * 3. Use direct `client.player.jump()` method call - bypasses Baritone's input handling
     * 4. Check every 150ms and only jump if on ground (prevents flying detection)
     * 5. Stop jumping when pathing stops (arrival callback handles this)
     * 
     * Note: Baritone's `#set allowjump` command does NOT exist, and key press simulation doesn't work
     * because Baritone overrides keyboard input. Using `client.player.jump()` bypasses this entirely.
     * We use realistic timing (only jump when on ground) to avoid anti-cheat detection.
     */
    private void startJumpingWhileMoving() {
        // Stop any existing jump task (cleanup)
        if (jumpRepeatTask != null) {
            jumpRepeatTask.cancel(false);
            jumpRepeatTask = null;
        }
        
        if (client.player == null) return;
        
        isJumpKeyHeld = true;
        KeyBinding jumpKey = client.options.jumpKey;
        
        // CRITICAL: Track initial position to detect when Baritone actually starts moving
        // This prevents jump from starting before Baritone processes the #goto command
        final double[] initialX = {client.player.getX()};
        final double[] initialZ = {client.player.getZ()};
        final long startTime = System.currentTimeMillis();
        final boolean[] movementDetected = {false};
        
        // Track last position to detect if player is still moving (prevents jumping when stuck)
        final double[] lastX = {client.player.getX()};
        final double[] lastZ = {client.player.getZ()};
        final long[] lastMovementTime = {System.currentTimeMillis()};
        
        // Start periodic jump execution - but wait for movement first
        // Use 150ms interval for realistic sprint-jump timing (not every tick)
        jumpRepeatTask = scheduler.scheduleAtFixedRate(() -> {
            // Check exit conditions
            if (!antiAfkActive || !isJumpKeyHeld || client.player == null || !BaritoneController.isPathing()) {
                // Release jump key before stopping
                if (jumpKey != null) {
                    client.execute(() -> {
                        if (jumpKey != null) {
                            jumpKey.setPressed(false);
                        }
                    });
                }
                if (jumpRepeatTask != null) {
                    jumpRepeatTask.cancel(false);
                    jumpRepeatTask = null;
                }
                isJumpKeyHeld = false;
                PokeAlertClient.LOGGER.debug("Jump task: Stopping (pathing stopped or conditions failed)");
                return;
            }
            
            // CRITICAL: Wait for Baritone to actually start moving before jumping
            // Check if player has moved at least 0.5 blocks from initial position
            if (!movementDetected[0]) {
                double currentX = client.player.getX();
                double currentZ = client.player.getZ();
                double distanceMoved = Math.sqrt(
                    Math.pow(currentX - initialX[0], 2) + 
                    Math.pow(currentZ - initialZ[0], 2)
                );
                
                // Also check timeout: if 2 seconds pass without movement, start jumping anyway (Baritone might be stuck)
                long elapsed = System.currentTimeMillis() - startTime;
                if (distanceMoved >= 0.5 || elapsed > 2000) {
                    movementDetected[0] = true;
                    lastX[0] = currentX;
                    lastZ[0] = currentZ;
                    lastMovementTime[0] = System.currentTimeMillis();
                    PokeAlertClient.LOGGER.info("Jump task: Movement detected (distance={}, elapsed={}ms) - starting sprint-jump", 
                        String.format("%.2f", distanceMoved), elapsed);
                } else {
                    // Not moving yet, skip this iteration
                    return;
                }
            }
            
            // CRITICAL: Check if player is still moving (not stuck)
            // If player hasn't moved in the last 500ms, don't jump (Baritone might be stuck)
            double currentX = client.player.getX();
            double currentZ = client.player.getZ();
            double distanceSinceLastCheck = Math.sqrt(
                Math.pow(currentX - lastX[0], 2) + 
                Math.pow(currentZ - lastZ[0], 2)
            );
            
            // Update last position and movement time
            boolean isMoving = distanceSinceLastCheck >= 0.1; // Moved at least 0.1 blocks (10cm)
            if (isMoving) {
                lastX[0] = currentX;
                lastZ[0] = currentZ;
                lastMovementTime[0] = System.currentTimeMillis();
            } else {
                // Check if we've been stationary for too long (500ms)
                long timeSinceLastMovement = System.currentTimeMillis() - lastMovementTime[0];
                if (timeSinceLastMovement > 500) {
                    PokeAlertClient.LOGGER.debug("Jump task: Player stationary for {}ms - skipping jump (Baritone may be stuck)", timeSinceLastMovement);
                    return; // Don't jump if stuck
                }
            }
            
            // CRITICAL: Only jump when on ground AND moving - realistic sprint-jump pattern
            // This prevents flying detection and prevents jumping when Baritone gets stuck
            client.execute(() -> {
                if (isJumpKeyHeld && BaritoneController.isPathing() && client.player != null && jumpKey != null) {
                    // Only jump if player is on ground AND moving (not stuck)
                    if (client.player.isOnGround() && isMoving) {
                        // Hold jump key briefly
                        jumpKey.setPressed(true);
                        // Call jump() - only executes when on ground and moving
                        client.player.jump();
                        // Release jump key after a short delay (simulates key press, not hold)
                        scheduler.schedule(() -> {
                            if (jumpKey != null && isJumpKeyHeld) {
                                client.execute(() -> {
                                    if (jumpKey != null) {
                                        jumpKey.setPressed(false);
                                    }
                                });
                            }
                        }, 50, TimeUnit.MILLISECONDS);
                    }
                }
            });
        }, 0, 150, TimeUnit.MILLISECONDS); // 150ms interval for realistic sprint-jump timing
        
        PokeAlertClient.LOGGER.info("Jump while moving: Enabled - realistic sprint-jump timing (waiting for movement)");
    }
    
    /**
     * Stop jumping (release jump key and stop task)
     */
    private void stopJumping() {
        isJumpKeyHeld = false;
        recentPositions.clear(); // Clear movement tracking
        
        if (jumpRepeatTask != null) {
            jumpRepeatTask.cancel(false);
            jumpRepeatTask = null;
        }
        
        if (jumpTask != null) {
            jumpTask.cancel(false);
            jumpTask = null;
        }
        
        // Release jump key
        if (client.player != null) {
            KeyBinding jumpKey = client.options.jumpKey;
            client.execute(() -> {
                if (jumpKey != null) {
                    jumpKey.setPressed(false);
                }
            });
        }
        
        PokeAlertClient.LOGGER.debug("Jump while moving: Disabled");
    }
    
    /**
     * Send a chat command
     */
    private void sendChatCommand(String command) {
        if (client.player != null) {
            client.execute(() -> {
                // Open chat screen and send command
                client.player.networkHandler.sendChatCommand(command.substring(1)); // Remove the '/'
            });
        }
    }
    
    /**
     * Start the player suspicion monitor
     * Checks if player is in top N of tab list (admin suspicion detection)
     */
    private void startPlayerSuspicionMonitor() {
        // Cancel any existing monitor
        if (playerSuspicionMonitorTask != null && !playerSuspicionMonitorTask.isDone()) {
            playerSuspicionMonitorTask.cancel(false);
        }
        
        // Fixed 1 minute interval for PlayerSuspicionMonitor
        int suspicionCheckInterval = 60000; // 1 minute (60 seconds)
        
        PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Starting suspicion monitor (check interval: {}ms / 1 minute)", suspicionCheckInterval);
        
        playerSuspicionMonitorTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                // Refresh config for each check
                PokeAlertConfig config = ConfigManager.getConfig();
                
                // Always check position regardless of mode (security check)
                // Skip checks during resource pack loading
                if (isResourcePackLoading) {
                    PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Skipping check - resource pack loading");
                    return;
                }
                
                // Check if player is in top N of tab list (admin suspicion detection)
                int topNThreshold = config.playerSuspicionTopNThreshold;
                PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Running position check (threshold: top {}, mode: {})", 
                    topNThreshold, mode);
                boolean inTopN = PlayerMonitor.isPlayerInTopN(topNThreshold);
                if (inTopN) {
                    PokeAlertClient.LOGGER.warn("[PlayerSuspicionMonitor] 🚨 CRITICAL: Player detected in TOP {} of tab list - Disabling Egg Hatcher and force closing game!", topNThreshold);
                    
                    // Check if automation was running before stopping
                    boolean wasRunning = isAutomationRunning || antiAfkActive || currentState != State.IDLE || spawnDetectionTime > 0;
                    
                    // Disable automation completely
                    mode = AutomationMode.DISABLED;
                    PokeAlertConfig currentConfig = ConfigManager.getConfig();
                    currentConfig.eggHatcherEnabled = false;
                    ConfigManager.saveSettings(currentConfig);
                    
                    // Stop all automation (will show "stopped and disabled" if was running)
                    PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Stopping all Egg Hatcher automation...");
                    stopAutomation();
                    
                    // Only show separate notification if automation wasn't running
                    // (if it was running, stopAutomation() already showed "stopped and disabled")
                    if (!wasRunning) {
                        sendNotification("Egg Hatcher", "⚠️ Disabled: Detected in top " + topNThreshold + " of tab list", Formatting.RED);
                    }
                    
                    // Send Telegram notification
                    PokeAlertConfig telegramConfig = ConfigManager.getConfig();
                    if (telegramConfig.telegramEnabled && telegramConfig.isTelegramValid()) {
                        CompletableFuture.runAsync(() -> {
                            try {
                                TelegramNotification telegram = new TelegramNotification();
                                telegram.initialize();
                                
                                StringBuilder message = new StringBuilder();
                                message.append("🚨 <b>Player Suspicion Alert</b>\n");
                                message.append("• <b>Status:</b> <i>You are being watched!</i>\n");
                                message.append("• <b>Detection:</b> You are in the <b>TOP ").append(topNThreshold).append("</b> of the server tab list\n");
                                message.append("• <b>Action:</b> Egg Hatcher has been <b>disabled</b> for safety\n");
                                message.append("• <b>Reason:</b> Admins typically monitor players in top positions\n");
                                message.append("• <b>Game:</b> Force closing game for security\n");
                                message.append("\n⚠️ <i>Manual restart required after situation clears</i>");
                                
                                telegram.sendEggTimerNotification(message.toString());
                                PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Telegram notification sent - Player suspicion alert");
                                
                                // Wait 2 seconds for Telegram to send before force closing
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                
                                // Force close the game after Telegram notification
                                PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] 🚨 Forcing game shutdown due to suspicion detection...");
                                System.exit(1); // Force immediate shutdown
                            } catch (Exception e) {
                                PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] Failed to send Telegram notification", e);
                                // Still force close even if Telegram fails
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] 🚨 Forcing game shutdown...");
                                System.exit(1);
                            }
                        });
                    } else {
                        // No Telegram configured - force close immediately
                        PokeAlertClient.LOGGER.warn("[PlayerSuspicionMonitor] Telegram not configured - force closing immediately");
                        try {
                            Thread.sleep(1000); // Brief delay for logs
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] 🚨 Forcing game shutdown...");
                        System.exit(1);
                    }
                } else {
                    int currentPosition = PlayerMonitor.getPlayerPositionInTabList();
                    if (currentPosition >= 0) {
                        PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Player not in top N of tab list (current position: {} / 1-indexed: {})", 
                            currentPosition, currentPosition + 1);
                    } else {
                        PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Player not in top N of tab list (position unknown)");
                    }
                }
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] Error in player suspicion monitor", e);
            }
        }, 5000, suspicionCheckInterval, TimeUnit.MILLISECONDS);
        
        PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Player suspicion monitor started (check interval: {}ms / 1 minute)", 
            suspicionCheckInterval);
    }
    
    /**
     * Start the safety monitor
     * Checks realm state consistency, Baritone pathing, and Anti-AFK state
     */
    private void startSafetyMonitor() {
        // Cancel any existing monitor
        if (safetyMonitorTask != null && !safetyMonitorTask.isDone()) {
            safetyMonitorTask.cancel(false);
        }
        
        // Safety checks run every 5 seconds
        int safetyCheckInterval = 5000; // 5 seconds
        
        PokeAlertClient.LOGGER.info("Starting safety monitor (check interval: {}ms)", safetyCheckInterval);
        
        safetyMonitorTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                String location = isAtSpawn() ? "spawn" : "overworld";
                
                // v3.0.0: Check if Anti-AFK should be running at overworld
                if (!isAtSpawn() && antiAfkActive && !isInBreakState && !isCameraRotating) {
                    // We're at overworld with Anti-AFK active - verify Baritone is pathing
                    // Skip check if in break state (intentional pause) or camera rotating (normal behavior)
                    if (!BaritoneController.isPathing() && !SafetyManager.isSafetyTriggered()) {
                        PokeAlertClient.LOGGER.warn("⚠️ Safety: Baritone not pathing at overworld, restarting navigation");
                        navigateToNextLocation(null);
                    }
                }
                
                // v3.0.0: If at spawn but Anti-AFK thinks it's active, stop it
                if (isAtSpawn() && antiAfkActive) {
                    PokeAlertClient.LOGGER.warn("⚠️ Safety: Anti-AFK active at spawn - stopping");
                    stopBaritoneAntiAfk();
                }
                
                PokeAlertClient.LOGGER.debug("🛡️ Safety check: location={}, antiAfkActive={}", 
                    location, antiAfkActive);
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("Error in safety monitor", e);
            }
        }, 5000, safetyCheckInterval, TimeUnit.MILLISECONDS);
        
        PokeAlertClient.LOGGER.info("Safety monitor started (check interval: {}ms)", safetyCheckInterval);
    }
    
    /**
     * Stop the safety monitor
     * Note: PlayerSuspicionMonitor is NOT stopped here - it runs independently as a security check
     */
    private void stopSafetyMonitor() {
        if (safetyMonitorTask != null && !safetyMonitorTask.isDone()) {
            safetyMonitorTask.cancel(false);
            safetyMonitorTask = null;
            PokeAlertClient.LOGGER.info("Safety monitor stopped");
        }
        // PlayerSuspicionMonitor continues running - it's a security check that should not be stopped during teleports
    }
    
    /**
     * Stop the player suspicion monitor
     */
    private void stopPlayerSuspicionMonitor() {
        if (playerSuspicionMonitorTask != null && !playerSuspicionMonitorTask.isDone()) {
            playerSuspicionMonitorTask.cancel(false);
            playerSuspicionMonitorTask = null;
            PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Player suspicion monitor stopped");
        }
    }
    
    /**
     * Cancel all pending automation step tasks
     * CRITICAL: Prevents old scheduled tasks from executing after restart
     */
    private void cancelAllAutomationTasks() {
        int cancelledCount = 0;
        
        if (step1Task != null && !step1Task.isDone()) {
            step1Task.cancel(false);
            step1Task = null;
            cancelledCount++;
        }
        if (step2Task != null && !step2Task.isDone()) {
            step2Task.cancel(false);
            step2Task = null;
            cancelledCount++;
        }
        if (step3Task != null && !step3Task.isDone()) {
            step3Task.cancel(false);
            step3Task = null;
            cancelledCount++;
        }
        if (step4Task != null && !step4Task.isDone()) {
            step4Task.cancel(false);
            step4Task = null;
            cancelledCount++;
        }
        if (step5Task != null && !step5Task.isDone()) {
            step5Task.cancel(false);
            step5Task = null;
            cancelledCount++;
        }
        
        if (cancelledCount > 0) {
            PokeAlertClient.LOGGER.info("Cancelled " + cancelledCount + " pending automation tasks");
        }
    }
    
    /**
     * v3.0.0: Complete the automation (Step 5)
     * Note: Anti-AFK continues running after completion - this is just a checkpoint
     */
    private void completeAutomation() {
        currentState = State.COMPLETED;
        // v3.0.0: DON'T set isAutomationRunning = false - Anti-AFK continues!
        
        // Cancel stuck detector (we're successfully in overworld now)
        if (stuckDetector != null) {
            stuckDetector.cancel(false);
            stuckDetector = null;
        }
        
        // Reset flags
        antiAfkDisabledOnReconnect = false;
        
        // Step 5: Completion (was Step 6 in v2.0.0)
        // v3.0.0: This is a checkpoint, Anti-AFK continues!
        String location = AntiAfkManager.getPlayerLocationInfo();
        sendNotification("Egg Hatcher [5/5]", "Completion: Anti-AFK active ✓", Formatting.GREEN);
        PokeAlertClient.LOGGER.info("🎯 Step 5/5: Completion - {} locations visited, Anti-AFK continues at " + location, 
            locationQueue.getVisitedCount());
        
        // Send Telegram notification for ALL successful completions
        // Include journey type in message (already handled in sendTelegramNotification)
        long duration = (System.currentTimeMillis() - automationStartTime) / 1000;
        sendTelegramNotification(true, duration);
        
        // v3.0.0: Anti-AFK keeps running - navigateToNextLocation will be called by arrival callback
        PokeAlertClient.LOGGER.info("✅ Automation checkpoint reached - Anti-AFK continues perpetually");
    }
    
    /**
     * Handle being stuck at spawn - sends Telegram alert and force closes game
     */
    private void handleStuckAtSpawn() {
        isAutomationRunning = false;
        
        // Don't reset journey tracking flags here - they should persist until Telegram notification is sent
        // Flags are reset in sendTelegramNotification() after sending, or in stopAutomation() if no notification
        
        // Stop Anti-AFK state monitoring
        AntiAfkManager.stopStateMonitoring();
        
        // Stop safety monitor
        stopSafetyMonitor();
        
        // Cancel all automation tasks
        cancelAllAutomationTasks();
        
        sendNotification("Egg Hatcher", 
            "CRITICAL: Stuck at spawn for over 2 minutes - Closing game", 
            Formatting.RED);
        
        PokeAlertClient.LOGGER.error("❌ CRITICAL: Stuck at spawn for 2 minutes - sending Telegram alert and force closing game");
        
        // Send Telegram alert FIRST before closing
        sendTelegramNotification(false, 120);
        
        // Wait 2 seconds for Telegram to send
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Force close the game
        PokeAlertClient.LOGGER.error("🚨 Forcing game shutdown...");
        System.exit(1); // Force immediate shutdown
    }
    
    /**
     * Stop all automation tasks
     * v3.0.0: Also stops Baritone Anti-AFK
     */
    public void stopAutomation() {
        // Check if automation was actually running
        boolean wasRunning = isAutomationRunning || antiAfkActive || currentState != State.IDLE || spawnDetectionTime > 0;
        
        isAutomationRunning = false;
        currentState = State.CANCELLED;
        spawnDetectionTime = 0;
        step5RetryCount = 0;
        manuallyCancelled = true;
        
        // Reset journey tracking flags (backup - in case notification wasn't sent)
        // Primary reset is in sendTelegramNotification(), but this ensures flags are cleared
        hadDisconnect = false;
        hadOverworldCrash = false;
        startedInOverworld = false;
        
        // Clear stored navigation action
        currentNavigationAction = null;
        
        // Reset arrival processed flag
        arrivalProcessed.set(false);
        
        // v3.0.0: Stop Baritone Anti-AFK
        stopBaritoneAntiAfk();
        
        // Stop Anti-AFK state monitoring (legacy, kept for compatibility)
        AntiAfkManager.stopStateMonitoring();
        
        // Stop safety monitor
        stopSafetyMonitor();
        
        // Cancel ALL automation tasks
        cancelAllAutomationTasks();
        
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
        
        if (stuckDetector != null) {
            stuckDetector.cancel(false);
            stuckDetector = null;
        }
        
        if (automationDelayTask != null) {
            automationDelayTask.cancel(false);
            automationDelayTask = null;
        }
        
        // Reset state to IDLE after cleanup
        currentState = State.IDLE;
        
        // Send notification if we actually cancelled something
        if (wasRunning) {
            // Check if mode is DISABLED - if so, show "stopped and disabled" in one line
            if (mode == AutomationMode.DISABLED) {
                sendNotification("Egg Hatcher", "Stopped and disabled", Formatting.RED);
            } else {
                // Automation stopped but not disabled (e.g., error recovery, manual cancel)
                sendNotification("Egg Hatcher", "Automation stopped", Formatting.YELLOW);
            }
        } else {
            // Automation wasn't running - only send notification if being disabled
            // This handles the case where user disables without automation running
            if (mode == AutomationMode.DISABLED) {
                sendNotification("Egg Hatcher", "Disabled", Formatting.RED);
            }
        }
    }
    
    /**
     * Send in-game notification with standardized [PokeAlert] prefix
     */
    /**
     * Format notification message: capitalize after "- ", clean up multiple dots/colons
     */
    private String formatNotificationMessage(String message) {
        if (message == null || message.isEmpty()) return message;
        
        // Split by " - " and capitalize first letter after dash
        String[] parts = message.split(" - ", 2);
        if (parts.length == 2) {
            String afterDash = parts[1].trim();
            if (!afterDash.isEmpty()) {
                afterDash = Character.toUpperCase(afterDash.charAt(0)) + afterDash.substring(1);
            }
            message = parts[0] + " - " + afterDash;
        }
        
        // Clean up multiple dots/colons (replace "..." with ".", "::" with ":")
        message = message.replaceAll("\\.{2,}", ".");
        message = message.replaceAll(":{2,}", ":");
        
        return message;
    }
    
    private void sendNotification(String title, String message, Formatting color) {
        if (client.player != null) {
            PokeAlertConfig config = PokeAlertClient.getInstance().config;
            if (config.inGameTextEnabled) {
                String formattedMessage = formatNotificationMessage(message);
                Text notification = Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal(title + ": ").formatted(Formatting.WHITE))
                    .append(Text.literal(formattedMessage).formatted(color));
                
                client.player.sendMessage(notification, false);
            }
        }
    }
    
    /**
     * Send Telegram notification
     * 
     * Notification Rules:
     * - If automation started from spawn/step 1 (startedFromSpawn == true) → send notification on successful completion
     * - If automation started from overworld/step 4 (startedInOverworld == true) → skip notification on successful completion
     * - Any other notification (stuck at spawn, failures, etc.) → send regardless of start location
     */
    private void sendTelegramNotification(boolean success, long durationSeconds) {
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        // Check both telegramEnabled and isTelegramValid
        if (!config.telegramEnabled || !config.isTelegramValid()) {
            PokeAlertClient.LOGGER.debug("Telegram notification skipped - not enabled or not configured");
            return;
        }
        
        // Skip notification if automation started from overworld/step 4 AND it's a successful completion
        // This prevents notifications when user manually starts Anti-AFK in overworld
        if (success && startedInOverworld) {
            PokeAlertClient.LOGGER.debug("Telegram notification skipped - automation started from overworld/step 4 (successful completion)");
            // Still reset flags even if we skip notification
            hadDisconnect = false;
            hadOverworldCrash = false;
            startedInOverworld = false;
            startedFromSpawn = false;
            return;
        }
        
        // All other cases: send notification (spawn starts, failures, stuck at spawn, etc.)
            CompletableFuture.runAsync(() -> {
            try {
                TelegramNotification telegram = new TelegramNotification();
                telegram.initialize();
                
                StringBuilder message = new StringBuilder();
                message.append("🥚 <b>Egg Hatcher</b>\n");
                
                // Status line
                if (success) {
                    message.append("• <b>Status:</b> <i>Successful</i>\n");
                } else {
                    message.append("• <b>Status:</b> <i>Unsuccessful</i> 🚨\n");
                }
                
                // Journey line - detect journey type
                String journey;
                if (hadDisconnect) {
                    journey = "Disconnect → Reconnect";
                } else if (hadOverworldCrash) {
                    journey = "Overworld crash → Spawn → Overworld";
                } else if (startedFromSpawn) {
                    journey = "Spawn → Overworld";
                } else {
                    journey = "Overworld (direct)";
                }
                message.append("• <b>Journey:</b> ").append(journey).append("\n");
                
                // Include avoided players info on successful completion
                if (success) {
                    List<String> onlineAvoided = PlayerMonitor.getCurrentOnlineAvoidedPlayers();
                    if (!onlineAvoided.isEmpty()) {
                        message.append("• <b>OPs Online:</b> <code>").append(String.join(",", onlineAvoided)).append("</code>\n");
                    }
                }
                
                telegram.sendEggTimerNotification(message.toString());
                PokeAlertClient.LOGGER.info("EggHatcher: Telegram notification sent (success={}, duration={}s)", success, durationSeconds);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("EggHatcher: Failed to send Telegram notification", e);
            } finally {
                // Reset journey tracking flags AFTER notification attempt (success or failure)
                // This ensures flags are always reset, even if notification fails
                hadDisconnect = false;
                hadOverworldCrash = false;
                startedInOverworld = false;
                startedFromSpawn = false;
            }
        });
    }
    
    /**
     * Get current automation status
     * v3.0.0: Includes Baritone Anti-AFK status
     */
    public String getStatus() {
        if (mode == AutomationMode.DISABLED) {
            return "Disabled";
        }
        
        String statusStr = "Enabled";
        
        // v3.0.0: Show Anti-AFK status if active
        if (antiAfkActive) {
            return statusStr + " | Anti-AFK: " + locationQueue.getStatus();
        }
        
        if (isAutomationRunning) {
            return statusStr + " | Running: " + currentState.name();
        }
        
        // Check for active countdown
        long currentTime = System.currentTimeMillis();
        if (mode == AutomationMode.AUTO && spawnDetectionTime > 0) {
            long remaining = (REALM_SWITCH_BUFFER - (currentTime - spawnDetectionTime)) / 1000;
            if (remaining > 0) {
                return statusStr + " | Server Buffer: " + remaining + "s";
            }
        }
        
        return statusStr + " | Monitoring";
    }
    
    /**
     * v3.0.0: Check if Baritone Anti-AFK is currently active
     */
    public boolean isAntiAfkActive() {
        return antiAfkActive;
    }
    
    /**
     * Get current mode
     */
    public AutomationMode getMode() {
        return mode;
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        stopAutomation();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
