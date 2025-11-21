package com.afiqhasiff.pokealert.client.automation;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.notification.TelegramNotification;
import com.afiqhasiff.pokealert.client.util.AntiAfkManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Automation manager for handling realm transitions after disconnects
 * Automates the process of returning from spawn to main realm with anti-afk management
 */
public class RealmManager {
    private static RealmManager instance;
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
    
    // Automation modes
    public enum AutomationMode {
        AUTO,       // Smart detection: immediate after reconnect, delayed otherwise
        MANUAL,     // Only triggers on manual command
        DISABLED    // Completely disabled
    }
    
    // Timing constants
    private static final long ANTIAFK_TOGGLE_DELAY = 100; // 100ms delay after toggling anti-afk (key press buffer)
    private static final long HOME_COMMAND_DELAY = 100; // 100ms delay before sending /home (command formatting buffer)
    private static final long TELEPORT_WAIT_TIME = 17000; // 17s wait: 5s server delay + 3s world load + 3s stabilization + 6s fresh data
    private static final long STUCK_TIMEOUT = 180000; // 3 minutes stuck detection
    private static final long REALM_SWITCH_BUFFER = 30000; // 30 seconds server buffer for realm switching
    private static final long AUTO_MODE_DELAY = 30000; // 30 seconds delay for normal spawn visits
    private static final long CANCEL_WINDOW = 5000; // 5 seconds to cancel automation
    
    private RealmManager() {
        this.client = MinecraftClient.getInstance();
        this.scheduler = Executors.newScheduledThreadPool(1);
    }
    
