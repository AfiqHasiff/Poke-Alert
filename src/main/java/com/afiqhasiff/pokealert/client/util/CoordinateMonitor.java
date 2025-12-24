package com.afiqhasiff.pokealert.client.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;

import net.minecraft.client.MinecraftClient;

/**
 * Monitors player coordinates during Anti-AFK movement.
 * Handles arrival detection and teleport/manual movement detection.
 * 
 * Detection Logic:
 * - Arrival: Player within arrivalThreshold blocks of destination
 * - Teleport: Position changed > teleportDetectionOffset since last check
 * - World Change: Different dimension detected
 * 
 * @since v3.0.0
 */
public class CoordinateMonitor {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> monitorTask;
    
    // Configuration (set from PokeAlertConfig)
    private static int arrivalThreshold = 3;
    private static int teleportDetectionOffset = 10;
    private static int checkIntervalMs = 500;
    
    // Destination tracking
    private static int destinationX, destinationZ;
    private static boolean hasDestination = false;
    
    // Position tracking for teleport detection
    private static int lastX, lastZ;
    private static String currentWorld;
    
    // State
    private static volatile boolean isMonitoring = false;
    
    // Callbacks
    private static Runnable onTeleportCallback;
    private static Runnable onArrivalCallback;
    private static Consumer<String> onWorldChangeCallback;
    
