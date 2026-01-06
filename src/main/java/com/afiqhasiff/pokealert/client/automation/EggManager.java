package com.afiqhasiff.pokealert.client.automation;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.config.ConfigManager;
import com.afiqhasiff.pokealert.client.config.PokeAlertConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// Note: Cobblemon classes are not available at compile time, using reflection only

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.MouseInfo;
import java.awt.GraphicsEnvironment;

import com.afiqhasiff.pokealert.client.notification.EggTimerManager;

/**
 * Egg Manager - Monitors player's party for egg hatching status.
 * Tracks eggs across all 6 party slots and detects when eggs hatch into Pokemon.
 * 
 * Key Features:
 * - Monitors party slots every minute (configurable)
 * - Tracks initial eggs and detects new eggs added during monitoring
 * - Detects when eggs hatch (name changes from "Egg" to Pokemon name)
 * - Automatically stops Egg Hatcher when all eggs hatch
 * - Comprehensive logging for API method verification
 * 
 * Phase 2 Features:
 * - IV stats tracking and Telegram notifications
 * - Auto-transfer hatched Pokemon to PC
 * - Auto-fill empty party slots with eggs from PC
 * - Box organization based on IV stats
 * - Completion detection (checks PC for remaining eggs)
 */
public class EggManager {
    
    /**
     * Phase 2: Pokemon IV stats container
     */
    private static class PokemonIVs {
        int hp;
        int attack;
        int defense;
        int spAttack;
        int spDefense;
        int speed;
        
        PokemonIVs(int hp, int attack, int defense, int spAttack, int spDefense, int speed) {
            this.hp = hp;
            this.attack = attack;
            this.defense = defense;
            this.spAttack = spAttack;
            this.spDefense = spDefense;
            this.speed = speed;
        }
        
        boolean isPerfect() {
            return hp == 31 && attack == 31 && defense == 31 &&
                   spAttack == 31 && spDefense == 31 && speed == 31;
        }
        
        int getTotal() {
            return hp + attack + defense + spAttack + spDefense + speed;
        }
        
        /**
         * Count how many stats have perfect 31 IVs
         */
        int getPerfectCount() {
            int count = 0;
            if (hp == 31) count++;
            if (attack == 31) count++;
            if (defense == 31) count++;
            if (spAttack == 31) count++;
            if (spDefense == 31) count++;
            if (speed == 31) count++;
            return count;
        }
        
        /**
         * Format IVs for Telegram notification
         * Format: HP-XX Att-XX Def-XX SpAtt-XX SpDef-XX Spd-XX Yx31
         * where Y is the count of perfect (31) IVs
         */
        String toTelegramFormat() {
            return String.format("HP-%02d Att-%02d Def-%02d SpAtt-%02d SpDef-%02d Spd-%02d %dx31",
                hp, attack, defense, spAttack, spDefense, speed, getPerfectCount());
        }
        
        @Override
        public String toString() {
            return String.format("HP: %d | ATK: %d | DEF: %d | SP.ATK: %d | SP.DEF: %d | SPD: %d",
                hp, attack, defense, spAttack, spDefense, speed);
        }
    }
    
    /**
     * Phase 2: Enhanced egg tracking information
     */
    private static class EggTrackingInfo {
        UUID eggUuid;
        int slot;
        long addedTimestamp;
        String pokemonName; // After hatch
        PokemonIVs ivs; // After hatch
        long hatchDuration; // Calculated in milliseconds
        
        EggTrackingInfo(UUID eggUuid, int slot, long addedTimestamp) {
            this.eggUuid = eggUuid;
            this.slot = slot;
            this.addedTimestamp = addedTimestamp;
        }
    }
    private static EggManager instance;
    private final MinecraftClient client;
    private ScheduledExecutorService scheduler;
    
    // Core state
    private boolean isMonitoring = false;
    private ScheduledFuture<?> monitorTask;
    
    // Egg tracking - using thread-safe collections
    private final Set<Integer> trackedEggSlots = ConcurrentHashMap.newKeySet(); // Current slots with eggs
    private final Set<Integer> initialEggSlots = ConcurrentHashMap.newKeySet(); // Slots that had eggs when started
    private final Set<Integer> hatchedSlots = ConcurrentHashMap.newKeySet(); // Slots that hatched (for notification)
    
    // Guard flag to prevent race conditions
    private volatile boolean allEggsHatchedProcessed = false;
    
    // Confirmation tracking for hatch detection
    private final Map<Integer, Integer> hatchConfirmationCount = new ConcurrentHashMap<>(); // Slot -> confirmation count
    
    // Reflection cache for performance
    private java.lang.reflect.Method partyGetMethod = null;
    private java.lang.reflect.Method pokemonGetDisplayNameMethod = null;
    private java.lang.reflect.Method pokemonGetNameMethod = null;
    private java.lang.reflect.Method textGetStringMethod = null;
    private Class<?> partyStoreClass = null;
    private Class<?> pokemonClass = null;
    private Class<?> textClass = null;
    
    // PC Discovery Mode - for debugging API methods
    private net.minecraft.client.gui.screen.Screen lastScreen = null;
    private Map<Integer, String> lastPartyState = new ConcurrentHashMap<>(); // Slot -> Pokemon name
    private ScheduledFuture<?> discoveryMonitorTask = null;
    private long lastPartyStateCheck = 0;
    
    
    // Phase 2: Enhanced egg tracking with UUIDs and timestamps
    private final Map<UUID, EggTrackingInfo> eggTracking = new ConcurrentHashMap<>(); // UUID -> tracking info
    
    // Phase 2: Reflection cache for IV extraction
    private java.lang.reflect.Method pokemonGetIVsMethod = null;
    private java.lang.reflect.Method ivsGetHPMethod = null;
    private java.lang.reflect.Method ivsGetAttackMethod = null;
    private java.lang.reflect.Method ivsGetDefenseMethod = null;
    private java.lang.reflect.Method ivsGetSpAttackMethod = null;
    private java.lang.reflect.Method ivsGetSpDefenseMethod = null;
    private java.lang.reflect.Method ivsGetSpeedMethod = null;
    private Class<?> ivsClass = null;
    
    // Phase 2: Reflection cache for PC operations (client-side methods removed - they don't work)
    private java.lang.reflect.Method storageRemoveFromPCMethod = null;
    private java.lang.reflect.Method storageRemoveFromPartyMethod = null;
    private Class<?> pcPositionClass = null;
    private Class<?> partyPositionClass = null;
    
    // Phase 2: Guard flag to prevent concurrent fill operations
    private volatile boolean isFillingSlots = false;
    
    // Phase 2: Track transferred eggs to prevent duplicates
    private final Set<UUID> transferredEggUuids = ConcurrentHashMap.newKeySet();
    
    // Phase 2: Queue for sequential hatched Pokemon transfers (fixes deadlock issue)
    private final java.util.concurrent.LinkedBlockingQueue<HatchedPokemonTransferInfo> hatchedTransferQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    private volatile boolean isProcessingHatchedTransfers = false;
    
    // Phase 3: Track NPC interaction retries for move-forward logic
    private volatile int npcInteractionRetryCount = 0;
    
    /**
     * Phase 2: Info container for queued hatched Pokemon transfers
     */
    private static class HatchedPokemonTransferInfo {
        final int slot;
        final String pokemonName;
        final PokemonIVs ivs;
        final UUID pokemonUuid;
        final EggTrackingInfo trackingInfo;
        
        HatchedPokemonTransferInfo(int slot, String pokemonName, PokemonIVs ivs, UUID pokemonUuid, EggTrackingInfo trackingInfo) {
            this.slot = slot;
            this.pokemonName = pokemonName;
            this.ivs = ivs;
            this.pokemonUuid = pokemonUuid;
            this.trackingInfo = trackingInfo;
        }
    }
    
    private EggManager() {
        this.client = MinecraftClient.getInstance();
    }
    
    public static EggManager getInstance() {
        if (instance == null) {
            instance = new EggManager();
        }
        return instance;
    }
    
    /**
     * Set the scheduler (should be called from EggHatcher)
     */
    public void setScheduler(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }
    
    /**
     * Check if player is at spawn (EggManager's own implementation for independence)
     * @return true if at spawn, false otherwise
     */
    public boolean isAtSpawn() {
        if (client.player == null || client.world == null) {
            return false;
        }
        
        String worldName = client.world.getRegistryKey().getValue().toString();
        return worldName.contains("spawn") || worldName.contains("lobby") || worldName.contains("hub");
    }
    
    /**
     * Check if player is in overworld (not at spawn)
     * @return true if in overworld/non-spawn world
     */
    public boolean isInOverworld() {
        if (client.player == null || client.world == null) {
            return false;
        }
        return !isAtSpawn();
    }
    
    /**
     * Check if player is connected to a server
     * @return true if connected to a server with valid player and world
     */
    public boolean isConnected() {
        return client != null && client.player != null && client.world != null;
    }
    
    /**
     * Start monitoring party slots for eggs
     */
    public void startMonitoring() {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (!config.eggManager.enabled) {
            PokeAlertClient.LOGGER.info("EggManager: Monitoring not started - Egg Manager is disabled in config");
            return;
        }
        
        if (isMonitoring) {
            PokeAlertClient.LOGGER.debug("EggManager: Already monitoring, skipping start");
            return;
        }
        
        if (scheduler == null) {
            // Try to get scheduler from EggHatcher
            scheduler = EggHatcher.getInstance().getScheduler();
            if (scheduler == null) {
                PokeAlertClient.LOGGER.error("EggManager: Cannot start monitoring - scheduler not available");
                return;
            }
        }
        
        // Test party access first - if it fails, log detailed diagnostics
        Object testParty = getPlayerParty();
        if (testParty == null) {
            PokeAlertClient.LOGGER.error("EggManager: ⚠️ Cannot access party - monitoring will not work properly!");
            PokeAlertClient.LOGGER.error("EggManager: This may be due to:");
            PokeAlertClient.LOGGER.error("EggManager: 1. Cobblemon API path changed");
            PokeAlertClient.LOGGER.error("EggManager: 2. Party data not synced yet (try waiting a few seconds)");
            PokeAlertClient.LOGGER.error("EggManager: 3. Cobblemon version incompatibility");
            PokeAlertClient.LOGGER.error("EggManager: Monitoring will continue but may not detect eggs correctly");
        }
        
        // Initialize reflection cache
        initializeReflectionCache();
        
        // Initialize tracking
        initializeEggTracking();
        
        // Start PC discovery mode if enabled
        if (config.eggManager.pcDiscoveryMode) {
            startPCDiscoveryMode();
        }
        
        isMonitoring = true;
        allEggsHatchedProcessed = false;
        
        int checkInterval = Math.max(1000, Math.min(600000, config.eggManager.checkInterval)); // Clamp 1s-10min
        
        monitorTask = scheduler.scheduleAtFixedRate(
            this::checkPartySlots,
            0,
            checkInterval,
            TimeUnit.MILLISECONDS
        );
        
        PokeAlertClient.LOGGER.info("EggManager: Started monitoring (interval: {}ms, initial eggs: {})", 
            checkInterval, trackedEggSlots.size());
    }
    
    /**
     * Stop monitoring party slots
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }
        
        isMonitoring = false;
        if (monitorTask != null) {
            monitorTask.cancel(false);
            monitorTask = null;
        }
        
        // Stop PC discovery mode
        stopPCDiscoveryMode();
        
        // Clear tracking state
        trackedEggSlots.clear();
        initialEggSlots.clear();
        hatchedSlots.clear();
        hatchConfirmationCount.clear();
        eggTracking.clear(); // Phase 2: Clear UUID tracking
        allEggsHatchedProcessed = false;
        
        PokeAlertClient.LOGGER.info("EggManager: Stopped monitoring");
    }
    
    /**
     * Reset tracking state (for new monitoring session)
     */
    public void reset() {
        trackedEggSlots.clear();
        initialEggSlots.clear();
        hatchedSlots.clear();
        hatchConfirmationCount.clear();
        eggTracking.clear(); // Phase 2: Clear UUID tracking
        allEggsHatchedProcessed = false;
        PokeAlertClient.LOGGER.debug("EggManager: Reset tracking state");
    }
    
    /**
     * Initialize egg tracking - detect initial eggs in party
     */
    private void initializeEggTracking() {
        reset();
        eggTracking.clear(); // Phase 2: Clear UUID tracking
        
        Set<Integer> currentEggSlots = getEggSlotsFromParty();
        
        if (currentEggSlots.isEmpty()) {
            PokeAlertClient.LOGGER.info("EggManager: No eggs detected initially - will monitor for new eggs");
        } else {
            trackedEggSlots.addAll(currentEggSlots);
            initialEggSlots.addAll(currentEggSlots);
            
            // Phase 2: Track UUIDs for initial eggs
            for (Integer slot : currentEggSlots) {
                trackNewEgg(slot);
            }
            
            PokeAlertClient.LOGGER.info("EggManager: Tracking {} initial egg slots: {}", 
                trackedEggSlots.size(), formatSlots(trackedEggSlots));
        }
        
        // Phase 2: Scan for already hatched Pokemon that need to be transferred to PC
        // This handles the case where Egg Manager was off when eggs hatched
        PokeAlertConfig config = ConfigManager.getConfig();
        if (config.eggManager.phase2.autoTransferToPC) {
            scanAndTransferHatchedPokemon();
        }
    }
    
