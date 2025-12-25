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
import net.minecraft.client.option.GameOptions;
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
        
        // Prevent mode change during active automation
        if (isAutomationRunning) {
            sendNotification("Egg Hatcher", "Cannot change mode during automation", Formatting.RED);
            PokeAlertClient.LOGGER.warn("Mode change blocked - automation is running");
            return;
        }
        
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
            PokeAlertClient.LOGGER.info("🔍 Anti-AFK state monitoring restarted for mode toggle");
            
            PokeAlertClient.LOGGER.info("Mode changed to AUTO - all session flags reset (fresh start)");
            
            sendNotification("Egg Hatcher", "Enabled", Formatting.GREEN);
            startMonitoring();
            
            // Start safety monitor if not already running
            if (safetyMonitorTask == null || safetyMonitorTask.isDone()) {
                PokeAlertClient.LOGGER.info("Starting safety monitor for AUTO mode");
                startSafetyMonitor();
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
            mode = AutomationMode.DISABLED;
            config.eggHatcherEnabled = false;
            ConfigManager.saveSettings(config);

            antiAfkDisabledOnReconnect = false;
            manuallyCancelled = false;
            spawnDetectionTime = 0;
            
            PokeAlertClient.LOGGER.info("Mode changed to DISABLED - all session flags reset");
            sendNotification("Egg Hatcher", "Disabled", Formatting.RED);
            stopAutomation();
        }
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
                    .append(Text.literal(" - Press Home to cancel").formatted(Formatting.GRAY));
                
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
            
            // Cancel timeout timer
            if (locationTimeoutTask != null) {
                locationTimeoutTask.cancel(false);
                locationTimeoutTask = null;
            }
            
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
                    PokeAlertClient.LOGGER.debug("Human behavior: Selected action '{}' with chance {} (lowest among successful rolls)", 
                        selectedAction, lowestChance);
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

                            setBreakState(true); // Skip anti-AFK checking during break
                            simulateCameraRotation(pauseMs);

                            scheduler.schedule(() -> {
                                setBreakState(false);
                                breakNotificationSent = false; // Reset notification flag
                                if (antiAfkActive && !SafetyManager.isSafetyTriggered()) {
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
        
        // Guard: Don't start new navigation if Baritone is already pathing
        // This prevents duplicate #goto commands from multiple callbacks (arrival, timeout, safety monitor)
        if (BaritoneController.isPathing()) {
            PokeAlertClient.LOGGER.debug("Baritone already pathing, skipping duplicate navigation");
            return;
        }
        
        // Guard: Don't start navigation if camera is rotating (look around action in progress)
        // Queue navigation to execute after camera rotation completes instead of skipping
        if (isCameraRotating) {
            PokeAlertClient.LOGGER.debug("Camera rotating (look around in progress), queuing navigation");
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
        
        int[] destination = locationQueue.getCurrentDestination();
        if (destination == null) {
            PokeAlertClient.LOGGER.error("❌ No destination available!");
            return;
        }

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
     * v3.0.0: Handle location timeout
     */
    private void handleLocationTimeout() {
        if (!antiAfkActive) return;
        
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
     * Enable jumping while Baritone is pathing using direct key press injection
     * 
     * Strategy:
     * 1. Use periodic key press injection (every 100ms) to trigger jumps
     * 2. Press and release jump key rapidly to simulate sprint-jumping
     * 3. This works even when Baritone controls movement by injecting at game engine level
     * 4. Stop jumping when pathing stops (arrival callback handles this)
     * 
     * Note: Baritone's `#set allowjump` command does NOT exist, so we use direct input injection
     */
    private void startJumpingWhileMoving() {
        // Stop any existing jump task (cleanup)
        if (jumpRepeatTask != null) {
            jumpRepeatTask.cancel(false);
            jumpRepeatTask = null;
        }
        
        if (client.player == null) return;
        
        isJumpKeyHeld = true;
        GameOptions options = client.options;
        KeyBinding jumpKey = options.jumpKey;
        
        // Start periodic jump execution - press and release jump key every 100ms
        // This simulates continuous sprint-jumping behavior
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
            
            // Press and release jump key to trigger jump
            // This works even when Baritone controls movement by injecting at the game engine level
            client.execute(() -> {
                if (jumpKey != null && isJumpKeyHeld && BaritoneController.isPathing() && client.player != null) {
                    // Press jump key
                    jumpKey.setPressed(true);
                    // Immediately release to trigger jump action
                    // The game will process the jump on the next tick
                }
            });
            
            // Release jump key after a short delay (allows jump to register)
                        scheduler.schedule(() -> {
                if (jumpKey != null && isJumpKeyHeld) {
                    client.execute(() -> {
                        if (jumpKey != null) {
                            jumpKey.setPressed(false);
                        }
                    });
                }
            }, 50, TimeUnit.MILLISECONDS);
        }, 0, 100, TimeUnit.MILLISECONDS);
        
        PokeAlertClient.LOGGER.debug("Jump while moving: Enabled via direct key press injection");
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
            GameOptions options = client.options;
            KeyBinding jumpKey = options.jumpKey;
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
     * v3.0.0: Safety monitor - simplified for Baritone-based Anti-AFK
     * Main safety checks are now handled by CoordinateMonitor and PlayerMonitor
     * This monitor just checks realm state consistency
     */
    private void startSafetyMonitor() {
        // Cancel any existing monitor
        if (safetyMonitorTask != null && !safetyMonitorTask.isDone()) {
            safetyMonitorTask.cancel(false);
        }
        
        PokeAlertConfig config = ConfigManager.getConfig();
        int checkInterval = isAtSpawn() ? config.realmCheckIntervalSpawn : config.realmCheckIntervalOverworld;
        
        safetyMonitorTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (mode != AutomationMode.AUTO) {
                    return;
                }
                
                // Skip checks during resource pack loading
                if (isResourcePackLoading) {
                    return;
                }
                
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
        }, 5000, checkInterval, TimeUnit.MILLISECONDS);
        
        PokeAlertClient.LOGGER.info("Safety monitor started (interval: {}ms)", checkInterval);
    }
    
    /**
     * Stop the safety monitor
     */
    private void stopSafetyMonitor() {
        if (safetyMonitorTask != null && !safetyMonitorTask.isDone()) {
            safetyMonitorTask.cancel(false);
            safetyMonitorTask = null;
            PokeAlertClient.LOGGER.info("Safety monitor stopped");
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
            sendNotification("Egg Hatcher", "Automation stopped", Formatting.YELLOW);
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
        
        String modeStr = "Mode: " + mode.name();
        
        // v3.0.0: Show Anti-AFK status if active
        if (antiAfkActive) {
            return modeStr + " | Anti-AFK: " + locationQueue.getStatus();
        }
        
        if (isAutomationRunning) {
            return modeStr + " | Running: " + currentState.name();
        }
        
        // Check for active countdown
        long currentTime = System.currentTimeMillis();
        if (mode == AutomationMode.AUTO && spawnDetectionTime > 0) {
            long remaining = (REALM_SWITCH_BUFFER - (currentTime - spawnDetectionTime)) / 1000;
            if (remaining > 0) {
                return modeStr + " | Server Buffer: " + remaining + "s";
            }
        }
        
        return modeStr + " | Monitoring";
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
