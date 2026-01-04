package com.afiqhasiff.pokealert.client.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.afiqhasiff.pokealert.client.util.PokemonLists;

/**
 * PokeAlert Configuration - Nested Structure (v4.0.0)
 * 
 * Migration from flat config to nested config is handled automatically.
 * Old configs will be converted to the new format on first load.
 */
public class PokeAlertConfig {
    // ============ MASTER TOGGLE ============
    public boolean modEnabled = true;
    
    // ============ NESTED FEATURE CONFIGS ============
    public PokemonDetectionConfig detection = new PokemonDetectionConfig();
    public NotificationConfig notifications = new NotificationConfig();
    public TelegramConfig telegram = new TelegramConfig();
    public EggTimerConfig eggTimer = new EggTimerConfig();
    public EggHatcherConfig eggHatcher = new EggHatcherConfig();
    public EggManagerConfig eggManager = new EggManagerConfig();
    public AntiAfkConfig antiAfk = new AntiAfkConfig();
    
    // ============ LEGACY FIELDS FOR MIGRATION ============
    // These fields are kept for backward compatibility during migration
    // They will be ignored if nested configs exist
    
    // Legacy: Detection categories (migrate to detection.*)
    @Deprecated public Boolean broadcastAllLegendaries = null;
    @Deprecated public Boolean broadcastAllMythics = null;
    @Deprecated public Boolean broadcastAllStarter = null;
    @Deprecated public Boolean broadcastAllBabies = null;
    @Deprecated public Boolean broadcastAllUltraBeasts = null;
    @Deprecated public Boolean broadcastAllShinies = null;
    @Deprecated public Boolean broadcastAllParadox = null;
    @Deprecated public String[] broadcastWhitelist = null;
    @Deprecated public String[] broadcastBlacklist = null;
    @Deprecated public String blacklistCharacter = null;
    @Deprecated public String[] excludedWorlds = null;
    
    // Legacy: Notifications (migrate to notifications.*)
    @Deprecated public Boolean inGameTextEnabled = null;
    @Deprecated public Boolean inGameSoundEnabled = null;
    @Deprecated public Float inGameSoundVolume = null;
    
    // Legacy: Telegram (migrate to telegram.*)
    @Deprecated public Boolean telegramEnabled = null;
    @Deprecated public String telegramBotToken = null;
    @Deprecated public String telegramChatId = null;
    @Deprecated public String telegramApiUrl = null;
    @Deprecated public Integer telegramMaxNotificationsPerMinute = null;
    @Deprecated public Integer telegramCooldownSeconds = null;
    @Deprecated public Boolean telegramCommandExecutionEnabled = null;
    @Deprecated public long[] telegramAuthorizedUsers = null;
    @Deprecated public Integer telegramPollingInterval = null;
    @Deprecated public Integer telegramCommandRateLimitSeconds = null;
    
    // Legacy: Egg Timer (migrate to eggTimer.*)
    @Deprecated public Integer eggTimerDuration = null;
    @Deprecated public Boolean eggTimerTextNotification = null;
    @Deprecated public Boolean eggTimerTelegramNotification = null;
    
    // Legacy: Egg Hatcher (migrate to eggHatcher.*)
    @Deprecated public Boolean eggHatcherEnabled = null;
    @Deprecated public String realmReturnCommand = null;
    @Deprecated public Boolean dmDetectionEnabled = null;
    @Deprecated public Boolean dmTelegramNotification = null;
    @Deprecated public Boolean dmInGameNotification = null;
    @Deprecated public Boolean dmReplyEnabled = null;
    @Deprecated public String dmCommandFormat = null;
    