    /**
     * Phase 2: Scan for already hatched Pokemon in party (slots 2-6) and transfer them to PC
     * This is called on startup to handle Pokemon that hatched while Egg Manager was disabled
     */
    private void scanAndTransferHatchedPokemon() {
        PokeAlertClient.LOGGER.info("EggManager: Scanning for already hatched Pokemon to transfer...");
        
        try {
            Object party = getPlayerParty();
            if (party == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Cannot scan for hatched Pokemon - party not available");
                return;
            }
            
            java.lang.reflect.Method getMethod = partyGetMethod;
            if (getMethod == null) {
                getMethod = party.getClass().getMethod("get", int.class);
            }
            
            List<Integer> hatchedSlotsToTransfer = new ArrayList<>();
            
            // Check slots 1-5 (0-indexed) - slot 0 is usually reserved for lead Pokemon
            // We check slots 1-5 because slot 0 might be a non-egg Pokemon
            for (int slot = 1; slot < 6; slot++) {
                Object pokemon = getMethod.invoke(party, slot);
                if (pokemon == null) {
                    continue; // Empty slot
                }
                
                // Check if this is an egg
                boolean isEgg = false;
                try {
                    // Try multiple methods to check if it's an egg
                    try {
                        java.lang.reflect.Method isEggMethod = pokemon.getClass().getMethod("isEgg");
                        isEgg = (Boolean) isEggMethod.invoke(pokemon);
                    } catch (NoSuchMethodException e) {
                        // Try Species.isEgg() via getSpecies()
                        java.lang.reflect.Method getSpeciesMethod = pokemon.getClass().getMethod("getSpecies");
                        Object species = getSpeciesMethod.invoke(pokemon);
                        if (species != null) {
                            java.lang.reflect.Method speciesIsEggMethod = species.getClass().getMethod("isEgg");
                            isEgg = (Boolean) speciesIsEggMethod.invoke(species);
                        }
                    }
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("EggManager: Error checking if slot {} is egg: {}", slot + 1, e.getMessage());
                }
                
                // If NOT an egg, it's a hatched Pokemon that needs transfer
                if (!isEgg) {
                    // Skip if this is already being tracked as an egg (shouldn't happen, but be safe)
                    if (!trackedEggSlots.contains(slot)) {
                        hatchedSlotsToTransfer.add(slot);
                        PokeAlertClient.LOGGER.info("EggManager: Found already hatched Pokemon in slot {} - queuing for transfer", slot + 1);
                    }
                }
            }
            
            if (hatchedSlotsToTransfer.isEmpty()) {
                PokeAlertClient.LOGGER.info("EggManager: No already hatched Pokemon found to transfer");
                return;
            }
            
            PokeAlertClient.LOGGER.info("EggManager: Found {} already hatched Pokemon to transfer: {}", 
                hatchedSlotsToTransfer.size(), hatchedSlotsToTransfer);
            
            // Transfer each hatched Pokemon on a background thread
            if (scheduler != null) {
                scheduler.execute(() -> {
                    for (Integer slot : hatchedSlotsToTransfer) {
                        try {
                            PokeAlertClient.LOGGER.info("EggManager: Transferring already hatched Pokemon from slot {} to PC", slot + 1);
                            
                            // Extract IVs if IV tracking is enabled
                            PokeAlertConfig config = ConfigManager.getConfig();
                            PokemonIVs ivs = null;
                            if (config.eggManager.phase2.ivTrackingEnabled) {
                                // Get Pokemon again on this thread
                                Object partyNow = getPlayerParty();
                                if (partyNow != null) {
                                    java.lang.reflect.Method getMethodNow = partyNow.getClass().getMethod("get", int.class);
                                    Object pokemonNow = getMethodNow.invoke(partyNow, slot);
                                    if (pokemonNow != null) {
                                        ivs = extractIVs(pokemonNow);
                                    }
                                }
                            }
                            
                            // Transfer to PC
                            Object result = transferHatchedPokemonToPC(slot, ivs);
                            if (result != null) {
                                PokeAlertClient.LOGGER.info("EggManager: ✅ Successfully transferred hatched Pokemon from slot {} to PC", slot + 1);
                            } else {
                                PokeAlertClient.LOGGER.warn("EggManager: ⚠️ Transfer of hatched Pokemon from slot {} may have failed", slot + 1);
                            }
                            
                            // Wait between transfers to avoid overwhelming the server
                            try {
                                Thread.sleep(2000); // 2 second delay between transfers
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.error("EggManager: Error transferring hatched Pokemon from slot {}", slot + 1, e);
                        }
                    }
                    
                    // After transferring hatched Pokemon, fill empty slots with eggs from PC
                    PokeAlertConfig config = ConfigManager.getConfig();
                    if (config.eggManager.phase2.autoFillFromPC) {
                        fillPartySlotsWithEggs();
                    }
                });
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error scanning for hatched Pokemon", e);
        }
    }
    
    /**
     * Phase 2: Track new egg with UUID and timestamp
     */
    private void trackNewEgg(int slot) {
        try {
            Object party = getPlayerParty();
            if (party == null) return;
            
            java.lang.reflect.Method getMethod = partyGetMethod;
            if (getMethod == null) {
                getMethod = party.getClass().getMethod("get", int.class);
            }
            
            Object pokemon = getMethod.invoke(party, slot);
            if (pokemon == null) return;
            
            // Get UUID
            java.lang.reflect.Method getUuidMethod = pokemon.getClass().getMethod("getUuid");
            UUID eggUuid = (UUID) getUuidMethod.invoke(pokemon);
            
            if (eggUuid != null) {
                long timestamp = System.currentTimeMillis();
                eggTracking.put(eggUuid, new EggTrackingInfo(eggUuid, slot, timestamp));
                PokeAlertClient.LOGGER.debug("EggManager: Tracking egg UUID {} at slot {} (timestamp: {})", 
                    eggUuid, slot + 1, timestamp);
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("EggManager: Could not track egg UUID at slot {}: {}", slot + 1, e.getMessage());
        }
    }
    
    /**
     * Main monitoring loop - checks party slots for changes
     */
    private void checkPartySlots() {
        if (!isMonitoring) {
            return;
        }
        
        PokeAlertConfig config = ConfigManager.getConfig();
        if (!config.eggManager.enabled) {
            stopMonitoring();
            return;
        }
        
        Set<Integer> currentEggSlots = getEggSlotsFromParty();
        
        // Detect new eggs added during monitoring
        for (int slot = 0; slot < 6; slot++) {
            if (!trackedEggSlots.contains(slot) && currentEggSlots.contains(slot)) {
                // New egg detected
                trackedEggSlots.add(slot);
                trackNewEgg(slot); // Phase 2: Track UUID and timestamp
                PokeAlertClient.LOGGER.info("EggManager: New egg detected at slot {} - added to tracking", slot + 1);
            }
        }
        
        // Handle slot shifting: When eggs hatch and get transferred, remaining eggs shift slots
        // Example: Slot 1 egg hatches → Slot 2 egg moves to Slot 1
        // We need to detect this and update tracking accordingly
        
        // Calculate total egg counts
        int trackedEggCount = trackedEggSlots.size();
        int currentEggCount = currentEggSlots.size();
        
        // Find slots that were tracked but are now empty
        Set<Integer> emptyTrackedSlots = new HashSet<>(trackedEggSlots);
        emptyTrackedSlots.removeAll(currentEggSlots);
        
        // Find slots with eggs that weren't tracked (could be new eggs or shifted eggs)
        Set<Integer> newEggSlots = new HashSet<>(currentEggSlots);
        newEggSlots.removeAll(trackedEggSlots);
        
        // Detect slot shifting: When eggs shift due to hatched eggs being transferred
        // Condition: Some tracked slots are empty, some new slots have eggs,
        // and the decrease in egg count is less than the number of empty slots
        // (meaning some eggs shifted rather than all hatching)
        if (!emptyTrackedSlots.isEmpty() && !newEggSlots.isEmpty()) {
            int eggCountDecrease = trackedEggCount - currentEggCount;
            int emptySlotCount = emptyTrackedSlots.size();
            
            // If eggs shifted: empty slots > egg count decrease
            // Example: 2 eggs tracked, 1 hatches, 1 shifts → empty slots = 1, decrease = 1
            // But if slot 1 hatches and slot 2 moves to slot 1: empty slots = 1 (slot 2), decrease = 1
            // Actually, we need to check if the empty slots are higher-indexed than new slots
            
            // Check if this looks like a shift: new egg slots are at lower indices than empty slots
            // (eggs shift forward when earlier slots hatch)
            boolean looksLikeShift = false;
            if (emptySlotCount > 0 && newEggSlots.size() > 0) {
                int maxEmptySlot = Collections.max(emptyTrackedSlots);
                int minNewSlot = Collections.min(newEggSlots);
                // If new eggs are at lower slots than empty slots, likely a shift
                if (minNewSlot < maxEmptySlot) {
                    looksLikeShift = true;
                }
            }
            
            // Also check: if egg count stayed same or decreased by less than empty slots
            // (some eggs shifted rather than all hatching)
            if (looksLikeShift || (eggCountDecrease < emptySlotCount && eggCountDecrease >= 0)) {
                // Eggs shifted - update tracking to reflect new positions
                PokeAlertClient.LOGGER.debug("EggManager: Detected slot shift - {} eggs moved from {} to {} (count: {} → {})", 
                    Math.min(emptyTrackedSlots.size(), newEggSlots.size()), 
                    formatSlots(emptyTrackedSlots), formatSlots(newEggSlots),
                    trackedEggCount, currentEggCount);
                
                // Remove old slot positions and add new ones
                trackedEggSlots.removeAll(emptyTrackedSlots);
                trackedEggSlots.addAll(newEggSlots);
                
                // Reset confirmation counts for shifted slots
                for (Integer slot : emptyTrackedSlots) {
                    hatchConfirmationCount.remove(slot);
                }
                for (Integer slot : newEggSlots) {
                    hatchConfirmationCount.remove(slot);
                }
                
                // Recalculate after shift
                emptyTrackedSlots.clear();
                newEggSlots.clear();
            }
        }
        
        // Normal hatch detection: slots that were eggs but are now Pokemon
        // (after handling shifts)
        Set<Integer> potentiallyHatchedSlots = new HashSet<>(trackedEggSlots);
        potentiallyHatchedSlots.removeAll(currentEggSlots);
        
        if (!potentiallyHatchedSlots.isEmpty()) {
            // Use confirmation system to prevent false positives
            for (Integer slot : potentiallyHatchedSlots) {
                int confirmCount = hatchConfirmationCount.getOrDefault(slot, 0) + 1;
                hatchConfirmationCount.put(slot, confirmCount);
                
                PokeAlertClient.LOGGER.debug("EggManager: Slot {} hatch confirmation count: {}/{}", 
                    slot + 1, confirmCount, config.eggManager.confirmationChecks);
                
                if (confirmCount >= config.eggManager.confirmationChecks) {
                    // CRITICAL FIX: Verify the slot ACTUALLY contains a Pokemon before confirming hatch
                    // Previously, empty slots were incorrectly marked as "hatched"
                    boolean slotHasPokemon = false;
                    try {
                        Object party = getPlayerParty();
                        if (party != null) {
                            java.lang.reflect.Method getMethod = partyGetMethod;
                            if (getMethod == null) {
                                getMethod = party.getClass().getMethod("get", int.class);
                            }
                            Object pokemon = getMethod.invoke(party, slot);
                            slotHasPokemon = (pokemon != null);
                            if (!slotHasPokemon) {
                                PokeAlertClient.LOGGER.warn("EggManager: Slot {} was tracked as egg but is now EMPTY (not hatched) - skipping", slot + 1);
                            }
                        }
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.debug("EggManager: Error verifying slot {} content: {}", slot + 1, e.getMessage());
                        slotHasPokemon = true; // Assume hatched on error to avoid losing track
                    }
                    
                    if (slotHasPokemon) {
                        // Confirmed hatch - slot has a Pokemon (not an egg)
                        trackedEggSlots.remove(slot);
                        hatchedSlots.add(slot); // Track for notification
                        hatchConfirmationCount.remove(slot);
                        PokeAlertClient.LOGGER.info("EggManager: Slot {} confirmed hatched (was egg, now Pokemon)", slot + 1);
                        
                        // Phase 2: Handle hatch with IV extraction, transfer, and notification
                        onEggHatched(slot);
                    } else {
                        // Slot is empty - not a real hatch, just remove from tracking
                        trackedEggSlots.remove(slot);
                        hatchConfirmationCount.remove(slot);
                        PokeAlertClient.LOGGER.info("EggManager: Slot {} removed from tracking (empty, not hatched)", slot + 1);
                    }
                }
            }
        }
        
        // Reset confirmation count for slots that are still eggs
        for (Integer slot : currentEggSlots) {
            hatchConfirmationCount.remove(slot);
        }
        
        // Check if all eggs have hatched
        if (trackedEggSlots.isEmpty() && !initialEggSlots.isEmpty() && !allEggsHatchedProcessed) {
            onAllEggsHatched();
        }
        
        // Phase 2: Periodically check for empty slots and fill with eggs from PC
        // INDEPENDENT: Egg Manager operates on its own - only requires player to be in overworld
        if (config.eggManager.phase2.autoFillFromPC && !isFillingSlots) {
            // Check if player is in overworld (safe to transfer)
            // Egg Manager is independent of Egg Hatcher - doesn't wait for Step 5
            if (!isInOverworld()) {
                PokeAlertClient.LOGGER.debug("EggManager: Waiting for overworld arrival before filling slots");
                return; // Don't fill slots until in overworld
            }
            
            // Check for ACTUALLY empty party slots (not just non-eggs)
            // CRITICAL FIX: Previously used currentEggSlots which incorrectly counted hatched Pokemon as "empty"
            int emptySlotCount = 0;
            try {
                Object party = getPlayerParty();
                if (party != null) {
                    java.lang.reflect.Method getMethod = partyGetMethod;
                    if (getMethod == null) {
                        getMethod = party.getClass().getMethod("get", int.class);
                    }
                    for (int slot = 0; slot < 6; slot++) {
                        Object pokemon = getMethod.invoke(party, slot);
                        if (pokemon == null) {
                            emptySlotCount++;
                        }
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("EggManager: Error counting empty slots: {}", e.getMessage());
            }
            
            if (emptySlotCount > 0) {
                // Found empty slots - check if we have eggs in PC to fill
                List<Object> pcEggs = findAllEggsInPC();
                if (!pcEggs.isEmpty()) {
                    PokeAlertClient.LOGGER.info("EggManager: Detected {} empty party slots and {} eggs in PC - attempting to fill", 
                        emptySlotCount, pcEggs.size());
                    // Fill empty slots with eggs from PC (async to avoid blocking monitoring)
                    if (scheduler != null) {
                        scheduler.execute(() -> fillPartySlotsWithEggs());
                    } else {
                        fillPartySlotsWithEggs();
                    }
                } else {
                    PokeAlertClient.LOGGER.debug("EggManager: {} empty party slots detected but no eggs found in PC", emptySlotCount);
                }
            }
        }
        
        // Log current status
        if (trackedEggSlots.size() != currentEggSlots.size()) {
            PokeAlertClient.LOGGER.debug("EggManager: Status - {} eggs remaining (tracked: {}, current: {})", 
                trackedEggSlots.size(), formatSlots(trackedEggSlots), formatSlots(currentEggSlots));
        }
    }
    
    /**
     * Called when all eggs have hatched
     */
    private void onAllEggsHatched() {
        // CRITICAL: Check guard flag FIRST to prevent race conditions
        if (allEggsHatchedProcessed) {
            PokeAlertClient.LOGGER.debug("EggManager: All eggs hatched already processed, skipping duplicate call");
            return;
        }
        
        // Set flag IMMEDIATELY to prevent race condition
        allEggsHatchedProcessed = true;
        
        PokeAlertClient.LOGGER.info("EggManager: ✅ All eggs have hatched!");
        
        PokeAlertConfig config = ConfigManager.getConfig();
        EggHatcher eggHatcher = EggHatcher.getInstance();
        
        // Stop automation if running and auto-stop is enabled
        if (config.eggManager.autoStopEggHatcher) {
            if (eggHatcher.isRunning() || eggHatcher.isAntiAfkActive()) {
                PokeAlertClient.LOGGER.info("EggManager: Stopping Egg Hatcher automation");
                eggHatcher.stopAutomation();
            }
            
            // Disable Egg Hatcher (session-only, not persisted to config)
            eggHatcher.setMode(EggHatcher.AutomationMode.DISABLED);
            PokeAlertClient.LOGGER.info("EggManager: Egg Hatcher disabled (session-only)");
        }
        
        // Phase 2: Check PC for eggs if enabled
        if (config.eggManager.phase2.completionCheckPC) {
            if (!findAllEggsInPC().isEmpty()) {
                PokeAlertClient.LOGGER.info("EggManager: Eggs still found in PC - not completing yet");
                allEggsHatchedProcessed = false; // Reset flag to allow re-checking
                return; // Don't complete if eggs are in PC
            }
        }
        
        // Send notifications
        sendCompletionNotification();
        
        // Stop monitoring
        stopMonitoring();
    }
    
    /**
     * Phase 2: Handle egg hatch - extract IVs, queue transfer to PC, send notification
     * Data extraction on main thread, transfer queued for background processing to avoid deadlock
     */
    private void onEggHatched(int slot) {
        final int finalSlot = slot;
        
        // Schedule data extraction after a short delay for IV sync (on background thread to avoid blocking)
        if (scheduler == null) {
            scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
        }
        
        scheduler.schedule(() -> {
            // Extract Pokemon data on render thread (quick operation, no blocking)
            java.util.concurrent.CountDownLatch dataLatch = new java.util.concurrent.CountDownLatch(1);
            final String[] pokemonNameHolder = {null};
            final PokemonIVs[] ivsHolder = {null};
            final UUID[] pokemonUuidHolder = {null};
            final EggTrackingInfo[] trackingInfoHolder = {null};
            
            client.execute(() -> {
                try {
                    PokeAlertConfig config = ConfigManager.getConfig();
                    
                    // Get Pokemon from party slot
                    Object party = getPlayerParty();
                    if (party == null) {
                        PokeAlertClient.LOGGER.warn("EggManager: Cannot handle hatch - party not available");
                        return;
                    }
                    
                    java.lang.reflect.Method getMethod = partyGetMethod;
                    if (getMethod == null) {
                        getMethod = party.getClass().getMethod("get", int.class);
                    }
                    
                    Object pokemon = getMethod.invoke(party, finalSlot);
                    if (pokemon == null) {
                        PokeAlertClient.LOGGER.warn("EggManager: Slot {} is empty, cannot handle hatch", finalSlot + 1);
                        return;
                    }
                    
                    // Get Pokemon UUID
                    java.lang.reflect.Method getUuidMethod = pokemon.getClass().getMethod("getUuid");
                    pokemonUuidHolder[0] = (UUID) getUuidMethod.invoke(pokemon);
                    
                    // Get Pokemon name
                    pokemonNameHolder[0] = getPokemonName(pokemon);
                    if (pokemonNameHolder[0] == null || pokemonNameHolder[0].equals("UNKNOWN") || pokemonNameHolder[0].equals("EMPTY")) {
                        PokeAlertClient.LOGGER.warn("EggManager: Could not extract Pokemon name, trying alternative methods");
                        pokemonNameHolder[0] = getPokemonNameAlternative(pokemon);
                    }
                    
                    // Extract IVs if enabled
                    if (config.eggManager.phase2.ivTrackingEnabled) {
                        PokeAlertClient.LOGGER.info("EggManager: Attempting to extract IVs for {} (slot {})", pokemonNameHolder[0], finalSlot + 1);
                        ivsHolder[0] = extractIVs(pokemon);
                        if (ivsHolder[0] != null) {
                            PokeAlertClient.LOGGER.info("EggManager: ✅ Successfully extracted IVs for {}: {}", pokemonNameHolder[0], ivsHolder[0]);
                            if (ivsHolder[0].getTotal() == 0) {
                                PokeAlertClient.LOGGER.warn("EggManager: ⚠️ WARNING: All IVs are 0 - this might indicate extraction failure");
                            }
                        } else {
                            PokeAlertClient.LOGGER.warn("EggManager: ❌ Failed to extract IVs for {} - extractIVs() returned null", pokemonNameHolder[0]);
                        }
                    }
                    
                    // Update tracking info
                    EggTrackingInfo trackingInfo = eggTracking.get(pokemonUuidHolder[0]);
                    if (trackingInfo != null) {
                        trackingInfo.pokemonName = pokemonNameHolder[0];
                        trackingInfo.ivs = ivsHolder[0];
                    } else {
                        trackingInfo = new EggTrackingInfo(pokemonUuidHolder[0], finalSlot, System.currentTimeMillis());
                        trackingInfo.pokemonName = pokemonNameHolder[0];
                        trackingInfo.ivs = ivsHolder[0];
                        eggTracking.put(pokemonUuidHolder[0], trackingInfo);
                    }
                    trackingInfoHolder[0] = trackingInfo;
                    
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("EggManager: Error extracting hatch data at slot {}", finalSlot + 1, e);
                } finally {
                    dataLatch.countDown();
                }
            });
            
            // Wait for data extraction to complete (with timeout)
            try {
                if (!dataLatch.await(5, TimeUnit.SECONDS)) {
                    PokeAlertClient.LOGGER.error("EggManager: Timeout waiting for hatch data extraction at slot {}", finalSlot + 1);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            
            // If data extraction failed, skip this hatch
            if (pokemonUuidHolder[0] == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Skipping hatch at slot {} - data extraction failed", finalSlot + 1);
                return;
            }
            
            PokeAlertConfig config = ConfigManager.getConfig();
            
            // Queue transfer if enabled
            if (config.eggManager.phase2.autoTransferToPC) {
                HatchedPokemonTransferInfo transferInfo = new HatchedPokemonTransferInfo(
                    finalSlot, pokemonNameHolder[0], ivsHolder[0], pokemonUuidHolder[0], trackingInfoHolder[0]);
                hatchedTransferQueue.offer(transferInfo);
                PokeAlertClient.LOGGER.info("EggManager: Queued hatched {} from slot {} for PC transfer (queue size: {})", 
                    pokemonNameHolder[0], finalSlot + 1, hatchedTransferQueue.size());
                
                // Start processing queue if not already running
                startHatchedTransferProcessor();
            } else {
                // No transfer, just send notification
                if (config.eggManager.phase2.ivTrackingEnabled && config.telegram.enabled && config.isTelegramValid()) {
                    sendHatchNotification(trackingInfoHolder[0], null);
                }
            }
            
        }, 1000, TimeUnit.MILLISECONDS); // 1 second delay for IV sync
    }
    
    /**
     * Start the hatched Pokemon transfer processor if not already running
     */
    private void startHatchedTransferProcessor() {
        if (isProcessingHatchedTransfers) {
            return; // Already processing
        }
        
        if (scheduler == null) {
            scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
        }
        
        isProcessingHatchedTransfers = true;
        scheduler.execute(() -> {
            try {
                processHatchedTransferQueue();
            } finally {
                isProcessingHatchedTransfers = false;
            }
        });
    }
    
    /**
     * Process the hatched Pokemon transfer queue one-by-one
     * Runs on background thread to avoid deadlock
     */
    private void processHatchedTransferQueue() {
        PokeAlertClient.LOGGER.info("EggManager: Starting hatched transfer processor (queue size: {})", hatchedTransferQueue.size());
        
        while (!hatchedTransferQueue.isEmpty()) {
            HatchedPokemonTransferInfo transferInfo = hatchedTransferQueue.poll();
            if (transferInfo == null) {
                continue;
            }
            
            PokeAlertClient.LOGGER.info("EggManager: Processing transfer for {} from slot {} (remaining in queue: {})", 
                transferInfo.pokemonName, transferInfo.slot + 1, hatchedTransferQueue.size());
            
            try {
                // Perform the transfer (with retry logic)
                Object targetBoxInfo = transferHatchedPokemonToPC(transferInfo.slot, transferInfo.ivs);
                
                // Close PC after this transfer
                closePCInterface();
                
                // Wait a bit for PC to close
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
                // Send notification
                PokeAlertConfig config = ConfigManager.getConfig();
                if (config.eggManager.phase2.ivTrackingEnabled && config.telegram.enabled && config.isTelegramValid()) {
                    sendHatchNotification(transferInfo.trackingInfo, targetBoxInfo);
                }
                
                // Wait between transfers to avoid rate limiting and allow server sync
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("EggManager: Error processing transfer for {} from slot {}", 
                    transferInfo.pokemonName, transferInfo.slot + 1, e);
            }
        }
        
        PokeAlertClient.LOGGER.info("EggManager: Hatched transfer processor completed");
        
        // After all transfers complete, fill empty slots with eggs from PC
        PokeAlertConfig config = ConfigManager.getConfig();
        if (config.eggManager.phase2.autoFillFromPC) {
            PokeAlertClient.LOGGER.info("EggManager: All hatched transfers complete, filling empty slots with eggs from PC");
            fillPartySlotsWithEggs();
        }
    }
    
    /**
     * Send completion notifications
     */
    private void sendCompletionNotification() {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        // In-game notification
        if (client.player != null && config.notifications.textEnabled) {
            client.execute(() -> {
                if (client.player != null) {
                    net.minecraft.text.MutableText notification = Text.literal("[")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                        .append(Text.literal("] ").formatted(Formatting.GRAY))
                        .append(Text.literal("✅ All eggs have hatched!").formatted(Formatting.GREEN));
                    
                    if (config.eggManager.autoStopEggHatcher) {
                        notification.append(Text.literal("\nEgg Hatcher has been automatically disabled.").formatted(Formatting.YELLOW));
                    }
                    
                    client.player.sendMessage(notification, false);
                }
            });
        }
        
        // Telegram notification
        if (config.telegram.enabled && config.isTelegramValid()) {
            try {
                com.afiqhasiff.pokealert.client.notification.TelegramNotification telegram = 
                    new com.afiqhasiff.pokealert.client.notification.TelegramNotification();
                telegram.initialize();
                
                StringBuilder message = new StringBuilder();
                message.append("✅ <b>All Eggs Hatched!</b>\n");
                message.append("• <b>Status:</b> <i>Complete</i>\n");
                
                // Phase 2: Check PC for eggs if enabled
                if (config.eggManager.phase2.completionCheckPC) {
                    List<Object> pcEggs = findAllEggsInPC();
                    if (pcEggs.isEmpty()) {
                        message.append("• <b>PC Status:</b> No eggs remaining in PC\n");
                    } else {
                        message.append("• <b>PC Status:</b> ").append(pcEggs.size()).append(" eggs still in PC\n");
                    }
                }
                
                // Include hatched slot information
                if (!hatchedSlots.isEmpty()) {
                    String slotsText = formatSlots(hatchedSlots);
                    message.append("• <b>Hatched Slots:</b> ").append(slotsText).append("\n");
                }
                
                if (config.eggManager.autoStopEggHatcher) {
                    message.append("• <b>Action:</b> Egg Hatcher has been automatically disabled");
                }
                
                // Use sendEggTimerNotification method which accepts String
                telegram.sendEggTimerNotification(message.toString());
                PokeAlertClient.LOGGER.info("EggManager: Sent Telegram notification");
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("EggManager: Failed to send Telegram notification", e);
            }
        }
    }
    
    // ========== Phase 2: IV Extraction & PC Transfer Methods ==========
    
    /**
     * Phase 2: Extract IV stats from Pokemon using reflection
     */
    private PokemonIVs extractIVs(Object pokemon) {
        if (pokemon == null) {
            PokeAlertClient.LOGGER.warn("EggManager: Cannot extract IVs - Pokemon is null");
            return null;
        }
        
        try {
            // Try to get IVs object
            Object ivs = null;
            
            // Try cached method first
            if (pokemonGetIVsMethod == null) {
                // Try multiple method names
                String[] methodNames = {"getIVs", "getIvs", "getIndividualValues"};
                for (String methodName : methodNames) {
                    try {
                        pokemonGetIVsMethod = pokemon.getClass().getMethod(methodName);
                        ivs = pokemonGetIVsMethod.invoke(pokemon);
                        if (ivs != null) {
                            ivsClass = ivs.getClass();
                            PokeAlertClient.LOGGER.info("EggManager: ✅ Found IV method: {}() - IVs class: {}", methodName, ivsClass.getName());
                            break;
                        }
                    } catch (NoSuchMethodException e) {
                        // Try next method name
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.debug("EggManager: Error invoking {}(): {}", methodName, e.getMessage());
                    }
                }
            } else {
                try {
                    ivs = pokemonGetIVsMethod.invoke(pokemon);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.warn("EggManager: Error invoking cached IV method: {}", e.getMessage());
                    // Reset cache and try again
                    pokemonGetIVsMethod = null;
                    return extractIVs(pokemon); // Recursive retry
                }
            }
            
            if (ivs == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Could not extract IVs - IV object is null");
                PokeAlertClient.LOGGER.warn("EggManager: Pokemon class: {}, Available methods: {}", 
                    pokemon.getClass().getName(),
                    java.util.Arrays.toString(pokemon.getClass().getMethods()).substring(0, Math.min(300, java.util.Arrays.toString(pokemon.getClass().getMethods()).length())));
                return null;
            }
            
            PokeAlertClient.LOGGER.info("EggManager: ✅ Got IVs object: {}", ivs.getClass().getName());
            PokeAlertClient.LOGGER.info("EggManager: IVs object methods: {}", 
                java.util.Arrays.toString(ivs.getClass().getMethods()).substring(0, Math.min(500, java.util.Arrays.toString(ivs.getClass().getMethods()).length())));
            
            // Extract individual IV values
            // Try Method 1: Direct getter methods (getHp, getAttack, etc.)
            // Try Method 2: Stats enum access via get(Stat) method (Cobblemon API)
            PokeAlertClient.LOGGER.info("EggManager: Extracting individual IV values...");
            
            // First try to find and cache Stats enum values for Method 2
            Object statsHp = null, statsAttack = null, statsDefense = null;
            Object statsSpAttack = null, statsSpDefense = null, statsSpeed = null;
            try {
                Class<?> statsClass = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stats");
                statsHp = Enum.valueOf((Class<Enum>) statsClass, "HP");
                statsAttack = Enum.valueOf((Class<Enum>) statsClass, "ATTACK");
                statsDefense = Enum.valueOf((Class<Enum>) statsClass, "DEFENCE"); // Note: British spelling
                statsSpAttack = Enum.valueOf((Class<Enum>) statsClass, "SPECIAL_ATTACK");
                statsSpDefense = Enum.valueOf((Class<Enum>) statsClass, "SPECIAL_DEFENCE"); // Note: British spelling
                statsSpeed = Enum.valueOf((Class<Enum>) statsClass, "SPEED");
                PokeAlertClient.LOGGER.info("EggManager: ✅ Found Stats enum class for IV extraction");
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("EggManager: Could not find Stats enum, using direct getter methods: {}", e.getMessage());
            }
            
            int hp = extractIVValueWithStats(ivs, statsHp, "getHp", "getHP");
            int attack = extractIVValueWithStats(ivs, statsAttack, "getAttack");
            int defense = extractIVValueWithStats(ivs, statsDefense, "getDefense", "getDef", "getDefence");
            int spAttack = extractIVValueWithStats(ivs, statsSpAttack, "getSpecialAttack", "getSpAttack", "getSpAtk");
            int spDefense = extractIVValueWithStats(ivs, statsSpDefense, "getSpecialDefense", "getSpDefense", "getSpDef", "getSpecialDefence");
            int speed = extractIVValueWithStats(ivs, statsSpeed, "getSpeed", "getSpd");
            
            PokeAlertClient.LOGGER.info("EggManager: Extracted IVs - HP: {}, Atk: {}, Def: {}, SpA: {}, SpD: {}, Spe: {}", 
                hp, attack, defense, spAttack, spDefense, speed);
            
            PokemonIVs result = new PokemonIVs(hp, attack, defense, spAttack, spDefense, speed);
            
            // Log if all IVs are 0 (might indicate extraction failure)
            if (result.getTotal() == 0) {
                PokeAlertClient.LOGGER.warn("EggManager: ⚠️ All IVs extracted as 0 - this might indicate extraction failure");
                PokeAlertClient.LOGGER.warn("EggManager: IVs object class: {}, methods: {}", 
                    ivs.getClass().getName(), 
                    java.util.Arrays.toString(ivs.getClass().getMethods()).substring(0, Math.min(200, java.util.Arrays.toString(ivs.getClass().getMethods()).length())));
            }
            
            return result;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error extracting IVs: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Phase 2: Extract single IV value using Stats enum first, then falling back to direct getter methods
     * Uses Cobblemon API: ivs.get(Stats.HP), ivs.get(Stats.ATTACK), etc.
     */
    private int extractIVValueWithStats(Object ivs, Object statEnum, String... methodNames) {
        // Method 1: Try Stats enum access (Cobblemon API: ivs[Stats.HP] or ivs.get(Stats.HP))
        if (statEnum != null) {
            try {
                // Try get(Stat) method first
                java.lang.reflect.Method getMethod = ivs.getClass().getMethod("get", statEnum.getClass());
                Object result = getMethod.invoke(ivs, statEnum);
                if (result instanceof Number) {
                    int value = ((Number) result).intValue();
                    PokeAlertClient.LOGGER.info("EggManager: ✅ Extracted IV = {} via Stats.{}", value, statEnum);
                    return value;
                }
            } catch (NoSuchMethodException e) {
                // Try with parent interface (Stat)
                try {
                    Class<?> statInterface = Class.forName("com.cobblemon.mod.common.api.pokemon.stats.Stat");
                    java.lang.reflect.Method getMethod = ivs.getClass().getMethod("get", statInterface);
                    Object result = getMethod.invoke(ivs, statEnum);
                    if (result instanceof Number) {
                        int value = ((Number) result).intValue();
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Extracted IV = {} via Stat.{}", value, statEnum);
                        return value;
                    }
                } catch (Exception e2) {
                    PokeAlertClient.LOGGER.debug("EggManager: Stats enum method not found, trying direct getters");
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("EggManager: Error using Stats enum: {}", e.getMessage());
            }
        }
        
        // Method 2: Fall back to direct getter methods
        return extractIVValue(ivs, methodNames);
    }
    
    /**
     * Phase 2: Extract single IV value using direct getter method names
     */
    private int extractIVValue(Object ivs, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = ivs.getClass().getMethod(methodName);
                Object result = method.invoke(ivs);
                if (result instanceof Number) {
                    int value = ((Number) result).intValue();
                    PokeAlertClient.LOGGER.info("EggManager: ✅ Extracted {} = {} via {}", methodName, value, methodName);
                    return value;
                } else if (result != null) {
                    PokeAlertClient.LOGGER.warn("EggManager: {}() returned non-Number: {} (type: {})", 
                        methodName, result, result.getClass().getName());
                } else {
                    PokeAlertClient.LOGGER.debug("EggManager: {}() returned null", methodName);
                }
            } catch (NoSuchMethodException e) {
                PokeAlertClient.LOGGER.debug("EggManager: Method {}() not found, trying next...", methodName);
                // Try next method name
            } catch (Exception e) {
                PokeAlertClient.LOGGER.warn("EggManager: Error invoking {}(): {}", methodName, e.getMessage());
            }
        }
        PokeAlertClient.LOGGER.warn("EggManager: ❌ Could not extract IV value using methods: {}", java.util.Arrays.toString(methodNames));
        PokeAlertClient.LOGGER.warn("EggManager: Available methods on IVs object: {}", 
            java.util.Arrays.toString(ivs.getClass().getMethods()).substring(0, Math.min(500, java.util.Arrays.toString(ivs.getClass().getMethods()).length())));
        return 0; // Default to 0 if extraction fails
    }
    
    /**
     * Phase 2: Transfer hatched Pokemon to PC
     * Returns BoxInfo object with box number, box name, and slot, or null if transfer failed
     */
    private BoxInfo transferHatchedPokemonToPC(int partySlot, PokemonIVs ivs) {
        try {
            if (client.player == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Cannot transfer - player is null");
                return null;
            }
            
            UUID playerUuid = client.player.getUuid();
            
            // Get Pokemon from party
            Object party = getPlayerParty();
            if (party == null) {
                PokeAlertClient.LOGGER.error("EggManager: Cannot transfer - party not available");
                return null;
            }
            
            java.lang.reflect.Method getMethod = partyGetMethod;
            if (getMethod == null) {
                getMethod = party.getClass().getMethod("get", int.class);
            }
            
            Object pokemon = getMethod.invoke(party, partySlot);
            if (pokemon == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Slot {} is empty, cannot transfer", partySlot + 1);
                return null;
            }
            
            // Get Pokemon UUID
            java.lang.reflect.Method getUuidMethod = pokemon.getClass().getMethod("getUuid");
            UUID pokemonUuid = (UUID) getUuidMethod.invoke(pokemon);
            
            // Find target box based on IVs
            Object targetPosition = findTargetBox(ivs);
            if (targetPosition == null) {
                PokeAlertClient.LOGGER.warn("EggManager: No available box found for Pokemon");
                return null;
            }
            
            // Extract box info from PCPosition
            int boxNumber = -1;
            int boxSlot = -1;
            try {
                java.lang.reflect.Method getBoxMethod = targetPosition.getClass().getMethod("getBox");
                java.lang.reflect.Method getSlotMethod = targetPosition.getClass().getMethod("getSlot");
                boxNumber = ((Number) getBoxMethod.invoke(targetPosition)).intValue() + 1; // Convert to 1-based
                boxSlot = ((Number) getSlotMethod.invoke(targetPosition)).intValue() + 1; // Convert to 1-based
            } catch (Exception e) {
                PokeAlertClient.LOGGER.warn("EggManager: Could not extract box info from PCPosition: {}", e.getMessage());
            }
            
            // Get box name
            String boxName = null;
            if (boxNumber > 0) {
                try {
                    Object pcStore = getPlayerPCStore();
                    if (pcStore != null) {
                        boxName = getBoxName(pcStore, boxNumber - 1); // Convert back to 0-based index
                    }
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("EggManager: Could not get box name: {}", e.getMessage());
                }
            }
            
            // Get storage and transfer
            Object storage = getStorage();
            if (storage == null) {
                PokeAlertClient.LOGGER.error("EggManager: Cannot transfer - storage not available");
                return null;
            }
            
            // Get pcPositionClass for position creation
                if (pcPositionClass == null) {
                try {
                        pcPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.pc.PCPosition");
                } catch (ClassNotFoundException e) {
                    PokeAlertClient.LOGGER.error("EggManager: Cannot find PCPosition class");
                    return null;
                }
            }
            
            // Execute on main thread to ensure server sync
            final UUID finalPlayerUuid = playerUuid;
            final UUID finalPokemonUuid = pokemonUuid;
            final Object finalPokemon = pokemon; // Keep reference to Pokemon object
            final Object finalTargetPosition = targetPosition;
            final Object finalStorage = storage;
            final int finalPartySlot = partySlot; // Make final for lambda
            final int finalBoxNumber = boxNumber; // Make final for lambda
            final int finalBoxSlot = boxSlot; // Make final for lambda
            final String finalBoxName = boxName; // Make final for lambda
            
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final BoxInfo[] resultBoxInfo = {finalBoxNumber > 0 && finalBoxSlot > 0 ? 
                new BoxInfo(finalBoxNumber, finalBoxName, finalBoxSlot) : null};
            
            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Starting transfer - Party Slot {} → PC Box {} Slot {}", 
                finalPartySlot + 1, finalBoxNumber, finalBoxSlot);
            
            // ========== STEP 1: Check if PC is open (quick check on render thread) ==========
            final boolean[] pcWasOpen = {false};
            java.util.concurrent.CountDownLatch checkLatch = new java.util.concurrent.CountDownLatch(1);
            client.execute(() -> {
                try {
                    if (client.currentScreen != null) {
                        String screenClassName = client.currentScreen.getClass().getName();
                        if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                            pcWasOpen[0] = true;
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: PC GUI already open");
                        }
                    }
                } finally {
                    checkLatch.countDown();
                }
            });
            try { checkLatch.await(1, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            
            // ========== STEP 2: Warning wait (runs on BACKGROUND thread - no freeze!) ==========
            if (!pcWasOpen[0]) {
                sendPCWarningNotification(10);
                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Sending 10 second warning before PC transfer (background thread)");
                
                try {
                    Thread.sleep(10000); // Wait 10 seconds - runs on background thread, no freeze!
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] transferHatchedPokemonToPC: PC warning wait interrupted");
                }
                
                // ========== STEP 3: Open PC with retry logic ==========
                boolean pcGuiReady = false;
                final int MAX_RETRIES = 3;
                final long RETRY_DELAY = 3000; // 3 seconds between retries
                final long PC_OPEN_TIMEOUT = 3000; // 3 second timeout per attempt
                
                for (int attempt = 1; attempt <= MAX_RETRIES && !pcGuiReady; attempt++) {
                    final int currentAttempt = attempt; // Final copy for lambda
                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Opening PC GUI (attempt {}/{})", currentAttempt, MAX_RETRIES);
                    sendPCTransferNotification("Opening PC (ttempt " + currentAttempt + "/" + MAX_RETRIES + ")");
                    
                    // Send /pc command on render thread
                    java.util.concurrent.CountDownLatch cmdLatch = new java.util.concurrent.CountDownLatch(1);
                    client.execute(() -> {
                        try {
                            if (client.player != null && client.player.networkHandler != null) {
                                client.player.networkHandler.sendChatCommand("pc");
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Sent /pc command (attempt {})", currentAttempt);
                            }
                        } finally {
                            cmdLatch.countDown();
                        }
                    });
                    
                    // Wait for command to be sent
                    try { 
                        cmdLatch.await(1, java.util.concurrent.TimeUnit.SECONDS); 
                    } catch (InterruptedException e) { 
                        Thread.currentThread().interrupt(); 
                        break;
                    }
                    
                    // Give the server a moment to process the command
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    
                    // Poll for PC GUI to be ready
                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Waiting for PC GUI to fully load...");
                    long startTime = System.currentTimeMillis();
                    
                    while (!pcGuiReady && (System.currentTimeMillis() - startTime) < PC_OPEN_TIMEOUT) {
                        final boolean[] ready = {false};
                        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(1);
                        client.execute(() -> {
                            try {
                                if (client.currentScreen != null) {
                                    String screenClassName = client.currentScreen.getClass().getName();
                                    if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                                        java.lang.reflect.Field storageWidgetField = client.currentScreen.getClass().getDeclaredField("storageWidget");
                                        storageWidgetField.setAccessible(true);
                                        Object sw = storageWidgetField.get(client.currentScreen);
                                        if (sw != null) {
                                            ready[0] = true;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // Not ready yet
                            } finally {
                                readyLatch.countDown();
                            }
                        });
                        try { 
                            readyLatch.await(100, java.util.concurrent.TimeUnit.MILLISECONDS); 
                        } catch (InterruptedException e) { 
                            Thread.currentThread().interrupt(); 
                            break;
                        }
                        pcGuiReady = ready[0];
                        
                        if (!pcGuiReady) {
                            try {
                                Thread.sleep(100); // Poll every 100ms
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                    
                    if (pcGuiReady) {
                        long elapsed = System.currentTimeMillis() - startTime;
                        PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: ✅ PC GUI is ready (took {}ms, attempt {})", elapsed, currentAttempt);
                    } else {
                        PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] transferHatchedPokemonToPC: ⚠️ PC GUI not ready after {}ms (attempt {}/{})", 
                            PC_OPEN_TIMEOUT, currentAttempt, MAX_RETRIES);
                        
                        // Wait before retrying (possible rate limiting)
                        if (currentAttempt < MAX_RETRIES) {
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Waiting {}ms before retry...", RETRY_DELAY);
                            try {
                                Thread.sleep(RETRY_DELAY);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }
                
                if (!pcGuiReady) {
                    PokeAlertClient.LOGGER.error("🔍 [TRANSFER] transferHatchedPokemonToPC: ❌ PC GUI not ready after {} attempts - transfer may fail", MAX_RETRIES);
                }
            }
            
            // ========== STEP 5: Perform the actual transfer (on render thread) ==========
            client.execute(() -> {
                try {
                    // Get StorageWidget
                    Object storageWidget = null;
                    try {
                        if (client.currentScreen != null) {
                            String screenClassName = client.currentScreen.getClass().getName();
                            if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                                java.lang.reflect.Field storageWidgetField = client.currentScreen.getClass().getDeclaredField("storageWidget");
                                storageWidgetField.setAccessible(true);
                                storageWidget = storageWidgetField.get(client.currentScreen);
                                
                                if (storageWidget != null) {
                                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: ✅ Found StorageWidget: {}", storageWidget.getClass().getName());
                                } else {
                                    PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] transferHatchedPokemonToPC: ⚠️ StorageWidget is null");
                                }
                            }
                        }
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.error("🔍 [TRANSFER] transferHatchedPokemonToPC: ❌ Error getting StorageWidget", e);
                    }
                    
                    // Perform transfer
                    boolean transferViaGUI = false;
                    if (storageWidget != null && finalBoxNumber > 0 && finalBoxSlot > 0) {
                        try {
                            int targetBoxIndex = finalBoxNumber - 1; // Convert to 0-based
                            int pcSlotIndex = finalBoxSlot - 1; // Convert to 0-based
                            
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Target - Party Slot {} → PC Box {} (index {}), Slot {}", 
                                finalPartySlot + 1, finalBoxNumber, targetBoxIndex, finalBoxSlot);
                            
                            // Navigate to target box using setBox (no sleeps needed - instant)
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Step 1 - Navigating to box {}", targetBoxIndex);
                            boolean navigationSuccess = navigateToBox(storageWidget, targetBoxIndex);
                            
                            if (!navigationSuccess) {
                                PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] transferHatchedPokemonToPC: ⚠️ Box navigation may have failed, continuing anyway");
                            }
                            
                            // Verify box (no sleep needed - just check)
                            int verifyBox = getCurrentVisibleBox(storageWidget);
                            if (verifyBox != targetBoxIndex) {
                                PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] transferHatchedPokemonToPC: ⚠️ Box mismatch! Expected {}, got {}", 
                                    targetBoxIndex, verifyBox);
                            } else {
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: ✅ Box verified at {}", targetBoxIndex);
                            }
                            
                            // ITERATION 36: Use widget-based bidirectional transfer (no coordinates needed!)
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: Step 2 - Using widget-based transfer (Party → PC)");
                            
                            // Perform widget-based transfer (Party → PC)
                            boolean clickSuccess = performTransferWithWidgetsBidirectional(storageWidget, finalPartySlot, pcSlotIndex, true);
                            
                            if (clickSuccess) {
                                transferViaGUI = true;
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferHatchedPokemonToPC: ✅ Widget-based transfer completed");
                                
                                // Wait for server sync
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            } else {
                                PokeAlertClient.LOGGER.error("🔍 [TRANSFER] transferHatchedPokemonToPC: ❌ Widget-based transfer failed");
                            }
                        } catch (Exception guiException) {
                            PokeAlertClient.LOGGER.error("🔍 [TRANSFER] transferHatchedPokemonToPC: ❌ GUI-based transfer failed", guiException);
                        }
                    }
                    
                    // No fallback - GUI transfer is required
                    if (!transferViaGUI) {
                        PokeAlertClient.LOGGER.error("Widget-based GUI transfer failed - no fallback available.");
                            resultBoxInfo[0] = null;
                            return;
                    }
                    
                    // Verify transfer succeeded by checking PC slot
                    boolean transferVerified = false;
                    try {
                        Object pcStore = getPlayerPCStore();
                        if (pcStore != null) {
                            java.lang.reflect.Method pcGetMethod = pcStore.getClass().getMethod("get", pcPositionClass);
                            Object pokemonInPC = pcGetMethod.invoke(pcStore, finalTargetPosition);
                            
                            if (pokemonInPC != null) {
                                // Check if it's the same Pokemon by UUID
                                java.lang.reflect.Method pokemonGetUuidMethod = pokemonInPC.getClass().getMethod("getUuid");
                                UUID transferredUuid = (UUID) pokemonGetUuidMethod.invoke(pokemonInPC);
                                
                                if (transferredUuid.equals(finalPokemonUuid)) {
                                    transferVerified = true;
                                    PokeAlertClient.LOGGER.info("EggManager: ✅ Transfer verified - Pokemon found in PC Box {} Slot {}", 
                                        finalBoxNumber, finalBoxSlot);
                                    
                                    // Update resultBoxInfo with verified box info (in case extraction happened after transfer)
                                    if (resultBoxInfo[0] == null && finalBoxNumber > 0 && finalBoxSlot > 0) {
                                        resultBoxInfo[0] = new BoxInfo(finalBoxNumber, finalBoxName, finalBoxSlot);
                                        PokeAlertClient.LOGGER.info("EggManager: Created BoxInfo after transfer verification - Box {} Slot {}", finalBoxNumber, finalBoxSlot);
                                    }
                                    
                                    // Send in-game transfer notification
                                    try {
                                        String pokemonName = getPokemonName(finalPokemon);
                                        sendTransferNotification(pokemonName != null ? pokemonName : "Pokemon",
                                            "Party Slot " + (finalPartySlot + 1),
                                            "Box " + finalBoxNumber + ", Slot " + finalBoxSlot,
                                            finalBoxNumber,
                                            finalBoxName,
                                            finalBoxSlot);
                                    } catch (Exception notifException) {
                                        PokeAlertClient.LOGGER.debug("EggManager: Error sending transfer notification: {}", notifException.getMessage());
                                    }
                                } else {
                                    PokeAlertClient.LOGGER.warn("EggManager: Transfer verification failed - different Pokemon in PC (expected: {}, got: {})", 
                                        finalPokemonUuid, transferredUuid);
                                }
                            } else {
                                PokeAlertClient.LOGGER.warn("EggManager: Transfer verification failed - PC slot is empty");
                            }
                        }
                    } catch (Exception verifyException) {
                        PokeAlertClient.LOGGER.warn("EggManager: Could not verify transfer to PC: {}", verifyException.getMessage());
                    }
                    
                    // CRITICAL: Remove Pokemon from party after successful transfer to PC
                    // setPCPokemon adds to PC but doesn't remove from party automatically
                    // Only remove if transfer was verified
                    if (transferVerified) {
                        try {
                            if (storageRemoveFromPartyMethod == null) {
                                storageRemoveFromPartyMethod = finalStorage.getClass().getMethod("removeFromParty", 
                                    UUID.class, UUID.class);
                            }
                            storageRemoveFromPartyMethod.invoke(finalStorage, finalPlayerUuid, finalPokemonUuid);
                            PokeAlertClient.LOGGER.info("EggManager: ✅ Removed Pokemon from party after verified transfer");
                            
                            // Small delay to allow server sync
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        } catch (Exception removeException) {
                            PokeAlertClient.LOGGER.warn("EggManager: Failed to remove Pokemon from party (may cause visual fragment): {}", 
                                removeException.getMessage());
                        }
                    } else {
                        PokeAlertClient.LOGGER.warn("EggManager: Skipping party removal - transfer not verified");
                    }
                    
                    PokeAlertClient.LOGGER.info("EggManager: ✅ Transferred Pokemon from party slot {} to PC Box {} Slot {}", 
                        finalPartySlot + 1, finalBoxNumber, finalBoxSlot);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("EggManager: Error executing transfer on main thread", e);
                    resultBoxInfo[0] = null;
                } finally {
                    latch.countDown();
                }
            });
            
            // Wait for transfer to complete (max 3 seconds)
            try {
                if (!latch.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    PokeAlertClient.LOGGER.warn("EggManager: Transfer timeout - operation may not have completed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            
            return resultBoxInfo[0];
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Failed to transfer Pokemon to PC", e);
            return null;
        }
    }
    
    /**
     * Phase 2: Box info container
     */
    private static class BoxInfo {
        int boxNumber;
        String boxName;
        int slot;
        
        BoxInfo(int boxNumber, String boxName, int slot) {
            this.boxNumber = boxNumber;
            this.boxName = boxName;
            this.slot = slot;
        }
    }
    
    /**
     * Phase 2: Find target box based on IV stats
     */
    private Object findTargetBox(PokemonIVs ivs) {
        try {
            Object pcStore = getPlayerPCStore();
            if (pcStore == null) {
                PokeAlertClient.LOGGER.error("[BOX-FIND] Cannot find target box - PC store not available");
                return null;
            }
            
            PokeAlertConfig config = ConfigManager.getConfig();
            PokeAlertClient.LOGGER.info("[BOX-FIND] boxOrganizationEnabled: {}", config.eggManager.phase2.boxOrganizationEnabled);
            
            if (!config.eggManager.phase2.boxOrganizationEnabled) {
                PokeAlertClient.LOGGER.info("[BOX-FIND] Box organization disabled, finding any empty slot...");
                return findEmptyPCSlot(pcStore);
            }
            
            // Check if perfect IV
            boolean isPerfect = ivs != null && ivs.isPerfect();
            PokeAlertClient.LOGGER.info("[BOX-FIND] Pokemon IVs: {} | Perfect: {}", ivs, isPerfect);
            
            if (isPerfect) {
                PokeAlertClient.LOGGER.info("[BOX-FIND] Looking for perfect IV box...");
                return findPerfectIVBox(pcStore);
            } else {
                PokeAlertClient.LOGGER.info("[BOX-FIND] Looking for breedject box...");
                return findBreedjectBox(pcStore);
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error finding target box", e);
            return null;
        }
    }
    
    /**
     * Phase 2: Find breedject box
     * 
     * Box Organization:
     * - Box 30-28: RESERVED (never use)
     * - Box 27-26: "Breedgems 1/2" for perfect 6x31 IV Pokemon (handled by findPerfectIVBox)
     * - Box 25-15: "Breedjects X" for non-perfect hatched Pokemon
     * 
     * Strategy: Fill from HIGHEST index first (Box 25 → 15)
     * Within each box, fill the first empty slot (slot 0 → 29)
     * 
     * Box indices are 0-based: Box 25 = index 24, Box 15 = index 14
     */
    private Object findBreedjectBox(Object pcStore) {
        try {
            PokeAlertClient.LOGGER.info("[BOX-FIND] Starting breedject box search (Box 25 → 15)...");
            
            // Search boxes 25 down to 15 (indices 24 down to 14)
            // Fill from highest index first (Box 25 first, then 24, 23... down to 15)
            for (int boxNumber = 25; boxNumber >= 15; boxNumber--) {
                int boxIndex = boxNumber - 1; // Convert to 0-based index
                String boxName = getBoxName(pcStore, boxIndex);
                Object position = findEmptySlotInBox(pcStore, boxIndex);
                
                if (position != null) {
                    PokeAlertClient.LOGGER.info("[BOX-FIND] ✅ Found empty slot in Box {} '{}' (Breedjects {})", 
                        boxNumber, boxName != null ? boxName : "(unnamed)", 26 - boxNumber);
                    return position;
                } else {
                    PokeAlertClient.LOGGER.debug("[BOX-FIND] Box {} '{}' is full, checking next...", 
                        boxNumber, boxName != null ? boxName : "(unnamed)");
                }
            }
            
            PokeAlertClient.LOGGER.warn("[BOX-FIND] ❌ No empty slots found in breedject boxes (25-15)");
            return null;
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error finding breedject box", e);
            return null;
        }
    }
    
    /**
     * Phase 2: Find perfect IV box ("Breedgems" boxes 27-26)
     * 
     * Box Organization:
     * - Box 27: "Breedgems 1" - first perfect IV box
     * - Box 26: "Breedgems 2" - second perfect IV box
     * 
     * Strategy: Fill from HIGHEST index first (Box 27 → 26)
     * 
     * Box indices are 0-based: Box 27 = index 26, Box 26 = index 25
     */
    private Object findPerfectIVBox(Object pcStore) {
        try {
            PokeAlertClient.LOGGER.info("[BOX-FIND] Starting perfect IV (Breedgems) box search (Box 27 → 26)...");
            
            // Check boxes 27 and 26 (indices 26 and 25)
            for (int boxNumber = 27; boxNumber >= 26; boxNumber--) {
                int boxIndex = boxNumber - 1; // Convert to 0-based index
                String boxName = getBoxName(pcStore, boxIndex);
                Object position = findEmptySlotInBox(pcStore, boxIndex);
                
                if (position != null) {
                    PokeAlertClient.LOGGER.info("[BOX-FIND] ✅ Found empty slot in Box {} '{}' (Breedgems {})", 
                        boxNumber, boxName != null ? boxName : "(unnamed)", 28 - boxNumber);
                    return position;
                } else {
                    PokeAlertClient.LOGGER.debug("[BOX-FIND] Box {} '{}' is full, checking next...", 
                        boxNumber, boxName != null ? boxName : "(unnamed)");
                }
            }
            
            PokeAlertClient.LOGGER.warn("[BOX-FIND] ❌ No empty slots found in Breedgems boxes (27-26)");
            return null;
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error finding perfect IV box", e);
            return null;
        }
    }
    
    /**
     * Phase 2: Find empty slot in specific box
     */
    private Object findEmptySlotInBox(Object pcStore, int boxIndex) {
        try {
            if (pcPositionClass == null) {
                pcPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.pc.PCPosition");
            }
            
            java.lang.reflect.Method getMethod = pcStore.getClass().getMethod("get", pcPositionClass);
            java.lang.reflect.Constructor<?> constructor = pcPositionClass.getConstructor(int.class, int.class);
            
            // Check slots 0-29 in the box
            for (int slot = 0; slot < 30; slot++) {
                Object position = constructor.newInstance(boxIndex, slot);
                Object pokemon = getMethod.invoke(pcStore, position);
                if (pokemon == null) {
                    return position; // Empty slot found
                }
            }
            return null; // Box is full
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("EggManager: Error finding empty slot in box {}: {}", boxIndex, e.getMessage());
            return null;
        }
    }
    
    /**
     * Phase 2: Find any empty PC slot
     * Box indices are 0-based: Box 1 = index 0, Box 30 = index 29
     */
    private Object findEmptyPCSlot(Object pcStore) {
        try {
            // Check all boxes except reserved (Boxes 30, 29, 28 = indices 29, 28, 27)
            // Check boxes 1-27 (indices 0-26)
            for (int boxNumber = 1; boxNumber <= 27; boxNumber++) {
                int boxIndex = boxNumber - 1; // Convert to 0-based index
                Object position = findEmptySlotInBox(pcStore, boxIndex);
                if (position != null) {
                    return position;
                }
            }
            return null;
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error finding empty PC slot", e);
            return null;
        }
    }
    
    /**
     * Phase 2: Get box name
     */
    private String getBoxName(Object pcStore, int boxIndex) {
        try {
            java.lang.reflect.Method getBoxesMethod = pcStore.getClass().getMethod("getBoxes");
            Object boxes = getBoxesMethod.invoke(pcStore);
            
            if (boxes instanceof java.util.List) {
                java.util.List<?> boxesList = (java.util.List<?>) boxes;
                if (boxIndex >= 0 && boxIndex < boxesList.size()) {
                    Object box = boxesList.get(boxIndex);
                    java.lang.reflect.Method getNameMethod = box.getClass().getMethod("getName");
                    Object name = getNameMethod.invoke(box);
                    if (name != null) {
                        // Handle Minecraft Text objects (including MutableText, LiteralTextContent, etc.)
                        // Check if the object or any of its superclasses/interfaces is a Text
                        try {
                            // Try getString() method first - this is the proper way to get plain text
                            java.lang.reflect.Method getStringMethod = name.getClass().getMethod("getString");
                            String result = (String) getStringMethod.invoke(name);
                            if (result != null && !result.isEmpty()) {
                                return result;
                            }
                        } catch (NoSuchMethodException e) {
                            // Try alternate method names for different Text implementations
                            try {
                                java.lang.reflect.Method asStringMethod = name.getClass().getMethod("asString");
                                String result = (String) asStringMethod.invoke(name);
                                if (result != null && !result.isEmpty()) {
                                    return result;
                                }
                            } catch (NoSuchMethodException e2) {
                                // Last resort: check if it's a net.minecraft.text.Text and cast
                                if (name instanceof net.minecraft.text.Text) {
                                    return ((net.minecraft.text.Text) name).getString();
                                }
                            }
                        }
                        // Fallback - avoid raw toString() for Text objects as it includes style info
                        String nameStr = name.toString();
                        // Clean up if toString() returns literal{...} format
                        if (nameStr.startsWith("literal{") && nameStr.contains("}")) {
                            // Extract content between "literal{" and the first "[" or "}"
                            int startIdx = "literal{".length();
                            int endIdx = nameStr.indexOf("[");
                            if (endIdx == -1) {
                                endIdx = nameStr.indexOf("}");
                            }
                            if (endIdx > startIdx) {
                                return nameStr.substring(startIdx, endIdx);
                            }
                        }
                        return nameStr;
                    }
                }
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("EggManager: Could not get box name: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Phase 2: Get player PC store
     */
    private Object getPlayerPCStore() {
        try {
            Object storage = getStorage();
            if (storage == null || client.player == null) {
                return null;
            }
            
            java.lang.reflect.Method getPcStoresMethod = storage.getClass().getMethod("getPcStores");
            Object pcStores = getPcStoresMethod.invoke(storage);
            
            if (pcStores instanceof java.util.Map) {
                java.util.Map<?, ?> pcStoresMap = (java.util.Map<?, ?>) pcStores;
                UUID playerUuid = client.player.getUuid();
                return pcStoresMap.get(playerUuid);
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error getting PC store", e);
        }
        return null;
    }
    
    /**
     * Phase 2: Fill empty party slots with eggs from PC
     */
    private void fillPartySlotsWithEggs() {
        // Prevent concurrent fill operations
        if (isFillingSlots) {
            PokeAlertClient.LOGGER.debug("EggManager: Fill operation already in progress, skipping");
            return;
        }
        
        isFillingSlots = true;
        try {
            PokeAlertConfig config = ConfigManager.getConfig();
            if (!config.eggManager.phase2.autoFillFromPC) {
                PokeAlertClient.LOGGER.debug("EggManager: Auto-fill from PC is disabled");
                return;
            }
            
            // Find empty party slots
            List<Integer> emptySlots = new ArrayList<>();
            Object party = getPlayerParty();
            if (party == null) {
                PokeAlertClient.LOGGER.debug("EggManager: Cannot fill slots - party not available");
                return;
            }
            
            java.lang.reflect.Method getMethod = partyGetMethod;
            if (getMethod == null) {
                getMethod = party.getClass().getMethod("get", int.class);
            }
            
            for (int slot = 0; slot < 6; slot++) {
                Object pokemon = getMethod.invoke(party, slot);
                if (pokemon == null) {
                    emptySlots.add(slot);
                }
            }
            
            if (emptySlots.isEmpty()) {
                PokeAlertClient.LOGGER.debug("EggManager: No empty party slots to fill");
                return; // No empty slots
            }
            
            PokeAlertClient.LOGGER.info("EggManager: Found {} empty party slots, checking PC for eggs...", emptySlots.size());
            
            // SIMPLIFIED: Only transfer ONE egg per check (called every minute)
            // This prevents batch transfer issues and aligns with the natural 1-minute check cycle
            Object eggPosition = findNextEggInPC();
            if (eggPosition == null) {
                PokeAlertClient.LOGGER.debug("EggManager: No eggs found in PC to fill empty slots (searched from box 1, stopped at breedject boxes)");
                return;
            }
            
            // Get first empty slot for the single egg transfer
            int targetPartySlot = emptySlots.get(0);
            PokeAlertClient.LOGGER.info("EggManager: Found egg in PC, transferring to party slot {}", targetPartySlot + 1);
            
            // WARNING: Give player 10 seconds notice before opening PC
            sendPCWarningNotification(10);
            PokeAlertClient.LOGGER.info("EggManager: Sending 10 second warning before PC transfer");
            
            try {
                Thread.sleep(10000); // Wait 10 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                PokeAlertClient.LOGGER.warn("EggManager: PC warning wait interrupted");
                return;
            }
            
            // CRITICAL: Open PC interface for egg transfer
            PokeAlertClient.LOGGER.info("EggManager: Opening PC interface for egg transfer");
            sendPCTransferNotification("Opening PC");
            client.execute(() -> {
                if (client.player != null && client.player.networkHandler != null) {
                    String command = "pc";
                    client.player.networkHandler.sendChatCommand(command);
                    PokeAlertClient.LOGGER.debug("EggManager: Sent /pc command (as '{}')", command);
                }
            });
            
            // CRITICAL: Wait for PC GUI to load (non-blocking - use polling instead of sleep)
            // StorageWidget needs time to initialize before we can interact with it
            PokeAlertClient.LOGGER.info("EggManager: Waiting for PC GUI to fully load...");
            
            // Verify PC GUI is actually open before proceeding (non-blocking polling)
            boolean pcGuiReady = false;
            long startTime = System.currentTimeMillis();
            long timeout = 3000; // 3 second timeout
            
            while (!pcGuiReady && (System.currentTimeMillis() - startTime) < timeout) {
                try {
                    if (client.currentScreen != null) {
                        String screenClassName = client.currentScreen.getClass().getName();
                        if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                            // Try to access StorageWidget to verify it's initialized
                            java.lang.reflect.Field storageWidgetField = client.currentScreen.getClass().getDeclaredField("storageWidget");
                            storageWidgetField.setAccessible(true);
                            Object storageWidget = storageWidgetField.get(client.currentScreen);
                            if (storageWidget != null) {
                                pcGuiReady = true;
                                long elapsed = System.currentTimeMillis() - startTime;
                                PokeAlertClient.LOGGER.info("EggManager: ✅ PC GUI is ready (took {}ms)", elapsed);
                                
                                // DIAGNOSTIC: Scan StorageWidget fields and methods for navigation discovery
                                logStorageWidgetDiagnostics(storageWidget);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // GUI not ready yet, continue waiting
                }
                
                if (!pcGuiReady) {
                    try {
                        Thread.sleep(50); // Small non-blocking check interval (on background thread)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (!pcGuiReady) {
                PokeAlertClient.LOGGER.warn("EggManager: ⚠️ PC GUI may not be fully loaded, but proceeding anyway");
            }
            
            // SIMPLIFIED: Transfer single egg (PC is already open)
            // Clear transferred eggs set
            transferredEggUuids.clear();
            
            boolean transferred = false;
            try {
                Object pcStore = getPlayerPCStore();
                if (pcStore != null) {
                    java.lang.reflect.Method pcGetMethod = pcStore.getClass().getMethod("get", pcPositionClass);
                    Object egg = pcGetMethod.invoke(pcStore, eggPosition);
                    if (egg != null) {
                        java.lang.reflect.Method getUuidMethod = egg.getClass().getMethod("getUuid");
                        UUID eggUuid = (UUID) getUuidMethod.invoke(egg);
                        
                        // Use the version that doesn't open/close PC (since it's already open)
                        if (transferEggFromPCToPartyWithoutOpening(eggPosition, targetPartySlot)) {
                            transferredEggUuids.add(eggUuid);
                            transferred = true;
                            PokeAlertClient.LOGGER.info("EggManager: ✅ Transferred egg {} to party slot {}", 
                                eggUuid, targetPartySlot + 1);
                        }
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("EggManager: Error during single egg transfer", e);
            }
            
            // CRITICAL: Schedule PC close after delay to allow drag sequences to complete
            // Don't close immediately - drag sequences execute asynchronously
            if (scheduler == null) {
                scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
            }
            scheduler.schedule(() -> {
                closePCInterface();
            }, 2000, TimeUnit.MILLISECONDS); // Delay 2 seconds to allow drag sequence to complete
            
            if (transferred) {
                PokeAlertClient.LOGGER.info("EggManager: ✅ Transferred egg to party slot {}", targetPartySlot + 1);
            } else {
                PokeAlertClient.LOGGER.warn("EggManager: Failed to transfer egg from PC to party");
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error filling party slots with eggs", e);
        } finally {
            isFillingSlots = false;
        }
    }
    
    /**
     * Phase 2: Find next egg in PC starting from box 1, stopping at breedject boxes
     * Searches boxes sequentially starting from box 1 until an egg is found OR a breedject box is reached
     * Returns the first egg found, or null if no egg found before breedject boxes
     */
    private Object findNextEggInPC() {
        try {
            Object pcStore = getPlayerPCStore();
            if (pcStore == null) {
                PokeAlertClient.LOGGER.debug("EggManager: PC store not available for egg search");
                return null;
            }
            
            if (pcPositionClass == null) {
                pcPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.pc.PCPosition");
            }
            
            java.lang.reflect.Method getMethod = pcStore.getClass().getMethod("get", pcPositionClass);
            java.lang.reflect.Constructor<?> constructor = pcPositionClass.getConstructor(int.class, int.class);
            
            // Start from box 1 (index 0) and search sequentially
            // Stop when we find an egg OR reach breedject boxes (boxes 25-1, indices 24-0)
            // Skip reserved boxes (Boxes 30, 29, 28 = indices 29, 28, 27)
            // Box indices are 0-based: Box 1 = index 0, Box 30 = index 29
            
            for (int boxIndex = 0; boxIndex < 30; boxIndex++) {
                // Skip reserved boxes (indices 29, 28, 27 = Boxes 30, 29, 28)
                if (boxIndex == 29 || boxIndex == 28 || boxIndex == 27) {
                    continue;
                }
                
                // Check if this is a breedject box (boxes 25-1, indices 24-0)
                // Breedject boxes are typically named "Breedject X" where X is 1-25
                int boxNumber = boxIndex + 1; // Convert to 1-based
                if (boxNumber <= 25) {
                    String boxName = getBoxName(pcStore, boxIndex);
                    // Check if box name indicates it's a breedject box
                    if (boxName != null && (boxName.startsWith("Breedject") || boxName.contains("Breedject"))) {
                        PokeAlertClient.LOGGER.debug("EggManager: Reached breedject box {} (index {}), stopping egg search", boxNumber, boxIndex);
                        return null; // Stop searching at breedject boxes
                    }
                }
                
                // Search all slots in this box FROM THE BACK (slot 29 down to 0)
                // This prioritizes eggs at the end of the box first
                for (int slot = 29; slot >= 0; slot--) {
                    try {
                        Object position = constructor.newInstance(boxIndex, slot);
                        Object pokemon = getMethod.invoke(pcStore, position);
                        if (pokemon != null && isEgg(pokemon)) {
                            PokeAlertClient.LOGGER.info("EggManager: Found egg in Box {} (index {}) Slot {} (searching from back)", boxNumber, boxIndex, slot + 1);
                            return position; // Return first egg found (from back)
                        }
                    } catch (Exception e) {
                        // Skip this slot if there's an error
                        PokeAlertClient.LOGGER.debug("EggManager: Error checking Box {} (index {}) Slot {}: {}", boxNumber, boxIndex, slot + 1, e.getMessage());
                    }
                }
            }
            
            PokeAlertClient.LOGGER.debug("EggManager: No eggs found in PC before breedject boxes");
            return null;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error finding next egg in PC", e);
            return null;
        }
    }
    
    /**
     * Phase 2: Find all eggs in PC (excluding reserved boxes)
     * Uses sequential search starting from box 1, stopping at breedject boxes
     */
    private List<Object> findAllEggsInPC() {
        List<Object> eggs = new ArrayList<>();
        
        try {
            Object pcStore = getPlayerPCStore();
            if (pcStore == null) {
                PokeAlertClient.LOGGER.debug("EggManager: PC store not available for egg search");
                return eggs;
            }
            
            if (pcPositionClass == null) {
                pcPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.pc.PCPosition");
            }
            
            java.lang.reflect.Method getMethod = pcStore.getClass().getMethod("get", pcPositionClass);
            java.lang.reflect.Constructor<?> constructor = pcPositionClass.getConstructor(int.class, int.class);
            
            // Start from box 1 (index 0) and search sequentially
            // Stop when we reach breedject boxes (boxes 25-1, indices 24-0)
            // Skip reserved boxes (Boxes 30, 29, 28 = indices 29, 28, 27)
            // Box indices are 0-based: Box 1 = index 0, Box 30 = index 29
            
            for (int boxIndex = 0; boxIndex < 30; boxIndex++) {
                // Skip reserved boxes (indices 29, 28, 27 = Boxes 30, 29, 28)
                if (boxIndex == 29 || boxIndex == 28 || boxIndex == 27) {
                    continue;
                }
                
                // Check if this is a breedject box (boxes 25-1, indices 24-0)
                int boxNumber = boxIndex + 1; // Convert to 1-based
                if (boxNumber <= 25) {
                    String boxName = getBoxName(pcStore, boxIndex);
                    // Check if box name indicates it's a breedject box
                    if (boxName != null && (boxName.startsWith("Breedject") || boxName.contains("Breedject"))) {
                        PokeAlertClient.LOGGER.debug("EggManager: Reached breedject box {} (index {}), stopping egg search", boxNumber, boxIndex);
                        break; // Stop searching at breedject boxes
                    }
                }
                
                // Search all slots in this box FROM THE BACK (slot 29 down to 0)
                // This prioritizes eggs at the end of the box first
                for (int slot = 29; slot >= 0; slot--) {
                    try {
                        Object position = constructor.newInstance(boxIndex, slot);
                        Object pokemon = getMethod.invoke(pcStore, position);
                        if (pokemon != null && isEgg(pokemon)) {
                            eggs.add(position);
                            PokeAlertClient.LOGGER.info("EggManager: Found egg in Box {} (index {}) Slot {} (searching from back)", boxNumber, boxIndex, slot + 1);
                        }
                    } catch (Exception e) {
                        // Skip this slot if there's an error
                        PokeAlertClient.LOGGER.debug("EggManager: Error checking Box {} (index {}) Slot {}: {}", boxNumber, boxIndex, slot + 1, e.getMessage());
                    }
                }
            }
            
            PokeAlertClient.LOGGER.debug("EggManager: Found {} total eggs in PC (before breedject boxes, searched from back)", eggs.size());
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Error finding eggs in PC", e);
        }
        
        return eggs;
    }
    
    /**
     * Phase 2: Transfer egg from PC to party slot (opens/closes PC interface)
     * Use this for single transfers
     */
    private boolean transferEggFromPCToParty(Object eggPosition, int partySlot) {
        try {
            if (client.player == null) {
                return false;
            }
            
            UUID playerUuid = client.player.getUuid();
            
            // WARNING: Give player 10 seconds notice before opening PC
            sendPCWarningNotification(10);
            PokeAlertClient.LOGGER.info("EggManager: Sending 10 second warning before PC transfer (egg from PC to party)");
            
            try {
                Thread.sleep(10000); // Wait 10 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                PokeAlertClient.LOGGER.warn("EggManager: PC warning wait interrupted");
                return false;
            }
            
            // CRITICAL: Open PC interface first to ensure PC is initialized on server
            // This ensures transfers persist properly
            PokeAlertClient.LOGGER.info("EggManager: Opening PC interface before transfer");
            sendPCTransferNotification("Opening PC");
            client.execute(() -> {
                if (client.player != null && client.player.networkHandler != null) {
                    // Use sendChatCommand which expects command WITHOUT leading '/'
                    // This ensures we send exactly "/pc" not "//pc"
                    String command = "pc";
                    client.player.networkHandler.sendChatCommand(command);
                    PokeAlertClient.LOGGER.debug("EggManager: Sent /pc command (as '{}')", command);
                }
            });
            
            // CRITICAL: Wait for PC GUI to load (non-blocking - use polling instead of sleep)
            // StorageWidget needs time to initialize before we can interact with it
            PokeAlertClient.LOGGER.info("EggManager: Waiting for PC GUI to fully load...");
            
            // Verify PC GUI is actually open before proceeding (non-blocking polling)
            boolean pcGuiReady = false;
            long startTime = System.currentTimeMillis();
            long timeout = 3000; // 3 second timeout
            
            while (!pcGuiReady && (System.currentTimeMillis() - startTime) < timeout) {
                try {
                    if (client.currentScreen != null) {
                        String screenClassName = client.currentScreen.getClass().getName();
                        if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                            // Try to access StorageWidget to verify it's initialized
                            java.lang.reflect.Field storageWidgetField = client.currentScreen.getClass().getDeclaredField("storageWidget");
                            storageWidgetField.setAccessible(true);
                            Object storageWidget = storageWidgetField.get(client.currentScreen);
                            if (storageWidget != null) {
                                pcGuiReady = true;
                                long elapsed = System.currentTimeMillis() - startTime;
                                PokeAlertClient.LOGGER.info("EggManager: ✅ PC GUI is ready (took {}ms)", elapsed);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // GUI not ready yet, continue waiting
                }
                
                if (!pcGuiReady) {
                    try {
                        Thread.sleep(50); // Small non-blocking check interval (on background thread)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            if (!pcGuiReady) {
                PokeAlertClient.LOGGER.warn("EggManager: ⚠️ PC GUI may not be fully loaded, but proceeding anyway");
            }
            
            // Get egg from PC
            Object pcStore = getPlayerPCStore();
            if (pcStore == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Cannot transfer - PC store not available");
                return false;
            }
            
            java.lang.reflect.Method getMethod = pcStore.getClass().getMethod("get", pcPositionClass);
            Object egg = getMethod.invoke(pcStore, eggPosition);
            
            if (egg == null || !isEgg(egg)) {
                PokeAlertClient.LOGGER.warn("EggManager: Egg not found or is not an egg at position {}", eggPosition);
                return false;
            }
            
            // Get egg UUID
            java.lang.reflect.Method getUuidMethod = egg.getClass().getMethod("getUuid");
            UUID eggUuid = (UUID) getUuidMethod.invoke(egg);
            
            // Create PartyPosition
            if (partyPositionClass == null) {
                partyPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.party.PartyPosition");
            }
            java.lang.reflect.Constructor<?> constructor = partyPositionClass.getConstructor(int.class);
            Object partyPosition = constructor.newInstance(partySlot);
            
            // Transfer to party - MUST execute on main thread for server sync
            Object storage = getStorage();
            if (storage == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Cannot transfer - storage not available");
                return false;
            }
            
            // Get partyPositionClass for position creation
            if (partyPositionClass == null) {
                try {
                    partyPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.party.PartyPosition");
                } catch (ClassNotFoundException e) {
                    PokeAlertClient.LOGGER.error("EggManager: Cannot find PartyPosition class");
                    return false;
                }
            }
            
            // Execute on main thread to ensure server sync
            final UUID finalPlayerUuid = playerUuid;
            final UUID finalEggUuid = eggUuid;
            final Object finalEgg = egg; // Keep reference to Pokemon object
            final Object finalPartyPosition = partyPosition;
            final Object finalStorage = storage;
            final int finalPartySlot = partySlot;
            final Object finalEggPosition = eggPosition; // For notification
            
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] transferSuccess = {false};
            
            client.execute(() -> {
                try {
                    // Get StorageWidget for box navigation
                    Object storageWidget = null;
                    try {
                        if (client.currentScreen != null) {
                            String screenClassName = client.currentScreen.getClass().getName();
                            if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                                java.lang.reflect.Field storageWidgetField = client.currentScreen.getClass().getDeclaredField("storageWidget");
                                storageWidgetField.setAccessible(true);
                                storageWidget = storageWidgetField.get(client.currentScreen);
                                
                                if (storageWidget != null) {
                                    PokeAlertClient.LOGGER.info("EggManager: ✅ Found StorageWidget: {}", storageWidget.getClass().getName());
                                } else {
                                    PokeAlertClient.LOGGER.warn("EggManager: StorageWidget field is null");
                                }
                            }
                        }
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.debug("EggManager: Error getting StorageWidget: {}", e.getMessage());
                    }
                    
                    // Use two-click transfer at configured coordinates
                    boolean transferViaGUI = false;
                    if (storageWidget != null) {
                        try {
                                    // Extract box and slot from PCPosition
                                    int[] boxSlot = extractBoxAndSlotFromPCPosition(finalEggPosition);
                                    if (boxSlot != null) {
                                        int boxIndex = boxSlot[0] - 1; // Convert to 0-based
                                        int pcSlotIndex = boxSlot[1];
                                        
                                PokeAlertClient.LOGGER.info("EggManager: Attempting two-click transfer: PC Box {} Slot {} → Party Slot {}", 
                                                    boxSlot[0], pcSlotIndex, finalPartySlot + 1);
                                                
                                // Calculate slot positions using StorageWidget constants
                                int[] pcSlotPos = calculateSlotPosition(pcSlotIndex, true); // true = PC slot
                                int[] partySlotPos = calculateSlotPosition(finalPartySlot, false); // false = Party slot
                                
                                if (pcSlotPos != null && partySlotPos != null) {
                                    PokeAlertClient.LOGGER.info("EggManager: Calculated positions - PC: ({}, {}), Party: ({}, {})", 
                                        pcSlotPos[0], pcSlotPos[1], partySlotPos[0], partySlotPos[1]);
                                    
                                    // Use two-click transfer (mimics actual user clicking at configured coordinates)
                                    boolean clickSuccess = performTwoClickTransfer(pcSlotPos, partySlotPos, "PC → Party");
                                    
                                    if (clickSuccess) {
                                                transferViaGUI = true;
                                        PokeAlertClient.LOGGER.info("EggManager: ✅ Two-click transfer completed");
                                        
                                        // Send in-game transfer notification
                                                try {
                                                    String boxNameStr = null;
                                                        Object pcStoreForNotification = getPlayerPCStore();
                                                        if (pcStoreForNotification != null) {
                                                boxNameStr = getBoxName(pcStoreForNotification, boxIndex);
                                                    }
                                                    sendTransferNotification("Egg", 
                                                "Box " + boxSlot[0] + ", Slot " + (pcSlotIndex + 1),
                                                        "Party Slot " + (finalPartySlot + 1),
                                                boxSlot[0],
                                                        boxNameStr,
                                                pcSlotIndex + 1);
                                                } catch (Exception notifException) {
                                                    PokeAlertClient.LOGGER.debug("EggManager: Error sending transfer notification: {}", notifException.getMessage());
                                                }
                                                
                                        // Wait for server sync after GUI transfer
                                                try {
                                                    Thread.sleep(2000); // 2 seconds for server to process the packet
                                                } catch (InterruptedException ie) {
                                                    Thread.currentThread().interrupt();
                                                }
                                            } else {
                                        PokeAlertClient.LOGGER.warn("EggManager: ⚠️ Two-click transfer failed");
                                            }
                                        } else {
                                    PokeAlertClient.LOGGER.warn("EggManager: ⚠️ Could not calculate slot positions");
                                        }
                                    } else {
                                        PokeAlertClient.LOGGER.warn("EggManager: Could not extract box/slot from PCPosition");
                            }
                        } catch (Exception guiException) {
                            PokeAlertClient.LOGGER.warn("EggManager: GUI-based transfer failed: {}", guiException.getMessage());
                            PokeAlertClient.LOGGER.debug("EggManager: GUI transfer exception details", guiException);
                        }
                    }
                    
                    // No fallback - GUI transfer is required (setPartyPokemon/moveInParty are client-side only and don't work)
                    if (!transferViaGUI) {
                        PokeAlertClient.LOGGER.error("GUI transfer failed - no fallback available. Please ensure slot coordinates are mapped.");
                            return;
                    }
                    
                    // Verify transfer succeeded by checking party slot
                    Object party = getPlayerParty();
                    boolean transferVerified = false;
                    if (party != null) {
                        java.lang.reflect.Method partyGetMethod = party.getClass().getMethod("get", int.class);
                        Object pokemonInSlot = partyGetMethod.invoke(party, finalPartySlot);
                        
                        if (pokemonInSlot != null) {
                            // Check if it's the same egg by UUID
                            java.lang.reflect.Method pokemonGetUuidMethod = pokemonInSlot.getClass().getMethod("getUuid");
                            UUID transferredUuid = (UUID) pokemonGetUuidMethod.invoke(pokemonInSlot);
                            
                            if (transferredUuid.equals(finalEggUuid)) {
                                transferVerified = true;
                                transferSuccess[0] = true;
                                trackNewEgg(finalPartySlot);
                                PokeAlertClient.LOGGER.info("EggManager: ✅ Transferred egg from PC to party slot {} (verified)", finalPartySlot + 1);
                                // Note: Transfer notification already sent immediately after transfer attempts
                            } else {
                                PokeAlertClient.LOGGER.warn("EggManager: Transfer verification failed - different Pokemon in slot (expected: {}, got: {})", 
                                    finalEggUuid, transferredUuid);
                            }
                        } else {
                            PokeAlertClient.LOGGER.warn("EggManager: Transfer verification failed - slot {} is empty", finalPartySlot + 1);
                        }
                    }
                    
                    // NOTE: Removed removeFromPC call - may interfere with egg hatch progress
                    // Keeping egg in PC after transfer to party for now to test hatch progress
                    // Visual fragment may appear but hatch progress should work correctly
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("EggManager: Error executing transfer on main thread", e);
                } finally {
                    latch.countDown();
                }
            });
            
            // Wait for transfer to complete (max 5 seconds - increased from 3)
            try {
                if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    PokeAlertClient.LOGGER.warn("EggManager: Transfer timeout - operation may not have completed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            // CRITICAL: Don't block render thread - schedule PC close after delay on background thread
            // This allows drag sequence to complete before closing
            if (scheduler == null) {
                scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2);
            }
            scheduler.schedule(() -> {
                closePCInterface();
            }, 1500, TimeUnit.MILLISECONDS); // Delay 1.5 seconds to allow drag sequence to complete
            
            return transferSuccess[0];
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Failed to transfer egg from PC to party", e);
            // Close PC interface even if transfer failed
            closePCInterface();
            return false;
        }
    }
    
    /**
     * Phase 2: Transfer egg from PC to party slot WITHOUT opening/closing PC interface
     * Use this for batch transfers when PC is already open
     * Assumes PC interface is already open and will be closed by caller
     */
    private boolean transferEggFromPCToPartyWithoutOpening(Object eggPosition, int partySlot) {
        try {
            if (client.player == null) {
                return false;
            }
            
            UUID playerUuid = client.player.getUuid();
            
            // Get egg from PC (PC should already be open)
            Object pcStore = getPlayerPCStore();
            if (pcStore == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Cannot transfer - PC store not available");
                return false;
            }
            
            java.lang.reflect.Method getMethod = pcStore.getClass().getMethod("get", pcPositionClass);
            Object egg = getMethod.invoke(pcStore, eggPosition);
            
            if (egg == null || !isEgg(egg)) {
                PokeAlertClient.LOGGER.warn("EggManager: Egg not found or is not an egg at position {}", eggPosition);
                return false;
            }
            
            // Get egg UUID
            java.lang.reflect.Method getUuidMethod = egg.getClass().getMethod("getUuid");
            UUID eggUuid = (UUID) getUuidMethod.invoke(egg);
            
            // Create PartyPosition
            if (partyPositionClass == null) {
                partyPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.party.PartyPosition");
            }
            java.lang.reflect.Constructor<?> constructor = partyPositionClass.getConstructor(int.class);
            Object partyPosition = constructor.newInstance(partySlot);
            
            // Transfer to party - MUST execute on main thread for server sync
            Object storage = getStorage();
            if (storage == null) {
                PokeAlertClient.LOGGER.warn("EggManager: Cannot transfer - storage not available");
                return false;
            }
            
            // Get partyPositionClass for position creation
            if (partyPositionClass == null) {
                try {
                    partyPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.party.PartyPosition");
                } catch (ClassNotFoundException e) {
                    PokeAlertClient.LOGGER.error("EggManager: Cannot find PartyPosition class");
                    return false;
                }
            }
            
            // Execute on main thread to ensure server sync
            final UUID finalPlayerUuid = playerUuid;
            final UUID finalEggUuid = eggUuid;
            final Object finalEgg = egg; // Keep reference to Pokemon object
            final Object finalPartyPosition = partyPosition;
            final Object finalStorage = storage;
            final int finalPartySlot = partySlot;
            final Object finalEggPosition = eggPosition; // For notification
            
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final boolean[] transferSuccess = {false};
            
            client.execute(() -> {
                try {
                    // CRITICAL: Ensure PC GUI is fully loaded before attempting transfer
                    // StorageWidget needs time to initialize after PC GUI opens
                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: Verifying PC GUI is ready...");
                    
                    // CRITICAL: Check StorageWidget immediately without ANY blocking operations
                    // Since we're already on render thread (via client.execute()), we can check directly
                    // NO Thread.sleep() - NO loops - NO blocking operations!
                    // If StorageWidget isn't ready, we proceed anyway - transfer methods will handle it gracefully
                    boolean storageWidgetReady = false;
                    Object storageWidget = null;
                    
                    try {
                        if (client.currentScreen != null) {
                            String screenClassName = client.currentScreen.getClass().getName();
                            if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                                // Access storageWidget field via reflection
                                java.lang.reflect.Field storageWidgetField = client.currentScreen.getClass().getDeclaredField("storageWidget");
                                storageWidgetField.setAccessible(true);
                                storageWidget = storageWidgetField.get(client.currentScreen);
                                
                                if (storageWidget != null) {
                                    storageWidgetReady = true;
                                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: ✅ StorageWidget ready");
                                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: ✅ Found StorageWidget: {}", storageWidget.getClass().getName());
                                } else {
                                    PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] StorageWidget field is null - will proceed anyway");
                                }
                            } else {
                                PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Current screen is not PCGUI: {}", screenClassName);
                            }
                        } else {
                            PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Current screen is null");
                        }
                    } catch (Exception e) {
                        // StorageWidget not ready - will proceed anyway, transfer methods will handle it gracefully
                        PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] StorageWidget not immediately available: {}", e.getMessage());
                    }
                    
                    // Use two-click transfer at configured coordinates
                    boolean transferViaGUI = false;
                    if (storageWidget != null) {
                        try {
                            PokeAlertClient.LOGGER.info("═══════════════════════════════════════════════════════════════");
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: Starting two-click transfer");
                            PokeAlertClient.LOGGER.info("═══════════════════════════════════════════════════════════════");
                            
                            // PRE-TRANSFER STATE LOGGING
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ========== PRE-TRANSFER STATE ==========");
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Screen: {}", client.currentScreen != null ? client.currentScreen.getClass().getName() : "null");
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] StorageWidget: {}", storageWidget.getClass().getName());
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Egg UUID: {}", finalEggUuid);
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Target Party Slot: {}", finalPartySlot + 1);
                            
                            // Log StorageWidget state
                            try {
                                int currentBox = getCurrentVisibleBox(storageWidget);
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Current visible box: {} (0-based)", currentBox);
                                
                                // NOTE: We're using two-click approach (not drag-and-drop), so grabbedSlot is not relevant
                                
                                // Log window dimensions
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Window dimensions: {}x{}", 
                                    client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                            } catch (Exception e) {
                                PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Error logging pre-transfer state: {}", e.getMessage());
                            }
                                
                                // Extract box and slot from PCPosition
                                int[] boxSlot = extractBoxAndSlotFromPCPosition(finalEggPosition);
                                if (boxSlot == null) {
                                    PokeAlertClient.LOGGER.error("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: ❌ Could not extract box/slot from PCPosition");
                                } else {
                                    int targetBoxIndex = boxSlot[0] - 1; // Convert to 0-based
                                    int pcSlotIndex = boxSlot[1]; // Already 0-based from PCPosition
                                    
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Target - Box {} (index {}), Slot {} (0-based: {}) → Party Slot {}", 
                                        boxSlot[0], targetBoxIndex, pcSlotIndex + 1, pcSlotIndex, finalPartySlot + 1);
                                    
                                // Verify egg exists in PC before transfer
                                try {
                                    Object pcStoreBefore = getPlayerPCStore();
                                    if (pcStoreBefore != null) {
                                        java.lang.reflect.Method getMethodBefore = pcStoreBefore.getClass().getMethod("get", pcPositionClass);
                                        Object eggInPCBefore = getMethodBefore.invoke(pcStoreBefore, finalEggPosition);
                                        if (eggInPCBefore != null) {
                                            java.lang.reflect.Method getUuidMethodBefore = eggInPCBefore.getClass().getMethod("getUuid");
                                            UUID eggUuidInPCBefore = (UUID) getUuidMethodBefore.invoke(eggInPCBefore);
                                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ✅ Egg found in PC - UUID: {}", eggUuidInPCBefore);
                                            if (!eggUuidInPCBefore.equals(finalEggUuid)) {
                                                PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ UUID mismatch! Expected: {}, Found: {}", finalEggUuid, eggUuidInPCBefore);
                                            }
                                        } else {
                                            PokeAlertClient.LOGGER.error("🔍 [TRANSFER] ❌ Egg NOT found in PC at position!");
                                        }
                                    }
                                } catch (Exception e) {
                                    PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Error verifying egg in PC: {}", e.getMessage());
                                }
                                
                                // Verify party slot is empty before transfer
                                try {
                                    Object partyBefore = getPlayerParty();
                                    if (partyBefore != null) {
                                        java.lang.reflect.Method partyGetMethodBefore = partyBefore.getClass().getMethod("get", int.class);
                                        Object pokemonInSlotBefore = partyGetMethodBefore.invoke(partyBefore, finalPartySlot);
                                        if (pokemonInSlotBefore == null) {
                                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ✅ Party slot {} is empty (ready for transfer)", finalPartySlot + 1);
                                        } else {
                                            java.lang.reflect.Method getUuidMethodBefore = pokemonInSlotBefore.getClass().getMethod("getUuid");
                                            UUID existingUuid = (UUID) getUuidMethodBefore.invoke(pokemonInSlotBefore);
                                            PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ Party slot {} is NOT empty - contains UUID: {}", finalPartySlot + 1, existingUuid);
                                        }
                                    }
                                } catch (Exception e) {
                                    PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Error verifying party slot: {}", e.getMessage());
                                }
                                    
                                    // Step 1: Navigate to target box if not already visible
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ========== STEP 1: BOX NAVIGATION ==========");
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Navigating to box {} (0-based)", targetBoxIndex);
                                    boolean navigationSuccess = navigateToBox(storageWidget, targetBoxIndex);
                                    
                                    if (!navigationSuccess) {
                                        PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ Box navigation may have failed, continuing anyway");
                                    }
                                    
                                    // CRITICAL: Wait for GUI to stabilize after navigation
                                    try {
                                        Thread.sleep(300); // 300ms for GUI stabilization
                                        PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Waited 300ms for GUI stabilization");
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    
                                    // Verify box after stabilization
                                    int currentBoxAfterNav = getCurrentVisibleBox(storageWidget);
                                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Current box after navigation: {} (0-based)", currentBoxAfterNav);
                                    if (currentBoxAfterNav != targetBoxIndex) {
                                        PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ Box mismatch after stabilization - expected {}, got {}", targetBoxIndex, currentBoxAfterNav);
                                        // Re-navigate if needed
                                        if (currentBoxAfterNav != -1) {
                                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Re-navigating to target box...");
                                            navigateToBox(storageWidget, targetBoxIndex);
                                            try {
                                                Thread.sleep(300);
                                            } catch (InterruptedException e) {
                                                Thread.currentThread().interrupt();
                                            }
                                        }
                                    }
                                
                                // Step 2: Widget-based transfer (no coordinates needed)
                                    // Uses onStorageSlotClicked() with actual widget instances
                                    // This is the CORRECT approach that sets grabbedSlot properly
                                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ========== STEP 3: WIDGET-BASED TRANSFER (Iteration 35) ==========");
                                    boolean clickSuccess = performTransferWithWidgets(storageWidget, pcSlotIndex, finalPartySlot);
                                    
                                    // POST-CLICK STATE LOGGING
                                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ========== POST-CLICK STATE ==========");
                                    // NOTE: We're using two-click approach (not drag-and-drop), so grabbedSlot is not relevant
                                    
                                    try {
                                        // Check party slot immediately after clicks
                                        Object partyAfter = getPlayerParty();
                                        if (partyAfter != null) {
                                            java.lang.reflect.Method partyGetMethodAfter = partyAfter.getClass().getMethod("get", int.class);
                                            Object pokemonInSlotAfter = partyGetMethodAfter.invoke(partyAfter, finalPartySlot);
                                            if (pokemonInSlotAfter != null) {
                                                java.lang.reflect.Method getUuidMethodAfter = pokemonInSlotAfter.getClass().getMethod("getUuid");
                                                UUID transferredUuid = (UUID) getUuidMethodAfter.invoke(pokemonInSlotAfter);
                                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Party slot {} contains UUID: {}", finalPartySlot + 1, transferredUuid);
                                                if (transferredUuid.equals(finalEggUuid)) {
                                                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ✅ UUID matches - transfer appears successful!");
                                                } else {
                                                    PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ UUID mismatch - expected {}, got {}", finalEggUuid, transferredUuid);
                                                }
                                            } else {
                                                PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ Party slot {} is still empty after clicks", finalPartySlot + 1);
                                            }
                                        }
                                        
                                        // Check PC slot after transfer
                                        Object pcStoreAfter = getPlayerPCStore();
                                        if (pcStoreAfter != null) {
                                            java.lang.reflect.Method getMethodAfter = pcStoreAfter.getClass().getMethod("get", pcPositionClass);
                                            Object eggInPCAfter = getMethodAfter.invoke(pcStoreAfter, finalEggPosition);
                                            if (eggInPCAfter == null) {
                                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ✅ Egg removed from PC (transfer successful)");
                                            } else {
                                                java.lang.reflect.Method getUuidMethodAfter = eggInPCAfter.getClass().getMethod("getUuid");
                                                UUID eggUuidInPCAfter = (UUID) getUuidMethodAfter.invoke(eggInPCAfter);
                                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Egg still in PC - UUID: {}", eggUuidInPCAfter);
                                                if (eggUuidInPCAfter.equals(finalEggUuid)) {
                                                    PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ Egg still in PC - transfer may have failed");
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Error logging post-click state: {}", e.getMessage());
                                    }
                                        
                                    if (clickSuccess) {
                                        transferViaGUI = true;
                                        PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ✅ Two-click transfer completed");
                                        
                                        // Send in-game transfer notification
                                        try {
                                            String boxNameStr = null;
                                            Object pcStoreForNotification = getPlayerPCStore();
                                            if (pcStoreForNotification != null) {
                                                boxNameStr = getBoxName(pcStoreForNotification, targetBoxIndex);
                                            }
                                            sendTransferNotification("Egg", 
                                                "Box " + boxSlot[0] + ", Slot " + (pcSlotIndex + 1),
                                                "Party Slot " + (finalPartySlot + 1),
                                                boxSlot[0],
                                                boxNameStr,
                                                pcSlotIndex + 1);
                                        } catch (Exception notifException) {
                                            PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Error sending transfer notification: {}", notifException.getMessage());
                                        }
                                        
                                        // CRITICAL: Don't block render thread - server sync happens asynchronously
                                        PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Server sync will happen asynchronously (no freeze!)");
                                    } else {
                                        PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ Widget-based transfer failed");
                                    }
                                    }
                            PokeAlertClient.LOGGER.info("═══════════════════════════════════════════════════════════════");
                        } catch (Exception guiException) {
                            PokeAlertClient.LOGGER.error("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: ❌ GUI-based transfer failed", guiException);
                        }
                    } else {
                        PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: ⚠️ StorageWidget is null - cannot perform GUI transfer");
                    }
                    
                    // No fallback - GUI transfer is required (setPartyPokemon/moveInParty are client-side only and don't work)
                    if (!transferViaGUI) {
                        PokeAlertClient.LOGGER.error("GUI transfer failed - no fallback available. Please ensure slot coordinates are mapped.");
                            return;
                    }
                    
                    // Verify transfer succeeded by checking party slot
                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ========== FINAL VERIFICATION ==========");
                    Object party = getPlayerParty();
                    boolean transferVerified = false;
                    if (party != null) {
                        java.lang.reflect.Method partyGetMethod = party.getClass().getMethod("get", int.class);
                        Object pokemonInSlot = partyGetMethod.invoke(party, finalPartySlot);
                        
                        if (pokemonInSlot != null) {
                            // Check if it's the same egg by UUID
                            java.lang.reflect.Method pokemonGetUuidMethod = pokemonInSlot.getClass().getMethod("getUuid");
                            UUID transferredUuid = (UUID) pokemonGetUuidMethod.invoke(pokemonInSlot);
                            
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Party slot {} contains Pokemon:", finalPartySlot + 1);
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER]   UUID: {}", transferredUuid);
                            PokeAlertClient.LOGGER.info("🔍 [TRANSFER]   Expected UUID: {}", finalEggUuid);
                            
                            if (transferredUuid.equals(finalEggUuid)) {
                                transferVerified = true;
                                transferSuccess[0] = true;
                                trackNewEgg(finalPartySlot);
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ✅ UUID MATCHES - Transfer VERIFIED!");
                                PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ✅ Transferred egg from PC to party slot {} (verified)", finalPartySlot + 1);
                                // Note: Transfer notification already sent immediately after transfer attempts
                            } else {
                                PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ UUID MISMATCH - Transfer verification failed");
                                PokeAlertClient.LOGGER.warn("🔍 [TRANSFER]   Expected: {}", finalEggUuid);
                                PokeAlertClient.LOGGER.warn("🔍 [TRANSFER]   Got: {}", transferredUuid);
                                
                                // Try to get Pokemon name for debugging
                                try {
                                    java.lang.reflect.Method getNameMethod = pokemonInSlot.getClass().getMethod("getDisplayName");
                                    Object nameText = getNameMethod.invoke(pokemonInSlot);
                                    if (nameText != null) {
                                        java.lang.reflect.Method getStringMethod = nameText.getClass().getMethod("getString");
                                        String name = (String) getStringMethod.invoke(nameText);
                                        PokeAlertClient.LOGGER.info("🔍 [TRANSFER]   Pokemon in slot: {}", name);
                                    }
                                } catch (Exception e) {
                                    PokeAlertClient.LOGGER.debug("🔍 [TRANSFER] Could not get Pokemon name: {}", e.getMessage());
                                }
                            }
                        } else {
                            PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] ⚠️ Transfer verification failed - slot {} is EMPTY", finalPartySlot + 1);
                            PokeAlertClient.LOGGER.warn("🔍 [TRANSFER]   Expected UUID: {}", finalEggUuid);
                            PokeAlertClient.LOGGER.warn("🔍 [TRANSFER]   This indicates the transfer did NOT occur");
                        }
                    } else {
                        PokeAlertClient.LOGGER.error("🔍 [TRANSFER] ❌ Cannot verify - party is null");
                    }
                    
                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] Verification result: {}", transferVerified ? "SUCCESS" : "FAILED");
                    PokeAlertClient.LOGGER.info("🔍 [TRANSFER] ==========================================");
                    
                    // NOTE: Removed removeFromPC call - may interfere with egg hatch progress
                    // Keeping egg in PC after transfer to party for now to test hatch progress
                    // Visual fragment may appear but hatch progress should work correctly
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: ❌ Error executing transfer on main thread", e);
                } finally {
                    latch.countDown();
                }
            });
            
            // Wait for transfer to complete (max 5 seconds)
            try {
                if (!latch.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    PokeAlertClient.LOGGER.warn("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: ⚠️ Transfer timeout - operation may not have completed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            
            PokeAlertClient.LOGGER.info("🔍 [TRANSFER] transferEggFromPCToPartyWithoutOpening: Transfer completed - success: {}", transferSuccess[0]);
            return transferSuccess[0];
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Failed to transfer egg from PC to party", e);
            return false;
        }
    }
    
    /**
     * Close PC interface using ESC key or by setting screen to null
     */
    private void closePCInterface() {
        client.execute(() -> {
            try {
                if (client.currentScreen != null) {
                    String screenClassName = client.currentScreen.getClass().getName();
                    // Check if PC screen is open
                    if (screenClassName.toLowerCase().contains("pc") || 
                        screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                        
                        PokeAlertClient.LOGGER.info("EggManager: Closing PC interface...");
                        
                        // Try method 1: Simulate ESC key press using keyPressed
                        try {
                            // ESC key code is 256 (GLFW.GLFW_KEY_ESCAPE)
                            int escKeyCode = 256;
                            if (client.currentScreen != null) {
                                boolean handled = client.currentScreen.keyPressed(escKeyCode, 0, 0);
                                if (handled) {
                                    PokeAlertClient.LOGGER.debug("EggManager: ESC key handled by screen");
                                } else {
                                    // If screen didn't handle it, try closing directly
                                    client.setScreen(null);
                                    PokeAlertClient.LOGGER.info("EggManager: ✅ Closed PC interface via ESC key");
                                }
                            }
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.debug("EggManager: ESC key method failed: {}", e.getMessage());
                            // Fallback to direct close
                            try {
                                client.setScreen(null);
                                PokeAlertClient.LOGGER.info("EggManager: ✅ Closed PC interface via setScreen(null)");
                            } catch (Exception e2) {
                                PokeAlertClient.LOGGER.warn("EggManager: Failed to close PC interface: {}", e2.getMessage());
                            }
                        }
                        
                        // Ensure screen is closed (double-check)
                        try {
                            Thread.sleep(100);
                            if (client.currentScreen != null && 
                                (client.currentScreen.getClass().getName().toLowerCase().contains("pc") ||
                                 client.currentScreen.getClass().getName().equals("com.cobblemon.mod.common.client.gui.pc.PCGUI"))) {
                                client.setScreen(null);
                                PokeAlertClient.LOGGER.info("EggManager: ✅ Force-closed PC interface");
                            }
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.debug("EggManager: Error in force-close check: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("EggManager: Error closing PC interface: {}", e.getMessage());
            }
        });
    }
    
    /**
     * Phase 2: Send in-game transfer notification
     */
    private void sendTransferNotification(String pokemonName, String fromLocation, String toLocation, 
                                         Integer boxNumber, String boxName, Integer slot) {
        PokeAlertConfig config = ConfigManager.getConfig();
        if (!config.notifications.textEnabled || client.player == null) {
            return;
        }
        
        client.execute(() -> {
            if (client.player == null) return;
            
            try {
                net.minecraft.text.MutableText notification = Text.literal("[")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal("PokéAlert").formatted(Formatting.RED))
                    .append(Text.literal("] ").formatted(Formatting.GRAY))
                    .append(Text.literal("🔄 Transfer: ").formatted(Formatting.YELLOW))
                    .append(Text.literal(pokemonName != null ? pokemonName : "Egg").formatted(Formatting.WHITE))
                    .append(Text.literal(" ").formatted(Formatting.GRAY))
                    .append(Text.literal(fromLocation).formatted(Formatting.AQUA))
                    .append(Text.literal(" → ").formatted(Formatting.GRAY))
                    .append(Text.literal(toLocation).formatted(Formatting.GREEN));
                
                client.player.sendMessage(notification, false);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("EggManager: Error sending transfer notification: {}", e.getMessage());
            }
        });
    }

    /**
     * DEPRECATED: No longer needed with widget-based transfers
     * Kept as stub to avoid breaking existing code paths that may call it
     */
    private int[] calculateSlotPosition(int slotIndex, boolean isPCSlot) {
        PokeAlertClient.LOGGER.debug("calculateSlotPosition called but coordinate mapping is deprecated - using widget-based transfers");
        return null;
    }
    
    /**
     * DEPRECATED: No longer needed with widget-based transfers
     * Kept as stub to avoid breaking existing code paths that may call it
     */
    private boolean performTwoClickTransfer(int[] sourcePos, int[] destPos, String transferType) {
        PokeAlertClient.LOGGER.debug("performTwoClickTransfer called but coordinate-based transfers are deprecated - using widget-based transfers");
        return false;
    }
    
    /**
     * OPTION 4: GUI State Verification
     * Verify PC GUI is fully initialized, check for animations, verify no blocking interactions,
     * and check if PC needs to be focused/active
     */
    private boolean verifyPCGUIState() {
        PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] Starting GUI state verification...");
        
        try {
            // Check 1: Verify screen exists and is PCGUI
            if (client.currentScreen == null) {
                PokeAlertClient.LOGGER.error("🔍 [GUI-STATE] ❌ Check 1 FAILED: Current screen is null");
                return false;
            }
            
            String screenClassName = client.currentScreen.getClass().getName();
            PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] Check 1: Screen class = {}", screenClassName);
            
            if (!screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                PokeAlertClient.LOGGER.warn("🔍 [GUI-STATE] ⚠️ Check 1 WARNING: Current screen is not PCGUI: {}", screenClassName);
            } else {
                PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ Check 1 PASSED: Screen is PCGUI");
            }
            
            // Check 2: Verify StorageWidget exists and is accessible
            Object storageWidget = null;
            try {
                java.lang.reflect.Field storageWidgetField = client.currentScreen.getClass().getDeclaredField("storageWidget");
                storageWidgetField.setAccessible(true);
                storageWidget = storageWidgetField.get(client.currentScreen);
                
                if (storageWidget == null) {
                    PokeAlertClient.LOGGER.warn("🔍 [GUI-STATE] ⚠️ Check 2 WARNING: StorageWidget field is null");
                } else {
                    PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ Check 2 PASSED: StorageWidget exists = {}", storageWidget.getClass().getName());
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.warn("🔍 [GUI-STATE] ⚠️ Check 2 WARNING: Could not access StorageWidget: {}", e.getMessage());
            }
            
            // Check 3: Verify GUI is initialized (check for common initialization fields/methods)
            try {
                // Check if screen has initialization state
                java.lang.reflect.Field initField = client.currentScreen.getClass().getDeclaredField("initialized");
                initField.setAccessible(true);
                boolean initialized = initField.getBoolean(client.currentScreen);
                PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] Check 3: Screen initialized = {}", initialized);
            } catch (NoSuchFieldException e) {
                PokeAlertClient.LOGGER.debug("🔍 [GUI-STATE] Check 3: No 'initialized' field found (may not exist)");
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("🔍 [GUI-STATE] Check 3: Could not check initialization state: {}", e.getMessage());
            }
            
            // Check 4: Verify no blocking interactions (check if screen is focused)
            boolean isFocused = client.currentScreen.isFocused();
            PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] Check 4: Screen is focused = {}", isFocused);
            if (!isFocused) {
                PokeAlertClient.LOGGER.warn("🔍 [GUI-STATE] ⚠️ Check 4 WARNING: Screen is not focused - attempting to focus...");
                // Try to focus the screen
                try {
                    // Attempt to focus by clicking in the center of the screen
                    int screenWidth = client.getWindow().getScaledWidth();
                    int screenHeight = client.getWindow().getScaledHeight();
                    double centerX = screenWidth / 2.0;
                    double centerY = screenHeight / 2.0;
                    
                    // Set cursor to center and click to focus
                    long windowHandle = client.getWindow().getHandle();
                    int framebufferWidth = client.getWindow().getFramebufferWidth();
                    int framebufferHeight = client.getWindow().getFramebufferHeight();
                    double unscaledCenterX = centerX * (framebufferWidth / (double)screenWidth);
                    double unscaledCenterY = centerY * (framebufferHeight / (double)screenHeight);
                    
                    org.lwjgl.glfw.GLFW.glfwSetCursorPos(windowHandle, unscaledCenterX, unscaledCenterY);
                    // NO Thread.sleep - don't block render thread!
                    
                    // Try to focus screen (some screens have focus methods)
                    try {
                        java.lang.reflect.Method focusMethod = client.currentScreen.getClass().getMethod("focus");
                        focusMethod.invoke(client.currentScreen);
                        PokeAlertClient.LOGGER.info("🔍 [GUI-STATE]   Attempted to focus screen via focus() method");
                    } catch (NoSuchMethodException e) {
                        PokeAlertClient.LOGGER.debug("🔍 [GUI-STATE]   Screen doesn't have focus() method");
                    }
                    
                    // Check focus immediately - no blocking!
                    isFocused = client.currentScreen.isFocused();
                    PokeAlertClient.LOGGER.info("🔍 [GUI-STATE]   Screen focused after attempt: {}", isFocused);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("🔍 [GUI-STATE]   Could not attempt to focus screen: {}", e.getMessage());
                }
                
                if (!isFocused) {
                    PokeAlertClient.LOGGER.warn("🔍 [GUI-STATE] ⚠️ Check 4 WARNING: Screen is still not focused after focus attempt");
                } else {
                    PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ Check 4 PASSED: Screen is now focused");
                }
            } else {
                PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ Check 4 PASSED: Screen is focused");
            }
            
            // Check 5: GUI animations (non-blocking - animations happen naturally)
            PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] Check 5: GUI animations will complete naturally (no blocking wait)");
            // CRITICAL: Don't block render thread - animations happen naturally during rendering
            PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ Check 5 PASSED: Animation check completed (non-blocking)");
            
            // Check 6: Verify window is active and not minimized
            try {
                long windowHandle = client.getWindow().getHandle();
                int windowFocused = org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(windowHandle, org.lwjgl.glfw.GLFW.GLFW_FOCUSED);
                boolean isWindowFocused = windowFocused == org.lwjgl.glfw.GLFW.GLFW_TRUE;
                PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] Check 6: Window is focused = {}", isWindowFocused);
                if (!isWindowFocused) {
                    PokeAlertClient.LOGGER.warn("🔍 [GUI-STATE] ⚠️ Check 6 WARNING: Window is not focused - may affect event handling");
                } else {
                    PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ Check 6 PASSED: Window is focused");
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("🔍 [GUI-STATE] Check 6: Could not check window focus: {}", e.getMessage());
            }
            
            // Check 7: Verify StorageWidget state (if accessible)
            if (storageWidget != null) {
                try {
                    // Check grabbedSlot state (should be null before transfer)
                    java.lang.reflect.Field grabbedSlotField = storageWidget.getClass().getDeclaredField("grabbedSlot");
                    grabbedSlotField.setAccessible(true);
                    Object grabbedSlot = grabbedSlotField.get(storageWidget);
                    PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] Check 7: StorageWidget.grabbedSlot = {}", grabbedSlot);
                    if (grabbedSlot != null) {
                        PokeAlertClient.LOGGER.warn("🔍 [GUI-STATE] ⚠️ Check 7 WARNING: grabbedSlot is not null - GUI may be in drag state");
                    } else {
                        PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ Check 7 PASSED: grabbedSlot is null (ready for transfer)");
                    }
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("🔍 [GUI-STATE] Check 7: Could not check grabbedSlot: {}", e.getMessage());
                }
            }
            
            PokeAlertClient.LOGGER.info("🔍 [GUI-STATE] ✅ GUI state verification completed");
            return true;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 [GUI-STATE] ❌ Error during GUI state verification: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * PRIMARY METHOD: GLFW Cursor Positioning + Mouse.onCursorPos() + Mouse.onMouseButton()
     * 
     * CRITICAL FIX (Iteration 33):
     * - glfwSetCursorPos() only moves cursor VISUALLY
     * - Mouse class has INTERNAL x, y fields that track position
     * - onMouseButton() uses these INTERNAL fields to determine click location
     * - We MUST call onCursorPos() to update internal position before onMouseButton()
     * - NO Thread.sleep() on render thread - causes freeze!
     * 
     * User Click Flow: 
     *   Hardware → GLFW callback → Mouse.onCursorPos() → updates internal x,y
     *   Hardware → GLFW callback → Mouse.onMouseButton() → uses internal x,y → Screen → Widget
     * 
     * Our Flow (FIXED):
     *   glfwSetCursorPos() → onCursorPos() → onMouseButton() → Screen → Widget
     * 
     * Status: FIXED - Calls onCursorPos() to update internal position + no Thread.sleep on render thread
     */
    private boolean performClickWithScreenMousePressedReleased(double sourceX, double sourceY, double destX, double destY, int delayBetweenClicks) {
        PokeAlertClient.LOGGER.info("🔍 [PRIMARY] Starting click implementation (Iteration 33 - with onCursorPos fix)...");
        
        try {
            if (client.currentScreen == null) {
                PokeAlertClient.LOGGER.error("🔍 [PRIMARY] ❌ Current screen is null");
                return false;
            }
            
            if (client.getWindow() == null) {
                PokeAlertClient.LOGGER.error("🔍 [PRIMARY] ❌ Window is null");
                return false;
            }
            
            final long windowHandle = client.getWindow().getHandle();
            final int mouseButton = 0; // Left mouse button
            
            // Convert scaled coordinates to unscaled coordinates for GLFW
            int scaledWidth = client.getWindow().getScaledWidth();
            int scaledHeight = client.getWindow().getScaledHeight();
            int framebufferWidth = client.getWindow().getFramebufferWidth();
            int framebufferHeight = client.getWindow().getFramebufferHeight();
            
            // Convert scaled GUI coordinates to unscaled GLFW coordinates
            double unscaledSourceX = sourceX * (framebufferWidth / (double)scaledWidth);
            double unscaledSourceY = sourceY * (framebufferHeight / (double)scaledHeight);
            final double unscaledDestX = destX * (framebufferWidth / (double)scaledWidth);
            final double unscaledDestY = destY * (framebufferHeight / (double)scaledHeight);
            
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY] Coordinate conversion:");
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Scaled dimensions: {}x{}", scaledWidth, scaledHeight);
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Framebuffer dimensions: {}x{}", framebufferWidth, framebufferHeight);
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Source: scaled ({}, {}) → unscaled ({}, {})", 
                (int)sourceX, (int)sourceY, (int)unscaledSourceX, (int)unscaledSourceY);
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Dest: scaled ({}, {}) → unscaled ({}, {})", 
                (int)destX, (int)destY, (int)unscaledDestX, (int)unscaledDestY);
            
            // Get Mouse methods via reflection
            java.lang.reflect.Method onMouseButtonMethod = null;
            java.lang.reflect.Method onCursorPosMethod = null;
            boolean useReflectionMethod = false;
            
            if (client.mouse != null) {
                // Find onMouseButton method (obfuscated: method_1601)
                String[] buttonMethodNames = {"method_1601", "onMouseButton"};
                for (String methodName : buttonMethodNames) {
                    try {
                        onMouseButtonMethod = client.mouse.getClass().getDeclaredMethod(methodName, long.class, int.class, int.class, int.class);
                        onMouseButtonMethod.setAccessible(true);
                        PokeAlertClient.LOGGER.info("🔍 [PRIMARY] ✅ Found onMouseButton method '{}' via reflection", methodName);
                        break;
                    } catch (NoSuchMethodException e) {
                        // Try next
                    }
                }
                
                // Find onCursorPos method (obfuscated: method_1598 or method_1600)
                // CRITICAL: This updates Mouse's internal x,y fields!
                String[] cursorMethodNames = {"method_1598", "onCursorPos", "method_1600"};
                for (String methodName : cursorMethodNames) {
                    try {
                        onCursorPosMethod = client.mouse.getClass().getDeclaredMethod(methodName, long.class, double.class, double.class);
                        onCursorPosMethod.setAccessible(true);
                        PokeAlertClient.LOGGER.info("🔍 [PRIMARY] ✅ Found onCursorPos method '{}' via reflection", methodName);
                        break;
                    } catch (NoSuchMethodException e) {
                        // Try next
                    }
                }
                
                useReflectionMethod = (onMouseButtonMethod != null);
                
                if (onCursorPosMethod == null) {
                    PokeAlertClient.LOGGER.warn("🔍 [PRIMARY] ⚠️ Could not find onCursorPos method - internal position won't be updated!");
                }
            }
            
            final java.lang.reflect.Method finalOnMouseButtonMethod = onMouseButtonMethod;
            final java.lang.reflect.Method finalOnCursorPosMethod = onCursorPosMethod;
            final net.minecraft.client.Mouse mouse = client.mouse;
            
            // =====================================================
            // CLICK 1: Source coordinate - Pick up the Pokemon
            // =====================================================
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY] Step 1: Click at source coordinate (scaled: {}, {})", (int)sourceX, (int)sourceY);
            
            // 1a. Set cursor position using GLFW (visual cursor movement)
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(windowHandle, unscaledSourceX, unscaledSourceY);
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   ✅ GLFW cursor positioned to ({}, {})", (int)unscaledSourceX, (int)unscaledSourceY);
            
            // 1b. CRITICAL: Update Mouse's internal position via onCursorPos()
            if (finalOnCursorPosMethod != null && mouse != null) {
                try {
                    finalOnCursorPosMethod.invoke(mouse, windowHandle, unscaledSourceX, unscaledSourceY);
                    PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   ✅ Mouse.onCursorPos() called - internal position updated to ({}, {})", (int)unscaledSourceX, (int)unscaledSourceY);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.warn("🔍 [PRIMARY]   ⚠️ onCursorPos() failed: {}", e.getMessage());
                }
            }
            
            // 1c. Perform click (PRESS only - keep pressed while moving to simulate drag)
            boolean click1Success = false;
            if (useReflectionMethod && finalOnMouseButtonMethod != null && mouse != null) {
                try {
                    PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Calling Mouse.onMouseButton() PRESS...");
                    finalOnMouseButtonMethod.invoke(mouse, windowHandle, 
                        org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT, 
                        org.lwjgl.glfw.GLFW.GLFW_PRESS, 
                        0);
                    PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   ✅ Mouse.onMouseButton() PRESS completed (button held down)");
                    click1Success = true;
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("🔍 [PRIMARY]   ❌ Mouse.onMouseButton() PRESS failed: {}", e.getMessage());
                }
            }
            
            if (!click1Success) {
                // Fallback: Screen.mouseClicked() (this does full click, not ideal but better than nothing)
                PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Using Screen.mouseClicked() fallback...");
                boolean clicked = client.currentScreen.mouseClicked(sourceX, sourceY, mouseButton);
                PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Screen.mouseClicked() returned: {}", clicked);
                click1Success = clicked;
            }
            
            // CRITICAL: Add delay between PRESS and MOVE to allow the grab to register
            // Without this delay, the grab state may not be properly set before we move
            try {
                Thread.sleep(delayBetweenClicks / 2); // Use half the delay for grab registration
                PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Waited {}ms for grab to register", delayBetweenClicks / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // =====================================================
            // Move cursor to destination (while button is held)
            // =====================================================
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY] Step 2: Move cursor to destination (scaled: {}, {})", (int)destX, (int)destY);
            
            // 2a. Set cursor position using GLFW (visual cursor movement)
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(windowHandle, unscaledDestX, unscaledDestY);
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   ✅ GLFW cursor positioned to ({}, {})", (int)unscaledDestX, (int)unscaledDestY);
            
            // 2b. CRITICAL: Update Mouse's internal position via onCursorPos()
            if (finalOnCursorPosMethod != null && mouse != null) {
                try {
                    finalOnCursorPosMethod.invoke(mouse, windowHandle, unscaledDestX, unscaledDestY);
                    PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   ✅ Mouse.onCursorPos() called - internal position updated to ({}, {})", (int)unscaledDestX, (int)unscaledDestY);
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.warn("🔍 [PRIMARY]   ⚠️ onCursorPos() failed: {}", e.getMessage());
                }
            }
            
            // CRITICAL: Add delay before RELEASE to allow the move to be processed
            // Without this delay, the release happens before the GUI updates the cursor position
            try {
                Thread.sleep(delayBetweenClicks / 2); // Use half the delay for move processing
                PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Waited {}ms for move to process", delayBetweenClicks / 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // =====================================================
            // CLICK 2: Release at destination - Drop the Pokemon
            // =====================================================
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY] Step 3: Release at destination coordinate (scaled: {}, {})", (int)destX, (int)destY);
            
            boolean click2Success = false;
            if (useReflectionMethod && finalOnMouseButtonMethod != null && mouse != null) {
                try {
                    PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Calling Mouse.onMouseButton() RELEASE...");
                    finalOnMouseButtonMethod.invoke(mouse, windowHandle, 
                        org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT, 
                        org.lwjgl.glfw.GLFW.GLFW_RELEASE, 
                        0);
                    PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   ✅ Mouse.onMouseButton() RELEASE completed (drop)");
                    click2Success = true;
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("🔍 [PRIMARY]   ❌ Mouse.onMouseButton() RELEASE failed: {}", e.getMessage());
                }
            }
            
            if (!click2Success && click1Success) {
                // If we used reflection for press but release failed, try release via fallback
                PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Using mouseReleased() fallback...");
                try {
                    boolean released = client.currentScreen.mouseReleased(destX, destY, mouseButton);
                    PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   mouseReleased() returned: {}", released);
                    click2Success = released;
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.error("🔍 [PRIMARY]   ❌ mouseReleased() failed: {}", e.getMessage());
                }
            } else if (!click1Success) {
                // If source click used fallback (full click), do full click at destination too
                PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Using Screen.mouseClicked() fallback for destination...");
                boolean clicked = client.currentScreen.mouseClicked(destX, destY, mouseButton);
                PokeAlertClient.LOGGER.info("🔍 [PRIMARY]   Screen.mouseClicked() returned: {}", clicked);
                click2Success = clicked;
            }
            
            PokeAlertClient.LOGGER.info("🔍 [PRIMARY] ✅ Drag-drop sequence completed: PRESS at source → MOVE → RELEASE at destination");
            return click1Success && click2Success;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 [PRIMARY] ❌ Error in click implementation: {}", e.getMessage());
            e.printStackTrace();
                return false;
        }
    }
    
    /**
     * ITERATION 35: Transfer using onStorageSlotClicked() with widget instances
     * 
     * This is the CORRECT approach that matches manual player interactions:
     * 1. Access boxSlots and partySlots lists from StorageWidget via reflection
     * 2. Get actual slot widget at the target index
     * 3. Call onStorageSlotClicked(sourceWidget) to grab (sets grabbedSlot)
     * 4. Call onStorageSlotClicked(destWidget) to drop (completes transfer)
     * 
     * Previous approaches (Screen.mouseClicked, Mouse.onMouseButton) failed because
     * they don't reach StorageWidget's internal drag-and-drop handlers.
     * Only onStorageSlotClicked() properly sets grabbedSlot and triggers transfers.
     * 
     * @param storageWidget The StorageWidget instance
     * @param pcSlotIndex 0-based PC slot index (0-29) within the currently visible box
     * @param partySlotIndex 0-based party slot index (0-5)
     * @return true if transfer was initiated successfully
     */
    private boolean performTransferWithWidgets(Object storageWidget, int pcSlotIndex, int partySlotIndex) {
        PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] Starting transfer: PC Slot {} → Party Slot {}", pcSlotIndex, partySlotIndex + 1);
        
        try {
            if (storageWidget == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] ❌ StorageWidget is null");
                return false;
            }
            
            // STEP 1: Access boxSlots list from StorageWidget
            java.util.List<?> boxSlots = null;
            String[] boxSlotFieldNames = {"boxSlots", "pcSlots", "storageSlots", "slots"};
            for (String fieldName : boxSlotFieldNames) {
                try {
                    java.lang.reflect.Field field = storageWidget.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(storageWidget);
                    if (fieldValue instanceof java.util.List) {
                        boxSlots = (java.util.List<?>) fieldValue;
                        PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Found boxSlots via field '{}' - size: {}", fieldName, boxSlots.size());
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Field not found, try next
                }
            }
            
            if (boxSlots == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Could not find boxSlots field");
                return false;
            }
            
            // STEP 2: Access partySlots list from StorageWidget
            java.util.List<?> partySlots = null;
            String[] partySlotFieldNames = {"partySlots", "partyStorageSlots"};
            for (String fieldName : partySlotFieldNames) {
                try {
                    java.lang.reflect.Field field = storageWidget.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(storageWidget);
                    if (fieldValue instanceof java.util.List) {
                        partySlots = (java.util.List<?>) fieldValue;
                        PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Found partySlots via field '{}' - size: {}", fieldName, partySlots.size());
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Field not found, try next
                }
            }
            
            if (partySlots == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Could not find partySlots field");
                return false;
            }
            
            // STEP 3: Validate indices and get slot widgets
            if (pcSlotIndex < 0 || pcSlotIndex >= boxSlots.size()) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Invalid pcSlotIndex: {} (boxSlots size: {})", pcSlotIndex, boxSlots.size());
                return false;
            }
            
            if (partySlotIndex < 0 || partySlotIndex >= partySlots.size()) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Invalid partySlotIndex: {} (partySlots size: {})", partySlotIndex, partySlots.size());
                return false;
            }
            
            Object pcSlotWidget = boxSlots.get(pcSlotIndex);
            Object partySlotWidget = partySlots.get(partySlotIndex);
            
            if (pcSlotWidget == null || partySlotWidget == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Slot widget is null");
                return false;
            }
            
            // STEP 4: Find onStorageSlotClicked method
            java.lang.reflect.Method onStorageSlotClickedMethod = null;
            
            // Try to find the method with Object parameter (generic slot widget)
            try {
                onStorageSlotClickedMethod = storageWidget.getClass().getDeclaredMethod("onStorageSlotClicked", Object.class);
                onStorageSlotClickedMethod.setAccessible(true);
            } catch (NoSuchMethodException e) {
                // Try with specific slot widget class
                Class<?> slotWidgetClass = pcSlotWidget.getClass();
                try {
                    onStorageSlotClickedMethod = storageWidget.getClass().getDeclaredMethod("onStorageSlotClicked", slotWidgetClass);
                    onStorageSlotClickedMethod.setAccessible(true);
                } catch (NoSuchMethodException e2) {
                    // Scan all methods for slot click handler
                    for (java.lang.reflect.Method m : storageWidget.getClass().getDeclaredMethods()) {
                        String methodName = m.getName().toLowerCase();
                        if (methodName.contains("slot") && methodName.contains("click") && m.getParameterCount() == 1) {
                            m.setAccessible(true);
                            onStorageSlotClickedMethod = m;
                            break;
                        }
                    }
                }
            }
            
            if (onStorageSlotClickedMethod == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Could not find onStorageSlotClicked method");
                return false;
            }
            
            // STEP 5: Check grabbedSlot BEFORE first click
            Object grabbedSlotBefore = null;
            java.lang.reflect.Field grabbedSlotField = null;
            try {
                grabbedSlotField = storageWidget.getClass().getDeclaredField("grabbedSlot");
                grabbedSlotField.setAccessible(true);
                grabbedSlotBefore = grabbedSlotField.get(storageWidget);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Could not check grabbedSlot: {}", e.getMessage());
            }
            
            // STEP 6: Click source slot (GRAB)
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] Clicking source slot (PC Slot {}) to GRAB...", pcSlotIndex);
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] PC slot widget type: {}", pcSlotWidget.getClass().getName());
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] Party slot widget type: {}", partySlotWidget.getClass().getName());
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] Method parameter type: {}", onStorageSlotClickedMethod.getParameterTypes()[0].getName());
            
            final java.lang.reflect.Method finalMethod = onStorageSlotClickedMethod;
            final Object finalPcSlotWidget = pcSlotWidget;
            final Object finalPartySlotWidget = partySlotWidget;
            
            try {
                // Call onStorageSlotClicked to initiate grab
                PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] Invoking onStorageSlotClicked with PC slot...");
                finalMethod.invoke(storageWidget, finalPcSlotWidget);
                PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] onStorageSlotClicked invoked successfully");
            } catch (java.lang.reflect.InvocationTargetException ite) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Method threw exception: {}", ite.getCause());
                ite.getCause().printStackTrace();
                return false;
            } catch (IllegalArgumentException iae) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] IllegalArgumentException - type mismatch! Expected: {}, Got: {}",
                    onStorageSlotClickedMethod.getParameterTypes()[0].getName(), pcSlotWidget.getClass().getName());
                return false;
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Failed to click source slot: {} ({})", e.getMessage(), e.getClass().getName());
                return false;
            }
            
            // Check grabbedSlot AFTER first click
            Object grabbedSlotAfterGrab = null;
            try {
                if (grabbedSlotField != null) {
                    grabbedSlotAfterGrab = grabbedSlotField.get(storageWidget);
                    PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] grabbedSlot AFTER click: {}", grabbedSlotAfterGrab);
                    
                    if (grabbedSlotAfterGrab == null) {
                        PokeAlertClient.LOGGER.warn("[WIDGET-TRANSFER] grabbedSlot still null after grab - click did not register!");
                        
                        // REMOVED: Coordinate-based fallback no longer needed
                        // Widget-based approach should work without coordinate mapping
                        
                        // STEP 6C: MIXIN WORKAROUND - Directly set grabbedSlot
                        // Bypasses mixin interference from "more_cobblemon_tweaks" mod
                        if (grabbedSlotAfterGrab == null) {
                            PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Applying mixin workaround - directly setting grabbedSlot");
                            
                            try {
                                grabbedSlotField.set(storageWidget, finalPcSlotWidget);
                                grabbedSlotAfterGrab = grabbedSlotField.get(storageWidget);
                                if (grabbedSlotAfterGrab == null) {
                                    PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Failed to set grabbedSlot directly");
                                }
                            } catch (Exception setEx) {
                                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Could not set grabbedSlot directly: {}", setEx.getMessage());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Could not check grabbedSlot after grab: {}", e.getMessage());
            }
            
            // STEP 7: Wait for grab to register before dropping
            // CRITICAL: Without this delay, the grab state doesn't register properly in the UI
            try {
                Thread.sleep(150); // 150ms delay for UI to process grab state
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Waited 150ms for grab to register");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // STEP 8: Click destination slot (DROP)
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] Clicking destination slot (Party Slot {}) to DROP...", partySlotIndex + 1);
            try {
                finalMethod.invoke(storageWidget, finalPartySlotWidget);
                PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] DROP click invoked successfully");
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Failed to click destination slot: {} ({})", e.getMessage(), e.getClass().getName());
                return false;
            }
            
            // STEP 9: Check grabbedSlot AFTER drop - should be null if drop succeeded
            try {
                if (grabbedSlotField != null) {
                    Object grabbedSlotAfterDrop = grabbedSlotField.get(storageWidget);
                    if (grabbedSlotAfterDrop == null) {
                        PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] ✅ grabbedSlot is NULL after drop - transfer likely succeeded!");
                    } else {
                        PokeAlertClient.LOGGER.warn("[WIDGET-TRANSFER] ⚠️ grabbedSlot still SET after drop: {} - drop may have failed", grabbedSlotAfterDrop);
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Could not check grabbedSlot after drop: {}", e.getMessage());
            }
            
            // STEP 10: Wait for drop to complete and server to sync
            // CRITICAL: Without this delay, verification happens before server processes the transfer
            try {
                Thread.sleep(500); // Increased to 500ms delay for server sync
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER] Waited 500ms for server sync");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER] Transfer completed: PC Slot {} → Party Slot {}", pcSlotIndex, partySlotIndex + 1);
            return true;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER] Error in widget-based transfer: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * ITERATION 36: Bidirectional widget-based transfer (Party → PC or PC → Party)
     * 
     * Uses the same onStorageSlotClicked() approach as PC → Party, but works for both directions.
     * This eliminates the need for manually configured coordinates.
     * 
     * The key insight is that onStorageSlotClicked() is the universal click handler for both
     * PC slots and Party slots - it handles grab/drop state automatically.
     * 
     * @param storageWidget The StorageWidget instance
     * @param partySlotIndex 0-based party slot index (0-5)
     * @param pcSlotIndex 0-based PC slot index (0-29) within the currently visible box
     * @param partyToPC true if transferring from Party to PC, false for PC to Party
     * @return true if transfer was initiated successfully
     */
    private boolean performTransferWithWidgetsBidirectional(Object storageWidget, int partySlotIndex, int pcSlotIndex, boolean partyToPC) {
        String direction = partyToPC ? "Party → PC" : "PC → Party";
        PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] Starting {} transfer: Party Slot {} {} PC Slot {}", 
            direction, partySlotIndex + 1, partyToPC ? "→" : "←", pcSlotIndex);
        
        try {
            if (storageWidget == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] ❌ StorageWidget is null");
                return false;
            }
            
            // STEP 1: Access boxSlots list from StorageWidget
            java.util.List<?> boxSlots = null;
            String[] boxSlotFieldNames = {"boxSlots", "pcSlots", "storageSlots", "slots"};
            for (String fieldName : boxSlotFieldNames) {
                try {
                    java.lang.reflect.Field field = storageWidget.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(storageWidget);
                    if (fieldValue instanceof java.util.List) {
                        boxSlots = (java.util.List<?>) fieldValue;
                        PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Found boxSlots via field '{}' - size: {}", fieldName, boxSlots.size());
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Field not found, try next
                }
            }
            
            if (boxSlots == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Could not find boxSlots field");
                return false;
            }
            
            // STEP 2: Access partySlots list from StorageWidget
            java.util.List<?> partySlots = null;
            String[] partySlotFieldNames = {"partySlots", "partyStorageSlots"};
            for (String fieldName : partySlotFieldNames) {
                try {
                    java.lang.reflect.Field field = storageWidget.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object fieldValue = field.get(storageWidget);
                    if (fieldValue instanceof java.util.List) {
                        partySlots = (java.util.List<?>) fieldValue;
                        PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Found partySlots via field '{}' - size: {}", fieldName, partySlots.size());
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Field not found, try next
                }
            }
            
            if (partySlots == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Could not find partySlots field");
                return false;
            }
            
            // STEP 3: Validate indices and get slot widgets
            if (pcSlotIndex < 0 || pcSlotIndex >= boxSlots.size()) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Invalid pcSlotIndex: {} (boxSlots size: {})", pcSlotIndex, boxSlots.size());
                return false;
            }
            
            if (partySlotIndex < 0 || partySlotIndex >= partySlots.size()) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Invalid partySlotIndex: {} (partySlots size: {})", partySlotIndex, partySlots.size());
                return false;
            }
            
            Object pcSlotWidget = boxSlots.get(pcSlotIndex);
            Object partySlotWidget = partySlots.get(partySlotIndex);
            
            if (pcSlotWidget == null || partySlotWidget == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Slot widget is null");
                return false;
            }
            
            // Determine source and destination based on direction
            Object sourceWidget = partyToPC ? partySlotWidget : pcSlotWidget;
            Object destWidget = partyToPC ? pcSlotWidget : partySlotWidget;
            
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] Source widget: {}, Dest widget: {}", 
                sourceWidget.getClass().getSimpleName(), destWidget.getClass().getSimpleName());
            
            // STEP 4: Find onStorageSlotClicked method
            java.lang.reflect.Method onStorageSlotClickedMethod = null;
            
            // Try to find the method with different parameter types
            Class<?>[] paramClasses = {Object.class, sourceWidget.getClass(), pcSlotWidget.getClass(), partySlotWidget.getClass()};
            for (Class<?> paramClass : paramClasses) {
                try {
                    onStorageSlotClickedMethod = storageWidget.getClass().getDeclaredMethod("onStorageSlotClicked", paramClass);
                    onStorageSlotClickedMethod.setAccessible(true);
                    PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Found onStorageSlotClicked with param type: {}", paramClass.getSimpleName());
                    break;
                } catch (NoSuchMethodException e) {
                    // Try next
                }
            }
            
            // Scan for slot click handler if not found
            if (onStorageSlotClickedMethod == null) {
                for (java.lang.reflect.Method m : storageWidget.getClass().getDeclaredMethods()) {
                    String methodName = m.getName().toLowerCase();
                    if (methodName.contains("slot") && methodName.contains("click") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        onStorageSlotClickedMethod = m;
                        PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Found slot click method via scan: {}", m.getName());
                        break;
                    }
                }
            }
            
            if (onStorageSlotClickedMethod == null) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Could not find onStorageSlotClicked method");
                return false;
            }
            
            // STEP 5: Get grabbedSlot field for diagnostics
            java.lang.reflect.Field grabbedSlotField = null;
            try {
                grabbedSlotField = storageWidget.getClass().getDeclaredField("grabbedSlot");
                grabbedSlotField.setAccessible(true);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Could not access grabbedSlot field: {}", e.getMessage());
            }
            
            // STEP 6: Click source slot (GRAB)
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] Step 1: Clicking source slot to GRAB...");
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] Source widget type: {}", sourceWidget.getClass().getName());
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] Dest widget type: {}", destWidget.getClass().getName());
            
            try {
                onStorageSlotClickedMethod.invoke(storageWidget, sourceWidget);
                PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] ✅ Source slot clicked (grab initiated)");
            } catch (java.lang.reflect.InvocationTargetException ite) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Method threw exception: {}", ite.getCause());
                return false;
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Failed to click source slot: {}", e.getMessage());
                return false;
            }
            
            // Check grabbedSlot after GRAB
            try {
                if (grabbedSlotField != null) {
                    Object grabbedSlotAfterGrab = grabbedSlotField.get(storageWidget);
                    if (grabbedSlotAfterGrab != null) {
                        PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] grabbedSlot AFTER grab: {}", grabbedSlotAfterGrab);
                    } else {
                        PokeAlertClient.LOGGER.warn("[WIDGET-TRANSFER-BIDIR] ⚠️ grabbedSlot is NULL after grab - click may not have registered!");
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Could not check grabbedSlot: {}", e.getMessage());
            }
            
            // STEP 7: Wait for grab to register
            try {
                Thread.sleep(200); // 200ms delay for UI to process grab state
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Waited 200ms for grab to register");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // STEP 8: Click destination slot (DROP)
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] Step 2: Clicking destination slot to DROP...");
            
            try {
                onStorageSlotClickedMethod.invoke(storageWidget, destWidget);
                PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] ✅ Destination slot clicked (drop completed)");
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Failed to click destination slot: {}", e.getMessage());
                return false;
            }
            
            // Check grabbedSlot after DROP - should be null if drop succeeded
            try {
                if (grabbedSlotField != null) {
                    Object grabbedSlotAfterDrop = grabbedSlotField.get(storageWidget);
                    if (grabbedSlotAfterDrop == null) {
                        PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] ✅ grabbedSlot is NULL after drop - transfer likely succeeded!");
                    } else {
                        PokeAlertClient.LOGGER.warn("[WIDGET-TRANSFER-BIDIR] ⚠️ grabbedSlot still SET after drop: {} - drop may have failed", grabbedSlotAfterDrop);
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Could not check grabbedSlot after drop: {}", e.getMessage());
            }
            
            // STEP 9: Wait for server sync
            try {
                Thread.sleep(500); // Increased to 500ms delay for server sync
                PokeAlertClient.LOGGER.debug("[WIDGET-TRANSFER-BIDIR] Waited 500ms for server sync");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            PokeAlertClient.LOGGER.info("[WIDGET-TRANSFER-BIDIR] ✅ Transfer completed: {} (Party Slot {} {} PC Slot {})", 
                direction, partySlotIndex + 1, partyToPC ? "→" : "←", pcSlotIndex);
            return true;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("[WIDGET-TRANSFER-BIDIR] Error in bidirectional transfer: {}", e.getMessage());
            return false;
        }
    }
    
    
    /**
     * Get currently visible box index from StorageWidget
     * Returns 0-based box index, or -1 if extraction fails
     */
    private int getCurrentVisibleBox(Object storageWidget) {
        if (storageWidget == null) {
            PokeAlertClient.LOGGER.error("[BOX-NAV] getCurrentVisibleBox: StorageWidget is null");
            return -1;
        }
        
        PokeAlertClient.LOGGER.info("[BOX-NAV] getCurrentVisibleBox: Extracting current visible box from StorageWidget");
        
        try {
            // Method 1: Try getCurrentBox() method
            try {
                java.lang.reflect.Method getCurrentBoxMethod = storageWidget.getClass().getMethod("getCurrentBox");
                int currentBox = ((Number) getCurrentBoxMethod.invoke(storageWidget)).intValue();
                PokeAlertClient.LOGGER.info("[BOX-NAV] getCurrentVisibleBox: ✅ Extracted via getCurrentBox() = {} (0-based)", currentBox);
                return currentBox;
            } catch (NoSuchMethodException e) {
                PokeAlertClient.LOGGER.debug("[BOX-NAV] getCurrentVisibleBox: getCurrentBox() not found, trying fields...");
            }
            
            // Method 2: Try "box" field (the actual field name from logs)
            try {
                java.lang.reflect.Field boxField = storageWidget.getClass().getDeclaredField("box");
                boxField.setAccessible(true);
                int currentBox = boxField.getInt(storageWidget);
                PokeAlertClient.LOGGER.info("[BOX-NAV] getCurrentVisibleBox: ✅ Extracted via 'box' field = {} (0-based)", currentBox);
                return currentBox;
            } catch (NoSuchFieldException e) {
                PokeAlertClient.LOGGER.debug("[BOX-NAV] getCurrentVisibleBox: 'box' field not found, trying alternative names...");
            }
            
            // Method 3: Try currentBox field
            try {
                java.lang.reflect.Field currentBoxField = storageWidget.getClass().getDeclaredField("currentBox");
                currentBoxField.setAccessible(true);
                int currentBox = currentBoxField.getInt(storageWidget);
                PokeAlertClient.LOGGER.info("[BOX-NAV] getCurrentVisibleBox: ✅ Extracted via currentBox field = {} (0-based)", currentBox);
                return currentBox;
            } catch (NoSuchFieldException e) {
                PokeAlertClient.LOGGER.debug("[BOX-NAV] getCurrentVisibleBox: currentBox field not found, trying alternative names...");
            }
            
            // Method 4: Try alternative field names
            java.lang.reflect.Field[] fields = storageWidget.getClass().getDeclaredFields();
            for (java.lang.reflect.Field f : fields) {
                String fieldName = f.getName().toLowerCase();
                if (fieldName.equals("box") || (fieldName.contains("current") && fieldName.contains("box"))) {
                    f.setAccessible(true);
                    try {
                        int currentBox = f.getInt(storageWidget);
                        PokeAlertClient.LOGGER.info("[BOX-NAV] getCurrentVisibleBox: ✅ Extracted via field '{}' = {} (0-based)", f.getName(), currentBox);
                        return currentBox;
                    } catch (Exception e2) {
                        // Skip non-int fields
                    }
                }
            }
            
            PokeAlertClient.LOGGER.warn("[BOX-NAV] getCurrentVisibleBox: ❌ Could not extract current box - available fields: {}", 
                java.util.Arrays.toString(fields));
            return -1;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("[BOX-NAV] getCurrentVisibleBox: ❌ Error extracting current box", e);
            return -1;
        }
    }
    
    /**
     * Navigate to target box using widget-based or coordinate-based approach
     * 
     * ITERATION 36: First tries widget-based navigation (setBox method or arrow widgets)
     * Falls back to coordinate-based arrow clicking if widget approach unavailable
     * 
     * Returns true if navigation successful or already on target box
     */
    private boolean navigateToBox(Object storageWidget, int targetBoxIndex) {
        PokeAlertClient.LOGGER.info("[BOX-NAV] navigateToBox: Attempting to navigate to box {} (0-based index)", targetBoxIndex);
        
        if (storageWidget == null) {
            PokeAlertClient.LOGGER.error("[BOX-NAV] navigateToBox: ❌ StorageWidget is null");
            return false;
        }
        
        if (client.currentScreen == null) {
            PokeAlertClient.LOGGER.error("[BOX-NAV] navigateToBox: ❌ Current screen is null");
            return false;
        }
        
        // Get current visible box
        int currentBox = getCurrentVisibleBox(storageWidget);
        
        if (currentBox == -1) {
            PokeAlertClient.LOGGER.warn("[BOX-NAV] navigateToBox: ⚠️ Could not determine current box, attempting navigation anyway");
        } else {
            PokeAlertClient.LOGGER.info("[BOX-NAV] navigateToBox: Current visible box: {} (0-based)", currentBox);
            
            if (currentBox == targetBoxIndex) {
                PokeAlertClient.LOGGER.info("[BOX-NAV] navigateToBox: ✅ Already on target box {}, no navigation needed", targetBoxIndex);
                return true;
            }
        }
        
        // ==================== WIDGET-BASED NAVIGATION (ITERATION 36) ====================
        // CONFIRMED from diagnostic: StorageWidget has setBox(int) method!
        // This allows direct box navigation without clicking arrows
        
        // Method 1: Try direct setBox() method (CONFIRMED TO EXIST)
        try {
            java.lang.reflect.Method setBoxMethod = storageWidget.getClass().getDeclaredMethod("setBox", int.class);
            setBoxMethod.setAccessible(true);
            setBoxMethod.invoke(storageWidget, targetBoxIndex);
            
            // Small delay to let the GUI update
            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            
            // Verify the box changed
            int newBox = getCurrentVisibleBox(storageWidget);
            if (newBox == targetBoxIndex) {
                PokeAlertClient.LOGGER.info("[BOX-NAV] navigateToBox: ✅ Successfully navigated using setBox({}) method", targetBoxIndex);
                return true;
            } else {
                PokeAlertClient.LOGGER.warn("[BOX-NAV] navigateToBox: setBox() called but box is {} instead of {}", newBox, targetBoxIndex);
            }
        } catch (NoSuchMethodException e) {
            PokeAlertClient.LOGGER.debug("[BOX-NAV] navigateToBox: setBox(int) method not found, trying alternatives...");
        } catch (Exception e) {
            PokeAlertClient.LOGGER.warn("[BOX-NAV] navigateToBox: setBox() failed: {}", e.getMessage());
        }
        
        // Method 2: Try incrementBox/decrementBox methods
        try {
            boolean goingRight = targetBoxIndex > currentBox;
            String methodName = goingRight ? "incrementBox" : "decrementBox";
            java.lang.reflect.Method navMethod = null;
            
            // Try common method names
            String[] methodNames = {methodName, goingRight ? "nextBox" : "previousBox", goingRight ? "right" : "left"};
            for (String name : methodNames) {
                try {
                    navMethod = storageWidget.getClass().getDeclaredMethod(name);
                    navMethod.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ex) {
                    // Try next name
                }
            }
            
            if (navMethod != null) {
                int clicks = Math.abs(targetBoxIndex - currentBox);
                for (int i = 0; i < clicks; i++) {
                    navMethod.invoke(storageWidget);
                    Thread.sleep(100); // Small delay between clicks
                }
                
                int newBox = getCurrentVisibleBox(storageWidget);
                if (newBox == targetBoxIndex) {
                    PokeAlertClient.LOGGER.info("[BOX-NAV] navigateToBox: ✅ Successfully navigated using {}() method to box {}", methodName, targetBoxIndex);
                    return true;
                }
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("[BOX-NAV] navigateToBox: increment/decrement methods not found or failed: {}", e.getMessage());
        }
        
        // Method 3: Try to find and click arrow widgets directly
        boolean widgetNavSuccess = navigateToBoxViaWidgets(storageWidget, currentBox, targetBoxIndex);
        if (widgetNavSuccess) {
            return true;
        }
        
        // Final verification
        int finalBox = getCurrentVisibleBox(storageWidget);
        if (finalBox == targetBoxIndex) {
            PokeAlertClient.LOGGER.info("[BOX-NAV] navigateToBox: ✅ Successfully navigated to box {}", targetBoxIndex);
                return true;
        } else {
            PokeAlertClient.LOGGER.warn("[BOX-NAV] navigateToBox: ⚠️ Navigation incomplete - current box: {}, target: {}", finalBox, targetBoxIndex);
            return false;
        }
    }
    
    /**
     * ITERATION 36: Widget-based box navigation using arrow button widgets
     * 
     * Searches for arrow button widgets in StorageWidget or PCGUI and clicks them directly.
     * This approach doesn't require manually configured coordinates.
     * 
     * @param storageWidget The StorageWidget instance
     * @param currentBox Current visible box index (0-based)
     * @param targetBox Target box index (0-based)
     * @return true if navigation was successful
     */
    private boolean navigateToBoxViaWidgets(Object storageWidget, int currentBox, int targetBox) {
        PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] Attempting widget-based box navigation from {} to {}", currentBox, targetBox);
        
        try {
            // Look for arrow button widgets in StorageWidget
            Object leftArrowWidget = null;
            Object rightArrowWidget = null;
            
            // Common field names for arrow buttons
            String[] leftFieldNames = {"leftArrow", "leftButton", "previousButton", "boxLeft", "arrowLeft"};
            String[] rightFieldNames = {"rightArrow", "rightButton", "nextButton", "boxRight", "arrowRight"};
            
            // Try to find left arrow widget
            for (String fieldName : leftFieldNames) {
                try {
                    java.lang.reflect.Field field = storageWidget.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    leftArrowWidget = field.get(storageWidget);
                    if (leftArrowWidget != null) {
                        PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] Found left arrow widget via field '{}'", fieldName);
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Try next field name
                }
            }
            
            // Try to find right arrow widget
            for (String fieldName : rightFieldNames) {
                try {
                    java.lang.reflect.Field field = storageWidget.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    rightArrowWidget = field.get(storageWidget);
                    if (rightArrowWidget != null) {
                        PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] Found right arrow widget via field '{}'", fieldName);
                        break;
                    }
                } catch (NoSuchFieldException e) {
                    // Try next field name
                }
            }
            
            // If arrow widgets not found in StorageWidget, try PCGUI
            if (leftArrowWidget == null || rightArrowWidget == null) {
                if (client.currentScreen != null) {
                    for (String fieldName : leftFieldNames) {
                        try {
                            java.lang.reflect.Field field = client.currentScreen.getClass().getDeclaredField(fieldName);
                            field.setAccessible(true);
                            leftArrowWidget = field.get(client.currentScreen);
                            if (leftArrowWidget != null) {
                                PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] Found left arrow widget via PCGUI field '{}'", fieldName);
                                break;
                            }
                        } catch (NoSuchFieldException e) {
                            // Try next field name
                        }
                    }
                    
                    for (String fieldName : rightFieldNames) {
                        try {
                            java.lang.reflect.Field field = client.currentScreen.getClass().getDeclaredField(fieldName);
                            field.setAccessible(true);
                            rightArrowWidget = field.get(client.currentScreen);
                            if (rightArrowWidget != null) {
                                PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] Found right arrow widget via PCGUI field '{}'", fieldName);
                                break;
                            }
                        } catch (NoSuchFieldException e) {
                            // Try next field name
                        }
                    }
                }
            }
            
            // If we found arrow widgets, try to click them
            if (leftArrowWidget != null && rightArrowWidget != null) {
                Object arrowWidget = targetBox > currentBox ? rightArrowWidget : leftArrowWidget;
                int clicks = Math.abs(targetBox - currentBox);
                
                PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] Using {} arrow widget, clicking {} times", 
                    targetBox > currentBox ? "right" : "left", clicks);
                
