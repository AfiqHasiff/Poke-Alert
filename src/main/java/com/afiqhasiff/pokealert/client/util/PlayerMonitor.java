package com.afiqhasiff.pokealert.client.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Monitors for specific players on the server or nearby.
 * Used to detect moderators or other players to avoid.
 * 
 * Detection Methods:
 * - Player List: Checks server TAB list for specified usernames
 * - Nearby Players: Checks for players within specified radius
 * 
 * @since v3.0.0
 */
public class PlayerMonitor {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> monitorTask;
    
    // Configuration
    private static List<String> playersToAvoid = new ArrayList<>();
    private static double nearbyDetectionRadius = 32.0;
    private static int checkIntervalMs = 5000;
    private static boolean enablePlayerList = true;
    private static boolean enableNearbyDetection = true;
    
    // State
    private static volatile boolean isMonitoring = false;
    
    // Callbacks
    private static Consumer<String> onPlayerDetectedCallback;
    private static BiConsumer<String, String> onPlayerDetectedWithTypeCallback;
    
    /**
     * Start continuous player monitoring
     * @param config PokeAlertConfig to read settings from
     */
    public static void startMonitoring(PokeAlertConfig config) {
        if (isMonitoring) {
            PokeAlertClient.LOGGER.warn("PlayerMonitor: Already monitoring, stopping first");
            stopMonitoring();
        }
        
        // Parse players to avoid (lowercase for case-insensitive matching)
        playersToAvoid = Arrays.stream(config.playersToAvoid)
            .filter(s -> s != null && !s.trim().isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toList());
        
        nearbyDetectionRadius = config.nearbyPlayerDetectionRadius;
        checkIntervalMs = config.playerMonitorInterval;
        enablePlayerList = config.enablePlayerListMonitoring;
        enableNearbyDetection = config.enableNearbyPlayerDetection;
        
        if (playersToAvoid.isEmpty()) {
            PokeAlertClient.LOGGER.info("PlayerMonitor: No players to avoid configured, skipping start");
            return;
        }
        
        if (!enablePlayerList && !enableNearbyDetection) {
            PokeAlertClient.LOGGER.info("PlayerMonitor: Both detection methods disabled, skipping start");
            return;
        }
        
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PlayerMonitor");
                t.setDaemon(true);
                return t;
            });
        }
        
        isMonitoring = true;
        scheduleMonitorTask();
        
        PokeAlertClient.LOGGER.info("PlayerMonitor: Started monitoring for {} players (list={}, nearby={}, radius={})",
            playersToAvoid.size(), enablePlayerList, enableNearbyDetection, nearbyDetectionRadius);
    }
    
    /**
     * Stop all monitoring
     */
    public static void stopMonitoring() {
        isMonitoring = false;
        
        if (monitorTask != null) {
            monitorTask.cancel(false);
            monitorTask = null;
        }
        
        PokeAlertClient.LOGGER.info("PlayerMonitor: Stopped");
    }
    
    /**
     * Check server player list for avoided players
     * @return List of detected player names (original case, empty if none)
     */
    public static List<String> checkPlayerList() {
        List<String> detected = new ArrayList<>();
        
        if (client.getNetworkHandler() == null) return detected;
        
        Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
        for (PlayerListEntry entry : playerList) {
            if (entry.getProfile() == null) continue;
            
            String playerName = entry.getProfile().getName();
            if (playerName != null && playersToAvoid.contains(playerName.toLowerCase())) {
                detected.add(playerName);
            }
        }
        
        return detected;
    }
    
    /**
     * Check for nearby avoided players within detection radius
     * @return List of detected player names (original case, empty if none)
     */
    public static List<String> checkNearbyPlayers() {
        List<String> detected = new ArrayList<>();
        
        if (client.world == null || client.player == null) return detected;
        
        for (PlayerEntity player : client.world.getPlayers()) {
            // Skip self
            if (player == client.player) continue;
            
            String playerName = player.getName().getString();
            if (playerName != null && playersToAvoid.contains(playerName.toLowerCase())) {
                double distance = player.distanceTo(client.player);
                if (distance <= nearbyDetectionRadius) {
                    detected.add(playerName);
                    PokeAlertClient.LOGGER.warn("PlayerMonitor: {} detected nearby at {:.1f} blocks",
                        playerName, distance);
                }
            }
        }
        
        return detected;
    }
    
    /**
     * Register callback for player detection events
     * @param callback Consumer receiving the detected player name
     */
    public static void onPlayerDetected(Consumer<String> callback) {
        onPlayerDetectedCallback = callback;
    }
    
    /**
     * Register callback with detection type information
     * @param callback BiConsumer receiving (playerName, detectionType)
     *                 where detectionType is "playerlist" or "nearby"
     */
    public static void onPlayerDetectedWithType(BiConsumer<String, String> callback) {
        onPlayerDetectedWithTypeCallback = callback;
    }
    
    /**
     * Update the list of players to avoid (runtime update)
     * @param players Array of player names
     */
    public static void updatePlayersToAvoid(String[] players) {
        playersToAvoid = Arrays.stream(players)
            .filter(s -> s != null && !s.trim().isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toList());
        
        PokeAlertClient.LOGGER.info("PlayerMonitor: Updated avoid list to {} players", playersToAvoid.size());
    }
    
    /**
     * Check if monitoring is active
     * @return true if monitoring
     */
    public static boolean isMonitoring() {
        return isMonitoring;
    }
    
    /**
     * Get number of players being watched
     * @return Count of players in avoid list
     */
    public static int getWatchedPlayerCount() {
        return playersToAvoid.size();
    }
    
    /**
     * Internal: Schedule the monitoring task
     */
    private static void scheduleMonitorTask() {
        monitorTask = scheduler.scheduleAtFixedRate(() -> {
            if (!isMonitoring) return;
            
            try {
                boolean detected = false;
                
                // Check player list
                if (enablePlayerList) {
                    List<String> listDetected = checkPlayerList();
                    for (String player : listDetected) {
                        PokeAlertClient.LOGGER.warn("PlayerMonitor: {} detected in server player list!", player);
                        triggerCallback(player, "playerlist");
                        detected = true;
                        break; // One detection is enough to trigger safety
                    }
                }
                
                // Check nearby players (only if not already detected)
                if (!detected && enableNearbyDetection) {
                    List<String> nearbyDetected = checkNearbyPlayers();
                    for (String player : nearbyDetected) {
                        PokeAlertClient.LOGGER.warn("PlayerMonitor: {} detected nearby!", player);
                        triggerCallback(player, "nearby");
                        break; // One detection is enough
                    }
                }
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("PlayerMonitor: Error in monitor task", e);
            }
        }, 0, checkIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Internal: Trigger appropriate callbacks
     */
    private static void triggerCallback(String playerName, String detectionType) {
        MinecraftClient.getInstance().execute(() -> {
            if (onPlayerDetectedCallback != null) {
                onPlayerDetectedCallback.accept(playerName);
            }
            if (onPlayerDetectedWithTypeCallback != null) {
                onPlayerDetectedWithTypeCallback.accept(playerName, detectionType);
            }
        });
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
        PokeAlertClient.LOGGER.info("PlayerMonitor: Shutdown complete");
    }
    
    /**
     * Get status string for debugging
     */
    public static String getStatus() {
        if (!isMonitoring) return "Stopped";
        return String.format("Monitoring %d players", playersToAvoid.size());
    }
}