    // Legacy: Egg Manager (migrate to eggManager.*)
    @Deprecated public Boolean eggManagerEnabled = null;
    @Deprecated public Integer eggManagerCheckInterval = null;
    @Deprecated public Integer eggManagerConfirmationChecks = null;
    @Deprecated public Boolean eggManagerAutoStopEggHatcher = null;
    @Deprecated public Boolean eggManagerPCDiscoveryMode = null;
    @Deprecated public Boolean eggManagerIVTrackingEnabled = null;
    @Deprecated public Boolean eggManagerAutoTransferToPC = null;
    @Deprecated public Boolean eggManagerAutoFillFromPC = null;
    @Deprecated public Boolean eggManagerBoxOrganizationEnabled = null;
    @Deprecated public Boolean eggManagerCompletionCheckPC = null;
    @Deprecated public Boolean eggManagerDaycareEnabled = null;
    @Deprecated public String eggManagerDaycareCommand = null;
    @Deprecated public String eggManagerHomeCommand = null;
    @Deprecated public Integer eggManagerDaycareRetryCount = null;
    @Deprecated public Integer eggManagerDaycareTeleportWait = null;
    @Deprecated public Integer eggManagerDaycareWorldLoadWait = null;
    
    // Legacy: Anti-AFK (migrate to antiAfk.*)
    @Deprecated public Integer antiAfkRegionX1 = null;
    @Deprecated public Integer antiAfkRegionZ1 = null;
    @Deprecated public Integer antiAfkRegionX2 = null;
    @Deprecated public Integer antiAfkRegionZ2 = null;
    @Deprecated public Integer arrivalThreshold = null;
    @Deprecated public Integer teleportDetectionOffset = null;
    @Deprecated public Integer coordinateCheckInterval = null;
    @Deprecated public Integer realmCheckIntervalSpawn = null;
    @Deprecated public Integer realmCheckIntervalOverworld = null;
    @Deprecated public Integer playerMonitorInterval = null;
    @Deprecated public Integer locationTimeout = null;
    @Deprecated public String[] playersToAvoid = null;
    @Deprecated public Boolean enablePlayerListMonitoring = null;
    @Deprecated public Boolean enableNearbyPlayerDetection = null;
    @Deprecated public Double nearbyPlayerDetectionRadius = null;
    @Deprecated public Integer playerSuspicionTopNThreshold = null;
    @Deprecated public Integer initialQueueSize = null;
    @Deprecated public Integer replenishCount = null;
    @Deprecated public Integer locationsForStep5 = null;
    @Deprecated public Integer maxConsecutiveTimeouts = null;
    @Deprecated public Boolean enableHumanLikeBehavior = null;
    @Deprecated public Integer minLongPauseMs = null;
    @Deprecated public Integer maxLongPauseMs = null;
    @Deprecated public Integer minBreakPauseMs = null;
    @Deprecated public Integer maxBreakPauseMs = null;
    @Deprecated public Double longPauseChance = null;
    @Deprecated public Double breakPauseChance = null;
    @Deprecated public Double backtrackChance = null;
    @Deprecated public Double walkChance = null;
    @Deprecated public Double hotbarSwitchChance = null;
    @Deprecated public Double jumpWhileMovingChance = null;
    @Deprecated public Double lookAroundChance = null;
    
    
    // ============ NESTED CLASS DEFINITIONS ============
    
    public static class PokemonDetectionConfig {
        public boolean legendaries = true;
        public boolean mythics = true;
        public boolean starters = false;
        public boolean babies = false;
        public boolean ultraBeasts = false;
        public boolean shinies = true;
        public boolean paradox = false;
        public String[] whitelist = {"Mew", "Mewtwo"};
        public String[] blacklist = {};
        public String blacklistCharacter = "-";
        public String[] excludedWorlds = {"spawn"};
    }
    
    public static class NotificationConfig {
        public boolean textEnabled = true;
        public boolean soundEnabled = true;
        public float soundVolume = 1.0f;
    }
    
    public static class TelegramConfig {
        public boolean enabled = true;
        public String botToken = "";
        public String chatId = "";
        public String apiUrl = "https://api.telegram.org";
        public int maxNotificationsPerMinute = 10;
        public int cooldownSeconds = 30;
        public CommandExecutionConfig commands = new CommandExecutionConfig();
        
        public static class CommandExecutionConfig {
            public boolean enabled = true;
            public long[] authorizedUsers = {};
            public int pollingInterval = 5000;
            public int rateLimitSeconds = 5;
        }
    }
    
    public static class EggTimerConfig {
        public int duration = 30;
        public boolean textNotification = true;
        public boolean telegramNotification = true;
    }
    
