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
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                startAutomationSequence(false, false);
            } else if (!isAtSpawn()) {
                // Provide feedback when enabling at overworld
                // Safety monitor will verify and correct Anti-AFK state after 5s
                sendNotification("Egg Hatcher", "Enabled - safety monitor active", Formatting.GRAY);
                PokeAlertClient.LOGGER.info("Enabled at overworld - safety monitor will verify Anti-AFK state");
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
                sendNotification("", "Teleport cooldown active - waiting", Formatting.DARK_GRAY);
                
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
                        sendNotification("", "World verification failed - restarting", Formatting.RED);
                        
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
        navigateToNextLocation();
        
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
            
            // Cancel timeout timer
            if (locationTimeoutTask != null) {
                locationTimeoutTask.cancel(false);
            }
            
            BaritoneController.markPathComplete();
            
            // Check if Step 5 should trigger (3 successful visits)
            locationQueue.markVisitedAndAdvance();
            
            if (locationQueue.shouldTriggerStep5()) {
                locationQueue.markStep5Completed();
                completeAutomation();
            }
            
            // Continue to next location (perpetual)
            if (antiAfkActive && !SafetyManager.isSafetyTriggered()) {
                navigateToNextLocation();
            }
        });
        
        // Teleport detection callback
        CoordinateMonitor.onTeleportDetected(() -> {
            PokeAlertClient.LOGGER.warn("⚠️ Teleport/manual movement detected!");
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
                // Trigger safety stop with notification but allow restart
                SafetyManager.triggerSafetyStop(SafetyManager.REASON_WORLD_CHANGE + " (to spawn)", false); // No telegram for spawn return
            } else {
                // Went to unexpected world (not spawn, not overworld where we started)
                PokeAlertClient.LOGGER.warn("📍 Teleported to unknown world - stopping");
                SafetyManager.triggerSafetyStop(SafetyManager.REASON_WORLD_CHANGE, true);
            }
        });
        
        // Player detection callback
        PlayerMonitor.onPlayerDetectedWithType((playerName, type) -> {
            String reason = type.equals("nearby") ? 
                SafetyManager.REASON_PLAYER_NEARBY : SafetyManager.REASON_PLAYER_LIST;
            PokeAlertClient.LOGGER.warn("⚠️ Avoided player detected: " + playerName + " (" + type + ")");
            SafetyManager.triggerSafetyStop(reason + ": " + playerName, true);
            stopBaritoneAntiAfk();
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
                sendNotification("Egg Hatcher", "Teleported to spawn - restarting", Formatting.YELLOW);
                
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
     * v3.0.0: Navigate to the next location in queue
     */
    private void navigateToNextLocation() {
        if (!antiAfkActive || SafetyManager.isSafetyTriggered()) {
            return;
        }
        
        int[] destination = locationQueue.getCurrentDestination();
        if (destination == null) {
            PokeAlertClient.LOGGER.error("❌ No destination available!");
            return;
        }
        
        // Set destination in monitor
        CoordinateMonitor.setDestination(destination[0], destination[1]);
        
        // Start navigation via Baritone
        BaritoneController.gotoLocation(destination[0], destination[1]);
        
        PokeAlertClient.LOGGER.info("🚶 Navigating to {} - {}", 
            locationQueue.getStatus(), 
            String.format("(%d, %d)", destination[0], destination[1]));
        
        // Start timeout timer
        PokeAlertConfig config = ConfigManager.getConfig();
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
        sendNotification("Egg Hatcher", "Location timeout - skipping", Formatting.YELLOW);
        
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
        
        // Small delay before next navigation
        scheduler.schedule(() -> {
            if (antiAfkActive && !SafetyManager.isSafetyTriggered()) {
                navigateToNextLocation();
            }
        }, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * v3.0.0: Stop Baritone Anti-AFK system
     */
    private void stopBaritoneAntiAfk() {
        antiAfkActive = false;
        
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
                if (!isAtSpawn() && antiAfkActive) {
                    // We're at overworld with Anti-AFK active - verify Baritone is pathing
                    if (!BaritoneController.isPathing() && !SafetyManager.isSafetyTriggered()) {
                        PokeAlertClient.LOGGER.warn("⚠️ Safety: Baritone not pathing at overworld, restarting navigation");
                        navigateToNextLocation();
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
        
        // Send Telegram notification
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
    private void sendNotification(String title, String message, Formatting color) {
        if (client.player != null) {
            PokeAlertConfig config = PokeAlertClient.getInstance().config;
            if (config.inGameTextEnabled) {
                Text notification = Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal(title + ": ").formatted(Formatting.WHITE))
                    .append(Text.literal(message).formatted(color));
                
                client.player.sendMessage(notification, false);
            }
        }
    }
    
    /**
     * Send Telegram notification
     */
    private void sendTelegramNotification(boolean success, long durationSeconds) {
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        if (config.telegramEnabled) {
            CompletableFuture.runAsync(() -> {
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
                
                // Mode line (always Auto since Manual mode was removed)
                message.append("• <b>Mode:</b> <code>Auto</code>\n");
                
                // Journey line
                message.append("• <b>Journey:</b> Spawn → Overworld\n");
                
                telegram.sendEggTimerNotification(message.toString());
            });
        }
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