    /**
     * Start monitoring with specified configuration
     * @param config PokeAlertConfig to read thresholds from
     */
    public static void startMonitoring(PokeAlertConfig config) {
        if (isMonitoring) {
            PokeAlertClient.LOGGER.warn("CoordinateMonitor: Already monitoring, stopping first");
            stopMonitoring();
        }
        
        arrivalThreshold = config.arrivalThreshold;
        teleportDetectionOffset = config.teleportDetectionOffset;
        checkIntervalMs = config.coordinateCheckInterval;
        
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CoordinateMonitor");
                t.setDaemon(true);
                return t;
            });
        }
        
        // Initialize position
        if (client.player != null && client.world != null) {
            lastX = (int) client.player.getX();
            lastZ = (int) client.player.getZ();
            currentWorld = client.world.getRegistryKey().getValue().toString();
        }
        
        hasDestination = false;
        isMonitoring = true;
        scheduleMonitorTask();
        
        PokeAlertClient.LOGGER.info("CoordinateMonitor: Started (arrival={}, teleport={}, interval={}ms)",
            arrivalThreshold, teleportDetectionOffset, checkIntervalMs);
    }
    
    /**
     * Stop all monitoring
     */
    public static void stopMonitoring() {
        isMonitoring = false;
        hasDestination = false;
        
        if (monitorTask != null) {
            monitorTask.cancel(false);
            monitorTask = null;
        }
        
        PokeAlertClient.LOGGER.info("CoordinateMonitor: Stopped");
    }
    
    /**
     * Set current destination for arrival checking
     * Also resets last position to current to avoid false teleport detection
     * @param x Destination X coordinate
     * @param z Destination Z coordinate
     */
    public static void setDestination(int x, int z) {
        destinationX = x;
        destinationZ = z;
        hasDestination = true;
        
        // Reset last position to current to avoid false teleport on destination change
        if (client.player != null) {
            lastX = (int) client.player.getX();
            lastZ = (int) client.player.getZ();
        }
        
        PokeAlertClient.LOGGER.info("CoordinateMonitor: Destination set to ({}, {})", x, z);
    }
    
    /**
     * Clear destination (no arrival checking)
     */
    public static void clearDestination() {
        hasDestination = false;
        PokeAlertClient.LOGGER.debug("CoordinateMonitor: Destination cleared");
    }
    
    /**
     * Check if player has arrived at current destination
     * Uses 2D distance (X, Z only)
     * @return true if within arrivalThreshold blocks of destination
     */
    public static boolean hasArrived() {
        if (!hasDestination || client.player == null) return false;
        
        int currentX = (int) client.player.getX();
        int currentZ = (int) client.player.getZ();
        
        double distance = Math.sqrt(
            Math.pow(currentX - destinationX, 2) + 
            Math.pow(currentZ - destinationZ, 2)
        );
        
        return distance <= arrivalThreshold;
    }
    
    /**
     * Check for teleport or manual movement exceeding threshold
     * Compares current position to last checked position
     * @return true if position delta > teleportDetectionOffset
     */
    public static boolean checkForTeleport() {
        if (client.player == null) return false;
        
        int currentX = (int) client.player.getX();
        int currentZ = (int) client.player.getZ();
        
        int deltaX = Math.abs(currentX - lastX);
        int deltaZ = Math.abs(currentZ - lastZ);
        int maxDelta = Math.max(deltaX, deltaZ);
        
        return maxDelta > teleportDetectionOffset;
    }
    
    /**
     * Get current player position
     * @return int[] with [x, z] or null if player unavailable
     */
    public static int[] getCurrentPosition() {
        if (client.player == null) return null;
        return new int[] { (int) client.player.getX(), (int) client.player.getZ() };
    }
    
    /**
     * Get distance to current destination
     * @return Distance in blocks, or -1 if no destination
     */
    public static double getDistanceToDestination() {
        if (!hasDestination || client.player == null) return -1;
        
        int currentX = (int) client.player.getX();
        int currentZ = (int) client.player.getZ();
        
        return Math.sqrt(
            Math.pow(currentX - destinationX, 2) + 
            Math.pow(currentZ - destinationZ, 2)
        );
    }
    
    /**
     * Register callback for teleport detection events
     * @param callback Runnable to execute when teleport detected
     */
    public static void onTeleportDetected(Runnable callback) {
        onTeleportCallback = callback;
    }
    
    /**
     * Register callback for arrival events
     * @param callback Runnable to execute when arrived at destination
     */
    public static void onArrival(Runnable callback) {
        onArrivalCallback = callback;
    }
    
    /**
     * Register callback for world change events
     * @param callback Consumer receiving the new world name
     */
    public static void onWorldChange(Consumer<String> callback) {
        onWorldChangeCallback = callback;
    }
    
    /**
     * Check if monitoring is active
     * @return true if monitoring
     */
    public static boolean isMonitoring() {
        return isMonitoring;
    }
    
    /**
     * Check if a destination is set
     * @return true if destination is set
     */
    public static boolean hasDestination() {
        return hasDestination;
    }
    
    /**
     * Internal: Schedule the monitoring task
     */
    private static void scheduleMonitorTask() {
        monitorTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isMonitoring) return;
            
            try {
                if (client.player == null || client.world == null) {
                    return;
                }
                
                // Check for world change first (highest priority)
                String newWorld = client.world.getRegistryKey().getValue().toString();
                if (currentWorld != null && !newWorld.equals(currentWorld)) {
                    PokeAlertClient.LOGGER.warn("CoordinateMonitor: World changed {} → {}", 
                        currentWorld, newWorld);
                    
                    String oldWorld = currentWorld;
                    currentWorld = newWorld;
                    
                    if (onWorldChangeCallback != null) {
                        client.execute(() -> onWorldChangeCallback.accept(newWorld));
                    }
                    return; // Skip other checks this tick
                }
                currentWorld = newWorld;
                
                // Check arrival BEFORE teleport (arrival takes priority)
                if (hasDestination && hasArrived()) {
                    PokeAlertClient.LOGGER.info("CoordinateMonitor: Arrived at ({}, {})", 
                        destinationX, destinationZ);
                    
                    // Update last position
                    lastX = (int) client.player.getX();
                    lastZ = (int) client.player.getZ();
                    
                    if (onArrivalCallback != null) {
                        client.execute(() -> onArrivalCallback.run());
                    }
                    return;
                }
                
                // Check for teleport/manual movement
                if (checkForTeleport()) {
                    int currentX = (int) client.player.getX();
                    int currentZ = (int) client.player.getZ();
                    
                    PokeAlertClient.LOGGER.warn("CoordinateMonitor: Teleport detected! ({},{}) → ({},{})",
                        lastX, lastZ, currentX, currentZ);
                    
                    if (onTeleportCallback != null) {
                        client.execute(() -> onTeleportCallback.run());
                    }
                    return;
                }
                
                // Update last position for next check
                lastX = (int) client.player.getX();
                lastZ = (int) client.player.getZ();
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("CoordinateMonitor: Error in monitor task", e);
            }
        }, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Shutdown the scheduler (call on mod unload)
     */
    public static void shutdown() {
        stopMonitoring();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        PokeAlertClient.LOGGER.info("CoordinateMonitor: Shutdown complete");
    }
    
    /**
     * Get status string for debugging
     */
    public static String getStatus() {
        if (!isMonitoring) return "Stopped";
        if (!hasDestination) return "Monitoring (no destination)";
        
        double dist = getDistanceToDestination();
        return String.format("Monitoring → (%d,%d) dist=%.1f", destinationX, destinationZ, dist);
    }
}