    public static class EggHatcherConfig {
        public boolean enabled = true;
        public String realmReturnCommand = "/home new";
        public DmDetectionConfig dmDetection = new DmDetectionConfig();
        
        public static class DmDetectionConfig {
            public boolean enabled = true;
            public boolean telegramNotification = true;
            public boolean inGameNotification = false;
            public boolean replyEnabled = true;
            public String commandFormat = "/dm";
        }
    }
    
    public static class EggManagerConfig {
        // Core settings
        public boolean enabled = true;
        public int checkInterval = 60000;
        public int confirmationChecks = 2;
        public boolean autoStopEggHatcher = true;
        public boolean pcDiscoveryMode = false;
        
        // Phase 2: PC Transfer & IV Tracking
        public Phase2Config phase2 = new Phase2Config();
        
        // Phase 3: Daycare Fetching
        public DaycareConfig daycare = new DaycareConfig();
        
        public static class Phase2Config {
            public boolean ivTrackingEnabled = true;
            public boolean autoTransferToPC = true;
            public boolean autoFillFromPC = true;
            public boolean boxOrganizationEnabled = true;
            public boolean completionCheckPC = true;
        }
        
        public static class DaycareConfig {
            public boolean enabled = true;
            public String warpCommand = "/warp daycare";
            public String homeCommand = "/home new";
            public int retryCount = 3;
            public int teleportWait = 5000;
            public int worldLoadWait = 17000;
        }
    }
    
    public static class AntiAfkConfig {
        // Region bounds
        public int regionX1 = 0;
        public int regionZ1 = 0;
        public int regionX2 = 100;
        public int regionZ2 = 100;
        
        // Thresholds
        public ThresholdsConfig thresholds = new ThresholdsConfig();
        
        // Timing
        public TimingConfig timing = new TimingConfig();
        
        // Player Safety
        public PlayerSafetyConfig playerSafety = new PlayerSafetyConfig();
        
        // Queue
        public QueueConfig queue = new QueueConfig();
        
        // Human-like behavior
        public HumanBehaviorConfig humanBehavior = new HumanBehaviorConfig();
        
        public static class ThresholdsConfig {
            public int arrivalThreshold = 3;
            public int teleportDetectionOffset = 10;
        }
        
        public static class TimingConfig {
            public int coordinateCheckInterval = 500;
            public int realmCheckIntervalSpawn = 200;
            public int realmCheckIntervalOverworld = 30000;
            public int playerMonitorInterval = 5000;
            public int locationTimeout = 45000;
        }
        
        public static class PlayerSafetyConfig {
            public String[] playersToAvoid = {};
            public boolean enablePlayerListMonitoring = true;
            public boolean enableNearbyPlayerDetection = true;
            public double nearbyPlayerDetectionRadius = 32.0;
            public int suspicionTopNThreshold = 5;
        }
        
        public static class QueueConfig {
            public int initialSize = 5;
            public int replenishCount = 3;
            public int locationsForCompletion = 3;
            public int maxConsecutiveTimeouts = 3;
        }
        
        public static class HumanBehaviorConfig {
            public boolean enabled = true;
            public int minLongPauseMs = 5000;
            public int maxLongPauseMs = 15000;
            public int minBreakPauseMs = 30000;
            public int maxBreakPauseMs = 60000;
            public double longPauseChance = 0.05;
            public double breakPauseChance = 0.01;
            public double backtrackChance = 0.04;
            public double walkChance = 0.15;
            public double hotbarSwitchChance = 0.05;
            public double jumpWhileMovingChance = 0.0005;
            public double lookAroundChance = 0.03;
        }
    }
    
    // ============ MIGRATION LOGIC ============
    
