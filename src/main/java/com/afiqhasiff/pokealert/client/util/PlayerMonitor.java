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
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.lang.reflect.Method;

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
    
    // Player list tracking (for spam prevention and change detection)
    private static final java.util.Set<String> lastKnownOnlinePlayers = new java.util.HashSet<>();
    private static final long PLAYER_LIST_CHECK_INTERVAL_MS = 300000; // 5 minutes
    private static long lastPlayerListCheckTime = 0;
    
    // Callbacks
    private static Consumer<String> onPlayerDetectedCallback;
    private static BiConsumer<String, String> onPlayerDetectedWithTypeCallback;
    private static BiConsumer<java.util.List<String>, java.util.List<String>> onPlayerListChangedCallback; // (joined, left)
    
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
        lastKnownOnlinePlayers.clear(); // Reset tracking on start
        lastPlayerListCheckTime = 0; // Force immediate check on first run
        scheduleMonitorTask();
        
        PokeAlertClient.LOGGER.info("PlayerMonitor: Started monitoring for {} players (list={}, nearby={}, radius={}, listCheckInterval=5min)",
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
     * Check for nearby avoided players within detection radius.
     * Also detects vanished players (in world but not in player list).
     * @return List of detected player names with detection info (original case, empty if none)
     */
    public static List<String> checkNearbyPlayers() {
        List<String> detected = new ArrayList<>();
        
        if (client.world == null || client.player == null) return detected;
        
        // Get player list for vanish detection
        List<String> playerListNames = new ArrayList<>();
        if (client.getNetworkHandler() != null) {
            playerListNames = client.getNetworkHandler().getPlayerList().stream()
                .map(entry -> entry.getProfile() != null ? entry.getProfile().getName() : null)
                .filter(name -> name != null)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        }
        
        for (PlayerEntity player : client.world.getPlayers()) {
            // Skip self
            if (player == client.player) continue;
            
            String playerName = player.getName().getString();
            if (playerName != null && playersToAvoid.contains(playerName.toLowerCase())) {
                double distance = player.distanceTo(client.player);
                if (distance <= nearbyDetectionRadius) {
                    // Check if player is vanished (in world but not in player list)
                    boolean isVanished = !playerListNames.contains(playerName.toLowerCase());
                    if (isVanished) {
                        PokeAlertClient.LOGGER.warn("PlayerMonitor: {} detected nearby and VANISHED at {:.1f} blocks (likely admin!)",
                            playerName, distance);
                    } else {
                        PokeAlertClient.LOGGER.warn("PlayerMonitor: {} detected nearby at {:.1f} blocks",
                            playerName, distance);
                    }
                    detected.add(playerName);
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
     * Register callback for player list changes (joined/left)
     * @param callback BiConsumer receiving (List<String> joined, List<String> left)
     */
    public static void onPlayerListChanged(BiConsumer<List<String>, List<String>> callback) {
        onPlayerListChangedCallback = callback;
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
                long currentTime = System.currentTimeMillis();
                boolean shouldCheckPlayerList = (currentTime - lastPlayerListCheckTime) >= PLAYER_LIST_CHECK_INTERVAL_MS;
                
                // Check player list every 5 minutes (for notifications, no safety stop)
                if (enablePlayerList && shouldCheckPlayerList) {
                    checkPlayerListWithTracking();
                    lastPlayerListCheckTime = currentTime;
                }
                
                // Check nearby players continuously (triggers safety stop immediately)
                if (enableNearbyDetection) {
                    List<String> nearbyDetected = checkNearbyPlayers();
                    for (String player : nearbyDetected) {
                        // Check if this player is also vanished
                        boolean isVanished = isPlayerVanished(player);
                        String detectionType = isVanished ? "nearby_vanished" : "nearby";
                        PokeAlertClient.LOGGER.warn("PlayerMonitor: {} detected nearby (type: {})", player, detectionType);
                        triggerCallback(player, detectionType);
                        break; // One nearby detection triggers safety stop
                    }
                }
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("PlayerMonitor: Error in monitor task", e);
            }
        }, 0, checkIntervalMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Check player list and track changes (joined/left)
     * Sends combined notification if changes detected
     */
    private static void checkPlayerListWithTracking() {
        if (client.getNetworkHandler() == null) return;
        
        // Get current online players (from avoid list)
        java.util.Set<String> currentOnline = new java.util.HashSet<>();
        Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
        for (PlayerListEntry entry : playerList) {
            if (entry.getProfile() == null) continue;
            String playerName = entry.getProfile().getName();
            if (playerName != null && playersToAvoid.contains(playerName.toLowerCase())) {
                currentOnline.add(playerName); // Use original case
            }
        }
        
        // Detect changes
        List<String> joined = new ArrayList<>();
        List<String> left = new ArrayList<>();
        
        for (String player : currentOnline) {
            if (!lastKnownOnlinePlayers.contains(player)) {
                joined.add(player);
            }
        }
        
        for (String player : lastKnownOnlinePlayers) {
            if (!currentOnline.contains(player)) {
                left.add(player);
            }
        }
        
        // Update tracking
        lastKnownOnlinePlayers.clear();
        lastKnownOnlinePlayers.addAll(currentOnline);
        
        // Notify if there are changes
        if (!joined.isEmpty() || !left.isEmpty()) {
            if (onPlayerListChangedCallback != null) {
                MinecraftClient.getInstance().execute(() -> {
                    onPlayerListChangedCallback.accept(joined, left);
                });
            }
        }
    }
    
    /**
     * Check if a player is vanished (exists in world but not in player list)
     * @param playerName Player name to check
     * @return true if player appears to be vanished
     */
    private static boolean isPlayerVanished(String playerName) {
        if (client.world == null || client.getNetworkHandler() == null) return false;
        
        // Check if player exists in world
        boolean inWorld = false;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(playerName)) {
                inWorld = true;
                break;
            }
        }
        
        if (!inWorld) return false; // Not in world, can't be vanished
        
        // Check if player is in player list
        boolean inPlayerList = client.getNetworkHandler().getPlayerList().stream()
            .anyMatch(entry -> entry.getProfile() != null && 
                     entry.getProfile().getName().equalsIgnoreCase(playerName));
        
        // Vanished = in world but NOT in player list
        return !inPlayerList;
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
     * Get current list of avoided players online on the server
     * @return List of player names (original case, empty if none)
     */
    public static List<String> getCurrentOnlineAvoidedPlayers() {
        List<String> online = new ArrayList<>();
        if (client.getNetworkHandler() == null) return online;
        
        Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
        for (PlayerListEntry entry : playerList) {
            if (entry.getProfile() == null) continue;
            String playerName = entry.getProfile().getName();
            if (playerName != null && playersToAvoid.contains(playerName.toLowerCase())) {
                online.add(playerName); // Use original case
            }
        }
        
        return online;
    }
    
    /**
     * Get status string for debugging
     */
    public static String getStatus() {
        if (!isMonitoring) return "Stopped";
        return String.format("Monitoring %d players", playersToAvoid.size());
    }
    
    /**
     * PlayerSuspicionMonitor Get the current player's position in the server tab list (0-indexed)
     * Returns -1 if player not found or unable to determine position
     * 
     * @return Player's position in tab list (0 = first/top, -1 = not found)
     */
    public static int getPlayerPositionInTabList() {
        if (client.getNetworkHandler() == null || client.player == null) {
            PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Cannot get position - network handler or player is null");
            return -1;
        }
        
        String currentPlayerName = client.player.getName().getString();
        if (currentPlayerName == null) {
            PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Cannot get position - player name is null");
            return -1;
        }
        
        // Try to get the sorted player list from PlayerListHud (matches tab list display order)
        // This uses the same sorting logic as the tab list (rank/scoreboard first, then alphabetical)
        List<PlayerListEntry> playerListArray = getSortedPlayerListFromTabList();
        
        if (playerListArray == null || playerListArray.isEmpty()) {
            PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Cannot get position - player list is empty");
            return -1;
        }
        
        int position = -1;
        for (int i = 0; i < playerListArray.size(); i++) {
            PlayerListEntry entry = playerListArray.get(i);
            if (entry.getProfile() == null) continue;
            
            String playerName = entry.getProfile().getName();
            if (playerName != null && playerName.equalsIgnoreCase(currentPlayerName)) {
                position = i;
                break;
            }
        }
        
        // Debug logging
        if (position >= 0) {
            PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Player '{}' found at position {} (0-indexed) / {} (1-indexed) in tab list (total players: {})", 
                currentPlayerName, position, position + 1, playerListArray.size());
            
            // Log players around the current position for verification (5 before, current, 5 after)
            if (playerListArray.size() > 0) {
                int startIdx = Math.max(0, position - 5);
                int endIdx = Math.min(playerListArray.size(), position + 6);
                StringBuilder context = new StringBuilder("Players around position: ");
                for (int i = startIdx; i < endIdx; i++) {
                    PlayerListEntry entry = playerListArray.get(i);
                    if (entry.getProfile() != null) {
                        String name = entry.getProfile().getName();
                        if (name != null) {
                            String marker = (i == position) ? ">>>" : "   ";
                            context.append(String.format("%s[%d]%s ", marker, i, name));
                        }
                    }
                }
                PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] {}", context.toString());
            }
        } else {
            PokeAlertClient.LOGGER.warn("[PlayerSuspicionMonitor] Player '{}' NOT found in tab list (total players: {})", 
                currentPlayerName, playerListArray.size());
        }
        
        return position;
    }
    
    /**
     * Get the sorted player list from PlayerListHud (matches tab list display order)
     * Uses reflection to dynamically find and invoke the private method that collects
     * and sorts player entries exactly as they appear in the tab list.
     * 
     * This method finds the exact method used by PlayerListHud.render() to get the sorted list
     * for rendering, ensuring 100% accuracy in position calculation without manual sorting.
     * 
     * Approach for Minecraft 1.21.1:
     * - Searches for private methods in PlayerListHud that return List<PlayerListEntry>
     * - Verifies the method returns PlayerListEntry objects by invoking it
     * - Uses the found method to get the sorted list (sorted by rank/scoreboard first, then alphabetically)
     * - This ensures we use the exact same method that render() uses, matching tab list display perfectly
     * 
     * Reference: https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/client/gui/hud/PlayerListHud.html
     * 
     * @return Sorted list of PlayerListEntry matching tab list order, or null if unavailable
     */
    private static List<PlayerListEntry> getSortedPlayerListFromTabList() {
        try {
            InGameHud inGameHud = client.inGameHud;
            if (inGameHud == null) {
                PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] InGameHud is null - cannot get sorted player list");
                return null;
            }
            
            // Get PlayerListHud instance
            PlayerListHud playerListHud = inGameHud.getPlayerListHud();
            if (playerListHud == null) {
                PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] PlayerListHud is null - cannot get sorted player list");
                return null;
            }
            
            // In Minecraft 1.21.1, we need to find the exact method that collects and sorts player entries.
            // This method is used by render() to get the sorted list for display.
            // Search for private methods that return List<PlayerListEntry> with no parameters
            java.lang.reflect.Method collectMethod = null;
            java.lang.reflect.Method[] allMethods = PlayerListHud.class.getDeclaredMethods();
            
            for (java.lang.reflect.Method method : allMethods) {
                // Look for private methods that return List and take no parameters
                // (the collection method is typically private)
                if (java.lang.reflect.Modifier.isPrivate(method.getModifiers()) &&
                    method.getReturnType() == List.class && 
                    method.getParameterCount() == 0) {
                    
                    // Verify it returns List<PlayerListEntry> by invoking it
                    try {
                        method.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        List<?> testList = (List<?>) method.invoke(playerListHud);
                        if (testList != null && !testList.isEmpty() && testList.get(0) instanceof PlayerListEntry) {
                            collectMethod = method;
                            PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Found player collection method: {} (returns {} entries)", 
                                method.getName(), testList.size());
                            break;
                        }
                    } catch (Exception ignored) {
                        // Continue searching - this method might not be the right one
                    }
                }
            }
            
            if (collectMethod == null) {
                throw new NoSuchMethodException("No private method found in PlayerListHud that returns List<PlayerListEntry> with no parameters");
            }
            
            // Invoke the found method to get the sorted list
            @SuppressWarnings("unchecked")
            List<PlayerListEntry> sortedList = 
                (List<PlayerListEntry>) collectMethod.invoke(playerListHud);
            
            if (sortedList == null || sortedList.isEmpty()) {
                PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Player collection method returned empty list");
                return null;
            }
            
            return sortedList;
        } catch (NoSuchMethodException e) {
            PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] No method found in PlayerListHud that returns List<PlayerListEntry> - Minecraft version mismatch? {}", e.getMessage());
            return null;
        } catch (IllegalAccessException e) {
            PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] Cannot access collectPlayerEntries() - reflection access denied: {}", e.getMessage());
            return null;
        } catch (java.lang.reflect.InvocationTargetException e) {
            PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] Error invoking collectPlayerEntries(): {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            return null;
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("[PlayerSuspicionMonitor] Failed to get sorted list from PlayerListHud: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * [PlayerSuspicionMonitor] Check if current player is in the top N positions of the tab list
     * 
     * @param topN Number of top positions to check (e.g., 5 for top 5)
     * @return true if player is in top N positions, false otherwise
     */
    public static boolean isPlayerInTopN(int topN) {
        int position = getPlayerPositionInTabList();
        if (position < 0) {
            // If we can't determine position, assume we're safe (not in top N)
            PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Cannot determine position, assuming NOT in top {}", topN);
            return false;
        }
        
        boolean inTopN = position < topN;
        if (inTopN) {
            PokeAlertClient.LOGGER.warn("[PlayerSuspicionMonitor] ⚠️ Player position check - Position: {} (0-indexed), Top {}: {}, In Top {}: TRUE", 
                position, topN, inTopN);
        } else {
            // Log at INFO level every 10th check to show it's working (reduce spam)
            // Use position as a simple counter - log when position % 10 == 0
            if (position > 0 && position % 10 == 0) {
                PokeAlertClient.LOGGER.info("[PlayerSuspicionMonitor] Position check OK - Position: {} (0-indexed), Top {}: {}, Safe", 
                    position, topN);
            } else {
                PokeAlertClient.LOGGER.debug("[PlayerSuspicionMonitor] Player position check - Position: {} (0-indexed), Top {}: {}, In Top {}: false", 
                    position, topN, inTopN);
            }
        }
        
        return inTopN;
    }
}