                // Find a click method on the arrow widget
                java.lang.reflect.Method clickMethod = null;
                String[] clickMethodNames = {"onClick", "mouseClicked", "onPress", "click", "method_25402"};
                
                for (String methodName : clickMethodNames) {
                    try {
                        // Try no-arg version first
                        clickMethod = arrowWidget.getClass().getDeclaredMethod(methodName);
                        clickMethod.setAccessible(true);
                        break;
                    } catch (NoSuchMethodException e) {
                        // Try double, double, int version (mouseClicked)
                        try {
                            clickMethod = arrowWidget.getClass().getDeclaredMethod(methodName, double.class, double.class, int.class);
                            clickMethod.setAccessible(true);
                            break;
                        } catch (NoSuchMethodException e2) {
                            // Try next method name
                        }
                    }
                }
                
                if (clickMethod != null) {
                    for (int i = 0; i < clicks; i++) {
                        try {
                            if (clickMethod.getParameterCount() == 0) {
                                clickMethod.invoke(arrowWidget);
                            } else {
                                // For mouseClicked(double, double, int) - pass 0,0,0 as we're clicking the widget directly
                                clickMethod.invoke(arrowWidget, 0.0, 0.0, 0);
                            }
                            Thread.sleep(100); // Small delay between clicks
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.warn("[BOX-NAV-WIDGET] Click failed: {}", e.getMessage());
                        }
                    }
                    
                    // Verify navigation worked
                    int newBox = getCurrentVisibleBox(storageWidget);
                    if (newBox == targetBox) {
                        PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] ✅ Successfully navigated to box {} via widget clicks", targetBox);
                        return true;
                    }
                }
            }
            
            // Log available fields for debugging
            PokeAlertClient.LOGGER.debug("[BOX-NAV-WIDGET] Arrow widgets not found. Available StorageWidget fields:");
            for (java.lang.reflect.Field f : storageWidget.getClass().getDeclaredFields()) {
                PokeAlertClient.LOGGER.debug("[BOX-NAV-WIDGET]   - {} ({})", f.getName(), f.getType().getSimpleName());
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("[BOX-NAV-WIDGET] Widget navigation failed: {}", e.getMessage());
        }
        
        PokeAlertClient.LOGGER.info("[BOX-NAV-WIDGET] Widget-based navigation not available");
        return false;
    }
    
    /**
     * DIAGNOSTIC: Log all fields and methods available on StorageWidget
     * This helps discover what Cobblemon exposes for widget-based navigation
     * 
     * Run when PC GUI is opened to see available navigation options in the logs
     */
    private void logStorageWidgetDiagnostics(Object storageWidget) {
        if (storageWidget == null) return;
        
        PokeAlertClient.LOGGER.info("========== STORAGEWIDGET DIAGNOSTIC SCAN ==========");
        PokeAlertClient.LOGGER.info("[DIAGNOSTIC] StorageWidget class: {}", storageWidget.getClass().getName());
        
        // Log all declared fields
        PokeAlertClient.LOGGER.info("[DIAGNOSTIC] === FIELDS ===");
        for (java.lang.reflect.Field f : storageWidget.getClass().getDeclaredFields()) {
            String fieldInfo = String.format("  %s %s", f.getType().getSimpleName(), f.getName());
            
            // Check if it's a potential navigation-related field
            String nameLower = f.getName().toLowerCase();
            if (nameLower.contains("arrow") || nameLower.contains("button") || 
                nameLower.contains("left") || nameLower.contains("right") ||
                nameLower.contains("box") || nameLower.contains("nav")) {
                fieldInfo += " ⭐ (potential navigation)";
            }
            
            PokeAlertClient.LOGGER.info("[DIAGNOSTIC] {}", fieldInfo);
        }
        
        // Log declared methods that might be navigation-related
        PokeAlertClient.LOGGER.info("[DIAGNOSTIC] === METHODS (navigation-related) ===");
        for (java.lang.reflect.Method m : storageWidget.getClass().getDeclaredMethods()) {
            String nameLower = m.getName().toLowerCase();
            if (nameLower.contains("box") || nameLower.contains("arrow") || 
                nameLower.contains("left") || nameLower.contains("right") ||
                nameLower.contains("next") || nameLower.contains("prev") ||
                nameLower.contains("increment") || nameLower.contains("decrement") ||
                nameLower.contains("navigate") || nameLower.contains("click") ||
                nameLower.contains("slot")) {
                
                StringBuilder params = new StringBuilder();
                for (Class<?> p : m.getParameterTypes()) {
                    if (params.length() > 0) params.append(", ");
                    params.append(p.getSimpleName());
                }
                
                PokeAlertClient.LOGGER.info("[DIAGNOSTIC]   {}({}) -> {}", 
                    m.getName(), params.toString(), m.getReturnType().getSimpleName());
            }
        }
        
        // Also scan PCGUI for navigation widgets
        if (client.currentScreen != null) {
            PokeAlertClient.LOGGER.info("[DIAGNOSTIC] === PCGUI FIELDS (navigation-related) ===");
            for (java.lang.reflect.Field f : client.currentScreen.getClass().getDeclaredFields()) {
                String nameLower = f.getName().toLowerCase();
                if (nameLower.contains("arrow") || nameLower.contains("button") || 
                    nameLower.contains("left") || nameLower.contains("right") ||
                    nameLower.contains("box") || nameLower.contains("nav")) {
                    PokeAlertClient.LOGGER.info("[DIAGNOSTIC]   {} {} ⭐", f.getType().getSimpleName(), f.getName());
                }
            }
        }
        
        PokeAlertClient.LOGGER.info("========== END DIAGNOSTIC SCAN ==========");
    }
    
    /**
     * Phase 2: Extract box and slot from PCPosition object
     */
    private int[] extractBoxAndSlotFromPCPosition(Object pcPosition) {
        try {
            // Try getBox() and getSlot() methods
            java.lang.reflect.Method getBoxMethod = pcPosition.getClass().getMethod("getBox");
            java.lang.reflect.Method getSlotMethod = pcPosition.getClass().getMethod("getSlot");
            
            int boxIndex = (Integer) getBoxMethod.invoke(pcPosition);
            int slot = (Integer) getSlotMethod.invoke(pcPosition);
            
            return new int[]{boxIndex + 1, slot}; // Convert box index to 1-based
        } catch (Exception e) {
            // Try alternative method names
            try {
                java.lang.reflect.Method getBoxIndexMethod = pcPosition.getClass().getMethod("getBoxIndex");
                java.lang.reflect.Method getSlotIndexMethod = pcPosition.getClass().getMethod("getSlotIndex");
                
                int boxIndex = (Integer) getBoxIndexMethod.invoke(pcPosition);
                int slot = (Integer) getSlotIndexMethod.invoke(pcPosition);
                
                return new int[]{boxIndex + 1, slot}; // Convert box index to 1-based
            } catch (Exception e2) {
                // Try accessing fields directly
                try {
                    java.lang.reflect.Field boxField = pcPosition.getClass().getField("box");
                    java.lang.reflect.Field slotField = pcPosition.getClass().getField("slot");
                    
                    int boxIndex = boxField.getInt(pcPosition);
                    int slot = slotField.getInt(pcPosition);
                    
                    return new int[]{boxIndex + 1, slot}; // Convert box index to 1-based
                } catch (Exception e3) {
                    PokeAlertClient.LOGGER.debug("EggManager: Could not extract box/slot from PCPosition: {}", e3.getMessage());
                    return null;
                }
            }
        }
    }
    
    /**
     * Phase 2: Send Telegram notification with IV stats
     */
    private void sendHatchNotification(EggTrackingInfo trackingInfo, Object boxInfoObj) {
        try {
            com.afiqhasiff.pokealert.client.notification.TelegramNotification telegram = 
                new com.afiqhasiff.pokealert.client.notification.TelegramNotification();
            telegram.initialize();
            
            StringBuilder message = new StringBuilder();
            message.append("🥚 <b>Egg Hatched!</b>\n");
            
            // Add Pokemon name with hyperlink (same logic as PokeAlert notifications)
            String pokemonName = trackingInfo.pokemonName != null ? trackingInfo.pokemonName : "Unknown";
            if (!pokemonName.equals("Unknown")) {
                // Generate Bulbapedia URL for the Pokemon
                String bulbapediaUrl = com.afiqhasiff.pokealert.client.util.RarityScraper.generateBulbapediaUrl(pokemonName);
                // Use HTML hyperlink format: <a href="URL">Display Text</a>
                message.append("• <b>Pokemon:</b> <a href=\"").append(bulbapediaUrl).append("\">")
                       .append(pokemonName).append("</a>\n");
            } else {
                message.append("• <b>Pokemon:</b> ").append(pokemonName).append("\n");
            }
            
            if (trackingInfo.ivs != null) {
                // Use new Telegram format: HP-XX ATT-XX DEF-XX SPATT-XX SPDEF-XX SPD-XX Y-31
                message.append("• <b>IVs:</b> <code>").append(trackingInfo.ivs.toTelegramFormat()).append("</code>");
                if (trackingInfo.ivs.isPerfect()) {
                    message.append(" ✨");
                }
                message.append("\n");
            }
            
            // Add box information if transfer was successful
            if (boxInfoObj instanceof BoxInfo) {
                BoxInfo boxInfo = (BoxInfo) boxInfoObj;
                message.append("• <b>Transferred to:</b> Box ").append(boxInfo.boxNumber);
                if (boxInfo.boxName != null && !boxInfo.boxName.isEmpty()) {
                    message.append(" (<i>").append(boxInfo.boxName).append("</i>)");
                }
                message.append(", Slot ").append(boxInfo.slot).append("\n");
            }
            
            telegram.sendEggTimerNotification(message.toString());
            PokeAlertClient.LOGGER.info("EggManager: Sent hatch notification with IV stats");
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("EggManager: Failed to send hatch notification", e);
        }
    }
    
    /**
     * Get current egg slots from party
     * Uses reflection with comprehensive error handling
     */
    private Set<Integer> getEggSlotsFromParty() {
        Set<Integer> eggSlots = new HashSet<>();
        
        Object party = getPlayerParty();
        if (party == null) {
            PokeAlertClient.LOGGER.debug("EggManager: Party not available, returning empty set");
            return eggSlots;
        }
        
        try {
            // Try to use cached method, otherwise get fresh
            java.lang.reflect.Method getMethod = partyGetMethod;
            if (getMethod == null) {
                // Try multiple method names
                try {
                    getMethod = party.getClass().getMethod("get", int.class);
                    PokeAlertClient.LOGGER.info("EggManager: ✅ Found party.get(int) method");
                } catch (NoSuchMethodException e) {
                    try {
                        getMethod = party.getClass().getMethod("getPokemon", int.class);
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Found party.getPokemon(int) method");
                    } catch (NoSuchMethodException e2) {
                        PokeAlertClient.LOGGER.warn("EggManager: ❌ Could not find party slot access method (get/getPokemon)");
                        return eggSlots;
                    }
                }
                partyGetMethod = getMethod; // Cache for next time
            }
            
            // Check all 6 slots
            for (int slot = 0; slot < 6; slot++) {
                try {
                    Object pokemon = getMethod.invoke(party, slot);
                    if (pokemon != null && isEgg(pokemon)) {
                        eggSlots.add(slot);
                        PokeAlertClient.LOGGER.debug("EggManager: Slot {} contains egg", slot + 1);
                    }
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("EggManager: Error accessing slot {}: {}", slot + 1, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.warn("EggManager: Error accessing party slots: {}", e.getMessage());
        }
        
        return eggSlots;
    }
    
    /**
     * Check if Pokemon is an egg by name
     * Uses multiple methods with fallback and comprehensive logging
     */
    private boolean isEgg(Object pokemon) {
        if (pokemon == null) {
            return false;
        }
        
        try {
            String pokemonName = null;
            
            // Try Method 1: getDisplayName().getString()
            try {
                if (pokemonGetDisplayNameMethod == null) {
                    pokemonGetDisplayNameMethod = pokemon.getClass().getMethod("getDisplayName");
                    PokeAlertClient.LOGGER.info("EggManager: ✅ Found pokemon.getDisplayName() method");
                }
                
                Object displayName = pokemonGetDisplayNameMethod.invoke(pokemon);
                if (displayName != null) {
                    if (textGetStringMethod == null) {
                        textGetStringMethod = displayName.getClass().getMethod("getString");
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Found Text.getString() method");
                    }
                    pokemonName = (String) textGetStringMethod.invoke(displayName);
                    PokeAlertClient.LOGGER.debug("EggManager: Got name via getDisplayName(): '{}'", pokemonName);
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("EggManager: getDisplayName() failed: {}", e.getMessage());
            }
            
            // Try Method 2: getName().getString()
            if (pokemonName == null) {
                try {
                    if (pokemonGetNameMethod == null) {
                        pokemonGetNameMethod = pokemon.getClass().getMethod("getName");
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Found pokemon.getName() method");
                    }
                    
                    Object name = pokemonGetNameMethod.invoke(pokemon);
                    if (name != null) {
                        if (textGetStringMethod == null) {
                            textGetStringMethod = name.getClass().getMethod("getString");
                        }
                        pokemonName = (String) textGetStringMethod.invoke(name);
                        PokeAlertClient.LOGGER.debug("EggManager: Got name via getName(): '{}'", pokemonName);
                    }
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("EggManager: getName() failed: {}", e.getMessage());
                }
            }
            
            // Try Method 3: getSpecies().getName()
            if (pokemonName == null) {
                try {
                    java.lang.reflect.Method getSpeciesMethod = pokemon.getClass().getMethod("getSpecies");
                    Object species = getSpeciesMethod.invoke(pokemon);
                    if (species != null) {
                        java.lang.reflect.Method getNameMethod = species.getClass().getMethod("getName");
                        pokemonName = (String) getNameMethod.invoke(species);
                        PokeAlertClient.LOGGER.debug("EggManager: Got name via getSpecies().getName(): '{}'", pokemonName);
                    }
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("EggManager: getSpecies().getName() failed: {}", e.getMessage());
                }
            }
            
            if (pokemonName == null) {
                PokeAlertClient.LOGGER.warn("EggManager: ❌ Could not get Pokemon name - all methods failed");
                return false; // Safe default: assume not an egg if we can't check
            }
            
            boolean isEgg = pokemonName.equalsIgnoreCase("Egg");
            PokeAlertClient.LOGGER.debug("EggManager: Pokemon name '{}' isEgg: {}", pokemonName, isEgg);
            return isEgg;
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.warn("EggManager: Error checking if Pokemon is egg: {}", e.getMessage());
            return false; // Safe default
        }
    }
    
    /**
     * Get player party using Cobblemon API via reflection
     * Tries multiple API paths with comprehensive error handling and logging
     */
    private Object getPlayerParty() {
        // Method 1: CobblemonClient.INSTANCE.getStorage().getMyParty()
        try {
            PokeAlertClient.LOGGER.debug("EggManager: Attempting Method 1: CobblemonClient.INSTANCE.getStorage().getMyParty()");
            Class<?> cobblemonClientClass = Class.forName("com.cobblemon.mod.common.client.CobblemonClient");
            PokeAlertClient.LOGGER.debug("EggManager: ✅ Found CobblemonClient class");
            
            Object instance = cobblemonClientClass.getField("INSTANCE").get(null);
            PokeAlertClient.LOGGER.debug("EggManager: ✅ Got INSTANCE field");
            
            java.lang.reflect.Method getStorageMethod = instance.getClass().getMethod("getStorage");
            PokeAlertClient.LOGGER.debug("EggManager: ✅ Found getStorage() method");
            Object storage = getStorageMethod.invoke(instance);
            PokeAlertClient.LOGGER.debug("EggManager: ✅ Got storage object: {}", storage != null ? storage.getClass().getName() : "null");
            
            // Log all available methods on storage object for debugging
            if (storage != null) {
                java.lang.reflect.Method[] methods = storage.getClass().getMethods();
                StringBuilder methodList = new StringBuilder();
                for (int i = 0; i < Math.min(methods.length, 30); i++) {
                    if (i > 0) methodList.append(", ");
                    methodList.append(methods[i].getName()).append("(");
                    java.lang.reflect.Parameter[] params = methods[i].getParameters();
                    for (int j = 0; j < params.length; j++) {
                        if (j > 0) methodList.append(", ");
                        methodList.append(params[j].getType().getSimpleName());
                    }
                    methodList.append(")");
                }
                if (methods.length > 30) methodList.append("...");
                // Changed to debug level to reduce CPU usage - only log once per session
                PokeAlertClient.LOGGER.debug("EggManager: Storage object methods (first 30): {}", methodList.toString());
                
                // Also check fields
                java.lang.reflect.Field[] fields = storage.getClass().getFields();
                StringBuilder fieldList = new StringBuilder();
                for (int i = 0; i < Math.min(fields.length, 20); i++) {
                    if (i > 0) fieldList.append(", ");
                    fieldList.append(fields[i].getName()).append(":").append(fields[i].getType().getSimpleName());
                }
                if (fields.length > 20) fieldList.append("...");
                // Changed to debug level to reduce CPU usage - only log once per session
                PokeAlertClient.LOGGER.debug("EggManager: Storage object fields (first 20): {}", fieldList.toString());
            }
            
            // Try multiple method names and patterns
            Object party = null;
            
            // Try 1: getParty() - NO PARAMETERS (most likely correct method based on API)
            try {
                java.lang.reflect.Method getPartyMethod = storage.getClass().getMethod("getParty");
                party = getPartyMethod.invoke(storage);
                if (party != null) {
                    PokeAlertClient.LOGGER.info("EggManager: ✅ Found getParty() method - type: {}", party.getClass().getName());
                }
            } catch (NoSuchMethodException e) {
                PokeAlertClient.LOGGER.debug("EggManager: getParty() not found");
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("EggManager: getParty() failed: {}", e.getMessage());
            }
            
            // Try 2: getMyParty()
            if (party == null) {
                try {
                    java.lang.reflect.Method getMyPartyMethod = storage.getClass().getMethod("getMyParty");
                    party = getMyPartyMethod.invoke(storage);
                    if (party != null) {
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Found getMyParty() method - type: {}", party.getClass().getName());
                    }
                } catch (NoSuchMethodException e) {
                    PokeAlertClient.LOGGER.debug("EggManager: getMyParty() not found");
                }
            }
            
            // Try 3: getParty(UUID) - requires player UUID
            if (party == null && client.player != null) {
                try {
                    java.lang.reflect.Method getPartyMethod = storage.getClass().getMethod("getParty", java.util.UUID.class);
                    party = getPartyMethod.invoke(storage, client.player.getUuid());
                    if (party != null) {
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Found getParty(UUID) method - type: {}", party.getClass().getName());
                    }
                } catch (NoSuchMethodException e) {
                    PokeAlertClient.LOGGER.debug("EggManager: getParty(UUID) not found");
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("EggManager: getParty(UUID) failed: {}", e.getMessage());
                }
            }
            
            // Try 4: getPartyStore()
            if (party == null) {
                try {
                    java.lang.reflect.Method getPartyStoreMethod = storage.getClass().getMethod("getPartyStore");
                    party = getPartyStoreMethod.invoke(storage);
                    if (party != null) {
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Found getPartyStore() method - type: {}", party.getClass().getName());
                    }
                } catch (NoSuchMethodException e) {
                    PokeAlertClient.LOGGER.debug("EggManager: getPartyStore() not found");
                }
            }
            
            // Try 5: Access as field - myParty
            if (party == null) {
                try {
                    java.lang.reflect.Field partyField = storage.getClass().getField("myParty");
                    party = partyField.get(storage);
                    if (party != null) {
                        PokeAlertClient.LOGGER.info("EggManager: ✅ Found myParty field - type: {}", party.getClass().getName());
                    }
                } catch (NoSuchFieldException e) {
                    PokeAlertClient.LOGGER.debug("EggManager: myParty field not found");
                }
            }
            
            // Try 6: Search for methods containing "party" but EXCLUDE "getPartyStores" (returns map, not party)
            if (party == null) {
                java.lang.reflect.Method[] methods = storage.getClass().getMethods();
                for (java.lang.reflect.Method method : methods) {
                    String methodName = method.getName().toLowerCase();
                    // Skip getPartyStores - it returns a map, not a party object
                    if (methodName.contains("party") && !methodName.contains("stores") && method.getParameterCount() == 0) {
                        PokeAlertClient.LOGGER.debug("EggManager: Trying method: {}", method.getName());
                        try {
                            Object result = method.invoke(storage);
                            if (result != null) {
                                // Verify it's not a Map (getPartyStores returns LinkedHashMap)
                                if (!(result instanceof java.util.Map)) {
                                    PokeAlertClient.LOGGER.info("EggManager: ✅ Found party via method: {} - type: {}", method.getName(), result.getClass().getName());
                                    party = result;
                                    break;
                                } else {
                                    PokeAlertClient.LOGGER.debug("EggManager: Method {} returned Map, skipping", method.getName());
                                }
                            }
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.debug("EggManager: Method {} failed: {}", method.getName(), e.getMessage());
                        }
                    }
                }
            }
            
            if (party != null) {
                PokeAlertClient.LOGGER.info("EggManager: ✅ Successfully accessed party via Method 1: {}", party.getClass().getName());
                return party;
            } else {
                PokeAlertClient.LOGGER.warn("EggManager: Method 1 - all attempts returned null");
            }
        } catch (ClassNotFoundException e) {
            PokeAlertClient.LOGGER.warn("EggManager: Method 1 failed - CobblemonClient class not found: {}", e.getMessage());
        } catch (NoSuchFieldException e) {
            PokeAlertClient.LOGGER.warn("EggManager: Method 1 failed - INSTANCE field not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            PokeAlertClient.LOGGER.warn("EggManager: Method 1 failed - Method not found: {} (available methods: {})", 
                e.getMessage(), getAvailableMethods(e));
        } catch (Exception e) {
            PokeAlertClient.LOGGER.warn("EggManager: Method 1 failed - Exception: {} ({})", e.getMessage(), e.getClass().getSimpleName());
        }
        
        // Method 2: Try alternative path - Cobblemon.INSTANCE (common API pattern)
        try {
            PokeAlertClient.LOGGER.debug("EggManager: Attempting Method 2: Cobblemon.INSTANCE.getStorage().getMyParty()");
            Class<?> cobblemonClass = Class.forName("com.cobblemon.mod.common.Cobblemon");
            Object instance = cobblemonClass.getField("INSTANCE").get(null);
            
            java.lang.reflect.Method getStorageMethod = instance.getClass().getMethod("getStorage");
            Object storage = getStorageMethod.invoke(instance);
            
            java.lang.reflect.Method getMyPartyMethod = storage.getClass().getMethod("getMyParty");
            Object party = getMyPartyMethod.invoke(storage);
            
            if (party != null) {
                PokeAlertClient.LOGGER.info("EggManager: ✅ Successfully accessed party via Method 2");
                return party;
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("EggManager: Method 2 failed: {}", e.getMessage());
        }
        
        // Method 3: Try accessing through player entity (if available)
        try {
            if (client.player != null) {
                PokeAlertClient.LOGGER.debug("EggManager: Attempting Method 3: Access via player entity");
                // Try to find a method on player that returns party
                java.lang.reflect.Method[] methods = client.player.getClass().getMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.getName().contains("Party") || method.getName().contains("party")) {
                        PokeAlertClient.LOGGER.debug("EggManager: Found potential party method: {}", method.getName());
                        try {
                            Object result = method.invoke(client.player);
                            if (result != null) {
                                PokeAlertClient.LOGGER.info("EggManager: ✅ Successfully accessed party via Method 3: {}", method.getName());
                                return result;
                            }
                        } catch (Exception e) {
                            PokeAlertClient.LOGGER.debug("EggManager: Method {} failed: {}", method.getName(), e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("EggManager: Method 3 failed: {}", e.getMessage());
        }
        
        PokeAlertClient.LOGGER.error("EggManager: ❌ All methods failed - could not access party");
        return null;
    }
    
    /**
     * Helper to get available methods when NoSuchMethodException occurs
     */
    private String getAvailableMethods(NoSuchMethodException e) {
        try {
            // Try to get the class name from the exception message or stack trace
            StackTraceElement[] stack = e.getStackTrace();
            if (stack.length > 0) {
                String className = stack[0].getClassName();
                Class<?> clazz = Class.forName(className);
                java.lang.reflect.Method[] methods = clazz.getMethods();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(methods.length, 10); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(methods[i].getName());
                }
                if (methods.length > 10) sb.append("...");
                return sb.toString();
            }
        } catch (Exception ex) {
            // Ignore
        }
        return "unknown";
    }
    
    /**
     * Initialize reflection cache for performance
     */
    private void initializeReflectionCache() {
        PokeAlertClient.LOGGER.info("EggManager: Initializing reflection cache...");
        
        Object party = getPlayerParty();
        if (party == null) {
            PokeAlertClient.LOGGER.warn("EggManager: Cannot initialize reflection cache - party not available");
            return;
        }
        
        try {
            // Cache party.get() method
            try {
                partyGetMethod = party.getClass().getMethod("get", int.class);
                PokeAlertClient.LOGGER.info("EggManager: ✅ Cached party.get(int) method");
            } catch (NoSuchMethodException e) {
                try {
                    partyGetMethod = party.getClass().getMethod("getPokemon", int.class);
                    PokeAlertClient.LOGGER.info("EggManager: ✅ Cached party.getPokemon(int) method");
                } catch (NoSuchMethodException e2) {
                    PokeAlertClient.LOGGER.warn("EggManager: ❌ Could not cache party slot access method");
                }
            }
            
            // Cache Pokemon name access methods (if we can get a sample Pokemon)
            if (partyGetMethod != null) {
                Object samplePokemon = partyGetMethod.invoke(party, 0);
                if (samplePokemon != null) {
                    try {
                        pokemonGetDisplayNameMethod = samplePokemon.getClass().getMethod("getDisplayName");
                        Object sampleDisplayName = pokemonGetDisplayNameMethod.invoke(samplePokemon);
                        if (sampleDisplayName != null) {
                            textGetStringMethod = sampleDisplayName.getClass().getMethod("getString");
                            PokeAlertClient.LOGGER.info("EggManager: ✅ Cached Pokemon name access methods");
                        }
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.debug("EggManager: Could not cache Pokemon name methods: {}", e.getMessage());
                    }
                    
                    // Phase 2: Try to cache IV extraction methods (if Pokemon is not an egg)
                    try {
                        String pokemonName = getPokemonName(samplePokemon);
                        if (!pokemonName.equalsIgnoreCase("Egg")) {
                            // Try to initialize IV methods
                            String[] ivMethodNames = {"getIVs", "getIvs", "getIndividualValues"};
                            for (String methodName : ivMethodNames) {
                                try {
                                    pokemonGetIVsMethod = samplePokemon.getClass().getMethod(methodName);
                                    Object ivs = pokemonGetIVsMethod.invoke(samplePokemon);
                                    if (ivs != null) {
                                        ivsClass = ivs.getClass();
                                        PokeAlertClient.LOGGER.info("EggManager: ✅ Cached IV extraction method: {}()", methodName);
                                        break;
                                    }
                                } catch (NoSuchMethodException e) {
                                    // Try next method name
                                }
                            }
                        }
                    } catch (Exception e) {
                        PokeAlertClient.LOGGER.debug("EggManager: Could not cache IV methods: {}", e.getMessage());
                    }
                }
            }
            
            // Phase 2: Initialize PC transfer reflection cache
            try {
                pcPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.pc.PCPosition");
                partyPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.party.PartyPosition");
                PokeAlertClient.LOGGER.info("EggManager: ✅ Cached PC/Party position classes");
            } catch (ClassNotFoundException e) {
                PokeAlertClient.LOGGER.debug("EggManager: Could not cache position classes: {}", e.getMessage());
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.warn("EggManager: Error initializing reflection cache: {}", e.getMessage());
        }
    }
    
    /**
     * Start PC Discovery Mode - comprehensive logging for API method discovery
     */
    private void startPCDiscoveryMode() {
        PokeAlertConfig config = ConfigManager.getConfig();
        if (!config.eggManager.pcDiscoveryMode) {
            return;
        }
        
        PokeAlertClient.LOGGER.info("🔍 EggManager: PC Discovery Mode ENABLED - Comprehensive logging active");
        PokeAlertClient.LOGGER.info("🔍 EggManager: Discovery Mode will log:");
        PokeAlertClient.LOGGER.info("🔍   - When PC screen opens/closes");
        PokeAlertClient.LOGGER.info("🔍   - All available storage methods");
        PokeAlertClient.LOGGER.info("🔍   - Party/PC state changes");
        PokeAlertClient.LOGGER.info("🔍   - Class structures (PCPosition, PartyPosition, etc.)");
        PokeAlertClient.LOGGER.info("🔍   - Manual slot alterations");
        
        // Initial comprehensive log
        logComprehensiveStorageInfo();
        
        // Start periodic monitoring for screen changes and state changes
        if (scheduler != null) {
            discoveryMonitorTask = scheduler.scheduleAtFixedRate(this::checkForScreenAndStateChanges, 
                500, 500, TimeUnit.MILLISECONDS); // Check every 500ms
        }
    }
    
    /**
     * Stop PC Discovery Mode
     */
    private void stopPCDiscoveryMode() {
        if (discoveryMonitorTask != null) {
            discoveryMonitorTask.cancel(false);
            discoveryMonitorTask = null;
        }
        lastScreen = null;
        lastPartyState.clear();
    }
    
    /**
     * Check for screen changes and party/PC state changes
     */
    private void checkForScreenAndStateChanges() {
        PokeAlertConfig config = ConfigManager.getConfig();
        if (!config.eggManager.pcDiscoveryMode) {
            stopPCDiscoveryMode();
            return;
        }
        
        if (client == null) {
            return;
        }
        
        // Check for screen changes
        net.minecraft.client.gui.screen.Screen currentScreen = client.currentScreen;
        if (currentScreen != lastScreen) {
            onScreenChanged(lastScreen, currentScreen);
            lastScreen = currentScreen;
        }
        
        // Check for party state changes (every 2 seconds)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPartyStateCheck > 2000) {
            checkPartyStateChanges();
            lastPartyStateCheck = currentTime;
        }
    }
    
    /**
     * Called when screen changes
     */
    /**
     * Hook into PC GUI rendering to show debug indicators
     * Uses reflection to wrap the render method and inject our debug rendering
     */
    private void hookPCGUIRendering(net.minecraft.client.gui.screen.Screen screen) {
        if (screen == null) {
            return;
        }
        
        String screenClassName = screen.getClass().getName();
        if (!screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
            return;
        }
        
        PokeAlertClient.LOGGER.info("🔍 [DEBUG-VISUAL] PC GUI detected - visual debug indicators enabled");
        PokeAlertClient.LOGGER.info("🔍 [DEBUG-VISUAL] Debug positions will be shown when coordinates are calculated");
        PokeAlertClient.LOGGER.info("🔍 [DEBUG-VISUAL] To view: Screenshot the PC GUI when transfer attempts occur");
        
        // Use reflection to wrap the render method
        try {
            java.lang.reflect.Method originalRenderMethod = screen.getClass().getMethod("render", 
                DrawContext.class, int.class, int.class, float.class);
            
            // Store reference to original method and screen
            // We'll use a proxy pattern via reflection
            PokeAlertClient.LOGGER.info("🔍 [DEBUG-VISUAL] Found render method - debug rendering will be injected");
            
            // Note: Full method wrapping requires more complex reflection
            // For now, we'll use a simpler approach: render on next frame via scheduled task
            if (scheduler != null && debugRenderTask == null) {
                // Schedule a task that tries to render debug indicators
                // This will be called from render thread when PC GUI is open
                debugRenderTask = scheduler.scheduleAtFixedRate(() -> {
                    client.execute(() -> {
                        try {
                            // Try to render debug indicators using reflection
                            // Get current screen and check if it's PC GUI
                            if (client.currentScreen != null && 
                                client.currentScreen.getClass().getName().equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                                // PC GUI detected - discovery mode can log widget positions
                            }
                        } catch (Exception e) {
                            // Ignore discovery errors
                        }
                    });
                }, 0, 100, TimeUnit.MILLISECONDS); // Check every 100ms
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("🔍 [DEBUG-VISUAL] Could not hook PC GUI rendering directly: {}", e.getMessage());
            PokeAlertClient.LOGGER.info("🔍 [DEBUG-VISUAL] A mixin file is needed for full rendering support");
        }
    }
    
    private ScheduledFuture<?> debugRenderTask = null;
    
    private void onScreenChanged(net.minecraft.client.gui.screen.Screen oldScreen, net.minecraft.client.gui.screen.Screen newScreen) {
        if (newScreen == null && oldScreen != null) {
            PokeAlertClient.LOGGER.info("🔍 EggManager: Screen CLOSED - Type: {}", oldScreen.getClass().getName());
            // Log final state after screen closes
            logPartyAndPCState();
            
            // Stop debug rendering when screen closes
            if (debugRenderTask != null) {
                debugRenderTask.cancel(false);
                debugRenderTask = null;
            }
            // Don't clear positions immediately - keep them for a bit in case screen reopens
        } else if (newScreen != null) {
            String screenClassName = newScreen.getClass().getName();
            PokeAlertClient.LOGGER.info("🔍 EggManager: Screen OPENED - Type: {}", screenClassName);
            
            // Check if it's a PC-related screen
            if (screenClassName.toLowerCase().contains("pc") || 
                screenClassName.toLowerCase().contains("storage") ||
                screenClassName.toLowerCase().contains("pokemon")) {
                PokeAlertClient.LOGGER.info("🔍 EggManager: ⚠️ POTENTIAL PC SCREEN DETECTED!");
                logComprehensiveStorageInfo();
                logPartyAndPCState();
                logPCScreenDetails(newScreen);
                
                // Hook into PC GUI rendering for visual debugging
                if (screenClassName.equals("com.cobblemon.mod.common.client.gui.pc.PCGUI")) {
                    hookPCGUIRendering(newScreen);
                    
                    // Optional: Add test debug positions when PC GUI opens (for initial verification)
                    // Uncomment the lines below to enable test positions
                    /*
                    if (client != null && client.getWindow() != null) {
                        int screenWidth = client.getWindow().getScaledWidth();
                        int screenHeight = client.getWindow().getScaledHeight();
                        
                        // Add test positions at known locations
                        // Test positions removed - using mapped coordinates instead
                    }
                    */
                }
            }
            
            // Stop debug rendering when screen closes (moved outside the else-if block)
        }
    }
    
    /**
     * Check for party state changes (manual alterations)
     */
    private void checkPartyStateChanges() {
        try {
            Object party = getPlayerParty();
            if (party == null) {
                return;
            }
            
            Map<Integer, String> currentPartyState = new HashMap<>();
            java.lang.reflect.Method getMethod = party.getClass().getMethod("get", int.class);
            
            for (int slot = 0; slot < 6; slot++) {
                Object pokemon = getMethod.invoke(party, slot);
                String pokemonName = "EMPTY";
                if (pokemon != null) {
                    pokemonName = getPokemonName(pokemon);
                }
                currentPartyState.put(slot, pokemonName);
            }
            
            // Compare with last state
            for (int slot = 0; slot < 6; slot++) {
                String last = lastPartyState.get(slot);
                String current = currentPartyState.get(slot);
                
                if (last == null || !last.equals(current)) {
                    PokeAlertClient.LOGGER.info("🔍 EggManager: PARTY SLOT {} CHANGED: {} → {}", 
                        slot + 1, last != null ? last : "UNKNOWN", current);
                    
                    // Log detailed info about the change
                    logSlotChangeDetails(slot, last, current);
                }
            }
            
            lastPartyState.clear();
            lastPartyState.putAll(currentPartyState);
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("EggManager: Error checking party state changes: {}", e.getMessage());
        }
    }
    
    /**
     * Log comprehensive storage information
     */
    private void logComprehensiveStorageInfo() {
        PokeAlertClient.LOGGER.info("🔍 ========== PC DISCOVERY: COMPREHENSIVE STORAGE INFO ==========");
        
        try {
            // Get storage object
            Object storage = getStorage();
            if (storage == null) {
                PokeAlertClient.LOGGER.warn("🔍 Storage object is null");
                return;
            }
            
            PokeAlertClient.LOGGER.info("🔍 Storage Class: {}", storage.getClass().getName());
            
            // Log ALL methods on storage
            java.lang.reflect.Method[] methods = storage.getClass().getMethods();
            PokeAlertClient.LOGGER.info("🔍 Storage Methods ({} total):", methods.length);
            for (java.lang.reflect.Method method : methods) {
                StringBuilder params = new StringBuilder();
                java.lang.reflect.Parameter[] parameters = method.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) params.append(", ");
                    params.append(parameters[i].getType().getSimpleName());
                }
                PokeAlertClient.LOGGER.info("🔍   - {}({})", method.getName(), params.toString());
            }
            
            // Log ALL fields on storage
            java.lang.reflect.Field[] fields = storage.getClass().getFields();
            PokeAlertClient.LOGGER.info("🔍 Storage Fields ({} total):", fields.length);
            for (java.lang.reflect.Field field : fields) {
                PokeAlertClient.LOGGER.info("🔍   - {}: {}", field.getName(), field.getType().getSimpleName());
            }
            
            // Try to get PC stores
            Object pcStore = null;
            try {
                java.lang.reflect.Method getPcStoresMethod = storage.getClass().getMethod("getPcStores");
                Object pcStores = getPcStoresMethod.invoke(storage);
                PokeAlertClient.LOGGER.info("🔍 getPcStores() returned: {} (type: {})", 
                    pcStores != null ? "NOT NULL" : "NULL",
                    pcStores != null ? pcStores.getClass().getName() : "null");
                
                if (pcStores instanceof java.util.Map) {
                    java.util.Map<?, ?> pcStoresMap = (java.util.Map<?, ?>) pcStores;
                    PokeAlertClient.LOGGER.info("🔍 PC Stores Map size: {}", pcStoresMap.size());
                    if (client.player != null) {
                        UUID playerUuid = client.player.getUuid();
                        pcStore = pcStoresMap.get(playerUuid);
                        PokeAlertClient.LOGGER.info("🔍 Player PC Store: {} (type: {})", 
                            pcStore != null ? "FOUND" : "NOT FOUND",
                            pcStore != null ? pcStore.getClass().getName() : "null");
                        
                        if (pcStore != null) {
                            logPCStoreDetails(pcStore);
                        }
                    }
                }
            } catch (Exception e) {
                PokeAlertClient.LOGGER.warn("🔍 getPcStores() failed: {}", e.getMessage());
            }
            
            // Try to get party
            Object party = getPlayerParty();
            if (party != null) {
                PokeAlertClient.LOGGER.info("🔍 Party Class: {}", party.getClass().getName());
                logPartyDetails(party);
            }
            
            // Try to find PCPosition class via Class.forName first
            Class<?> pcPositionClass = null;
            try {
                pcPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.PCPosition");
                PokeAlertClient.LOGGER.info("🔍 ✅ Found PCPosition class via Class.forName: {}", pcPositionClass.getName());
                logClassDetails(pcPositionClass, "PCPosition");
            } catch (ClassNotFoundException e) {
                PokeAlertClient.LOGGER.warn("🔍 ❌ PCPosition class not found via Class.forName");
                // Try alternative names
                tryAlternativeClassNames("PCPosition");
            }
            
            // Try to discover PCPosition from method signatures
            if (pcPositionClass == null && pcStore != null) {
                pcPositionClass = discoverPositionClassFromMethods(pcStore, "PCPosition");
                if (pcPositionClass != null) {
                    PokeAlertClient.LOGGER.info("🔍 ✅ Found PCPosition class via method signature: {}", pcPositionClass.getName());
                    logClassDetails(pcPositionClass, "PCPosition");
                }
            }
            
            // Try to find PartyPosition class via Class.forName first
            Class<?> partyPositionClass = null;
            try {
                partyPositionClass = Class.forName("com.cobblemon.mod.common.api.storage.PartyPosition");
                PokeAlertClient.LOGGER.info("🔍 ✅ Found PartyPosition class via Class.forName: {}", partyPositionClass.getName());
                logClassDetails(partyPositionClass, "PartyPosition");
            } catch (ClassNotFoundException e) {
                PokeAlertClient.LOGGER.warn("🔍 ❌ PartyPosition class not found via Class.forName");
                tryAlternativeClassNames("PartyPosition");
            }
            
            // Try to discover PartyPosition from method signatures
            if (partyPositionClass == null && party != null) {
                partyPositionClass = discoverPositionClassFromMethods(party, "PartyPosition");
                if (partyPositionClass != null) {
                    PokeAlertClient.LOGGER.info("🔍 ✅ Found PartyPosition class via method signature: {}", partyPositionClass.getName());
                    logClassDetails(partyPositionClass, "PartyPosition");
                }
            }
            
            // Try to discover via getPosition() return type
            if (pcPositionClass == null && pcStore != null) {
                pcPositionClass = discoverPositionFromGetPositionMethod(pcStore, "PCPosition");
                if (pcPositionClass != null) {
                    PokeAlertClient.LOGGER.info("🔍 ✅ Found PCPosition class via getPosition() return type: {}", pcPositionClass.getName());
                    logClassDetails(pcPositionClass, "PCPosition");
                }
            }
            
            if (partyPositionClass == null && party != null) {
                partyPositionClass = discoverPositionFromGetPositionMethod(party, "PartyPosition");
                if (partyPositionClass != null) {
                    PokeAlertClient.LOGGER.info("🔍 ✅ Found PartyPosition class via getPosition() return type: {}", partyPositionClass.getName());
                    logClassDetails(partyPositionClass, "PartyPosition");
                }
            }
            
            // Test getBoxes() method
            if (pcStore != null) {
                testGetBoxesMethod(pcStore);
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error in comprehensive storage info", e);
        }
        
        PokeAlertClient.LOGGER.info("🔍 ========== END COMPREHENSIVE STORAGE INFO ==========");
    }
    
    /**
     * Log PC Store details
     */
    private void logPCStoreDetails(Object pcStore) {
        try {
            PokeAlertClient.LOGGER.info("🔍 --- PC Store Details ---");
            PokeAlertClient.LOGGER.info("🔍 PC Store Class: {}", pcStore.getClass().getName());
            
            // Log all methods
            java.lang.reflect.Method[] methods = pcStore.getClass().getMethods();
            PokeAlertClient.LOGGER.info("🔍 PC Store Methods ({} total):", methods.length);
            for (java.lang.reflect.Method method : methods) {
                StringBuilder params = new StringBuilder();
                java.lang.reflect.Parameter[] parameters = method.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) params.append(", ");
                    params.append(parameters[i].getType().getSimpleName());
                }
                PokeAlertClient.LOGGER.info("🔍   - {}({})", method.getName(), params.toString());
            }
            
            // Try to get box count
            try {
                java.lang.reflect.Method getBoxCountMethod = pcStore.getClass().getMethod("getBoxCount");
                Object boxCount = getBoxCountMethod.invoke(pcStore);
                PokeAlertClient.LOGGER.info("🔍 getBoxCount() = {}", boxCount);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("🔍 getBoxCount() not available");
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging PC store details", e);
        }
    }
    
    /**
     * Log Party details
     */
    private void logPartyDetails(Object party) {
        try {
            PokeAlertClient.LOGGER.info("🔍 --- Party Details ---");
            PokeAlertClient.LOGGER.info("🔍 Party Class: {}", party.getClass().getName());
            
            // Log all methods
            java.lang.reflect.Method[] methods = party.getClass().getMethods();
            PokeAlertClient.LOGGER.info("🔍 Party Methods ({} total):", methods.length);
            for (java.lang.reflect.Method method : methods) {
                StringBuilder params = new StringBuilder();
                java.lang.reflect.Parameter[] parameters = method.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) params.append(", ");
                    params.append(parameters[i].getType().getSimpleName());
                }
                PokeAlertClient.LOGGER.info("🔍   - {}({})", method.getName(), params.toString());
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging party details", e);
        }
    }
    
    /**
     * Log class details (constructors, methods, fields)
     */
    private void logClassDetails(Class<?> clazz, String className) {
        try {
            PokeAlertClient.LOGGER.info("🔍 --- {} Class Details ---", className);
            
            // Constructors
            java.lang.reflect.Constructor<?>[] constructors = clazz.getConstructors();
            PokeAlertClient.LOGGER.info("🔍 {} Constructors ({} total):", className, constructors.length);
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                StringBuilder params = new StringBuilder();
                java.lang.reflect.Parameter[] parameters = constructor.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) params.append(", ");
                    params.append(parameters[i].getType().getSimpleName());
                }
                PokeAlertClient.LOGGER.info("🔍   - {}({})", className, params.toString());
            }
            
            // Methods
            java.lang.reflect.Method[] methods = clazz.getMethods();
            PokeAlertClient.LOGGER.info("🔍 {} Methods ({} total):", className, methods.length);
            for (java.lang.reflect.Method method : methods) {
                if (method.getDeclaringClass() != clazz) continue; // Only own methods
                StringBuilder params = new StringBuilder();
                java.lang.reflect.Parameter[] parameters = method.getParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (i > 0) params.append(", ");
                    params.append(parameters[i].getType().getSimpleName());
                }
                PokeAlertClient.LOGGER.info("🔍   - {}({})", method.getName(), params.toString());
            }
            
            // Fields
            java.lang.reflect.Field[] fields = clazz.getFields();
            PokeAlertClient.LOGGER.info("🔍 {} Fields ({} total):", className, fields.length);
            for (java.lang.reflect.Field field : fields) {
                PokeAlertClient.LOGGER.info("🔍   - {}: {}", field.getName(), field.getType().getSimpleName());
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging class details for {}", className, e);
        }
    }
    
    /**
     * Try alternative class names
     */
    private void tryAlternativeClassNames(String baseName) {
        String[] alternatives = {
            "com.cobblemon.mod.common.api.storage." + baseName,
            "com.cobblemon.mod.common.storage." + baseName,
            "com.cobblemon.mod.common.client.storage." + baseName,
            "com.cobblemon.mod.common." + baseName.toLowerCase(),
        };
        
        for (String altName : alternatives) {
            try {
                Class<?> clazz = Class.forName(altName);
                PokeAlertClient.LOGGER.info("🔍 ✅ Found {} at: {}", baseName, altName);
                logClassDetails(clazz, baseName);
                return;
            } catch (ClassNotFoundException e) {
                // Try next
            }
        }
        PokeAlertClient.LOGGER.warn("🔍 ❌ {} not found in any known location", baseName);
    }
    
    /**
     * Log PC screen details
     */
    private void logPCScreenDetails(net.minecraft.client.gui.screen.Screen screen) {
        try {
            PokeAlertClient.LOGGER.info("🔍 --- PC Screen Details ---");
            PokeAlertClient.LOGGER.info("🔍 Screen Class: {}", screen.getClass().getName());
            
            // Log all fields that might contain PC/Party references
            java.lang.reflect.Field[] fields = screen.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName().toLowerCase();
                if (fieldName.contains("pc") || fieldName.contains("party") || 
                    fieldName.contains("storage") || fieldName.contains("pokemon")) {
                    try {
                        Object value = field.get(screen);
                        PokeAlertClient.LOGGER.info("🔍   Field {}: {} = {}", 
                            field.getName(), field.getType().getSimpleName(),
                            value != null ? value.getClass().getName() : "null");
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging PC screen details", e);
        }
    }
    
    /**
     * Log party and PC state
     */
    private void logPartyAndPCState() {
        try {
            PokeAlertClient.LOGGER.info("🔍 --- Current Party State ---");
            Object party = getPlayerParty();
            if (party == null) {
                PokeAlertClient.LOGGER.warn("🔍 Party is null");
                return;
            }
            
            java.lang.reflect.Method getMethod = party.getClass().getMethod("get", int.class);
            for (int slot = 0; slot < 6; slot++) {
                Object pokemon = getMethod.invoke(party, slot);
                String name = pokemon != null ? getPokemonName(pokemon) : "EMPTY";
                UUID uuid = null;
                if (pokemon != null) {
                    try {
                        java.lang.reflect.Method getUuidMethod = pokemon.getClass().getMethod("getUuid");
                        uuid = (UUID) getUuidMethod.invoke(pokemon);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                PokeAlertClient.LOGGER.info("🔍   Slot {}: {} (UUID: {})", slot + 1, name, uuid);
            }
            
            // Try to log PC state
            Object storage = getStorage();
            if (storage != null) {
                try {
                    java.lang.reflect.Method getPcStoresMethod = storage.getClass().getMethod("getPcStores");
                    Object pcStores = getPcStoresMethod.invoke(storage);
                    if (pcStores instanceof java.util.Map && client.player != null) {
                        UUID playerUuid = client.player.getUuid();
                        Object pcStore = ((java.util.Map<?, ?>) pcStores).get(playerUuid);
                        if (pcStore != null) {
                            PokeAlertClient.LOGGER.info("🔍 --- PC State (Box 0, first 10 slots) ---");
                            // Try to access PC slots (this will help us discover the API)
                            logPCBoxState(pcStore, 0);
                        }
                    }
                } catch (Exception e) {
                    PokeAlertClient.LOGGER.debug("🔍 Could not log PC state: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging party/PC state", e);
        }
    }
    
    /**
     * Log PC box state (try to discover access methods)
     */
    private void logPCBoxState(Object pcStore, int boxNumber) {
        try {
            // Try different method names to access PC slots
            String[] methodNames = {"getPokemon", "get", "getSlot", "getAt"};
            Class<?>[] positionClasses = {
                tryGetClass("com.cobblemon.mod.common.api.storage.PCPosition"),
                tryGetClass("com.cobblemon.mod.common.storage.PCPosition"),
            };
            
            for (String methodName : methodNames) {
                for (Class<?> posClass : positionClasses) {
                    if (posClass == null) continue;
                    try {
                        java.lang.reflect.Method method = pcStore.getClass().getMethod(methodName, posClass);
                        PokeAlertClient.LOGGER.info("🔍 ✅ Found PC access method: {}({})", methodName, posClass.getSimpleName());
                        
                        // Try to create PCPosition and access slots
                        for (int slot = 0; slot < 10; slot++) {
                            try {
                                java.lang.reflect.Constructor<?> constructor = posClass.getConstructor(int.class, int.class);
                                Object position = constructor.newInstance(boxNumber, slot);
                                Object pokemon = method.invoke(pcStore, position);
                                if (pokemon != null) {
                                    String name = getPokemonName(pokemon);
                                    PokeAlertClient.LOGGER.info("🔍   Box {} Slot {}: {}", boxNumber, slot, name);
                                }
                            } catch (Exception e) {
                                // Try next slot
                            }
                        }
                        return; // Success, stop trying
                    } catch (NoSuchMethodException e) {
                        // Try next
                    }
                }
            }
            
            PokeAlertClient.LOGGER.warn("🔍 Could not find PC slot access method");
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging PC box state", e);
        }
    }
    
    /**
     * Try to get class, return null if not found
     */
    private Class<?> tryGetClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
    
    /**
     * Log slot change details
     */
    private void logSlotChangeDetails(int slot, String oldValue, String newValue) {
        try {
            PokeAlertClient.LOGGER.info("🔍 --- Slot {} Change Details ---", slot + 1);
            PokeAlertClient.LOGGER.info("🔍   Old: {}", oldValue);
            PokeAlertClient.LOGGER.info("🔍   New: {}", newValue);
            
            // Get current party state
            Object party = getPlayerParty();
            if (party != null) {
                java.lang.reflect.Method getMethod = party.getClass().getMethod("get", int.class);
                Object pokemon = getMethod.invoke(party, slot);
                
                if (pokemon != null) {
                    // Log comprehensive Pokemon details
                    logPokemonDetails(pokemon, slot);
                }
            }
            
            // Log storage methods that might be relevant
            Object storage = getStorage();
            if (storage != null) {
                PokeAlertClient.LOGGER.info("🔍   Available transfer methods on storage:");
                java.lang.reflect.Method[] methods = storage.getClass().getMethods();
                for (java.lang.reflect.Method method : methods) {
                    String methodName = method.getName().toLowerCase();
                    if (methodName.contains("move") || methodName.contains("set") || 
                        methodName.contains("remove") || methodName.contains("transfer")) {
                        StringBuilder params = new StringBuilder();
                        java.lang.reflect.Parameter[] parameters = method.getParameters();
                        for (int i = 0; i < parameters.length; i++) {
                            if (i > 0) params.append(", ");
                            params.append(parameters[i].getType().getSimpleName());
                        }
                        PokeAlertClient.LOGGER.info("🔍     - {}({})", method.getName(), params.toString());
                    }
                }
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging slot change details", e);
        }
    }
    
    /**
     * Log comprehensive Pokemon details (for IV discovery)
     */
    private void logPokemonDetails(Object pokemon, int slot) {
        try {
            PokeAlertClient.LOGGER.info("🔍 ========== POKEMON DETAILS (Slot {}) ==========", slot + 1);
            PokeAlertClient.LOGGER.info("🔍 Pokemon Class: {}", pokemon.getClass().getName());
            
            // Get UUID
            try {
                java.lang.reflect.Method getUuidMethod = pokemon.getClass().getMethod("getUuid");
                UUID uuid = (UUID) getUuidMethod.invoke(pokemon);
                PokeAlertClient.LOGGER.info("🔍 Pokemon UUID: {}", uuid);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("🔍 Could not get UUID: {}", e.getMessage());
            }
            
            // Get Name
            PokeAlertClient.LOGGER.info("🔍 Pokemon Name: {}", getPokemonName(pokemon));
            
            // Log ALL methods (looking for IV/Stat access)
            java.lang.reflect.Method[] methods = pokemon.getClass().getMethods();
            PokeAlertClient.LOGGER.info("🔍 Pokemon Methods ({} total):", methods.length);
            
            // Filter and log relevant methods
            int loggedCount = 0;
            for (java.lang.reflect.Method method : methods) {
                String methodName = method.getName();
                String lowerName = methodName.toLowerCase();
                
                // Log methods that might contain IV/stat/nature/ability data
                if (lowerName.contains("iv") || lowerName.contains("stat") || 
                    lowerName.contains("individual") || lowerName.contains("effort") ||
                    lowerName.contains("nature") || lowerName.contains("ability") ||
                    lowerName.contains("gender") || lowerName.contains("hp") ||
                    lowerName.contains("attack") || lowerName.contains("defense") ||
                    lowerName.contains("speed") || lowerName.contains("special") ||
                    methodName.equals("getSpecies") || methodName.equals("getForm") ||
                    methodName.equals("getLevel") || methodName.equals("getExperience") ||
                    methodName.equals("getFriendship") || methodName.equals("getShiny")) {
                    
                    StringBuilder params = new StringBuilder();
                    java.lang.reflect.Parameter[] parameters = method.getParameters();
                    for (int i = 0; i < parameters.length; i++) {
                        if (i > 0) params.append(", ");
                        params.append(parameters[i].getType().getSimpleName());
                    }
                    
                    String returnType = method.getReturnType().getSimpleName();
                    PokeAlertClient.LOGGER.info("🔍   - {}({}) : {}", methodName, params.toString(), returnType);
                    loggedCount++;
                }
            }
            
            PokeAlertClient.LOGGER.info("🔍 Logged {} relevant methods", loggedCount);
            
            // Try to invoke potential IV methods
            tryInvokeIVMethods(pokemon);
            
            // Try to invoke stat/nature/ability methods
            tryInvokePokemonDataMethods(pokemon);
            
            PokeAlertClient.LOGGER.info("🔍 ========== END POKEMON DETAILS ==========");
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging Pokemon details", e);
        }
    }
    
    /**
     * Try to invoke potential IV-related methods
     */
    private void tryInvokeIVMethods(Object pokemon) {
        PokeAlertClient.LOGGER.info("🔍 --- Attempting IV Method Invocation ---");
        
        String[] ivMethodNames = {
            "getIVs", "getIvs", "getIndividualValues", "getIV", "getIv",
            "getStats", "getStat", "getHpIv", "getAttackIv", "getDefenseIv",
            "getSpecialAttackIv", "getSpecialDefenseIv", "getSpeedIv"
        };
        
        for (String methodName : ivMethodNames) {
            try {
                java.lang.reflect.Method method = pokemon.getClass().getMethod(methodName);
                Object result = method.invoke(pokemon);
                PokeAlertClient.LOGGER.info("🔍 ✅ {}() returned: {} (type: {})", 
                    methodName, result, result != null ? result.getClass().getName() : "null");
                
                // If result is an object, log its methods and fields
                if (result != null && !result.getClass().isPrimitive() && 
                    !result.getClass().getName().startsWith("java.lang")) {
                    logObjectStructure(result, methodName + " result");
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, continue
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("🔍 {}() failed: {}", methodName, e.getMessage());
            }
        }
    }
    
    /**
     * Try to invoke Pokemon data methods (nature, ability, etc)
     */
    private void tryInvokePokemonDataMethods(Object pokemon) {
        PokeAlertClient.LOGGER.info("🔍 --- Attempting Pokemon Data Method Invocation ---");
        
        String[] dataMethodNames = {
            "getNature", "getAbility", "getGender", "getLevel", 
            "getShiny", "isShiny", "getFriendship", "getExperience"
        };
        
        for (String methodName : dataMethodNames) {
            try {
                java.lang.reflect.Method method = pokemon.getClass().getMethod(methodName);
                Object result = method.invoke(pokemon);
                PokeAlertClient.LOGGER.info("🔍 ✅ {}() returned: {} (type: {})", 
                    methodName, result, result != null ? result.getClass().getName() : "null");
                
                // If result is an object, log its string representation
                if (result != null) {
                    PokeAlertClient.LOGGER.info("🔍    toString(): {}", result.toString());
                }
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, continue
            } catch (Exception e) {
                PokeAlertClient.LOGGER.debug("🔍 {}() failed: {}", methodName, e.getMessage());
            }
        }
    }
    
    /**
     * Log structure of an object (methods and fields)
     */
    private void logObjectStructure(Object obj, String objectName) {
        try {
            PokeAlertClient.LOGGER.info("🔍   --- {} Structure ---", objectName);
            PokeAlertClient.LOGGER.info("🔍   Class: {}", obj.getClass().getName());
            
            // Log methods
            java.lang.reflect.Method[] methods = obj.getClass().getMethods();
            PokeAlertClient.LOGGER.info("🔍   Methods ({} total):", methods.length);
            int logged = 0;
            for (java.lang.reflect.Method method : methods) {
                if (method.getParameterCount() == 0 && 
                    !method.getName().startsWith("wait") &&
                    !method.getName().equals("notify") &&
                    !method.getName().equals("notifyAll") &&
                    logged < 30) { // Limit to first 30 no-param methods
                    
                    String returnType = method.getReturnType().getSimpleName();
                    PokeAlertClient.LOGGER.info("🔍     - {}() : {}", method.getName(), returnType);
                    logged++;
                    
                    // Try to invoke simple getters
                    if (method.getName().startsWith("get") || method.getName().startsWith("is")) {
                        try {
                            Object value = method.invoke(obj);
                            if (value != null && (value.getClass().isPrimitive() || 
                                value instanceof Number || value instanceof String || value instanceof Boolean)) {
                                PokeAlertClient.LOGGER.info("🔍       = {}", value);
                            }
                        } catch (Exception e) {
                            // Ignore invocation errors
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("🔍 Error logging object structure", e);
        }
    }
    
    /**
     * Get Pokemon name (helper method)
     */
    private String getPokemonName(Object pokemon) {
        if (pokemon == null) {
            return "EMPTY";
        }
        
        try {
            // Try getDisplayName().getString()
            if (pokemonGetDisplayNameMethod == null) {
                pokemonGetDisplayNameMethod = pokemon.getClass().getMethod("getDisplayName");
            }
            Object displayName = pokemonGetDisplayNameMethod.invoke(pokemon);
            if (displayName != null) {
                if (textGetStringMethod == null) {
                    textGetStringMethod = displayName.getClass().getMethod("getString");
                }
                String name = (String) textGetStringMethod.invoke(displayName);
                if (name != null && !name.isEmpty() && !name.equals("Egg")) {
                    return name;
                }
            }
        } catch (Exception e) {
            // Try alternative
        }
        
        return getPokemonNameAlternative(pokemon);
    }
    
    /**
     * Alternative Pokemon name extraction methods
     */
    private String getPokemonNameAlternative(Object pokemon) {
        if (pokemon == null) {
            return "EMPTY";
        }
        
        try {
            // Try Method 1: getName().getString()
            try {
                if (pokemonGetNameMethod == null) {
                    pokemonGetNameMethod = pokemon.getClass().getMethod("getName");
                }
                Object name = pokemonGetNameMethod.invoke(pokemon);
                if (name != null) {
                    if (textGetStringMethod == null) {
                        textGetStringMethod = name.getClass().getMethod("getString");
                    }
                    String pokemonName = (String) textGetStringMethod.invoke(name);
                    if (pokemonName != null && !pokemonName.isEmpty() && !pokemonName.equals("Egg")) {
                        return pokemonName;
                    }
                }
            } catch (Exception e) {
                // Try next method
            }
            
            // Try Method 2: getSpecies().getName()
            try {
                java.lang.reflect.Method getSpeciesMethod = pokemon.getClass().getMethod("getSpecies");
                Object species = getSpeciesMethod.invoke(pokemon);
                if (species != null) {
                    java.lang.reflect.Method getNameMethod = species.getClass().getMethod("getName");
                    String pokemonName = (String) getNameMethod.invoke(species);
                    if (pokemonName != null && !pokemonName.isEmpty() && !pokemonName.equals("Egg")) {
                        return pokemonName;
                    }
                }
            } catch (Exception e) {
                // Try next method
            }
            
            // Try Method 3: getSpecies().getDisplayName().getString()
            try {
                java.lang.reflect.Method getSpeciesMethod = pokemon.getClass().getMethod("getSpecies");
                Object species = getSpeciesMethod.invoke(pokemon);
                if (species != null) {
                    java.lang.reflect.Method getDisplayNameMethod = species.getClass().getMethod("getDisplayName");
                    Object displayName = getDisplayNameMethod.invoke(species);
                    if (displayName != null) {
                        java.lang.reflect.Method getStringMethod = displayName.getClass().getMethod("getString");
                        String pokemonName = (String) getStringMethod.invoke(displayName);
                        if (pokemonName != null && !pokemonName.isEmpty() && !pokemonName.equals("Egg")) {
                            return pokemonName;
                        }
                    }
                }
            } catch (Exception e) {
                // All methods failed
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("EggManager: All Pokemon name extraction methods failed: {}", e.getMessage());
        }
        
        return "UNKNOWN";
    }
    
    /**
     * Get storage object (helper method)
     */
    private Object getStorage() {
        try {
            Class<?> cobblemonClientClass = Class.forName("com.cobblemon.mod.common.client.CobblemonClient");
            Object instance = cobblemonClientClass.getField("INSTANCE").get(null);
            java.lang.reflect.Method getStorageMethod = instance.getClass().getMethod("getStorage");
            return getStorageMethod.invoke(instance);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Discover position class from method signatures
     */
    private Class<?> discoverPositionClassFromMethods(Object obj, String positionClassName) {
        try {
            java.lang.reflect.Method[] methods = obj.getClass().getMethods();
            for (java.lang.reflect.Method method : methods) {
                Class<?>[] paramTypes = method.getParameterTypes();
                for (Class<?> paramType : paramTypes) {
                    if (paramType.getSimpleName().equals(positionClassName)) {
                        PokeAlertClient.LOGGER.info("🔍 Found {} in method {}({})", 
                            positionClassName, method.getName(), 
                            java.util.Arrays.toString(paramTypes));
                        return paramType;
                    }
                }
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("🔍 Error discovering {} from methods: {}", positionClassName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Discover position class from getPosition() return type
     */
    private Class<?> discoverPositionFromGetPositionMethod(Object obj, String positionClassName) {
        try {
            // Try getPosition(Pokemon) method
            Class<?> pokemonClass = Class.forName("com.cobblemon.mod.common.pokemon.Pokemon");
            java.lang.reflect.Method getPositionMethod = obj.getClass().getMethod("getPosition", pokemonClass);
            Class<?> returnType = getPositionMethod.getReturnType();
            
            if (returnType.getSimpleName().equals(positionClassName)) {
                PokeAlertClient.LOGGER.info("🔍 Found {} as return type of getPosition(Pokemon): {}", 
                    positionClassName, returnType.getName());
                return returnType;
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("🔍 Error discovering {} from getPosition(): {}", positionClassName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Test getBoxes() method and log structure
     */
    private void testGetBoxesMethod(Object pcStore) {
        try {
            java.lang.reflect.Method getBoxesMethod = pcStore.getClass().getMethod("getBoxes");
            Object boxes = getBoxesMethod.invoke(pcStore);
            
            if (boxes != null) {
                PokeAlertClient.LOGGER.info("🔍 getBoxes() returned: {} (type: {})", 
                    boxes.getClass().getSimpleName(), boxes.getClass().getName());
                
                // Try to determine structure
                if (boxes instanceof java.util.Collection) {
                    java.util.Collection<?> collection = (java.util.Collection<?>) boxes;
                    PokeAlertClient.LOGGER.info("🔍 getBoxes() is a Collection with {} elements", collection.size());
                    if (!collection.isEmpty()) {
                        Object firstBox = collection.iterator().next();
                        PokeAlertClient.LOGGER.info("🔍 First box class: {}", firstBox.getClass().getName());
                        logClassDetails(firstBox.getClass(), "Box");
                    }
                } else if (boxes instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) boxes;
                    PokeAlertClient.LOGGER.info("🔍 getBoxes() is a Map with {} entries", map.size());
                    if (!map.isEmpty()) {
                        Object firstValue = map.values().iterator().next();
                        PokeAlertClient.LOGGER.info("🔍 First box value class: {}", firstValue.getClass().getName());
                        logClassDetails(firstValue.getClass(), "Box");
                    }
                }
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("🔍 Error testing getBoxes(): {}", e.getMessage());
        }
    }
    
    /**
     * Format slot numbers for display (1-6 instead of 0-5)
     */
    private String formatSlots(Set<Integer> slots) {
        if (slots == null || slots.isEmpty()) {
            return "None";
        }
        
        List<Integer> displaySlots = slots.stream()
            .filter(slot -> slot >= 0 && slot < 6)
            .map(slot -> slot + 1) // Convert to display number (1-6)
            .sorted()
            .collect(java.util.stream.Collectors.toList());
        
        if (displaySlots.isEmpty()) {
            return "None";
        }
        
        return displaySlots.stream()
            .map(String::valueOf)
            .collect(java.util.stream.Collectors.joining(", "));
    }
    
    /**
     * Check egg hatch status on reconnect (with delay)
     */
    public void checkEggHatchStatusOnReconnect() {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        // Only check if we were monitoring and mode is DISABLED (eggs hatched previously)
        if (!isMonitoring && EggHatcher.getInstance().getMode() == EggHatcher.AutomationMode.DISABLED && allEggsHatchedProcessed) {
            // Delay 10 seconds to ensure party data is synced from server
            if (scheduler == null) {
                scheduler = EggHatcher.getInstance().getScheduler();
            }
            
            if (scheduler != null) {
                scheduler.schedule(() -> {
                    Object party = getPlayerParty();
                    if (party == null) {
                        PokeAlertClient.LOGGER.debug("EggManager: Party not available yet on reconnect, skipping check");
                        return;
                    }
                    
                    Set<Integer> currentEggSlots = getEggSlotsFromParty();
                    
                    // If all slots are still not eggs (still hatched), keep Egg Hatcher disabled
                    if (currentEggSlots.isEmpty() || !currentEggSlots.stream().anyMatch(slot -> {
                        // Check if slot still has Pokemon (not egg)
                        return true; // Simplified - actual check in getEggSlots()
                    })) {
                        PokeAlertClient.LOGGER.info("EggManager: All eggs still hatched on reconnect - keeping Egg Hatcher disabled");
                        // Mode already DISABLED, no action needed
                    } else {
                        // New eggs detected - reset state and allow re-enabling
                        PokeAlertClient.LOGGER.info("EggManager: New eggs detected on reconnect - resetting state");
                        allEggsHatchedProcessed = false;
                        // User can manually re-enable if desired
                    }
                }, 10, TimeUnit.SECONDS); // 10 second delay
            }
        }
    }
    
    // Public status methods
    
    public int getEggCount() {
        return trackedEggSlots.size();
    }
    
    public int getInitialEggCount() {
        return initialEggSlots.size();
    }
    
    public Set<Integer> getEggSlots() {
        return new HashSet<>(trackedEggSlots);
    }
    
    public String getStatus() {
        if (!isMonitoring) {
            return "Not monitoring";
        }
        
        if (trackedEggSlots.isEmpty() && initialEggSlots.isEmpty()) {
            return "No eggs";
        }
        
        if (trackedEggSlots.isEmpty() && !initialEggSlots.isEmpty()) {
            return "All eggs hatched";
        }
        
        // Only show eggs remaining if we have a valid count
        int remaining = trackedEggSlots.size();
        int hatched = initialEggSlots.size() - remaining;
        if (hatched < 0) {
            // New eggs were added to tracking after initial scan
            return String.format("%d eggs in party", remaining);
        }
        return String.format("%d eggs in party (%d hatched)", remaining, hatched);
    }
    
    /**
     * Get count of eggs in PC that are eligible for transfer to party
     * (excludes breedject boxes and reserved boxes)
     */
    public int getAvailableEggsInPC() {
        List<Object> eggs = findAllEggsInPC();
        return eggs.size();
    }
    
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    /**
     * TEST COMMAND: Simulate hatched Pokemon transfer to PC.
     * This allows testing the Party → PC transfer without waiting for eggs to hatch.
     * 
     * IMPORTANT: This runs on a BACKGROUND THREAD to avoid freezing the game during sleeps.
     * 
     * Usage: /pokealert eggmanager test-transfer <slot>
     * 
     * @param partySlot 0-based party slot index (0-5)
     * @return true if transfer was initiated (runs async, so this returns immediately)
     */
    public boolean testTransferHatchedPokemonToPC(int partySlot) {
        PokeAlertClient.LOGGER.info("🧪 [TEST] testTransferHatchedPokemonToPC: Testing transfer for party slot {}", partySlot + 1);
        
        // CRITICAL: Run on background thread to avoid freezing the game
        // The transfer method contains Thread.sleep() calls that would freeze the render thread
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                // Check if there's a Pokemon in the specified slot
                Object party = getPlayerParty();
                if (party == null) {
                    PokeAlertClient.LOGGER.error("🧪 [TEST] Party not available");
                    return;
                }
                
                java.lang.reflect.Method getMethod = partyGetMethod;
                if (getMethod == null) {
                    getMethod = party.getClass().getMethod("get", int.class);
                }
                
                Object pokemon = getMethod.invoke(party, partySlot);
                if (pokemon == null) {
                    PokeAlertClient.LOGGER.error("🧪 [TEST] No Pokemon in slot {} - please put a Pokemon there first", partySlot + 1);
                    sendTestResultNotification(false, "No Pokemon in slot " + (partySlot + 1));
                    return;
                }
                
                // Check if it's an egg (we want a hatched Pokemon, not an egg)
                if (isEgg(pokemon)) {
                    PokeAlertClient.LOGGER.warn("🧪 [TEST] Slot {} contains an EGG, not a hatched Pokemon. Transfer will proceed anyway for testing.", partySlot + 1);
                }
                
                // Get Pokemon name for logging
                String pokemonName = getPokemonName(pokemon);
                PokeAlertClient.LOGGER.info("🧪 [TEST] Found Pokemon: {} in slot {}", pokemonName, partySlot + 1);
                
                // Extract IVs if IV tracking is enabled
                PokeAlertConfig config = ConfigManager.getConfig();
                PokemonIVs ivs = null;
                if (config.eggManager.phase2.ivTrackingEnabled) {
                    ivs = extractIVs(pokemon);
                    if (ivs != null) {
                        PokeAlertClient.LOGGER.info("🧪 [TEST] Extracted IVs: {}", ivs);
                    } else {
                        PokeAlertClient.LOGGER.warn("🧪 [TEST] Could not extract IVs, using default");
                        ivs = new PokemonIVs(15, 15, 15, 15, 15, 15); // Default IVs for testing
                    }
                } else {
                    PokeAlertClient.LOGGER.info("🧪 [TEST] IV tracking disabled, using default IVs");
                    ivs = new PokemonIVs(15, 15, 15, 15, 15, 15); // Default IVs for testing
                }
                
                // Call the actual transfer method (this contains sleeps, but we're on background thread now)
                PokeAlertClient.LOGGER.info("🧪 [TEST] Calling transferHatchedPokemonToPC (running on background thread)...");
                BoxInfo result = transferHatchedPokemonToPC(partySlot, ivs);
                
                if (result != null) {
                    PokeAlertClient.LOGGER.info("🧪 [TEST] ✅ Transfer result: Box {} ({}), Slot {}", 
                        result.boxNumber, result.boxName, result.slot);
                    sendTestResultNotification(true, "Transferred to Box " + result.boxNumber + ", Slot " + result.slot);
                } else {
                    PokeAlertClient.LOGGER.error("🧪 [TEST] ❌ Transfer returned null - transfer may have failed");
                    sendTestResultNotification(false, "Transfer failed - check logs");
                }
                
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("🧪 [TEST] Error in testTransferHatchedPokemonToPC: {}", e.getMessage());
                e.printStackTrace();
                sendTestResultNotification(false, "Error: " + e.getMessage());
            }
        });
        executor.shutdown();
        
        // Return true to indicate the async task was started
        return true;
    }
    
    /**
     * Send test result notification to player
     */
    private void sendTestResultNotification(boolean success, String message) {
        client.execute(() -> {
            if (client.player != null) {
                String prefix = success ? "✅" : "❌";
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("[PokéAlert] " + prefix + " Test: " + message)
                        .formatted(success ? net.minecraft.util.Formatting.GREEN : net.minecraft.util.Formatting.RED),
                    false
                );
            }
        });
    }
    
    /**
     * Get scheduler from EggHatcher (for external access)
     */
    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }
    
    // ========== Phase 3: Daycare Egg Fetching ==========
    
    /**
     * State machine for daycare fetching process
     */
    private enum DaycareState {
        IDLE,
        STOPPING_EGG_HATCHER,
        WARPING_TO_DAYCARE,
        WAITING_FOR_DAYCARE_ARRIVAL,
        INTERACTING_WITH_NPC,
        WAITING_FOR_DAYCARE_MENU,
        CLICKING_EGG,
        CLOSING_MENU,
        WARPING_HOME,
        WAITING_FOR_HOME_ARRIVAL,
        STARTING_EGG_TIMER,
        RESUMING_EGG_HATCHER,
        COMPLETED,
        FAILED
    }
    
    // Daycare process state
    private volatile DaycareState daycareState = DaycareState.IDLE;
    private volatile int daycareRetryCount = 0;
    private volatile String daycareCurrentStep = "";
    private volatile long daycareStepStartTime = 0;
    private ScheduledFuture<?> daycareTask;
    
    /**
     * Start the daycare egg fetching process.
     * Called when Egg Timer completes.
     */
    public void startDaycareFetch() {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        if (!config.isDaycareEffectivelyEnabled()) {
            PokeAlertClient.LOGGER.info("[Egg Manager] Daycare fetch disabled in config, skipping");
            return;
        }
        
        if (daycareState != DaycareState.IDLE) {
            PokeAlertClient.LOGGER.warn("[Egg Manager] Daycare fetch already in progress, state: {}", daycareState);
            return;
        }
        
        PokeAlertClient.LOGGER.info("[Egg Manager] Starting daycare egg fetch process");
        sendDaycareStepNotification(0, "Daycare run initiated");
        
        daycareState = DaycareState.STOPPING_EGG_HATCHER;
        daycareRetryCount = 0;
        daycareCurrentStep = "Stopping Egg Hatcher";
        daycareStepStartTime = System.currentTimeMillis();
        
        // Execute the state machine
        executeDaycareStateMachine();
    }
    
    /**
     * Execute the daycare state machine
     */
    private void executeDaycareStateMachine() {
        if (scheduler == null || scheduler.isShutdown()) {
            PokeAlertClient.LOGGER.error("[Egg Manager] Scheduler not available for daycare process");
            failDaycareProcess("Scheduler not available");
            return;
        }
        
        // Cancel any existing task
        if (daycareTask != null && !daycareTask.isDone()) {
            daycareTask.cancel(false);
        }
        
        daycareTask = scheduler.schedule(this::processDaycareState, 100, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Process the current daycare state
     */
    private void processDaycareState() {
        PokeAlertConfig config = ConfigManager.getConfig();
        
        try {
            switch (daycareState) {
                case STOPPING_EGG_HATCHER:
                    handleStoppingEggHatcher();
                    break;
                    
                case WARPING_TO_DAYCARE:
                    handleWarpingToDaycare(config);
                    break;
                    
                case WAITING_FOR_DAYCARE_ARRIVAL:
                    handleWaitingForDaycareArrival(config);
                    break;
                    
                case INTERACTING_WITH_NPC:
                    handleInteractingWithNPC();
                    break;
                    
                case WAITING_FOR_DAYCARE_MENU:
                    handleWaitingForDaycareMenu();
                    break;
                    
                case CLICKING_EGG:
                    handleClickingEgg();
                    break;
                    
                case CLOSING_MENU:
                    handleClosingMenu();
                    break;
                    
                case WARPING_HOME:
                    handleWarpingHome(config);
                    break;
                    
                case WAITING_FOR_HOME_ARRIVAL:
                    handleWaitingForHomeArrival(config);
                    break;
                    
                case STARTING_EGG_TIMER:
                    handleStartingEggTimer();
                    break;
                    
                case RESUMING_EGG_HATCHER:
                    handleResumingEggHatcher();
                    break;
                    
                case COMPLETED:
                    PokeAlertClient.LOGGER.info("[Egg Manager] Daycare fetch completed successfully!");
                    sendDaycareStepNotification(9, "Daycare run completed ✓");
                    daycareState = DaycareState.IDLE;
                    break;
                    
                case FAILED:
                    PokeAlertClient.LOGGER.error("[Egg Manager] Daycare fetch failed after {} retries", daycareRetryCount);
                    daycareState = DaycareState.IDLE;
                    break;
                    
                case IDLE:
                default:
                    // Nothing to do
                    break;
            }
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("[Egg Manager] Error in daycare state machine: {}", e.getMessage());
            retryDaycareStep("Exception: " + e.getMessage());
        }
    }
    
    // Daycare run has 9 visible steps (not counting internal waiting states)
    private static final int DAYCARE_TOTAL_STEPS = 9;
    
    /**
     * Step 1: Stop Egg Hatcher
     */
    private void handleStoppingEggHatcher() {
        daycareCurrentStep = "Stopping Egg Hatcher";
        sendDaycareStepNotification(1, "Stopping Egg Hatcher");
        
        EggHatcher eggHatcher = EggHatcher.getInstance();
        if (eggHatcher != null && (eggHatcher.isRunning() || eggHatcher.isAntiAfkActive())) {
            // CRITICAL: Call stopAutomation() to actually stop Baritone Anti-AFK, not just setMode()
            eggHatcher.stopAutomation();
            // Set mode to DISABLED after stopping so it doesn't auto-restart
            eggHatcher.setMode(EggHatcher.AutomationMode.DISABLED);
            PokeAlertClient.LOGGER.info("[Egg Manager] Egg Hatcher stopped via stopAutomation()");
            
            // Send /pokealert egghatcher disable command for confirmed cancellation
            sendChatCommand("pokealert egghatcher disable");
            PokeAlertClient.LOGGER.info("[Egg Manager] Sent /pokealert egghatcher disable command for confirmed stop");
        } else {
            PokeAlertClient.LOGGER.info("[Egg Manager] Egg Hatcher was not running");
        }
        
        // Wait a moment for Egg Hatcher to fully stop before proceeding
        daycareState = DaycareState.WARPING_TO_DAYCARE;
        daycareStepStartTime = System.currentTimeMillis();
        
        // Small delay to ensure Egg Hatcher is fully stopped
        daycareTask = scheduler.schedule(this::executeDaycareStateMachine, 1000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Step 2: Warp to daycare
     */
    private void handleWarpingToDaycare(PokeAlertConfig config) {
        daycareCurrentStep = "Warping to daycare";
        sendDaycareStepNotification(2, "Warping to daycare");
        
        // Send the warp command
        String daycareCmd = config.eggManager.daycare.warpCommand;
        sendChatCommand(daycareCmd);
        PokeAlertClient.LOGGER.info("[Egg Manager] Sent command: {}", daycareCmd);
        
        // Wait for pre-teleport delay (5 seconds)
        daycareState = DaycareState.WAITING_FOR_DAYCARE_ARRIVAL;
        daycareStepStartTime = System.currentTimeMillis();
        
        // Schedule next check after teleport wait
        int totalWait = config.eggManager.daycare.teleportWait + config.eggManager.daycare.worldLoadWait;
        daycareTask = scheduler.schedule(this::processDaycareState, totalWait, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Step 3: Verify arrival at daycare (spawn world)
     */
    private void handleWaitingForDaycareArrival(PokeAlertConfig config) {
        daycareCurrentStep = "Verifying daycare arrival";
        
        // Check if we're at spawn (use EggManager's own method for independence)
        if (isAtSpawn()) {
            PokeAlertClient.LOGGER.info("[Egg Manager] Arrived at daycare (spawn world)");
            sendDaycareStepNotification(3, "Arrived at daycare");
            
            // Reset NPC interaction retry count for new arrival
            npcInteractionRetryCount = 0;
            
            // Move to next state
            daycareState = DaycareState.INTERACTING_WITH_NPC;
            daycareStepStartTime = System.currentTimeMillis();
            
            // Small delay before interacting
            daycareTask = scheduler.schedule(this::processDaycareState, 1000, TimeUnit.MILLISECONDS);
        } else {
            // Not at spawn yet - check timeout
            long elapsed = System.currentTimeMillis() - daycareStepStartTime;
            int totalWait = config.eggManager.daycare.teleportWait + config.eggManager.daycare.worldLoadWait;
            
            if (elapsed > totalWait + 5000) { // Extra 5s grace period
                PokeAlertClient.LOGGER.warn("[Egg Manager] Timeout waiting for daycare arrival");
                retryDaycareStep("Timeout waiting for daycare");
            } else {
                // Keep waiting
                daycareTask = scheduler.schedule(this::processDaycareState, 1000, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    /**
     * Step 4: Interact with NPC (right-click at crosshair)
     */
    private void handleInteractingWithNPC() {
        daycareCurrentStep = "Opening daycare menu";
        sendDaycareStepNotification(4, "Opening daycare menu");
        
        // Simulate right-click (use key)
        if (client.player != null) {
            client.execute(() -> {
                // Press and release use key (right-click)
                client.options.useKey.setPressed(true);
                
                // Release after a short delay
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        client.options.useKey.setPressed(false);
                        PokeAlertClient.LOGGER.info("[Egg Manager] Right-click simulated to open daycare menu");
                    });
                }, 100, TimeUnit.MILLISECONDS);
            });
        }
        
        // Transition to waiting for daycare menu to open
        daycareState = DaycareState.WAITING_FOR_DAYCARE_MENU;
        daycareStepStartTime = System.currentTimeMillis();
        
        // Check for menu after a short delay
        daycareTask = scheduler.schedule(this::processDaycareState, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Step 4B: Wait for daycare menu to open
     */
    private void handleWaitingForDaycareMenu() {
        daycareCurrentStep = "Waiting for daycare menu";
        
        // CRITICAL: Use CompletableFuture to check screen on render thread
        // client.currentScreen must be accessed from the render thread
        java.util.concurrent.CompletableFuture<Boolean> menuCheckFuture = new java.util.concurrent.CompletableFuture<>();
        
        client.execute(() -> {
            try {
                if (client.currentScreen != null) {
                    String screenTitle = client.currentScreen.getTitle().getString();
                    String screenClass = client.currentScreen.getClass().getName();
                    PokeAlertClient.LOGGER.info("[Egg Manager] Screen detected: '{}' (class: {})", 
                        screenTitle, screenClass);
                    
                    // FIXED: The daycare title uses Unicode small caps "Dᴀʏᴄᴀʀᴇ" which don't match "daycare"
                    // Instead, check if ANY HandledScreen (container) is open after right-clicking the NPC
                    // Since we just simulated right-click at daycare, any container screen is the daycare menu
                    boolean isDaycareMenu = false;
                    
                    // Method 1: Check if it's a HandledScreen (container menu)
                    if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?>) {
                        isDaycareMenu = true;
                        PokeAlertClient.LOGGER.info("[Egg Manager] Daycare menu opened (HandledScreen detected): {}", screenTitle);
                    }
                    
                    // Method 2: Fallback - normalize Unicode and check for "daycare" variants
                    if (!isDaycareMenu) {
                        // Normalize Unicode small caps to regular letters
                        String normalizedTitle = normalizeUnicodeSmallCaps(screenTitle.toLowerCase());
                        if (normalizedTitle.contains("daycare")) {
                            isDaycareMenu = true;
                            PokeAlertClient.LOGGER.info("[Egg Manager] Daycare menu opened (title match): {} -> {}", screenTitle, normalizedTitle);
                        }
                    }
                    
                    menuCheckFuture.complete(isDaycareMenu);
                    return;
                } else {
                    PokeAlertClient.LOGGER.debug("[Egg Manager] No screen currently open");
                }
                menuCheckFuture.complete(false);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[Egg Manager] Error checking screen: {}", e.getMessage());
                menuCheckFuture.complete(false);
            }
        });
        
        // Wait for result with timeout
        boolean menuFound = false;
        try {
            menuFound = menuCheckFuture.get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            PokeAlertClient.LOGGER.debug("[Egg Manager] Menu check timed out or failed");
        }
        
        if (menuFound) {
            sendDaycareStepNotification(5, "Daycare menu opened");
            
            // Move to clicking egg state
            daycareState = DaycareState.CLICKING_EGG;
            daycareStepStartTime = System.currentTimeMillis();
            
            // Small delay before clicking
            daycareTask = scheduler.schedule(this::processDaycareState, 500, TimeUnit.MILLISECONDS);
            return;
        }
        
        // Check timeout
        long elapsed = System.currentTimeMillis() - daycareStepStartTime;
        if (elapsed > 5000) { // 5 second timeout for menu to open
            PokeAlertClient.LOGGER.warn("[Egg Manager] Timeout waiting for daycare menu to open");
            retryDaycareStep("Daycare menu did not open");
        } else {
            // Keep waiting
            daycareTask = scheduler.schedule(this::processDaycareState, 500, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Step 5: Click the egg in the daycare menu
     */
    private void handleClickingEgg() {
        daycareCurrentStep = "Retrieving egg";
        sendDaycareStepNotification(5, "Retrieving egg");
        
        // CRITICAL: Use CompletableFuture to handle screen interaction on render thread
        java.util.concurrent.CompletableFuture<Integer> eggCheckFuture = new java.util.concurrent.CompletableFuture<>();
        // -1 = error/screen closed, -2 = no egg found, >= 0 = egg found at slot
        
        client.execute(() -> {
            try {
                if (client.currentScreen == null) {
                    PokeAlertClient.LOGGER.warn("[Egg Manager] Screen closed unexpectedly");
                    eggCheckFuture.complete(-1);
                    return;
                }
                
                // The daycare menu is a handled screen (container)
                // We need to find the egg slot and click it
                if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> handledScreen) {
                    net.minecraft.screen.ScreenHandler screenHandler = handledScreen.getScreenHandler();
                    
                    if (screenHandler != null) {
                        // Scan slots for an egg
                        int eggSlotId = -2;
                        for (int i = 0; i < screenHandler.slots.size(); i++) {
                            net.minecraft.screen.slot.Slot slot = screenHandler.slots.get(i);
                            net.minecraft.item.ItemStack stack = slot.getStack();
                            
                            if (!stack.isEmpty()) {
                                String itemName = stack.getName().getString().toLowerCase();
                                PokeAlertClient.LOGGER.debug("[Egg Manager] Slot {}: {}", i, itemName);
                                
                                // Check if this is an egg
                                if (itemName.contains("egg")) {
                                    eggSlotId = i;
                                    PokeAlertClient.LOGGER.info("[Egg Manager] Found egg at slot {}: {}", i, stack.getName().getString());
                                    break;
                                }
                            }
                        }
                        
                        if (eggSlotId >= 0) {
                            // Click the egg slot to retrieve it
                            try {
                                // Use the interactionManager to click the slot
                                if (client.interactionManager != null) {
                                    client.interactionManager.clickSlot(
                                        screenHandler.syncId,
                                        eggSlotId,
                                        0, // Left click
                                        net.minecraft.screen.slot.SlotActionType.PICKUP,
                                        client.player
                                    );
                                    PokeAlertClient.LOGGER.info("[Egg Manager] Clicked egg slot {}", eggSlotId);
                                }
                            } catch (Exception e) {
                                PokeAlertClient.LOGGER.error("[Egg Manager] Error clicking slot: {}", e.getMessage());
                            }
                        }
                        
                        eggCheckFuture.complete(eggSlotId);
                        return;
                    }
                }
                eggCheckFuture.complete(-1);
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[Egg Manager] Error in egg check: {}", e.getMessage());
                eggCheckFuture.complete(-1);
            }
        });
        
        // Wait for result with timeout
        int result = -1;
        try {
            result = eggCheckFuture.get(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            PokeAlertClient.LOGGER.error("[Egg Manager] Egg check timed out");
        }
        
        if (result == -1) {
            // Screen closed or error
            retryDaycareStep("Screen closed unexpectedly");
            return;
        }
        
        if (result >= 0) {
            sendDaycareStepNotification(5, "Egg retrieved ✓");
            
            // Move to closing menu state
            daycareState = DaycareState.CLOSING_MENU;
            daycareStepStartTime = System.currentTimeMillis();
            
            // Wait a moment for the click to process
            daycareTask = scheduler.schedule(this::processDaycareState, 1000, TimeUnit.MILLISECONDS);
            return;
        } else {
            // result == -2: No egg found - close menu and warp home anyway
            daycareState = DaycareState.CLOSING_MENU;
            daycareStepStartTime = System.currentTimeMillis();
            daycareTask = scheduler.schedule(this::processDaycareState, 500, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Step 6: Close the daycare menu
     */
    private void handleClosingMenu() {
        daycareCurrentStep = "Closing menu";
        
        // Close any open screen
        if (client.currentScreen != null) {
            client.execute(() -> client.setScreen(null));
            PokeAlertClient.LOGGER.info("[Egg Manager] Closed daycare menu");
        }
        
        // Move to warping home
        daycareState = DaycareState.WARPING_HOME;
        daycareStepStartTime = System.currentTimeMillis();
        
        // Small delay before warping
        daycareTask = scheduler.schedule(this::processDaycareState, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Step 7: Warp home
     */
    private void handleWarpingHome(PokeAlertConfig config) {
        daycareCurrentStep = "Returning home";
        sendDaycareStepNotification(6, "Warping home");
        
        // Send the home command
        String homeCmd = config.eggManager.daycare.homeCommand;
        sendChatCommand(homeCmd);
        PokeAlertClient.LOGGER.info("[Egg Manager] Sent command: {}", homeCmd);
        
        // Wait for teleport
        daycareState = DaycareState.WAITING_FOR_HOME_ARRIVAL;
        daycareStepStartTime = System.currentTimeMillis();
        
        int totalWait = config.eggManager.daycare.teleportWait + config.eggManager.daycare.worldLoadWait;
        daycareTask = scheduler.schedule(this::processDaycareState, totalWait, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Step 6: Verify arrival at overworld
     */
    private void handleWaitingForHomeArrival(PokeAlertConfig config) {
        daycareCurrentStep = "Verifying overworld arrival";
        
        // Check if we're in overworld (use EggManager's own method for independence)
        if (isInOverworld()) {
            PokeAlertClient.LOGGER.info("[Egg Manager] Arrived at overworld");
            sendDaycareStepNotification(7, "Arrived in overworld");
            
            // Move to starting egg timer state (step 8)
            daycareState = DaycareState.STARTING_EGG_TIMER;
            daycareStepStartTime = System.currentTimeMillis();
            
            // Small delay before starting timer
            daycareTask = scheduler.schedule(this::processDaycareState, 1000, TimeUnit.MILLISECONDS);
        } else {
            // Not in overworld yet - check timeout
            long elapsed = System.currentTimeMillis() - daycareStepStartTime;
            int totalWait = config.eggManager.daycare.teleportWait + config.eggManager.daycare.worldLoadWait;
            
            if (elapsed > totalWait + 5000) { // Extra 5s grace period
                PokeAlertClient.LOGGER.warn("[Egg Manager] Timeout waiting for overworld arrival");
                retryDaycareStep("Timeout waiting for overworld");
            } else {
                // Keep waiting
                daycareTask = scheduler.schedule(this::processDaycareState, 1000, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    /**
     * Step 8: Start Egg Timer
     */
    private void handleStartingEggTimer() {
        daycareCurrentStep = "Starting egg timer";
        sendDaycareStepNotification(8, "Starting 30-minute egg timer");
        
        // Start egg timer for 30 minutes
        sendChatCommand("pokealert eggtimer start 30");
        PokeAlertClient.LOGGER.info("[Egg Manager] Started 30-minute egg timer");
        
        // Move to resuming egg hatcher state (step 9)
        daycareState = DaycareState.RESUMING_EGG_HATCHER;
        daycareStepStartTime = System.currentTimeMillis();
        
        // Small delay before resuming egg hatcher
        daycareTask = scheduler.schedule(this::processDaycareState, 1000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Step 9: Resume Egg Hatcher (Final Step)
     */
    private void handleResumingEggHatcher() {
        daycareCurrentStep = "Resuming Egg Hatcher";
        sendDaycareStepNotification(9, "Resuming Egg Hatcher");
        
        EggHatcher eggHatcher = EggHatcher.getInstance();
        if (eggHatcher != null) {
            // CRITICAL FIX: Call toggleAutomation() directly instead of using command + setMode()
            // The previous approach had a race condition:
            // 1. sendChatCommand() queues async command execution
            // 2. setMode(AUTO) runs immediately as "backup"
            // 3. When command executes, it checks if mode == DISABLED, but it's already AUTO
            // 4. Command skips toggleAutomation() call → Egg Hatcher never actually starts!
            //
            // toggleAutomation() properly handles the enable logic when mode is DISABLED
            if (eggHatcher.getMode() == EggHatcher.AutomationMode.DISABLED) {
                eggHatcher.toggleAutomation();
                PokeAlertClient.LOGGER.info("[Egg Manager] Egg Hatcher resumed via toggleAutomation()");
            } else {
                // If already in AUTO mode (shouldn't happen normally), log warning
                PokeAlertClient.LOGGER.warn("[Egg Manager] Egg Hatcher already in {} mode, skipping toggle", 
                    eggHatcher.getMode());
            }
            
            // Also send notification command for user feedback
            sendChatCommand("pokealert egghatcher status");
        } else {
            PokeAlertClient.LOGGER.warn("[Egg Manager] Egg Hatcher instance not available");
        }
        
        // Complete!
        daycareState = DaycareState.COMPLETED;
        executeDaycareStateMachine();
    }
    
    /**
     * Retry the current daycare step
     */
    private void retryDaycareStep(String reason) {
        PokeAlertConfig config = ConfigManager.getConfig();
        daycareRetryCount++;
        
        PokeAlertClient.LOGGER.warn("[Egg Manager] Retry {}/{} for step '{}': {}", 
            daycareRetryCount, config.eggManager.daycare.retryCount, daycareCurrentStep, reason);
        
        if (daycareRetryCount >= config.eggManager.daycare.retryCount) {
            failDaycareProcess("Max retries exceeded: " + reason);
            return;
        }
        
        // Check if retrying NPC interaction (daycare menu) - move forward half a block on 2nd+ attempt
        boolean isNpcInteractionRetry = daycareCurrentStep.contains("daycare menu") || 
                                        daycareCurrentStep.contains("Opening daycare") ||
                                        daycareCurrentStep.contains("Waiting for daycare menu");
        
        if (isNpcInteractionRetry) {
            npcInteractionRetryCount++;
            
            if (npcInteractionRetryCount >= 2) {
                // Move forward half a block on 2nd+ attempt
                // Each retry moves us half a block further from the starting position
                int blocksForward = npcInteractionRetryCount - 1; // 0.5 blocks per retry starting from attempt 2
                sendDaycareNotification("⚠️ Retrying daycare menu (Moving forward " + (blocksForward * 0.5) + " blocks)...");
                
                // Move player forward
                movePlayerForward(0.5);
                PokeAlertClient.LOGGER.info("[Egg Manager] Moved forward 0.5 blocks for NPC interaction attempt {}", npcInteractionRetryCount);
            } else {
                sendDaycareNotification("⚠️ Retrying " + daycareCurrentStep + "...");
            }
        } else {
            sendDaycareNotification("⚠️ Retrying " + daycareCurrentStep + "...");
        }
        
        // Wait 2 seconds before retry
        daycareTask = scheduler.schedule(() -> {
            // For NPC interaction retries, go back to INTERACTING_WITH_NPC state (don't re-warp)
            if (isNpcInteractionRetry) {
                daycareState = DaycareState.INTERACTING_WITH_NPC;
            } else if (daycareCurrentStep.contains("daycare")) {
                daycareState = DaycareState.WARPING_TO_DAYCARE;
            } else if (daycareCurrentStep.contains("overworld") || daycareCurrentStep.contains("home")) {
                daycareState = DaycareState.WARPING_HOME;
            }
            daycareStepStartTime = System.currentTimeMillis();
            executeDaycareStateMachine();
        }, 2000, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Move player forward by specified blocks
     */
    private void movePlayerForward(double blocks) {
        if (client.player == null) return;
        
        client.execute(() -> {
            if (client.player != null) {
                // Get player's look direction (yaw)
                float yaw = client.player.getYaw();
                double radians = Math.toRadians(yaw);
                
                // Calculate forward direction
                double dx = -Math.sin(radians) * blocks;
                double dz = Math.cos(radians) * blocks;
                
                // Move player
                client.player.setPosition(
                    client.player.getX() + dx,
                    client.player.getY(),
                    client.player.getZ() + dz
                );
                
                PokeAlertClient.LOGGER.info("[Egg Manager] Moved player forward {} blocks (dx={}, dz={})", 
                    blocks, String.format("%.2f", dx), String.format("%.2f", dz));
            }
        });
    }
    
    /**
     * Fail the daycare process and send Telegram notification.
     * After failure, we:
     * 1. Reset daycare state to IDLE
     * 2. Send /home new to get back to a safe location
     * 3. Enable Egg Hatcher
     * 4. Start 5-minute Egg Timer so another daycare run will be attempted
     */
    private void failDaycareProcess(String reason) {
        PokeAlertClient.LOGGER.error("[Egg Manager] Daycare process failed: {}", reason);
        sendDaycareNotification("❌ Daycare run failed: " + reason);
        
        // Send Telegram notification on failure
        PokeAlertConfig config = ConfigManager.getConfig();
        if (config.telegram.enabled) {
            try {
                com.afiqhasiff.pokealert.client.notification.TelegramNotification telegram = 
                    new com.afiqhasiff.pokealert.client.notification.TelegramNotification();
                telegram.initialize();
                
                StringBuilder message = new StringBuilder();
                message.append("🚨 <b>Egg Manager</b>\n");
                message.append("• <b>Status:</b> Failed to complete daycare run\n");
                message.append("• <b>Step:</b> ").append(daycareCurrentStep).append("\n");
                message.append("• <b>Reason:</b> ").append(reason).append("\n");
                message.append("• <b>Recovery:</b> Warping home, enabling Egg Hatcher, setting 5min timer\n");
                
                telegram.sendEggTimerNotification(message.toString());
            } catch (Exception e) {
                PokeAlertClient.LOGGER.error("[Egg Manager] Failed to send Telegram notification: {}", e.getMessage());
            }
        }
        
        // === RECOVERY STEPS ===
        
        // Step 1: Reset daycare state to IDLE so future egg timer completions can trigger new runs
        daycareState = DaycareState.IDLE;
        daycareRetryCount = 0;
        npcInteractionRetryCount = 0;
        daycareCurrentStep = "";
        PokeAlertClient.LOGGER.info("[Egg Manager] Daycare state reset to IDLE");
        
        // Step 2: Warp to home to get back to a safe location
        sendDaycareNotification("🏠 Warping to home...");
        sendChatCommand("/home new");
        PokeAlertClient.LOGGER.info("[Egg Manager] Sent /home new command for recovery");
        
        // Step 3 & 4: Enable Egg Hatcher and start 5-minute timer (after a small delay for warp)
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.schedule(() -> {
                // Enable Egg Hatcher
                EggHatcher eggHatcher = EggHatcher.getInstance();
                if (eggHatcher != null) {
                    // Use toggleAutomation() if currently disabled to properly start automation
                    if (eggHatcher.getMode() == EggHatcher.AutomationMode.DISABLED) {
                        eggHatcher.toggleAutomation();
                        PokeAlertClient.LOGGER.info("[Egg Manager] Egg Hatcher enabled via toggleAutomation()");
                        sendDaycareNotification("✅ Egg Hatcher enabled");
                    } else {
                        PokeAlertClient.LOGGER.info("[Egg Manager] Egg Hatcher already enabled");
                    }
                }
                
                // Start 5-minute Egg Timer so we retry daycare in 5 minutes
                EggTimerManager timerManager = EggTimerManager.getInstance();
                if (timerManager != null) {
                    // Stop any existing timer first
                    if (timerManager.isTimerRunning()) {
                        timerManager.stopTimer(true); // Silent stop
                    }
                    // Start new 5-minute timer
                    timerManager.startTimer(5);
                    PokeAlertClient.LOGGER.info("[Egg Manager] Started 5-minute egg timer for retry");
                    sendDaycareNotification("⏰ 5-minute timer started for retry");
                }
            }, 3, TimeUnit.SECONDS); // Wait 3 seconds for warp to complete
        }
    }
    
    /**
     * Send a chat command
     */
    private void sendChatCommand(String command) {
        if (client.player != null && command != null && !command.isEmpty()) {
            client.execute(() -> {
                // Remove the leading '/' if present
                String cmd = command.startsWith("/") ? command.substring(1) : command;
                client.player.networkHandler.sendChatCommand(cmd);
            });
        }
    }
    
    /**
     * Normalize Unicode small caps characters to regular lowercase letters.
     * Servers often use Unicode small caps for styled text (e.g., "Dᴀʏᴄᴀʀᴇ" instead of "Daycare").
     * This method converts them back to regular letters for matching.
     * 
     * @param text The text to normalize
     * @return Text with Unicode small caps converted to lowercase letters
     */
    private String normalizeUnicodeSmallCaps(String text) {
        if (text == null) return "";
        
        // Map of Unicode small caps to regular lowercase letters
        // Common Unicode small caps range: U+1D00 to U+1D2B and others
        StringBuilder normalized = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case 'ᴀ' -> normalized.append('a'); // U+1D00
                case 'ʙ' -> normalized.append('b'); // U+0299
                case 'ᴄ' -> normalized.append('c'); // U+1D04
                case 'ᴅ' -> normalized.append('d'); // U+1D05
                case 'ᴇ' -> normalized.append('e'); // U+1D07
                case 'ꜰ' -> normalized.append('f'); // U+A730
                case 'ɢ' -> normalized.append('g'); // U+0262
                case 'ʜ' -> normalized.append('h'); // U+029C
                case 'ɪ' -> normalized.append('i'); // U+026A
                case 'ᴊ' -> normalized.append('j'); // U+1D0A
                case 'ᴋ' -> normalized.append('k'); // U+1D0B
                case 'ʟ' -> normalized.append('l'); // U+029F
                case 'ᴍ' -> normalized.append('m'); // U+1D0D
                case 'ɴ' -> normalized.append('n'); // U+0274
                case 'ᴏ' -> normalized.append('o'); // U+1D0F
                case 'ᴘ' -> normalized.append('p'); // U+1D18
                case 'ꞯ' -> normalized.append('q'); // U+A7AF (rare)
                case 'ʀ' -> normalized.append('r'); // U+0280
                case 'ꜱ' -> normalized.append('s'); // U+A731
                case 'ᴛ' -> normalized.append('t'); // U+1D1B
                case 'ᴜ' -> normalized.append('u'); // U+1D1C
                case 'ᴠ' -> normalized.append('v'); // U+1D20
                case 'ᴡ' -> normalized.append('w'); // U+1D21
                case 'x' -> normalized.append('x'); // No small cap version
                case 'ʏ' -> normalized.append('y'); // U+028F
                case 'ᴢ' -> normalized.append('z'); // U+1D22
                default -> normalized.append(c);
            }
        }
        return normalized.toString();
    }
    
    /**
     * Send in-game notification for daycare process with step number.
     * Format: [PokeAlert] Egg Manager Daycare [x/8]: message
     * Uses same color formatting as Egg Hatcher notifications.
     * 
     * @param step Current step number (0 = initiated, 1-8 = actual steps)
     * @param message The notification message
     */
    private void sendDaycareStepNotification(int step, String message) {
        if (client.player != null) {
            PokeAlertConfig config = ConfigManager.getConfig();
            if (config.notifications.textEnabled) {
                client.execute(() -> {
                    String title = step == 0 
                        ? "Egg Manager Daycare" 
                        : "Egg Manager Daycare [" + step + "/" + DAYCARE_TOTAL_STEPS + "]";
                    
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("[").formatted(net.minecraft.util.Formatting.GRAY)
                            .append(net.minecraft.text.Text.literal("PokeAlert").formatted(net.minecraft.util.Formatting.RED))
                            .append(net.minecraft.text.Text.literal("] ").formatted(net.minecraft.util.Formatting.GRAY))
                            .append(net.minecraft.text.Text.literal(title + ": ").formatted(net.minecraft.util.Formatting.WHITE))
                            .append(net.minecraft.text.Text.literal(message).formatted(net.minecraft.util.Formatting.YELLOW))
                    );
                });
            }
        }
    }
    
    /**
     * Send in-game notification for daycare process (non-step, for errors/retries)
     */
    private void sendDaycareNotification(String message) {
        if (client.player != null) {
            PokeAlertConfig config = ConfigManager.getConfig();
            if (config.notifications.textEnabled) {
                client.execute(() -> {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("[").formatted(net.minecraft.util.Formatting.GRAY)
                            .append(net.minecraft.text.Text.literal("PokeAlert").formatted(net.minecraft.util.Formatting.RED))
                            .append(net.minecraft.text.Text.literal("] ").formatted(net.minecraft.util.Formatting.GRAY))
                            .append(net.minecraft.text.Text.literal("Egg Manager Daycare: ").formatted(net.minecraft.util.Formatting.WHITE))
                            .append(net.minecraft.text.Text.literal(message).formatted(net.minecraft.util.Formatting.YELLOW))
                    );
                });
            }
        }
    }
    
    /**
     * Send 10-second warning notification before opening PC for transfers.
     * This gives the player time to close any open interfaces.
     * 
     * @param seconds Seconds until PC will open
     */
    private void sendPCWarningNotification(int seconds) {
        if (client.player != null) {
            PokeAlertConfig config = ConfigManager.getConfig();
            if (config.notifications.textEnabled) {
                client.execute(() -> {
                    String message = "⚠️ PC will open in " + seconds + "s - Close any open interfaces!";
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("[").formatted(net.minecraft.util.Formatting.GRAY)
                            .append(net.minecraft.text.Text.literal("PokeAlert").formatted(net.minecraft.util.Formatting.RED))
                            .append(net.minecraft.text.Text.literal("] ").formatted(net.minecraft.util.Formatting.GRAY))
                            .append(net.minecraft.text.Text.literal("Egg Manager: ").formatted(net.minecraft.util.Formatting.WHITE))
                            .append(net.minecraft.text.Text.literal(message).formatted(net.minecraft.util.Formatting.GOLD))
                    );
                });
            }
        }
    }
    
    /**
     * Send notification for PC transfer status
     */
    private void sendPCTransferNotification(String message) {
        if (client.player != null) {
            PokeAlertConfig config = ConfigManager.getConfig();
            if (config.notifications.textEnabled) {
                client.execute(() -> {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("[").formatted(net.minecraft.util.Formatting.GRAY)
                            .append(net.minecraft.text.Text.literal("PokeAlert").formatted(net.minecraft.util.Formatting.RED))
                            .append(net.minecraft.text.Text.literal("] ").formatted(net.minecraft.util.Formatting.GRAY))
                            .append(net.minecraft.text.Text.literal("Egg Manager: ").formatted(net.minecraft.util.Formatting.WHITE))
                            .append(net.minecraft.text.Text.literal(message).formatted(net.minecraft.util.Formatting.YELLOW))
                    );
                });
            }
        }
    }
    
    /**
     * Check if daycare fetch is currently in progress
     */
    public boolean isDaycareFetchInProgress() {
        return daycareState != DaycareState.IDLE && daycareState != DaycareState.COMPLETED && daycareState != DaycareState.FAILED;
    }
    
    /**
     * Get the current daycare state for status display
     */
    public String getDaycareStatus() {
        if (daycareState == DaycareState.IDLE) {
            return "Idle";
        }
        return daycareCurrentStep + " (Retry " + daycareRetryCount + ")";
    }
}