    /**
     * Migrate legacy flat config to nested structure.
     * Called after loading config from file.
     * @return true if migration was performed
     */
    public boolean migrateFromLegacy() {
        boolean migrated = false;
        
        // Migrate detection settings
        if (broadcastAllLegendaries != null) { detection.legendaries = broadcastAllLegendaries; migrated = true; }
        if (broadcastAllMythics != null) { detection.mythics = broadcastAllMythics; migrated = true; }
        if (broadcastAllStarter != null) { detection.starters = broadcastAllStarter; migrated = true; }
        if (broadcastAllBabies != null) { detection.babies = broadcastAllBabies; migrated = true; }
        if (broadcastAllUltraBeasts != null) { detection.ultraBeasts = broadcastAllUltraBeasts; migrated = true; }
        if (broadcastAllShinies != null) { detection.shinies = broadcastAllShinies; migrated = true; }
        if (broadcastAllParadox != null) { detection.paradox = broadcastAllParadox; migrated = true; }
        if (broadcastWhitelist != null) { detection.whitelist = broadcastWhitelist; migrated = true; }
        if (broadcastBlacklist != null) { detection.blacklist = broadcastBlacklist; migrated = true; }
        if (blacklistCharacter != null) { detection.blacklistCharacter = blacklistCharacter; migrated = true; }
        if (excludedWorlds != null) { detection.excludedWorlds = excludedWorlds; migrated = true; }
        
        // Migrate notification settings
        if (inGameTextEnabled != null) { notifications.textEnabled = inGameTextEnabled; migrated = true; }
        if (inGameSoundEnabled != null) { notifications.soundEnabled = inGameSoundEnabled; migrated = true; }
        if (inGameSoundVolume != null) { notifications.soundVolume = inGameSoundVolume; migrated = true; }
        
        // Migrate telegram settings
        if (telegramEnabled != null) { telegram.enabled = telegramEnabled; migrated = true; }
        if (telegramBotToken != null) { telegram.botToken = telegramBotToken; migrated = true; }
        if (telegramChatId != null) { telegram.chatId = telegramChatId; migrated = true; }
        if (telegramApiUrl != null) { telegram.apiUrl = telegramApiUrl; migrated = true; }
        if (telegramMaxNotificationsPerMinute != null) { telegram.maxNotificationsPerMinute = telegramMaxNotificationsPerMinute; migrated = true; }
        if (telegramCooldownSeconds != null) { telegram.cooldownSeconds = telegramCooldownSeconds; migrated = true; }
        if (telegramCommandExecutionEnabled != null) { telegram.commands.enabled = telegramCommandExecutionEnabled; migrated = true; }
        if (telegramAuthorizedUsers != null) { telegram.commands.authorizedUsers = telegramAuthorizedUsers; migrated = true; }
        if (telegramPollingInterval != null) { telegram.commands.pollingInterval = telegramPollingInterval; migrated = true; }
        if (telegramCommandRateLimitSeconds != null) { telegram.commands.rateLimitSeconds = telegramCommandRateLimitSeconds; migrated = true; }
        
        // Migrate egg timer settings
        if (eggTimerDuration != null) { eggTimer.duration = eggTimerDuration; migrated = true; }
        if (eggTimerTextNotification != null) { eggTimer.textNotification = eggTimerTextNotification; migrated = true; }
        if (eggTimerTelegramNotification != null) { eggTimer.telegramNotification = eggTimerTelegramNotification; migrated = true; }
        
        // Migrate egg hatcher settings
        if (eggHatcherEnabled != null) { eggHatcher.enabled = eggHatcherEnabled; migrated = true; }
        if (realmReturnCommand != null) { eggHatcher.realmReturnCommand = realmReturnCommand; migrated = true; }
        if (dmDetectionEnabled != null) { eggHatcher.dmDetection.enabled = dmDetectionEnabled; migrated = true; }
        if (dmTelegramNotification != null) { eggHatcher.dmDetection.telegramNotification = dmTelegramNotification; migrated = true; }
        if (dmInGameNotification != null) { eggHatcher.dmDetection.inGameNotification = dmInGameNotification; migrated = true; }
        if (dmReplyEnabled != null) { eggHatcher.dmDetection.replyEnabled = dmReplyEnabled; migrated = true; }
        if (dmCommandFormat != null) { eggHatcher.dmDetection.commandFormat = dmCommandFormat; migrated = true; }
        
        // Migrate egg manager settings
        if (eggManagerEnabled != null) { eggManager.enabled = eggManagerEnabled; migrated = true; }
        if (eggManagerCheckInterval != null) { eggManager.checkInterval = eggManagerCheckInterval; migrated = true; }
        if (eggManagerConfirmationChecks != null) { eggManager.confirmationChecks = eggManagerConfirmationChecks; migrated = true; }
        if (eggManagerAutoStopEggHatcher != null) { eggManager.autoStopEggHatcher = eggManagerAutoStopEggHatcher; migrated = true; }
        if (eggManagerPCDiscoveryMode != null) { eggManager.pcDiscoveryMode = eggManagerPCDiscoveryMode; migrated = true; }
        if (eggManagerIVTrackingEnabled != null) { eggManager.phase2.ivTrackingEnabled = eggManagerIVTrackingEnabled; migrated = true; }
        if (eggManagerAutoTransferToPC != null) { eggManager.phase2.autoTransferToPC = eggManagerAutoTransferToPC; migrated = true; }
        if (eggManagerAutoFillFromPC != null) { eggManager.phase2.autoFillFromPC = eggManagerAutoFillFromPC; migrated = true; }
        if (eggManagerBoxOrganizationEnabled != null) { eggManager.phase2.boxOrganizationEnabled = eggManagerBoxOrganizationEnabled; migrated = true; }
        if (eggManagerCompletionCheckPC != null) { eggManager.phase2.completionCheckPC = eggManagerCompletionCheckPC; migrated = true; }
        if (eggManagerDaycareEnabled != null) { eggManager.daycare.enabled = eggManagerDaycareEnabled; migrated = true; }
        if (eggManagerDaycareCommand != null) { eggManager.daycare.warpCommand = eggManagerDaycareCommand; migrated = true; }
        if (eggManagerHomeCommand != null) { eggManager.daycare.homeCommand = eggManagerHomeCommand; migrated = true; }
        if (eggManagerDaycareRetryCount != null) { eggManager.daycare.retryCount = eggManagerDaycareRetryCount; migrated = true; }
        if (eggManagerDaycareTeleportWait != null) { eggManager.daycare.teleportWait = eggManagerDaycareTeleportWait; migrated = true; }
        if (eggManagerDaycareWorldLoadWait != null) { eggManager.daycare.worldLoadWait = eggManagerDaycareWorldLoadWait; migrated = true; }
        
        // Migrate anti-afk settings
        if (antiAfkRegionX1 != null) { antiAfk.regionX1 = antiAfkRegionX1; migrated = true; }
        if (antiAfkRegionZ1 != null) { antiAfk.regionZ1 = antiAfkRegionZ1; migrated = true; }
        if (antiAfkRegionX2 != null) { antiAfk.regionX2 = antiAfkRegionX2; migrated = true; }
        if (antiAfkRegionZ2 != null) { antiAfk.regionZ2 = antiAfkRegionZ2; migrated = true; }
        if (arrivalThreshold != null) { antiAfk.thresholds.arrivalThreshold = arrivalThreshold; migrated = true; }
        if (teleportDetectionOffset != null) { antiAfk.thresholds.teleportDetectionOffset = teleportDetectionOffset; migrated = true; }
        if (coordinateCheckInterval != null) { antiAfk.timing.coordinateCheckInterval = coordinateCheckInterval; migrated = true; }
        if (realmCheckIntervalSpawn != null) { antiAfk.timing.realmCheckIntervalSpawn = realmCheckIntervalSpawn; migrated = true; }
        if (realmCheckIntervalOverworld != null) { antiAfk.timing.realmCheckIntervalOverworld = realmCheckIntervalOverworld; migrated = true; }
        if (playerMonitorInterval != null) { antiAfk.timing.playerMonitorInterval = playerMonitorInterval; migrated = true; }
        if (locationTimeout != null) { antiAfk.timing.locationTimeout = locationTimeout; migrated = true; }
        if (playersToAvoid != null) { antiAfk.playerSafety.playersToAvoid = playersToAvoid; migrated = true; }
        if (enablePlayerListMonitoring != null) { antiAfk.playerSafety.enablePlayerListMonitoring = enablePlayerListMonitoring; migrated = true; }
        if (enableNearbyPlayerDetection != null) { antiAfk.playerSafety.enableNearbyPlayerDetection = enableNearbyPlayerDetection; migrated = true; }
        if (nearbyPlayerDetectionRadius != null) { antiAfk.playerSafety.nearbyPlayerDetectionRadius = nearbyPlayerDetectionRadius; migrated = true; }
        if (playerSuspicionTopNThreshold != null) { antiAfk.playerSafety.suspicionTopNThreshold = playerSuspicionTopNThreshold; migrated = true; }
        if (initialQueueSize != null) { antiAfk.queue.initialSize = initialQueueSize; migrated = true; }
        if (replenishCount != null) { antiAfk.queue.replenishCount = replenishCount; migrated = true; }
        if (locationsForStep5 != null) { antiAfk.queue.locationsForCompletion = locationsForStep5; migrated = true; }
        if (maxConsecutiveTimeouts != null) { antiAfk.queue.maxConsecutiveTimeouts = maxConsecutiveTimeouts; migrated = true; }
        if (enableHumanLikeBehavior != null) { antiAfk.humanBehavior.enabled = enableHumanLikeBehavior; migrated = true; }
        if (minLongPauseMs != null) { antiAfk.humanBehavior.minLongPauseMs = minLongPauseMs; migrated = true; }
        if (maxLongPauseMs != null) { antiAfk.humanBehavior.maxLongPauseMs = maxLongPauseMs; migrated = true; }
        if (minBreakPauseMs != null) { antiAfk.humanBehavior.minBreakPauseMs = minBreakPauseMs; migrated = true; }
        if (maxBreakPauseMs != null) { antiAfk.humanBehavior.maxBreakPauseMs = maxBreakPauseMs; migrated = true; }
        if (longPauseChance != null) { antiAfk.humanBehavior.longPauseChance = longPauseChance; migrated = true; }
        if (breakPauseChance != null) { antiAfk.humanBehavior.breakPauseChance = breakPauseChance; migrated = true; }
        if (backtrackChance != null) { antiAfk.humanBehavior.backtrackChance = backtrackChance; migrated = true; }
        if (walkChance != null) { antiAfk.humanBehavior.walkChance = walkChance; migrated = true; }
        if (hotbarSwitchChance != null) { antiAfk.humanBehavior.hotbarSwitchChance = hotbarSwitchChance; migrated = true; }
        if (jumpWhileMovingChance != null) { antiAfk.humanBehavior.jumpWhileMovingChance = jumpWhileMovingChance; migrated = true; }
        if (lookAroundChance != null) { antiAfk.humanBehavior.lookAroundChance = lookAroundChance; migrated = true; }
        
        // Clear legacy fields after migration
        if (migrated) {
            clearLegacyFields();
        }
        
        return migrated;
    }
    