    public static RealmManager getInstance() {
        if (instance == null) {
            instance = new RealmManager();
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
        // Meteor's Anti-AFK starts at 0s, so we need to collect movement data from the start
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
            }
        }, 10, TimeUnit.SECONDS);
    }
    
    /**
     * Toggle the realm return automation
     */
    public void toggleAutomation() {
        PokeAlertConfig config = PokeAlertClient.getInstance().config;
        
        // Check if mod is enabled
        if (!config.modEnabled) {
            return;
        }
        
        // Prevent mode change during active automation
        if (isAutomationRunning) {
            sendNotification("[PokeAlert]", "Cannot change mode during automation", Formatting.RED);
            PokeAlertClient.LOGGER.warn("Mode change blocked - automation is running");
            return;
        }
        
        if (mode == AutomationMode.DISABLED) {
            // Cycle to AUTO mode
            mode = AutomationMode.AUTO;
            config.realmManagerEnabled = true;
            ConfigManager.saveSettings(config);
            
            // Reset ALL session flags for fresh start
            // Treat mode cycle to AUTO as "fresh server join" behavior
            manuallyCancelled = false;
            antiAfkDisabledOnReconnect = false;
            spawnDetectionTime = 0;
            
            // CRITICAL: Reset Anti-AFK tracking including stateBeforeTeleport
            // This prevents stale teleport state from previous automation runs
            AntiAfkManager.resetTracking();
            
            PokeAlertClient.LOGGER.info("Mode changed to AUTO - all session flags reset (fresh start)");
            
            sendNotification("[PokeAlert]", "Realm Manager: Auto mode", Formatting.GREEN);
            startMonitoring();
            
            // Check current location and trigger if at spawn
            if (isAtSpawn() && !isAutomationRunning) {
                startAutomationSequence(false, false);
            } else if (!isAtSpawn()) {
                // Provide feedback when enabling at overworld
                sendNotification("[PokeAlert]", "Already at overworld - monitoring active", Formatting.GRAY);
            }
        } else if (mode == AutomationMode.AUTO) {
            // Cycle to MANUAL mode
            mode = AutomationMode.MANUAL;
            
            // Stop background monitoring in MANUAL mode
            if (currentTask != null) {
                currentTask.cancel(false);
                currentTask = null;
                PokeAlertClient.LOGGER.info("Background monitoring stopped (switched to MANUAL mode)");
            }
            
            manuallyCancelled = false;
            antiAfkDisabledOnReconnect = false;
            spawnDetectionTime = 0;
            
            PokeAlertClient.LOGGER.info("Mode changed to MANUAL - all session flags reset");
            sendNotification("[PokeAlert]", "Realm Manager: Manual mode", Formatting.AQUA);
        } else {
            // Cycle to DISABLED
            mode = AutomationMode.DISABLED;
            config.realmManagerEnabled = false;
            ConfigManager.saveSettings(config);

            antiAfkDisabledOnReconnect = false;
            manuallyCancelled = false;
            spawnDetectionTime = 0;
            
            PokeAlertClient.LOGGER.info("Mode changed to DISABLED - all session flags reset");
            sendNotification("[PokeAlert]", "Realm Manager: Disabled", Formatting.RED);
            stopAutomation();
        }
    }
    
    /**
     * Manually trigger the automation
     */
    public void manualTrigger() {
        if (isAtSpawn() && !isAutomationRunning) {
            // Start Anti-AFK state monitoring
            AntiAfkManager.startStateMonitoring();
            
            // Start with the full sequence including 30-second wait
            spawnDetectionTime = System.currentTimeMillis();
            isAutomationRunning = true;
            automationStartTime = System.currentTimeMillis();
            currentState = State.DETECTED_AT_SPAWN;
            
            // Step 1: Spawn Detection (Manual trigger)
            sendNotification("[Realm Manager - Manual] [1/6]", "Spawn Detection: Manual trigger", Formatting.YELLOW);
            PokeAlertClient.LOGGER.info("🎯 Step 1/6: Spawn Detection - Manual trigger initiated");
            
            // Step 2: Anti-AFK Check - Verify and disable if needed
            Boolean antiAfkState = AntiAfkManager.getAntiAfkState();
            if (antiAfkState == null) {
                // Can't verify state yet - wait a moment and try again
                sendNotification("[Realm Manager - Manual] [2/6]", "Anti-AFK Check: Initializing...", Formatting.YELLOW);
                PokeAlertClient.LOGGER.info("🎯 Step 2/6: Anti-AFK Check - Waiting for state initialization");
                // Schedule a retry in 2 seconds to allow movement detection to initialize
                scheduler.schedule(() -> {
                    sendNotification("[Realm Manager - Manual] [2/6]", "Anti-AFK Check: Disabling", Formatting.YELLOW);
                    PokeAlertClient.LOGGER.info("🎯 Step 2/6: Anti-AFK Check - Disabling now");
                    AntiAfkManager.toggleAntiAfk(false);
                    antiAfkDisabledOnReconnect = true;
                    startManualCountdown();
                }, 2, TimeUnit.SECONDS);
                return; // Exit early, countdown will start after retry
            }
            
            // Check if Anti-AFK needs to be disabled
            if (antiAfkState) {
                // Anti-AFK is ON, disable it
                sendNotification("[Realm Manager - Manual] [2/6]", "Anti-AFK Check: Disabling", Formatting.YELLOW);
                PokeAlertClient.LOGGER.info("🎯 Step 2/6: Anti-AFK Check - State is ON, disabling now");
                AntiAfkManager.toggleAntiAfk(false);
                antiAfkDisabledOnReconnect = true;
            } else {
                // Anti-AFK appears OFF, verify to catch false negatives
                sendNotification("[Realm Manager - Manual] [2/6]", "Anti-AFK Check: Verifying", Formatting.YELLOW);
                PokeAlertClient.LOGGER.info("🎯 Step 2/6: Anti-AFK Check - State appears OFF, verifying");
                // For manual trigger, we can trust the state more (user is actively triggering)
                antiAfkDisabledOnReconnect = true;
            }
            
            // Start safety monitor
            startSafetyMonitor();
            
            // Send Step 2 notification with custom formatting
            if (client.player != null) {
                PokeAlertConfig config = PokeAlertClient.getInstance().config;
                if (config.inGameTextEnabled) {
                    Text notification = Text.literal("[").formatted(Formatting.GRAY)
                        .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("[Realm Manager - Manual] [2/5]: ").formatted(Formatting.WHITE))
                        .append(Text.literal("Waiting 30s for realm return").formatted(Formatting.YELLOW))
                        .append(Text.literal(" - Press Home to cancel").formatted(Formatting.GRAY));
                    
                    client.player.sendMessage(notification, false);
                }
            }
            
            // Start countdown task for 30 seconds
            startManualCountdown();
        } else if (!isAtSpawn()) {
            sendNotification("[Realm Manager - Manual]", "Error: Not at spawn", Formatting.RED);
        } else {
            sendNotification("[Realm Manager - Manual]", "Already running", Formatting.YELLOW);
        }
    }
    
    /**
     * Handle countdown for manual trigger with 30-second server buffer
     */
    private void startManualCountdown() {
        final long bufferStart = System.currentTimeMillis();
        
        // Step 3: Server Buffer - Show notification with custom formatting (at the start)
        if (client.player != null) {
            PokeAlertConfig config = PokeAlertClient.getInstance().config;
            if (config.inGameTextEnabled) {
                Text notification = Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("[Realm Manager - Manual] [3/6]: ").formatted(Formatting.WHITE))
                    .append(Text.literal("Server Buffer: Waiting 30s").formatted(Formatting.YELLOW))
                    .append(Text.literal(" - Press Home to cancel").formatted(Formatting.DARK_GRAY));
                
                client.player.sendMessage(notification, false);
            }
        }
        PokeAlertClient.LOGGER.info("🎯 Step 3/6: Server Buffer - Waiting 30 seconds before realm change");
        
        automationDelayTask = scheduler.scheduleAtFixedRate(() -> {
            if (currentState == State.CANCELLED) {
                if (automationDelayTask != null) {
                    automationDelayTask.cancel(false);
                }
                return;
            }
            
            long elapsed = System.currentTimeMillis() - bufferStart;
            long remaining = (REALM_SWITCH_BUFFER - elapsed) / 1000;
            
            if (remaining <= 0) {
                // Buffer complete, execute automation
                if (automationDelayTask != null) {
                    automationDelayTask.cancel(false);
                }
                sendNotification("[Realm Manager - Manual] [4/6]", "Realm Change: Executing", Formatting.YELLOW);
                PokeAlertClient.LOGGER.info("🎯 Step 4/6: Realm Change - Executing teleport command");
                continueAutomationFromStep2();
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Cancel the automation if it's waiting or monitoring
     */
    public void cancelAutomation() {
        // Can cancel during waiting or if there's an active countdown
        boolean canCancel = currentState == State.WAITING_CONFIRMATION || 
                           (spawnDetectionTime > 0 && !isAutomationRunning);
        
        if (canCancel) {
            currentState = State.CANCELLED;
            isAutomationRunning = false;
            spawnDetectionTime = 0;  // Reset spawn detection
            manuallyCancelled = true;  // Mark as manually cancelled
            // Keep antiAfkDisabledOnReconnect true as Anti-AFK is still disabled
            
            // Stop Anti-AFK state monitoring
            AntiAfkManager.stopStateMonitoring();
            
            // Stop safety monitor
            stopSafetyMonitor();
            
            // Cancel all automation tasks
            cancelAllAutomationTasks();
            
            if (automationDelayTask != null) {
                automationDelayTask.cancel(false);
                automationDelayTask = null;
            }
            
            String modePrefix = mode == AutomationMode.MANUAL ? "[Realm Manager - Manual]" : "[Realm Manager - Auto]";
            sendNotification(modePrefix, "Realm change aborted", Formatting.YELLOW);
        }
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
                        
                    case MANUAL:
                        // Don't auto-trigger in manual mode
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
        
        // Reset spawn detection time
        spawnDetectionTime = 0;
        
        if (!isManual && mode != AutomationMode.MANUAL) {
            // Auto mode - execute directly without additional confirmation
                    executeAutomationSteps(antiAfkAlreadyDisabled);
        } else {
            // Manual trigger - start immediately
            executeAutomationSteps(false);
        }
    }
    
    // Overload for backward compatibility
    private void startAutomationSequence(boolean isManual) {
        startAutomationSequence(isManual, false);
    }
    
    /**
     * Execute the actual automation steps
     */
    private void executeAutomationSteps(boolean antiAfkAlreadyDisabled) {
        currentState = State.DETECTED_AT_SPAWN;
        
        // Don't show "Step 3" yet - that happens when we actually send /home command
        // This method is just preparing to execute the steps
        
        // Start safety monitor if not already running
        if (safetyMonitorTask == null || safetyMonitorTask.isDone()) {
            PokeAlertClient.LOGGER.info("Starting safety monitor at Step 3 (normal timing)");
            startSafetyMonitor();
        } else {
            PokeAlertClient.LOGGER.info("Safety monitor already running (started early on server join)");
        }
        
        // Start stuck detection timer
        stuckDetector = scheduler.schedule(() -> {
            if (isAutomationRunning && isAtSpawn()) {
                currentState = State.STUCK;
                handleStuckAtSpawn();
            }
        }, STUCK_TIMEOUT, TimeUnit.MILLISECONDS);
        
        if (!antiAfkAlreadyDisabled) {
            // Step 1: Disable anti-afk (if not already done)
            step1Task = scheduler.schedule(() -> {
                currentState = State.DISABLING_ANTIAFK;
                toggleAntiAfk(false);
                
                // Continue with the rest of the steps
                continueAutomationFromStep2();
            }, 3000, TimeUnit.MILLISECONDS);  // Wait 3s for movement tracking to re-initialize after teleport
        } else {
            // Anti-AFK already disabled, skip to step 2
            continueAutomationFromStep2();
        }
    }
    
    // Overload for backward compatibility
    private void executeAutomationSteps() {
        executeAutomationSteps(false);
    }
    
    /**
     * Proceed with spawn detection and automation steps (Steps 1-3)
     * Universal 3-second grace period ensures reliable state detection for all spawn scenarios
     */
    private void proceedWithSpawnDetection() {
        long currentTime = System.currentTimeMillis();
        String location = AntiAfkManager.getPlayerLocationInfo();
        
        Boolean antiAfkState = AntiAfkManager.getAntiAfkState();
        
        // Step 1: Spawn Detection
        sendNotification("[Realm Manager - Auto] [1/6]", "Spawn Detected", Formatting.YELLOW);
        PokeAlertClient.LOGGER.info("🎯 Step 1/6: Spawn Detection at " + location);
        
        // Step 2: Anti-AFK Check
        if (antiAfkState == null) {
            // Still unknown? Rare, but safety monitor will handle
            sendNotification("[Realm Manager - Auto] [2/6]", "Anti-AFK Check: State unknown (monitoring)", Formatting.YELLOW);
            PokeAlertClient.LOGGER.warn("⚠️ Step 2/6: Anti-AFK state unknown - safety monitor active at " + location);
        } else if (antiAfkState) {
            // Confirmed ON, disable immediately
            sendNotification("[Realm Manager - Auto] [2/6]", "Anti-AFK Check: Disabling", Formatting.YELLOW);
            PokeAlertClient.LOGGER.info("🎯 Step 2/6: Anti-AFK Check - State is ON, disabling now at " + location);
            AntiAfkManager.toggleAntiAfk(false);
        } else {
            // Confirmed OFF
            sendNotification("[Realm Manager - Auto] [2/6]", "Anti-AFK Check: Already OFF", Formatting.GRAY);
            PokeAlertClient.LOGGER.info("✅ Step 2/6: Anti-AFK Check - Already OFF at " + location);
        }
        
        antiAfkDisabledOnReconnect = true;
        
        // Set spawn detection time for 30s buffer
        spawnDetectionTime = currentTime;
        PokeAlertClient.LOGGER.info("Spawn detection time set, starting automation");
        
        // Step 3: Server Buffer - Show notification with custom formatting
        if (client.player != null) {
            PokeAlertConfig stepConfig = PokeAlertClient.getInstance().config;
            if (stepConfig.inGameTextEnabled) {
                Text notification = Text.literal("[").formatted(Formatting.GRAY)
                    .append(Text.literal("PokeAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("[Realm Manager - Auto] [3/6]: ").formatted(Formatting.WHITE))
                    .append(Text.literal("Server Buffer: Waiting 30s").formatted(Formatting.YELLOW))
                    .append(Text.literal(" - Press Home to cancel").formatted(Formatting.DARK_GRAY));
                
                client.player.sendMessage(notification, false);
            }
        }
        PokeAlertClient.LOGGER.info("🎯 Step 3/6: Server Buffer - Waiting 30 seconds before realm change at " + location);
    }
    
    /**
     * Continue automation from step 2 (after Anti-AFK is handled)
     * Note: Continuous safety monitoring is active, no need for checkpoint checks
     */
    private void continueAutomationFromStep2() {
        // Step 2: Send /home command after delay
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
            
            // Step 4: Realm Change - Execute teleport command
            String modePrefix = mode == AutomationMode.MANUAL ? "[Realm Manager - Manual] [4/6]" : "[Realm Manager - Auto] [4/6]";
            sendNotification(modePrefix, "Realm Change: Executing", Formatting.YELLOW);
            PokeAlertClient.LOGGER.info("🎯 Step 4/6: Realm Change - Sending teleport command at " + location);
            
            // CRITICAL: Save Anti-AFK state BEFORE teleport (not at world change)
            // This ensures we capture the state while player is still moving/standing at spawn
            AntiAfkManager.saveStateForTeleport();
            
            PokeAlertConfig config = PokeAlertClient.getInstance().config;
            String returnCmd = config.realmReturnCommand;
            sendChatCommand(returnCmd);
            
            // Update last teleport time
            lastTeleportCommandTime = currentTime;
            
            // CRITICAL: Stop safety monitor immediately after sending teleport
            // It will interfere with Step 5 (enable Anti-AFK) if it checks during transition
            stopSafetyMonitor();
            PokeAlertClient.LOGGER.info("🛡️ Safety monitor paused for teleport and Step 5/6 execution");
            
            // Step 3: Wait for teleport
            step3Task = scheduler.schedule(() -> {
                currentState = State.WAITING_FOR_TELEPORT;
                
                // Step 4: Re-enable anti-afk after teleport
                // CRITICAL: Wait 17s total = 5s server delay + 3s world load + 3s stabilization + 6s fresh data
                step4Task = scheduler.schedule(() -> {
                    // Verify we successfully teleported to overworld
                    if (!isInOverworld()) {
                        // Not in overworld yet - log and restart
                        String currentWorld = client.world != null ? 
                                            client.world.getRegistryKey().getValue().toString() : "unknown";
                        PokeAlertClient.LOGGER.error("❌ Step 5/6 FAILED: Not in overworld after 17s (current: " + currentWorld + ")");
                        sendNotification("", "World verification failed - restarting", Formatting.RED);
                        
                        // Cancel all tasks and restart
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
                    
                    // Confirmed in overworld - proceed with Step 5
                    String overworldLocation = AntiAfkManager.getPlayerLocationInfo();
                    PokeAlertClient.LOGGER.info("✅ World verification passed: In overworld at " + overworldLocation);
                    
                    // Step 5: Anti-AFK Enable - Turn on Anti-AFK at overworld
                    currentState = State.ENABLING_ANTIAFK;
                    String step5Prefix = mode == AutomationMode.MANUAL ? "[Realm Manager - Manual] [5/6]" : "[Realm Manager - Auto] [5/6]";
                    sendNotification(step5Prefix, "Anti-AFK Enable: Turning ON", Formatting.YELLOW);
                    PokeAlertClient.LOGGER.info("🎯 Step 5/6: Anti-AFK Enable - Enabling at " + overworldLocation + " (waited 17s: 5s delay + 3s load + 3s stabilization + 6s data)");
                        PokeAlertClient.LOGGER.info("Safety monitor paused for toggle operation");
                        
                        boolean toggleSuccess = toggleAntiAfk(true);
                        
                        if (toggleSuccess) {
                            // Complete automation (this will do final cleanup)
                            step5Task = scheduler.schedule(() -> {
                                completeAutomation();
                            }, ANTIAFK_TOGGLE_DELAY, TimeUnit.MILLISECONDS);
                        } else {
                            // Toggle failed - restart the entire process
                            PokeAlertClient.LOGGER.error("❌ Step 5/6 FAILED: Anti-AFK toggle aborted, restarting automation");
                            sendNotification("", "Step 5 failed - restarting process", Formatting.RED);
                            
                            // Cancel all pending tasks
                            cancelAllAutomationTasks();
                            
                            // Wait 3 seconds then restart
                            scheduler.schedule(() -> {
                                stopAutomation();
                                // Reset flags for fresh start
                                manuallyCancelled = false;
                                antiAfkDisabledOnReconnect = false;
                                spawnDetectionTime = 0;
                                
                                // Restart monitoring (which will detect spawn and restart the process)
                                startMonitoring();
                            }, 3, TimeUnit.SECONDS);
                        }
                }, TELEPORT_WAIT_TIME, TimeUnit.MILLISECONDS);
                
            }, HOME_COMMAND_DELAY, TimeUnit.MILLISECONDS);
            
        }, REALM_SWITCH_BUFFER, TimeUnit.MILLISECONDS); // 30 seconds to respect server realm switch buffer
    }
    
    /**
     * Toggle Anti-AFK using the AntiAfkManager utility
     * This will try multiple approaches to toggle Anti-AFK via keybinding simulation
     * @return true if toggle succeeded or already in desired state, false if failed
     */
    private boolean toggleAntiAfk(boolean enable) {
        if (client.player != null && client.currentScreen == null) {
            // Use the AntiAfkManager utility which tries multiple approaches
            boolean success = AntiAfkManager.toggleAntiAfk(enable);
            
            // Log the action
            PokeAlertClient.LOGGER.info("Anti-AFK toggle requested: " + (enable ? "Enable" : "Disable"));
            
            return success;
        }
        return false;  // Failed - no player or screen is open
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
     * Start continuous safety monitoring of anti-AFK state
     * Checks every 3 seconds to ensure anti-AFK is in the expected state
     */
    private void startSafetyMonitor() {
        // Cancel any existing monitor
        if (safetyMonitorTask != null && !safetyMonitorTask.isDone()) {
            safetyMonitorTask.cancel(false);
        }
        
        safetyMonitorTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                // Only monitor while automation is running
                if (!isAutomationRunning) {
                    return;
                }
                
                // Skip checks during resource pack loading (high CPU usage can cause false positives)
                if (isResourcePackLoading) {
                    PokeAlertClient.LOGGER.warn("Safety monitor: Skipping check during resource pack loading");
                    return;
                }
                
                // Determine expected anti-AFK state based on location
                boolean expectedState;
                if (isAtSpawn()) {
                    // At spawn: Anti-AFK should be OFF (false)
                    expectedState = false;
                } else {
                    // At overworld: Anti-AFK should be ON (true)
                    expectedState = true;
                }
                
                // Check current state
                Boolean actualState = AntiAfkManager.getAntiAfkState();
                
                // If we can't determine state, skip this check (might be initializing)
                if (actualState == null) {
                    PokeAlertClient.LOGGER.warn("Safety monitor: Cannot determine anti-AFK state, skipping check");
                    return;
                }
                
                // Check for mismatch
                if (actualState != expectedState) {
                    String location = isAtSpawn() ? "spawn" : "overworld";
                    String expected = expectedState ? "ON" : "OFF";
                    String actual = actualState ? "ON" : "OFF";
                    
                    PokeAlertClient.LOGGER.error("Safety monitor detected anomaly at " + location + 
                                                 ": Expected Anti-AFK " + expected + ", but found " + actual);
                    
                    String modePrefix = mode == AutomationMode.MANUAL ? "[Realm Manager - Manual]" : "[Realm Manager - Auto]";
                    sendNotification(modePrefix, 
                                   "Safety: Anti-AFK state mismatch - Restarting", 
                                   Formatting.RED);
                    
                    // Stop safety monitor
                    stopSafetyMonitor();
                    
                    // DO NOT correct the state here - let the restart flow handle it
                    // This prevents double-toggling when spawn detection also tries to toggle
                    
                    // Reset and restart the process
                    isAutomationRunning = false;
                    antiAfkDisabledOnReconnect = false; // Reset flag so spawn detection will toggle
                    currentState = State.IDLE;
                    spawnDetectionTime = 0; // Reset spawn detection time for clean restart
                    
                    // CRITICAL: Cancel ALL scheduled tasks to prevent old steps from executing
                    cancelAllAutomationTasks();
                    
                    // Cancel monitoring loop (CRITICAL: prevents old loop from triggering automation during restart)
                    if (currentTask != null) {
                        currentTask.cancel(false);
                        currentTask = null;
                        PokeAlertClient.LOGGER.info("Cancelled monitoring loop during safety restart");
                    }
                    
                    // Cancel delay and stuck detector tasks
                    if (automationDelayTask != null) {
                        automationDelayTask.cancel(false);
                    }
                    if (stuckDetector != null) {
                        stuckDetector.cancel(false);
                    }
                    
                    // Wait 5 seconds to allow movement tracking to fully initialize after restart
                    // Movement detection needs time to establish baseline after world changes/restarts
                    scheduler.schedule(() -> {
                        if (mode == AutomationMode.MANUAL) {
                            manualTrigger();
                        } else {
                            // For AUTO mode: restart monitoring to properly re-detect and handle spawn
                            // By now, movement tracking should be initialized and state detection reliable
                            startMonitoring();
                        }
                    }, 5, TimeUnit.SECONDS);
                }
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("Error in safety monitor", e);
            }
        }, 5, 3, TimeUnit.SECONDS); // Initial delay 5s, then check every 3s
        
        PokeAlertClient.LOGGER.info("Safety monitor started - first check in 5s, then every 3s");
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
     * Complete the automation successfully
     */
    private void completeAutomation() {
        currentState = State.COMPLETED;
        isAutomationRunning = false;
        
        // Stop Anti-AFK state monitoring
        AntiAfkManager.stopStateMonitoring();
        
        // Stop safety monitor
        stopSafetyMonitor();
        
        // Cancel all automation tasks (cleanup)
        cancelAllAutomationTasks();
        
        // Cancel stuck detector
        if (stuckDetector != null) {
            stuckDetector.cancel(false);
        }
        
        // Reset flags
        antiAfkDisabledOnReconnect = false;
        
        // Step 6: Completion - Automation complete
        String location = AntiAfkManager.getPlayerLocationInfo();
        String modePrefix = mode == AutomationMode.MANUAL ? "[Realm Manager - Manual] [6/6]" : "[Realm Manager - Auto] [6/6]";
        sendNotification(modePrefix, "Completion: Realm change complete ✓", Formatting.YELLOW);
        PokeAlertClient.LOGGER.info("🎯 Step 6/6: Completion - Automation finished successfully at " + location);
        
        // Send Telegram notification
        long duration = (System.currentTimeMillis() - automationStartTime) / 1000;
        sendTelegramNotification(true, duration);
        
        // Continue monitoring
        startMonitoring();
    }
    
    /**
     * Handle being stuck at spawn
     */
    private void handleStuckAtSpawn() {
        isAutomationRunning = false;
        
        // Stop Anti-AFK state monitoring
        AntiAfkManager.stopStateMonitoring();
        
        // Stop safety monitor
        stopSafetyMonitor();
        
        // Cancel all automation tasks
        cancelAllAutomationTasks();
        
        String modePrefix = mode == AutomationMode.MANUAL ? "[Realm Manager - Manual]" : "[Realm Manager - Auto]";
        sendNotification(modePrefix, 
            "Error: Stuck at spawn > 3 min", 
            Formatting.RED);
        
        // Send Telegram alert
        sendTelegramNotification(false, 180);
        
        // Stop automation but keep monitoring
        stopAutomation();
        startMonitoring();
    }
    
    /**
     * Stop all automation tasks
     */
    public void stopAutomation() {
        isAutomationRunning = false;
        currentState = State.IDLE;
        
        // Stop Anti-AFK state monitoring
        AntiAfkManager.stopStateMonitoring();
        
        // Stop safety monitor
        stopSafetyMonitor();
        
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
        
        // Reset detection times
        spawnDetectionTime = 0;
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
                message.append("🌍 <b>Realm Manager</b>\n");
                
                // Status line
                if (success) {
                    message.append("• <b>Status:</b> <i>Successful</i>\n");
                } else {
                    message.append("• <b>Status:</b> <i>Unsuccessful</i> 🚨\n");
                }
                
                // Mode line (determine if it was AUTO or MANUAL)
                String modeStr = (mode == AutomationMode.AUTO) ? "Auto" : "Manual";
                message.append("• <b>Mode:</b> <code>").append(modeStr).append("</code>\n");
                
                // Journey line
                message.append("• <b>Journey:</b> Spawn → Overworld\n");
                
                // Additional info based on failure (stuck at spawn)
                if (!success) {
                    message.append("\n<i>Stuck at spawn for over 3 minutes</i>\n");
                    message.append("<i>Manual intervention required</i>");
                }
                
                telegram.sendEggTimerNotification(message.toString());
            });
        }
    }
    
    /**
     * Get current automation status
     */
    public String getStatus() {
        if (mode == AutomationMode.DISABLED) {
            return "Disabled";
        }
        
        String modeStr = "Mode: " + mode.name();
        
        if (isAutomationRunning) {
            return modeStr + " | Running: " + currentState.name();
        }
        
        // Check for active countdown (simplified to single path)
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