    /**
     * Clear all legacy fields after migration
     */
    private void clearLegacyFields() {
        broadcastAllLegendaries = null;
        broadcastAllMythics = null;
        broadcastAllStarter = null;
        broadcastAllBabies = null;
        broadcastAllUltraBeasts = null;
        broadcastAllShinies = null;
        broadcastAllParadox = null;
        broadcastWhitelist = null;
        broadcastBlacklist = null;
        blacklistCharacter = null;
        excludedWorlds = null;
        inGameTextEnabled = null;
        inGameSoundEnabled = null;
        inGameSoundVolume = null;
        telegramEnabled = null;
        telegramBotToken = null;
        telegramChatId = null;
        telegramApiUrl = null;
        telegramMaxNotificationsPerMinute = null;
        telegramCooldownSeconds = null;
        telegramCommandExecutionEnabled = null;
        telegramAuthorizedUsers = null;
        telegramPollingInterval = null;
        telegramCommandRateLimitSeconds = null;
        eggTimerDuration = null;
        eggTimerTextNotification = null;
        eggTimerTelegramNotification = null;
        eggHatcherEnabled = null;
        realmReturnCommand = null;
        dmDetectionEnabled = null;
        dmTelegramNotification = null;
        dmInGameNotification = null;
        dmReplyEnabled = null;
        dmCommandFormat = null;
        eggManagerEnabled = null;
        eggManagerCheckInterval = null;
        eggManagerConfirmationChecks = null;
        eggManagerAutoStopEggHatcher = null;
        eggManagerPCDiscoveryMode = null;
        eggManagerIVTrackingEnabled = null;
        eggManagerAutoTransferToPC = null;
        eggManagerAutoFillFromPC = null;
        eggManagerBoxOrganizationEnabled = null;
        eggManagerCompletionCheckPC = null;
        eggManagerDaycareEnabled = null;
        eggManagerDaycareCommand = null;
        eggManagerHomeCommand = null;
        eggManagerDaycareRetryCount = null;
        eggManagerDaycareTeleportWait = null;
        eggManagerDaycareWorldLoadWait = null;
        antiAfkRegionX1 = null;
        antiAfkRegionZ1 = null;
        antiAfkRegionX2 = null;
        antiAfkRegionZ2 = null;
        arrivalThreshold = null;
        teleportDetectionOffset = null;
        coordinateCheckInterval = null;
        realmCheckIntervalSpawn = null;
        realmCheckIntervalOverworld = null;
        playerMonitorInterval = null;
        locationTimeout = null;
        playersToAvoid = null;
        enablePlayerListMonitoring = null;
        enableNearbyPlayerDetection = null;
        nearbyPlayerDetectionRadius = null;
        playerSuspicionTopNThreshold = null;
        initialQueueSize = null;
        replenishCount = null;
        locationsForStep5 = null;
        maxConsecutiveTimeouts = null;
        enableHumanLikeBehavior = null;
        minLongPauseMs = null;
        maxLongPauseMs = null;
        minBreakPauseMs = null;
        maxBreakPauseMs = null;
        longPauseChance = null;
        breakPauseChance = null;
        backtrackChance = null;
        walkChance = null;
        hotbarSwitchChance = null;
        jumpWhileMovingChance = null;
        lookAroundChance = null;
    }
    
    // ============ HELPER METHODS ============
    
    public String[] getCombinedWhitelist() {
        List<String> combinedList = new ArrayList<String>();

        if (detection.legendaries) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.legendaries)));
        }
        if (detection.mythics) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.mythics)));
        }
        if (detection.starters) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.starter)));
        }
        if (detection.babies) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.babies)));
        }
        if (detection.ultraBeasts) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.ultra_beasts)));
        }
        if (detection.paradox) {
            combinedList.addAll(new ArrayList<String>(Arrays.asList(PokemonLists.paradox_mons)));
        }

        combinedList.addAll(new ArrayList<String>(Arrays.asList(detection.whitelist)));

        // turn everything lowercase just in case
        for (int i = 0; i < combinedList.size(); i++) {
            combinedList.set(i, combinedList.get(i).toLowerCase());
        }

        return combinedList.toArray(new String[combinedList.size()]);
    }
    
    /**
     * Check if a Pokemon should trigger notification
     * @param pokemonName The name of the Pokemon to check
     * @return true if Pokemon should trigger notification
     */
    public boolean shouldNotify(String pokemonName) {
        if (!modEnabled) {
            return false;
        }
        
        String lowerName = pokemonName.toLowerCase();
        
        // Check blacklist first
        for (String blacklisted : detection.blacklist) {
            if (blacklisted.toLowerCase().equals(lowerName)) {
                return false;
            }
        }
        
        // Check whitelist
        String[] whitelist = getCombinedWhitelist();
        for (String whitelisted : whitelist) {
            if (whitelisted.equals(lowerName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if a world is excluded from notifications
     * @param worldName The name of the world to check
     * @return true if world is excluded
     */
    public boolean isWorldExcluded(String worldName) {
        for (String excluded : detection.excludedWorlds) {
            // Handle both formats: "spawn" and "minecraft:spawn"
            String normalizedExcluded = excluded.contains(":") ? excluded : "minecraft:" + excluded;
            String normalizedWorld = worldName.contains(":") ? worldName : "minecraft:" + worldName;
            
            if (normalizedExcluded.equalsIgnoreCase(normalizedWorld)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if Telegram configuration is valid for sending notifications
     */
    public boolean isTelegramValid() {
        return telegram.enabled 
            && telegram.botToken != null && !telegram.botToken.trim().isEmpty() 
            && telegram.chatId != null && !telegram.chatId.trim().isEmpty();
    }

    /**
     * Get the full Telegram API URL for sending messages
     */
    public String getTelegramSendMessageUrl() {
        return String.format("%s/bot%s/sendMessage", telegram.apiUrl, telegram.botToken);
    }
    
    // ============ ANTI-AFK HELPER METHODS ============
    
    /**
     * Check if Anti-AFK region has valid non-zero area
     * @return true if region is properly configured
     */
    public boolean isAntiAfkRegionValid() {
        return antiAfk.regionX1 != antiAfk.regionX2 && antiAfk.regionZ1 != antiAfk.regionZ2;
    }
    
    /**
     * Check if Anti-AFK region might be too small for effective movement
     * Recommended minimum is 20x20 blocks
     * @return true if region is smaller than recommended
     */
    public boolean isAntiAfkRegionTooSmall() {
        int width = Math.abs(antiAfk.regionX2 - antiAfk.regionX1);
        int depth = Math.abs(antiAfk.regionZ2 - antiAfk.regionZ1);
        return width < 20 || depth < 20;
    }
    
    /**
     * Get the Anti-AFK region width
     * @return Width in blocks
     */
    public int getAntiAfkRegionWidth() {
        return Math.abs(antiAfk.regionX2 - antiAfk.regionX1);
    }
    
    /**
     * Get the Anti-AFK region depth
     * @return Depth in blocks
     */
    public int getAntiAfkRegionDepth() {
        return Math.abs(antiAfk.regionZ2 - antiAfk.regionZ1);
    }
    
    /**
     * Validate all timing configuration values
     * Clamps values to valid ranges
     */
    public void validateTimingConfig() {
        antiAfk.thresholds.arrivalThreshold = clamp(antiAfk.thresholds.arrivalThreshold, 1, 10);
        antiAfk.thresholds.teleportDetectionOffset = clamp(antiAfk.thresholds.teleportDetectionOffset, 5, 50);
        antiAfk.timing.coordinateCheckInterval = clamp(antiAfk.timing.coordinateCheckInterval, 100, 2000);
        antiAfk.timing.realmCheckIntervalSpawn = clamp(antiAfk.timing.realmCheckIntervalSpawn, 100, 5000);
        antiAfk.timing.realmCheckIntervalOverworld = clamp(antiAfk.timing.realmCheckIntervalOverworld, 5000, 60000);
        antiAfk.timing.playerMonitorInterval = clamp(antiAfk.timing.playerMonitorInterval, 1000, 30000);
        antiAfk.timing.locationTimeout = clamp(antiAfk.timing.locationTimeout, 10000, 120000);
        antiAfk.playerSafety.nearbyPlayerDetectionRadius = clamp(antiAfk.playerSafety.nearbyPlayerDetectionRadius, 1.0, 128.0);
        antiAfk.queue.initialSize = clamp(antiAfk.queue.initialSize, 3, 10);
        antiAfk.queue.replenishCount = clamp(antiAfk.queue.replenishCount, 1, 5);
        antiAfk.queue.locationsForCompletion = clamp(antiAfk.queue.locationsForCompletion, 1, 10);
        antiAfk.queue.maxConsecutiveTimeouts = clamp(antiAfk.queue.maxConsecutiveTimeouts, 1, 10);
        antiAfk.playerSafety.suspicionTopNThreshold = clamp(antiAfk.playerSafety.suspicionTopNThreshold, 1, 20);
        
        // Ensure teleport detection is at least 2x arrival threshold
        if (antiAfk.thresholds.teleportDetectionOffset < antiAfk.thresholds.arrivalThreshold * 2) {
            antiAfk.thresholds.teleportDetectionOffset = antiAfk.thresholds.arrivalThreshold * 2;
        }
    }
    
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
    
    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    // ============ EGG MANAGER HELPER METHODS ============
    
    /**
     * Check if Egg Manager daycare feature is effectively enabled
     * Daycare requires Egg Manager to be enabled
     * @return true if daycare feature is enabled and Egg Manager is enabled
     */
    public boolean isDaycareEffectivelyEnabled() {
        return eggManager.enabled && eggManager.daycare.enabled;
    }
    
    /**
     * Check if Egg Manager Phase 2 features are effectively enabled
     * @return true if Phase 2 is enabled and Egg Manager is enabled
     */
    public boolean isPhase2EffectivelyEnabled() {
        return eggManager.enabled;
    }
}
