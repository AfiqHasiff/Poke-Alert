package com.afiqhasiff.pokealert.client.config;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
import com.afiqhasiff.pokealert.client.automation.EggHatcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;

/**
 * Configuration screen for PokéAlert v3.0.0.
 * Provides GUI controls for all mod settings with descriptions.
 * v3.0.0: Removed Anti-AFK keybind, added Baritone Anti-AFK region settings
 */
public class PokeAlertConfigScreen extends Screen {
    private final Screen parent;
    private PokeAlertConfig config;
    
    // Master toggle
    private ButtonWidget modEnabledButton;
    private ButtonWidget keybindButton;
    private boolean waitingForKey = false;
    
    // Scrolling
    private double scrollOffset = 0;
    private double maxScroll = 0;
    private int contentHeight = 0;
    private static final int SCROLL_SPEED = 10;
    private static final int TOP_MARGIN = 35; // Space for title
    private static final int BOTTOM_MARGIN = 50; // Space for buttons
    private static final int SEPARATOR_COLOR = 0x40FFFFFF; // Semi-transparent white (ARGB)
    // Color constants using ARGB format (0xAARRGGBB)
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFF808080;
    private static final int COLOR_YELLOW = 0xFFFFFF00;
    private static final int COLOR_AQUA = 0xFF00FFFF;
    private static final int COLOR_GOLD = 0xFFFFD700;
    private boolean isDraggingScrollbar = false;
    private double dragStartY = 0;
    private boolean isHoveringScrollbar = false;
    private static final int SCROLLBAR_WIDTH = 12; // Increased width for better visibility
    private static final int SCROLLBAR_X_OFFSET = 10; // Distance from right edge - ensure it's within visible area
    
    // Category toggles
    private ButtonWidget legendariesButton;
    private ButtonWidget mythicsButton;
    private ButtonWidget starterButton;
    private ButtonWidget babiesButton;
    private ButtonWidget ultraBeastsButton;
    private ButtonWidget shiniesButton;
    private ButtonWidget paradoxButton;
    
    // Notification toggles
    private ButtonWidget inGameTextButton;
    private ButtonWidget inGameSoundButton;
    private VolumeSliderWidget soundVolumeSlider;
    private ButtonWidget telegramButton;
    
    // Egg timer settings
    private ButtonWidget eggTimerDurationButton;
    private ButtonWidget eggTimerKeybindButton;
    private boolean waitingForEggTimerKey = false;
    
    // Egg hatcher settings
    private ButtonWidget realmReturnToggleButton;
    private ButtonWidget realmReturnKeybindButton;
    private boolean waitingForRealmReturnKey = false;
    // v3.0.0: Anti-AFK keybind REMOVED - now using internal Baritone control
    
    // DM Detection settings
    private ButtonWidget dmDetectionButton;
    private ButtonWidget dmTelegramNotificationButton;
    private ButtonWidget dmInGameNotificationButton;
    
    // v3.0.0: Anti-AFK Region settings
    private TextFieldWidget regionX1Field;
    private TextFieldWidget regionZ1Field;
    private TextFieldWidget regionX2Field;
    private TextFieldWidget regionZ2Field;
    private TextFieldWidget playersToAvoidField;
    
    // Human-like behavior settings
    private ButtonWidget enableHumanLikeBehaviorButton;
    private TextFieldWidget minLongPauseField;
    private TextFieldWidget maxLongPauseField;
    private TextFieldWidget minBreakPauseField;
    private TextFieldWidget maxBreakPauseField;
    private TextFieldWidget longPauseChanceField;
    private TextFieldWidget breakPauseChanceField;
    private TextFieldWidget backtrackChanceField;
    private TextFieldWidget walkChanceField;
    private TextFieldWidget hotbarSwitchChanceField;
    private TextFieldWidget jumpWhileMovingChanceField;
    private TextFieldWidget lookAroundChanceField;
    
    // Text fields
    private TextFieldWidget whitelistField;
    private TextFieldWidget blacklistField;
    private TextFieldWidget blacklistCharField;
    private TextFieldWidget excludedWorldsField;
    private TextFieldWidget realmReturnCommandField;
    
    // Bottom buttons
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    
    // Layout constants
    private static final int SIDE_MARGIN = 20;
    private static final int TOGGLE_WIDTH = 50;
    private static final int RESET_WIDTH = 55;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 120;
    private static final int ROW_HEIGHT = 30;  // Height for row with description
    private static final int SECTION_SPACING = 20;
    private static final int BUTTON_GAP = 5;

    public PokeAlertConfigScreen(Screen parent) {
        super(Text.literal("PokéAlert v3.0.0 Configuration"));
        this.parent = parent;
        
        // Reload config from file to pick up any manual edits
        ConfigManager.reload();
        
        this.config = loadConfigCopy();
        this.scrollOffset = 0; // Initialize scroll to top when screen opens
    }

    private PokeAlertConfig loadConfigCopy() {
        PokeAlertConfig original = ConfigManager.getConfig();
        PokeAlertConfig copy = new PokeAlertConfig();
        
        // Copy all fields from original config
        copy.modEnabled = original.modEnabled;
        
        // Detection categories
        copy.broadcastAllLegendaries = original.broadcastAllLegendaries;
        copy.broadcastAllMythics = original.broadcastAllMythics;
        copy.broadcastAllStarter = original.broadcastAllStarter;
        copy.broadcastAllBabies = original.broadcastAllBabies;
        copy.broadcastAllUltraBeasts = original.broadcastAllUltraBeasts;
        copy.broadcastAllShinies = original.broadcastAllShinies;
        copy.broadcastAllParadox = original.broadcastAllParadox;
        
        // Whitelist/Blacklist
        copy.broadcastWhitelist = Arrays.copyOf(original.broadcastWhitelist, original.broadcastWhitelist.length);
        copy.broadcastBlacklist = Arrays.copyOf(original.broadcastBlacklist, original.broadcastBlacklist.length);
        copy.blacklistCharacter = original.blacklistCharacter;
        
        // World exclusions
        copy.excludedWorlds = Arrays.copyOf(original.excludedWorlds, original.excludedWorlds.length);
        
        // Notification settings
        copy.inGameTextEnabled = original.inGameTextEnabled;
        copy.inGameSoundEnabled = original.inGameSoundEnabled;
        copy.inGameSoundVolume = original.inGameSoundVolume;
        copy.telegramEnabled = original.telegramEnabled;
        
        // Telegram config
        copy.telegramBotToken = original.telegramBotToken;
        copy.telegramChatId = original.telegramChatId;
        copy.telegramApiUrl = original.telegramApiUrl;
        copy.telegramMaxNotificationsPerMinute = original.telegramMaxNotificationsPerMinute;
        copy.telegramCooldownSeconds = original.telegramCooldownSeconds;
        
        // Egg timer settings
        copy.eggTimerDuration = original.eggTimerDuration;
        copy.eggTimerTextNotification = original.eggTimerTextNotification;
        copy.eggTimerTelegramNotification = original.eggTimerTelegramNotification;
        
        // Egg hatcher settings
        copy.eggHatcherEnabled = original.eggHatcherEnabled;
        copy.realmReturnCommand = original.realmReturnCommand;
        
        // v3.0.0: Anti-AFK Region settings
        copy.antiAfkRegionX1 = original.antiAfkRegionX1;
        copy.antiAfkRegionZ1 = original.antiAfkRegionZ1;
        copy.antiAfkRegionX2 = original.antiAfkRegionX2;
        copy.antiAfkRegionZ2 = original.antiAfkRegionZ2;
        copy.arrivalThreshold = original.arrivalThreshold;
        copy.teleportDetectionOffset = original.teleportDetectionOffset;
        copy.coordinateCheckInterval = original.coordinateCheckInterval;
        copy.realmCheckIntervalSpawn = original.realmCheckIntervalSpawn;
        copy.realmCheckIntervalOverworld = original.realmCheckIntervalOverworld;
        copy.playerMonitorInterval = original.playerMonitorInterval;
        copy.locationTimeout = original.locationTimeout;
        copy.playersToAvoid = Arrays.copyOf(original.playersToAvoid, original.playersToAvoid.length);
        copy.enablePlayerListMonitoring = original.enablePlayerListMonitoring;
        copy.enableNearbyPlayerDetection = original.enableNearbyPlayerDetection;
        copy.nearbyPlayerDetectionRadius = original.nearbyPlayerDetectionRadius;
        copy.initialQueueSize = original.initialQueueSize;
        copy.replenishCount = original.replenishCount;
        copy.locationsForStep5 = original.locationsForStep5;
        copy.maxConsecutiveTimeouts = original.maxConsecutiveTimeouts;
        
        // Human-like behavior settings
        copy.enableHumanLikeBehavior = original.enableHumanLikeBehavior;
        copy.minLongPauseMs = original.minLongPauseMs;
        copy.maxLongPauseMs = original.maxLongPauseMs;
        copy.minBreakPauseMs = original.minBreakPauseMs;
        copy.maxBreakPauseMs = original.maxBreakPauseMs;
        copy.longPauseChance = original.longPauseChance;
        copy.breakPauseChance = original.breakPauseChance;
        copy.backtrackChance = original.backtrackChance;
        copy.walkChance = original.walkChance;
        copy.hotbarSwitchChance = original.hotbarSwitchChance;
        copy.jumpWhileMovingChance = original.jumpWhileMovingChance;
        copy.lookAroundChance = original.lookAroundChance;
        
        return copy;
    }

    @Override
    protected void init() {
        this.clearChildren();
        
        // Don't reset scroll when reinitializing for scroll updates
        // scrollOffset is preserved to maintain position
        
        int currentY = TOP_MARGIN + 20;
        int centerX = this.width / 2;
        int baseY = currentY; // Track original position for content height calculation

        // ========== Master Toggle Section ==========
        currentY += 10;
        modEnabledButton = addMasterToggle(currentY - (int)scrollOffset);
        currentY += ROW_HEIGHT;
        
        // Keybind button
        int keybindX = this.width - SIDE_MARGIN - BUTTON_WIDTH;
        keybindButton = ButtonWidget.builder(
                getKeybindText(),
                button -> {
                    waitingForKey = true;
                    keybindButton.setMessage(Text.literal("Press any key...").formatted(Formatting.YELLOW));
                })
            .dimensions(keybindX, currentY - (int)scrollOffset, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
        addDrawableChild(keybindButton);
        currentY += ROW_HEIGHT + SECTION_SPACING + 10; // Extra space before separator

        // ========== Detection Categories Section ==========
        // Legendaries
        legendariesButton = addCategoryRow(currentY, 
            "Legendary Pokémon", 
            "Rare and powerful legendary spawns",
            config.broadcastAllLegendaries,
            button -> {
                config.broadcastAllLegendaries = !config.broadcastAllLegendaries;
                updateToggleButton(legendariesButton, config.broadcastAllLegendaries);
            });
        currentY += ROW_HEIGHT;

        // Mythics
        mythicsButton = addCategoryRow(currentY,
            "Mythical Pokémon",
            "Ultra-rare mythical encounters",
            config.broadcastAllMythics,
            button -> {
                config.broadcastAllMythics = !config.broadcastAllMythics;
                updateToggleButton(mythicsButton, config.broadcastAllMythics);
            });
        currentY += ROW_HEIGHT;

        // Starters
        starterButton = addCategoryRow(currentY,
            "Starter Pokémon",
            "All starter Pokémon and their evolutions",
            config.broadcastAllStarter,
            button -> {
                config.broadcastAllStarter = !config.broadcastAllStarter;
                updateToggleButton(starterButton, config.broadcastAllStarter);
            });
        currentY += ROW_HEIGHT;

        // Babies
        babiesButton = addCategoryRow(currentY,
            "Baby Pokémon",
            "Cute baby Pokémon like Pichu and Togepi",
            config.broadcastAllBabies,
            button -> {
                config.broadcastAllBabies = !config.broadcastAllBabies;
                updateToggleButton(babiesButton, config.broadcastAllBabies);
            });
        currentY += ROW_HEIGHT;

        // Ultra Beasts
        ultraBeastsButton = addCategoryRow(currentY,
            "Ultra Beasts",
            "Mysterious Pokémon from Ultra Space",
            config.broadcastAllUltraBeasts,
            button -> {
                config.broadcastAllUltraBeasts = !config.broadcastAllUltraBeasts;
                updateToggleButton(ultraBeastsButton, config.broadcastAllUltraBeasts);
            });
        currentY += ROW_HEIGHT;

        // Shinies
        shiniesButton = addCategoryRow(currentY,
            "All Shiny Pokémon",
            "Any Pokémon in their shiny variant",
            config.broadcastAllShinies,
            button -> {
                config.broadcastAllShinies = !config.broadcastAllShinies;
                updateToggleButton(shiniesButton, config.broadcastAllShinies);
            });
        currentY += ROW_HEIGHT;

        // Paradox
        paradoxButton = addCategoryRow(currentY,
            "Paradox Pokémon",
            "Ancient and Future Paradox forms",
            config.broadcastAllParadox,
            button -> {
                config.broadcastAllParadox = !config.broadcastAllParadox;
                updateToggleButton(paradoxButton, config.broadcastAllParadox);
            });
        currentY += ROW_HEIGHT + SECTION_SPACING;

        // ========== Notification Settings Section ==========
        // In-Game Text
        inGameTextButton = addNotificationRow(currentY,
            "In-Game Text",
            "Show chat notifications",
            config.inGameTextEnabled,
            button -> {
                config.inGameTextEnabled = !config.inGameTextEnabled;
                updateToggleButton(inGameTextButton, config.inGameTextEnabled);
            });
        currentY += ROW_HEIGHT;

        // In-Game Sound
        inGameSoundButton = addNotificationRow(currentY,
            "In-Game Sound",
            "Play notification sound",
            config.inGameSoundEnabled,
            button -> {
                config.inGameSoundEnabled = !config.inGameSoundEnabled;
                updateToggleButton(inGameSoundButton, config.inGameSoundEnabled);
                // Enable/disable volume slider based on sound toggle
                if (soundVolumeSlider != null) {
                    soundVolumeSlider.active = config.inGameSoundEnabled;
                }
            });
        currentY += ROW_HEIGHT;
        
        // Sound Volume Slider (only if sound is enabled)
        int sliderX = this.width - SIDE_MARGIN - 150;
        soundVolumeSlider = new VolumeSliderWidget(
            sliderX, currentY - (int)scrollOffset, 150, 20,
            Text.literal("Volume: "), config.inGameSoundVolume
        );
        soundVolumeSlider.active = config.inGameSoundEnabled;
        addDrawableChild(soundVolumeSlider);
        currentY += ROW_HEIGHT;

        // Telegram
        telegramButton = addNotificationRow(currentY,
            "Telegram",
            "Send notifications to Telegram bot",
            config.telegramEnabled,
            button -> {
                config.telegramEnabled = !config.telegramEnabled;
                updateToggleButton(telegramButton, config.telegramEnabled);
            });
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // ========== Egg Timer Section ==========
        // Egg timer duration button - use addEggTimerRow for proper positioning
        eggTimerDurationButton = addEggTimerRow(currentY,
            "Egg Timer Duration",
            "Default egg timer duration",
            Text.literal("Duration: " + config.eggTimerDuration + " min"),
            button -> {
                // Cycle through common durations: 1, 5, 15, 30, 45, 60, 90, 120
                int current = config.eggTimerDuration;
                int newDuration;
                if (current < 5) newDuration = 5;
                else if (current < 15) newDuration = 15;
                else if (current < 30) newDuration = 30;
                else if (current < 45) newDuration = 45;
                else if (current < 60) newDuration = 60;
                else if (current < 90) newDuration = 90;
                else if (current < 120) newDuration = 120;
                else newDuration = 1;
                
                config.eggTimerDuration = newDuration;
                button.setMessage(Text.literal("Duration: " + newDuration + " min"));
                // Config will be saved when user clicks "Save & Apply"
            });
        currentY += ROW_HEIGHT;
        
        // Egg timer keybind button
        eggTimerKeybindButton = addEggTimerRow(currentY,
            "Timer Keybind",
            "Start egg timer keybind",
            getEggTimerKeybindText(),
            button -> {
                waitingForEggTimerKey = true;
                button.setMessage(Text.literal("> Press a key <").formatted(Formatting.YELLOW));
            });
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // ========== Egg Hatcher Section ==========
        // (Separator drawn in render() method)
        
        // Egg hatcher toggle button
        realmReturnToggleButton = addEggTimerRow(currentY,
            "Egg Hatcher",
            "Auto-return from spawn",
            Text.literal(config.eggHatcherEnabled ? "Enabled" : "Disabled"),
            button -> {
                config.eggHatcherEnabled = !config.eggHatcherEnabled;
                button.setMessage(Text.literal(config.eggHatcherEnabled ? "Enabled" : "Disabled"));
                // DM notification widgets remain enabled - users can configure them even if parent features are off
                // Config will be saved when user clicks "Save & Apply"
            });
        currentY += ROW_HEIGHT;
        
        // Egg hatcher mode toggle keybind button
        realmReturnKeybindButton = addEggTimerRow(currentY,
            "Mode Toggle Key",
            "Cycle AUTO/DISABLED or cancel active automation",
            getRealmReturnKeybindText(),
            button -> {
                waitingForRealmReturnKey = true;
                button.setMessage(Text.literal("> Press a key <").formatted(Formatting.YELLOW));
            });
        currentY += ROW_HEIGHT;
        
        // v3.0.0: Anti-AFK keybind REMOVED - now using internal Baritone control
        
        // Egg hatcher command text field
        int realmCmdLabelWidth = this.textRenderer.getWidth("Return Command:");
        int realmCmdFieldWidth = this.width - (SIDE_MARGIN * 2) - realmCmdLabelWidth - 15;
        int realmCmdFieldX = SIDE_MARGIN + realmCmdLabelWidth + 10;
        
        realmReturnCommandField = new TextFieldWidget(
            this.textRenderer,
            realmCmdFieldX,
            currentY - (int)scrollOffset,
            realmCmdFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Egg Hatcher Command")
        );
        realmReturnCommandField.setMaxLength(100);
        realmReturnCommandField.setText(config.realmReturnCommand);
        realmReturnCommandField.setPlaceholder(Text.literal("/home new").formatted(Formatting.GRAY));
        addSelectableChild(realmReturnCommandField);
        addDrawableChild(realmReturnCommandField);
        currentY += ROW_HEIGHT;
        
        // DM Detection toggle button
        dmDetectionButton = addEggTimerRow(currentY,
            "DM Detection",
            "Detect and notify about direct messages",
            Text.literal(config.dmDetectionEnabled ? "Enabled" : "Disabled"),
            button -> {
                config.dmDetectionEnabled = !config.dmDetectionEnabled;
                button.setMessage(Text.literal(config.dmDetectionEnabled ? "Enabled" : "Disabled"));
                // DM notification widgets remain enabled - users can configure them even if parent features are off
            });
        currentY += ROW_HEIGHT;
        
        // DM Telegram notification toggle
        dmTelegramNotificationButton = addNotificationRow(currentY,
            "DM Telegram",
            "Send Telegram notifications for DMs",
            config.dmTelegramNotification,
            button -> {
                config.dmTelegramNotification = !config.dmTelegramNotification;
                updateToggleButton(dmTelegramNotificationButton, config.dmTelegramNotification);
            });
        currentY += ROW_HEIGHT;
        
        // DM In-game notification toggle
        dmInGameNotificationButton = addNotificationRow(currentY,
            "DM In-Game",
            "Show in-game notifications for DMs",
            config.dmInGameNotification,
            button -> {
                config.dmInGameNotification = !config.dmInGameNotification;
                updateToggleButton(dmInGameNotificationButton, config.dmInGameNotification);
            });
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // Initialize DM notification widgets enabled state
        // Always allow configuration - settings just won't take effect until Egg Hatcher and DM Detection are enabled
        updateDmNotificationWidgetsEnabled(true);
        
        // ========== v3.0.0: Anti-AFK Region Section ==========
        // Corner 1 (X1, Z1) - full width inputs
        int regionLabelWidth = Math.max(
            this.textRenderer.getWidth("Region Corner 1 X:"),
            Math.max(
                this.textRenderer.getWidth("Region Corner 1 Z:"),
                Math.max(
                    this.textRenderer.getWidth("Region Corner 2 X:"),
                    this.textRenderer.getWidth("Region Corner 2 Z:")
                )
            )
        );
        int regionFieldWidth = this.width - (SIDE_MARGIN * 2) - regionLabelWidth - 15;
        int regionFieldX = SIDE_MARGIN + regionLabelWidth + 10;
        
        regionX1Field = new TextFieldWidget(
            this.textRenderer,
            regionFieldX,
            currentY - (int)scrollOffset,
            regionFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("X1")
        );
        regionX1Field.setMaxLength(10);
        regionX1Field.setText(String.valueOf(config.antiAfkRegionX1));
        regionX1Field.setPlaceholder(Text.literal("X coordinate").formatted(Formatting.GRAY));
        addSelectableChild(regionX1Field);
        addDrawableChild(regionX1Field);
        currentY += ROW_HEIGHT;
        
        regionZ1Field = new TextFieldWidget(
            this.textRenderer,
            regionFieldX,
            currentY - (int)scrollOffset,
            regionFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Z1")
        );
        regionZ1Field.setMaxLength(10);
        regionZ1Field.setText(String.valueOf(config.antiAfkRegionZ1));
        regionZ1Field.setPlaceholder(Text.literal("Z coordinate").formatted(Formatting.GRAY));
        addSelectableChild(regionZ1Field);
        addDrawableChild(regionZ1Field);
        currentY += ROW_HEIGHT;
        
        // Corner 2 (X2, Z2) - full width inputs
        regionX2Field = new TextFieldWidget(
            this.textRenderer,
            regionFieldX,
            currentY - (int)scrollOffset,
            regionFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("X2")
        );
        regionX2Field.setMaxLength(10);
        regionX2Field.setText(String.valueOf(config.antiAfkRegionX2));
        regionX2Field.setPlaceholder(Text.literal("X coordinate").formatted(Formatting.GRAY));
        addSelectableChild(regionX2Field);
        addDrawableChild(regionX2Field);
        currentY += ROW_HEIGHT;
        
        regionZ2Field = new TextFieldWidget(
            this.textRenderer,
            regionFieldX,
            currentY - (int)scrollOffset,
            regionFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Z2")
        );
        regionZ2Field.setMaxLength(10);
        regionZ2Field.setText(String.valueOf(config.antiAfkRegionZ2));
        regionZ2Field.setPlaceholder(Text.literal("Z coordinate").formatted(Formatting.GRAY));
        addSelectableChild(regionZ2Field);
        addDrawableChild(regionZ2Field);
        currentY += ROW_HEIGHT;
        
        // Players to avoid text field
        int avoidLabelWidth = this.textRenderer.getWidth("Players to Avoid:");
        int avoidFieldWidth = this.width - (SIDE_MARGIN * 2) - avoidLabelWidth - 15;
        int avoidFieldX = SIDE_MARGIN + avoidLabelWidth + 10;
        
        playersToAvoidField = new TextFieldWidget(
            this.textRenderer,
            avoidFieldX,
            currentY - (int)scrollOffset,
            avoidFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Players to Avoid")
        );
        playersToAvoidField.setMaxLength(500);
        playersToAvoidField.setText(String.join(", ", config.playersToAvoid));
        playersToAvoidField.setPlaceholder(Text.literal("Usernames to trigger safety stop (comma-separated)").formatted(Formatting.GRAY));
        addSelectableChild(playersToAvoidField);
        addDrawableChild(playersToAvoidField);
        currentY += 30 + SECTION_SPACING;
        
        // ========== Human-like Behavior Section ==========
        // Enable toggle
        enableHumanLikeBehaviorButton = addNotificationRow(currentY,
            "Human-like Behavior",
            "Enable random human-like actions during Anti-AFK",
            config.enableHumanLikeBehavior,
            button -> {
                config.enableHumanLikeBehavior = !config.enableHumanLikeBehavior;
                updateToggleButton(enableHumanLikeBehaviorButton, config.enableHumanLikeBehavior);
                // Enable/disable all sliders based on toggle
                updateHumanLikeBehaviorWidgetsEnabled(config.enableHumanLikeBehavior);
            });
        currentY += ROW_HEIGHT;
        
        // Pause duration fields - full width inputs
        int pauseLabelWidth = Math.max(
            this.textRenderer.getWidth("Min Long Pause Duration (ms):"),
            Math.max(
                this.textRenderer.getWidth("Max Long Pause Duration (ms):"),
                Math.max(
                    this.textRenderer.getWidth("Min Break Pause Duration (ms):"),
                    this.textRenderer.getWidth("Max Break Pause Duration (ms):")
                )
            )
        );
        int pauseFieldWidth = this.width - (SIDE_MARGIN * 2) - pauseLabelWidth - 15;
        int pauseFieldX = SIDE_MARGIN + pauseLabelWidth + 10;
        
        minLongPauseField = new TextFieldWidget(
            this.textRenderer,
            pauseFieldX,
            currentY - (int)scrollOffset,
            pauseFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Min Long Pause")
        );
        minLongPauseField.setMaxLength(6);
        minLongPauseField.setText(String.valueOf(config.minLongPauseMs));
        minLongPauseField.setPlaceholder(Text.literal("Minimum pause duration in ms").formatted(Formatting.GRAY));
        minLongPauseField.active = config.enableHumanLikeBehavior;
        addSelectableChild(minLongPauseField);
        addDrawableChild(minLongPauseField);
        currentY += ROW_HEIGHT;
        
        maxLongPauseField = new TextFieldWidget(
            this.textRenderer,
            pauseFieldX,
            currentY - (int)scrollOffset,
            pauseFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Max Long Pause")
        );
        maxLongPauseField.setMaxLength(6);
        maxLongPauseField.setText(String.valueOf(config.maxLongPauseMs));
        maxLongPauseField.setPlaceholder(Text.literal("Maximum pause duration in ms").formatted(Formatting.GRAY));
        maxLongPauseField.active = config.enableHumanLikeBehavior;
        addSelectableChild(maxLongPauseField);
        addDrawableChild(maxLongPauseField);
        currentY += ROW_HEIGHT;
        
        minBreakPauseField = new TextFieldWidget(
            this.textRenderer,
            pauseFieldX,
            currentY - (int)scrollOffset,
            pauseFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Min Break Pause")
        );
        minBreakPauseField.setMaxLength(6);
        minBreakPauseField.setText(String.valueOf(config.minBreakPauseMs));
        minBreakPauseField.setPlaceholder(Text.literal("Minimum break pause duration in ms").formatted(Formatting.GRAY));
        minBreakPauseField.active = config.enableHumanLikeBehavior;
        addSelectableChild(minBreakPauseField);
        addDrawableChild(minBreakPauseField);
        currentY += ROW_HEIGHT;
        
        maxBreakPauseField = new TextFieldWidget(
            this.textRenderer,
            pauseFieldX,
            currentY - (int)scrollOffset,
            pauseFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Max Break Pause")
        );
        maxBreakPauseField.setMaxLength(6);
        maxBreakPauseField.setText(String.valueOf(config.maxBreakPauseMs));
        maxBreakPauseField.setPlaceholder(Text.literal("Maximum break pause duration in ms").formatted(Formatting.GRAY));
        maxBreakPauseField.active = config.enableHumanLikeBehavior;
        addSelectableChild(maxBreakPauseField);
        addDrawableChild(maxBreakPauseField);
        currentY += ROW_HEIGHT;
        
        // Chance fields - full width text inputs
        int chanceLabelWidth = Math.max(
            this.textRenderer.getWidth("Long Pause Chance:"),
            Math.max(
                this.textRenderer.getWidth("Break Pause Chance:"),
                Math.max(
                    this.textRenderer.getWidth("Backtrack Chance:"),
                    Math.max(
                        this.textRenderer.getWidth("Walk Chance:"),
                        Math.max(
                            this.textRenderer.getWidth("Hotbar Switch Chance:"),
                            Math.max(
                                this.textRenderer.getWidth("Jump While Moving Chance:"),
                                this.textRenderer.getWidth("Look Around Chance:")
                            )
                        )
                    )
                )
            )
        );
        int chanceFieldWidth = this.width - (SIDE_MARGIN * 2) - chanceLabelWidth - 15;
        int chanceFieldX = SIDE_MARGIN + chanceLabelWidth + 10;
        
        longPauseChanceField = new TextFieldWidget(
            this.textRenderer,
            chanceFieldX,
            currentY - (int)scrollOffset,
            chanceFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Long Pause Chance")
        );
        longPauseChanceField.setMaxLength(10);
        longPauseChanceField.setText(String.format("%.1f", config.longPauseChance * 100));
        longPauseChanceField.setPlaceholder(Text.literal("Percentage (0.0-100.0)").formatted(Formatting.GRAY));
        longPauseChanceField.active = config.enableHumanLikeBehavior;
        addSelectableChild(longPauseChanceField);
        addDrawableChild(longPauseChanceField);
        currentY += ROW_HEIGHT;
        
        breakPauseChanceField = new TextFieldWidget(
            this.textRenderer,
            chanceFieldX,
            currentY - (int)scrollOffset,
            chanceFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Break Pause Chance")
        );
        breakPauseChanceField.setMaxLength(10);
        breakPauseChanceField.setText(String.format("%.1f", config.breakPauseChance * 100));
        breakPauseChanceField.setPlaceholder(Text.literal("Percentage (0.0-100.0)").formatted(Formatting.GRAY));
        breakPauseChanceField.active = config.enableHumanLikeBehavior;
        addSelectableChild(breakPauseChanceField);
        addDrawableChild(breakPauseChanceField);
        currentY += ROW_HEIGHT;
        
        backtrackChanceField = new TextFieldWidget(
            this.textRenderer,
            chanceFieldX,
            currentY - (int)scrollOffset,
            chanceFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Backtrack Chance")
        );
        backtrackChanceField.setMaxLength(10);
        backtrackChanceField.setText(String.format("%.1f", config.backtrackChance * 100));
        backtrackChanceField.setPlaceholder(Text.literal("Percentage (0.0-100.0)").formatted(Formatting.GRAY));
        backtrackChanceField.active = config.enableHumanLikeBehavior;
        addSelectableChild(backtrackChanceField);
        addDrawableChild(backtrackChanceField);
        currentY += ROW_HEIGHT;
        
        walkChanceField = new TextFieldWidget(
            this.textRenderer,
            chanceFieldX,
            currentY - (int)scrollOffset,
            chanceFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Walk Chance")
        );
        walkChanceField.setMaxLength(10);
        walkChanceField.setText(String.format("%.1f", config.walkChance * 100));
        walkChanceField.setPlaceholder(Text.literal("Percentage (0.0-100.0)").formatted(Formatting.GRAY));
        walkChanceField.active = config.enableHumanLikeBehavior;
        addSelectableChild(walkChanceField);
        addDrawableChild(walkChanceField);
        currentY += ROW_HEIGHT;
        
        hotbarSwitchChanceField = new TextFieldWidget(
            this.textRenderer,
            chanceFieldX,
            currentY - (int)scrollOffset,
            chanceFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Hotbar Switch Chance")
        );
        hotbarSwitchChanceField.setMaxLength(10);
        hotbarSwitchChanceField.setText(String.format("%.1f", config.hotbarSwitchChance * 100));
        hotbarSwitchChanceField.setPlaceholder(Text.literal("Percentage (0.0-100.0)").formatted(Formatting.GRAY));
        hotbarSwitchChanceField.active = config.enableHumanLikeBehavior;
        addSelectableChild(hotbarSwitchChanceField);
        addDrawableChild(hotbarSwitchChanceField);
        currentY += ROW_HEIGHT;
        
        jumpWhileMovingChanceField = new TextFieldWidget(
            this.textRenderer,
            chanceFieldX,
            currentY - (int)scrollOffset,
            chanceFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Jump While Moving Chance")
        );
        jumpWhileMovingChanceField.setMaxLength(10);
        jumpWhileMovingChanceField.setText(String.format("%.3f", config.jumpWhileMovingChance * 100));
        jumpWhileMovingChanceField.setPlaceholder(Text.literal("Percentage (0.0-100.0)").formatted(Formatting.GRAY));
        jumpWhileMovingChanceField.active = config.enableHumanLikeBehavior;
        addSelectableChild(jumpWhileMovingChanceField);
        addDrawableChild(jumpWhileMovingChanceField);
        currentY += ROW_HEIGHT;
        
        lookAroundChanceField = new TextFieldWidget(
            this.textRenderer,
            chanceFieldX,
            currentY - (int)scrollOffset,
            chanceFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Look Around Chance")
        );
        lookAroundChanceField.setMaxLength(10);
        lookAroundChanceField.setText(String.format("%.1f", config.lookAroundChance * 100));
        lookAroundChanceField.setPlaceholder(Text.literal("Percentage (0.0-100.0)").formatted(Formatting.GRAY));
        lookAroundChanceField.active = config.enableHumanLikeBehavior;
        addSelectableChild(lookAroundChanceField);
        addDrawableChild(lookAroundChanceField);
        currentY += ROW_HEIGHT + SECTION_SPACING;

        // ========== Custom Lists Section ==========
        // Calculate proper label width based on actual text rendering
        int labelWidth = Math.max(
            this.textRenderer.getWidth("Whitelist:"),
            Math.max(
                this.textRenderer.getWidth("Blacklist:"),
                Math.max(
                    this.textRenderer.getWidth("Name Filter:"),
                    this.textRenderer.getWidth("Excluded:")
                )
            )
        );
        int fieldWidth = this.width - (SIDE_MARGIN * 2) - labelWidth - 15; // Increased gap for better spacing
        int fieldX = SIDE_MARGIN + labelWidth + 10; // Consistent field starting position
        
        // Whitelist text field
        whitelistField = new TextFieldWidget(
            this.textRenderer,
            fieldX,
            currentY - (int)scrollOffset,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Whitelist")
        );
        whitelistField.setMaxLength(2000);
        whitelistField.setText(String.join(", ", config.broadcastWhitelist));
        whitelistField.setPlaceholder(Text.literal("Additional Pokémon to track (comma-separated)").formatted(Formatting.GRAY));
        addSelectableChild(whitelistField);
        addDrawableChild(whitelistField);
        currentY += 30;

        // Blacklist text field
        blacklistField = new TextFieldWidget(
            this.textRenderer,
            fieldX,
            currentY - (int)scrollOffset,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Blacklist")
        );
        blacklistField.setMaxLength(2000);
        blacklistField.setText(String.join(", ", config.broadcastBlacklist));
        blacklistField.setPlaceholder(Text.literal("Pokémon to exclude from notifications").formatted(Formatting.GRAY));
        addSelectableChild(blacklistField);
        addDrawableChild(blacklistField);
        currentY += 30;
        
        // Blacklist character field (for multiplayer filtering)
        blacklistCharField = new TextFieldWidget(
            this.textRenderer,
            fieldX,
            currentY - (int)scrollOffset,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Blacklist Character")
        );
        blacklistCharField.setMaxLength(5);
        blacklistCharField.setText(config.blacklistCharacter != null ? config.blacklistCharacter : "-");
        blacklistCharField.setPlaceholder(Text.literal("Character to filter Pokémon names (e.g., -)").formatted(Formatting.GRAY));
        blacklistCharField.setChangedListener(text -> config.blacklistCharacter = text);
        addSelectableChild(blacklistCharField);
        addDrawableChild(blacklistCharField);
        currentY += 30;

        // Excluded worlds text field
        excludedWorldsField = new TextFieldWidget(
            this.textRenderer,
            fieldX,
            currentY - (int)scrollOffset,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Excluded Worlds")
        );
        excludedWorldsField.setMaxLength(500);
        excludedWorldsField.setText(String.join(", ", config.excludedWorlds));
        excludedWorldsField.setPlaceholder(Text.literal("Worlds to exclude (e.g., spawn, the_end, the_nether)").formatted(Formatting.GRAY));
        addSelectableChild(excludedWorldsField);
        addDrawableChild(excludedWorldsField);
        currentY += 35;

        // ========== Bottom buttons ==========
        int bottomY = this.height - 30;
        int buttonWidth = 120;
        int buttonSpacing = 10;
        
        cancelButton = ButtonWidget.builder(
            Text.literal("Cancel"),
            button -> close()
        )
        .dimensions(centerX - buttonWidth - buttonSpacing / 2, bottomY, buttonWidth, BUTTON_HEIGHT)
        .build();
        addDrawableChild(cancelButton);

        saveButton = ButtonWidget.builder(
            Text.literal("Save & Apply"),
            button -> saveAndClose()
        )
        .dimensions(centerX + buttonSpacing / 2, bottomY, buttonWidth, BUTTON_HEIGHT)
        .build();
        addDrawableChild(saveButton);
        
        // Calculate content height and max scroll
        contentHeight = currentY - baseY + 50; // Add some padding
        int viewHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
        maxScroll = Math.max(0, contentHeight - viewHeight);
    }

    private ButtonWidget addMasterToggle(int y) {
        int rightEdge = this.width - SIDE_MARGIN;
        int buttonX = rightEdge - BUTTON_WIDTH;
        
        ButtonWidget toggleButton = ButtonWidget.builder(
            getMasterToggleText(config.modEnabled),
            button -> {
                config.modEnabled = !config.modEnabled;
                updateMasterToggleButton(modEnabledButton, config.modEnabled);
            }
        )
        .dimensions(buttonX, y, BUTTON_WIDTH, BUTTON_HEIGHT)
        .build();
        addDrawableChild(toggleButton);
        
        return toggleButton;
    }

    private ButtonWidget addCategoryRow(int y, String label, String description, boolean enabled, ButtonWidget.PressAction onPress) {
        // Apply scroll offset to the y position
        int scrolledY = y - (int)scrollOffset;
        return addOptionRow(scrolledY, label, description, enabled, onPress, true);
    }

    private ButtonWidget addNotificationRow(int y, String label, String description, boolean enabled, ButtonWidget.PressAction onPress) {
        // Apply scroll offset to the y position
        int scrolledY = y - (int)scrollOffset;
        return addOptionRow(scrolledY, label, description, enabled, onPress, true); // Show reset button for all notification rows
    }
    
    private ButtonWidget addEggTimerRow(int y, String label, String description, Text buttonText, ButtonWidget.PressAction onPress) {
        // Apply scroll offset to the y position
        int scrolledY = y - (int)scrollOffset;
        int rightEdge = this.width - SIDE_MARGIN;
        
        // Custom button positioned at right edge
        ButtonWidget button = ButtonWidget.builder(buttonText, onPress)
            .dimensions(rightEdge - BUTTON_WIDTH, scrolledY, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build();
        addDrawableChild(button);
        
        return button;
    }

    private ButtonWidget addOptionRow(int y, String label, String description, boolean enabled, ButtonWidget.PressAction onPress, boolean showReset) {
        int rightEdge = this.width - SIDE_MARGIN;
        int resetX = rightEdge - RESET_WIDTH;
        int toggleX = showReset ? resetX - TOGGLE_WIDTH - BUTTON_GAP : rightEdge - TOGGLE_WIDTH;
        
        // Toggle button
        ButtonWidget toggleButton = ButtonWidget.builder(
            getToggleText(enabled),
            onPress
        )
        .dimensions(toggleX, y, TOGGLE_WIDTH, BUTTON_HEIGHT)
        .build();
        addDrawableChild(toggleButton);
        
        // Reset button (only for categories)
        if (showReset) {
        ButtonWidget resetButton = ButtonWidget.builder(
            Text.literal("Reset").formatted(Formatting.GRAY),
            button -> {
                    boolean defaultValue = getDefaultValue(label);
                    resetCategory(label, defaultValue);
                }
            )
            .dimensions(resetX, y, RESET_WIDTH, BUTTON_HEIGHT)
            .build();
            addDrawableChild(resetButton);
        }
        
        return toggleButton;
    }

    private boolean getDefaultValue(String label) {
        // Detection categories
        if (label.contains("Legendary")) return true;
        if (label.contains("Mythical")) return true;
        if (label.contains("Shiny")) return true;
        if (label.contains("Starter")) return false;
        if (label.contains("Baby")) return false;
        if (label.contains("Ultra")) return false;
        if (label.contains("Paradox")) return false;
        
        // Notification settings - all default to true except DM In-Game
        if (label.contains("In-Game Text")) return true;
        if (label.contains("In-Game Sound")) return true;
        if (label.contains("Telegram")) return true;
        if (label.contains("DM Telegram")) return true;
        if (label.contains("DM In-Game")) return false; // Default false to avoid spam
        
        // Human-like behavior
        if (label.contains("Human-like Behavior")) return true;
        
        // Default to true for most settings
        return true;
    }

    private void resetCategory(String label, boolean value) {
        // Detection categories
        if (label.contains("Legendary")) {
            config.broadcastAllLegendaries = value;
            updateToggleButton(legendariesButton, value);
        } else if (label.contains("Mythical")) {
            config.broadcastAllMythics = value;
            updateToggleButton(mythicsButton, value);
        } else if (label.contains("Starter")) {
            config.broadcastAllStarter = false;
            updateToggleButton(starterButton, false);
        } else if (label.contains("Baby")) {
            config.broadcastAllBabies = false;
            updateToggleButton(babiesButton, false);
        } else if (label.contains("Ultra")) {
            config.broadcastAllUltraBeasts = false;
            updateToggleButton(ultraBeastsButton, false);
        } else if (label.contains("Shiny")) {
            config.broadcastAllShinies = true;
            updateToggleButton(shiniesButton, true);
        } else if (label.contains("Paradox")) {
            config.broadcastAllParadox = false;
            updateToggleButton(paradoxButton, false);
        }
        // Notification settings
        else if (label.contains("In-Game Text")) {
            config.inGameTextEnabled = value;
            updateToggleButton(inGameTextButton, value);
        } else if (label.contains("In-Game Sound")) {
            config.inGameSoundEnabled = value;
            updateToggleButton(inGameSoundButton, value);
            if (soundVolumeSlider != null) {
                soundVolumeSlider.active = value;
            }
        } else if (label.contains("Telegram")) {
            config.telegramEnabled = value;
            updateToggleButton(telegramButton, value);
        } else if (label.contains("DM Telegram")) {
            config.dmTelegramNotification = value;
            updateToggleButton(dmTelegramNotificationButton, value);
        } else if (label.contains("DM In-Game")) {
            config.dmInGameNotification = value;
            updateToggleButton(dmInGameNotificationButton, value);
        }
        // Human-like behavior
        else if (label.contains("Human-like Behavior")) {
            config.enableHumanLikeBehavior = value;
            updateToggleButton(enableHumanLikeBehaviorButton, value);
            updateHumanLikeBehaviorWidgetsEnabled(value);
        }
    }

    private void updateToggleButton(ButtonWidget button, boolean enabled) {
        button.setMessage(getToggleText(enabled));
    }

    private void updateMasterToggleButton(ButtonWidget button, boolean enabled) {
        button.setMessage(getMasterToggleText(enabled));
    }

    private Text getToggleText(boolean enabled) {
        return Text.literal(enabled ? "On" : "Off")
            .formatted(enabled ? Formatting.GREEN : Formatting.RED);
    }

    private Text getMasterToggleText(boolean enabled) {
        return Text.literal(enabled ? "Enabled" : "Disabled")
            .formatted(enabled ? Formatting.GREEN : Formatting.RED);
    }

    private void saveAndClose() {
        // Read all text field values into config
        // (Buttons/toggles/sliders already update config directly)
        
        // Parse whitelist
        String whitelistText = whitelistField.getText().trim();
        config.broadcastWhitelist = parseList(whitelistText);
        
        // Parse blacklist
        String blacklistText = blacklistField.getText().trim();
        config.broadcastBlacklist = parseList(blacklistText);
        
        // Parse blacklist character
        String blacklistChar = blacklistCharField.getText().trim();
        config.blacklistCharacter = blacklistChar.isEmpty() ? "-" : blacklistChar;
        
        // Parse excluded worlds
        String worldsText = excludedWorldsField.getText().trim();
        config.excludedWorlds = parseList(worldsText);
        
        // Parse egg hatcher command
        String realmCmd = realmReturnCommandField.getText().trim();
        config.realmReturnCommand = realmCmd.isEmpty() ? "/home new" : realmCmd;
        
        // v3.0.0: Parse Anti-AFK region coordinates
        try {
            config.antiAfkRegionX1 = Integer.parseInt(regionX1Field.getText().trim());
            config.antiAfkRegionZ1 = Integer.parseInt(regionZ1Field.getText().trim());
            config.antiAfkRegionX2 = Integer.parseInt(regionX2Field.getText().trim());
            config.antiAfkRegionZ2 = Integer.parseInt(regionZ2Field.getText().trim());
        } catch (NumberFormatException e) {
            // Keep existing values if parsing fails
            PokeAlertClient.LOGGER.warn("Invalid region coordinates, keeping existing values");
        }
        
        // Parse players to avoid
        String playersText = playersToAvoidField.getText().trim();
        config.playersToAvoid = parseList(playersText);
        
        // Parse human-like behavior settings
        try {
            config.minLongPauseMs = Integer.parseInt(minLongPauseField.getText().trim());
            config.maxLongPauseMs = Integer.parseInt(maxLongPauseField.getText().trim());
            config.minBreakPauseMs = Integer.parseInt(minBreakPauseField.getText().trim());
            config.maxBreakPauseMs = Integer.parseInt(maxBreakPauseField.getText().trim());
        } catch (NumberFormatException e) {
            PokeAlertClient.LOGGER.warn("Invalid pause duration values, keeping existing values");
        }
        
        // Parse chance values from text fields (percentage to decimal)
        try {
            config.longPauseChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(longPauseChanceField.getText().trim()) / 100.0));
            config.breakPauseChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(breakPauseChanceField.getText().trim()) / 100.0));
            config.backtrackChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(backtrackChanceField.getText().trim()) / 100.0));
            config.walkChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(walkChanceField.getText().trim()) / 100.0));
            config.hotbarSwitchChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(hotbarSwitchChanceField.getText().trim()) / 100.0));
            config.jumpWhileMovingChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(jumpWhileMovingChanceField.getText().trim()) / 100.0));
            config.lookAroundChance = Math.max(0.0, Math.min(1.0, Double.parseDouble(lookAroundChanceField.getText().trim()) / 100.0));
        } catch (NumberFormatException e) {
            PokeAlertClient.LOGGER.warn("Invalid chance values, keeping existing values");
        }
        
        // Validate timing config
        config.validateTimingConfig();
        
        // Note: Volume is already updated by VolumeSliderWidget.applyValue()

        // Save configuration to file
        ConfigManager.updateConfig(config);
        
        // Reload config in the client
        if (com.afiqhasiff.pokealert.client.PokeAlertClient.getInstance() != null) {
            com.afiqhasiff.pokealert.client.PokeAlertClient.getInstance().reloadConfig();
        }
        
        // Show confirmation
        if (client != null && client.player != null) {
            client.player.sendMessage(
                Text.literal("PokéAlert settings saved!")
                    .formatted(Formatting.GREEN),
                false
            );
        }
        
        close();
    }

    private String[] parseList(String text) {
        if (text.isEmpty()) {
            return new String[0];
        }
        String[] entries = text.split(",");
        return Arrays.stream(entries)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Sync button states with current config (in case changed via keybind)
        if (realmReturnToggleButton != null) {
            realmReturnToggleButton.setMessage(Text.literal(config.eggHatcherEnabled ? "Enabled" : "Disabled"));
        }
        
        // Sync DM detection button state
        if (dmDetectionButton != null) {
            dmDetectionButton.setMessage(Text.literal(config.dmDetectionEnabled ? "Enabled" : "Disabled"));
        }
        
        // DM notification widgets are always enabled for configuration
        // They just won't take effect until both Egg Hatcher and DM Detection are enabled
        
        // Render background
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Title (always visible at top)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("PokéAlert v3.0.0").formatted(Formatting.GOLD),
            this.width / 2,
            15,
            COLOR_WHITE
        );
        
        // Apply scroll offset to all content
        int currentY = 50 - (int)scrollOffset;
        
        // Enable scissor clipping for scrollable content area
        // Leave space on the right for scrollbar (SCROLLBAR_WIDTH + SCROLLBAR_X_OFFSET + some padding)
        int scrollAreaTop = TOP_MARGIN;
        int scrollAreaBottom = this.height - BOTTOM_MARGIN;
        int scrollbarArea = SCROLLBAR_WIDTH + SCROLLBAR_X_OFFSET + 5; // Reserve space for scrollbar
        context.enableScissor(SIDE_MARGIN, scrollAreaTop, this.width - scrollbarArea, scrollAreaBottom);
        
        // Master toggle section
        currentY += 10;
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Master Toggle").formatted(Formatting.YELLOW),
            SIDE_MARGIN,
            currentY + 5,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        // Draw keybind label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Toggle Keybind"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Quick toggle mod on/off").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // Detection Categories header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Detection Categories").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            COLOR_WHITE
        );
        
        // Category labels and descriptions
        drawCategoryWithDescription(context, "Legendary Pokémon", "Rare and powerful legendary spawns", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "Mythical Pokémon", "Ultra-rare mythical encounters", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "Starter Pokémon", "All starter Pokémon and their evolutions", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "Baby Pokémon", "Cute baby Pokémon like Pichu and Togepi", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "Ultra Beasts", "Mysterious Pokémon from Ultra Space", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "All Shiny Pokémon", "Any Pokémon in their shiny variant", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "Paradox Pokémon", "Ancient and Future Paradox forms", currentY);
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // Notification Settings header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Notification Settings").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            COLOR_WHITE
        );
        
        drawCategoryWithDescription(context, "In-Game Text", "Show chat notifications", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "In-Game Sound", "Play notification sound", currentY);
        currentY += ROW_HEIGHT;
        
        // Draw volume label if sound is enabled
        if (config.inGameSoundEnabled) {
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Sound Volume"),
                SIDE_MARGIN,
                currentY + 2,
                COLOR_WHITE
            );
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Adjust notification sound volume").formatted(Formatting.GRAY),
                SIDE_MARGIN,
                currentY + 12,
                COLOR_GRAY
            );
        }
        currentY += ROW_HEIGHT;
        
        drawCategoryWithDescription(context, "Telegram", "Send notifications to Telegram bot", currentY);
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // Egg Timer header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Egg Timer").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            COLOR_WHITE
        );
        
        // Egg timer duration label
        drawCategoryWithDescription(context, "Egg Timer Duration", "Default egg timer duration", currentY);
        currentY += ROW_HEIGHT;
        
        // Egg timer keybind label  
        drawCategoryWithDescription(context, "Timer Keybind", "Start egg timer keybind", currentY);
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // Egg Hatcher header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Egg Hatcher").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            COLOR_WHITE
        );
        
        // Egg hatcher toggle label
        drawCategoryWithDescription(context, "Egg Hatcher", "Auto-return from spawn", currentY);
        currentY += ROW_HEIGHT;
        
        // Egg hatcher mode toggle keybind label
        drawCategoryWithDescription(context, "Mode Toggle Key", "Cycle AUTO/DISABLED or cancel active automation", currentY);
        currentY += ROW_HEIGHT;
        
        // v3.0.0: Anti-AFK keybind REMOVED
        
        // Egg hatcher command label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Return Command:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += 30;
        
        // DM Detection labels
        drawCategoryWithDescription(context, "DM Detection", "Detect and notify about direct messages", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "DM Telegram", "Send Telegram notifications for DMs", currentY);
        currentY += ROW_HEIGHT;
        drawCategoryWithDescription(context, "DM In-Game", "Show in-game notifications for DMs", currentY);
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // v3.0.0: Anti-AFK Region header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Anti-AFK Region").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            COLOR_WHITE
        );
        
        // Region Corner 1 X label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Region Corner 1 X:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        // Region Corner 1 Z label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Region Corner 1 Z:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        // Region Corner 2 X label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Region Corner 2 X:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        // Region Corner 2 Z label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Region Corner 2 Z:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        // Players to avoid label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Players to Avoid:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += 30 + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // Human-like Behavior header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Human-like Behavior").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            COLOR_WHITE
        );
        
        drawCategoryWithDescription(context, "Human-like Behavior", "Enable random human-like actions during Anti-AFK", currentY);
        currentY += ROW_HEIGHT;
        
        // Long pause duration labels
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Min Long Pause Duration (ms):"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Max Long Pause Duration (ms):"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        // Break pause duration labels
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Min Break Pause Duration (ms):"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Max Break Pause Duration (ms):"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        currentY += ROW_HEIGHT;
        
        // Chance field labels
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Long Pause Chance:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("5-15s pause with camera rotation").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Break Pause Chance:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("30-60s break with camera rotation").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Backtrack Chance:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Revisit previous location").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Walk Chance:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Walk instead of sprint").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Hotbar Switch Chance:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Switch to random hotbar slot").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Jump While Moving Chance:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Jump while sprinting").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Look Around Chance:"),
            SIDE_MARGIN,
            currentY + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Look around after arrival").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            COLOR_GRAY
        );
        currentY += ROW_HEIGHT + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // Custom Lists header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Custom Lists").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            COLOR_WHITE
        );
        
        currentY += 5;
        
        // List labels - aligned with text fields on same line
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Whitelist:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            COLOR_WHITE
        );
        currentY += 30;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Blacklist:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            COLOR_WHITE
        );
        currentY += 30;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Name Filter:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            COLOR_WHITE
        );
        currentY += 30;  // Move to next row
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Excluded:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            COLOR_WHITE
        );
        
        // Disable scissor clipping before rendering widgets (buttons need to be outside scissor)
        context.disableScissor();
        
        // Render widgets (buttons are rendered here, outside scissor area)
        super.render(context, mouseX, mouseY, delta);
        
        // Draw scrollbar OUTSIDE scissor area so it's always visible
        // Ensure scrollbar is positioned within visible screen bounds
        if (maxScroll > 0) {
            // Position scrollbar within visible area, accounting for screen bounds
            int scrollbarX = Math.max(SIDE_MARGIN, this.width - SCROLLBAR_X_OFFSET - SCROLLBAR_WIDTH);
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
            
            // Calculate thumb size and position
            double scrollRatio = scrollbarHeight / (double) Math.max(scrollbarHeight, contentHeight);
            int thumbHeight = Math.max(40, (int)(scrollbarHeight * scrollRatio));
            int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * (scrollOffset / maxScroll));
            
            // Ensure thumb stays within bounds
            thumbY = Math.max(scrollbarY, Math.min(scrollbarY + scrollbarHeight - thumbHeight, thumbY));
            
            // Check if mouse is hovering over scrollbar (with expanded click area)
            boolean hovering = mouseX >= scrollbarX - 3 && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 3 &&
                              mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight;
            isHoveringScrollbar = hovering;
            
            // Draw scrollbar track with more visible background
            int trackColor = hovering ? 0xA0000000 : 0x80000000; // More opaque for better visibility
            context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, trackColor);
            
            // Draw scrollbar track border (more visible white border)
            int borderColor = 0xFFFFFFFF; // Solid white border
            context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + 2, borderColor);
            context.fill(scrollbarX, scrollbarY + scrollbarHeight - 2, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, borderColor);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollbarHeight, borderColor);
            context.fill(scrollbarX + SCROLLBAR_WIDTH - 2, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, borderColor);
            
            // Draw scrollbar thumb (much brighter for visibility)
            int thumbColor = (isDraggingScrollbar || hovering) ? 0xFFCCCCCC : 0xFF999999; // Lighter gray
            context.fill(scrollbarX + 2, thumbY, scrollbarX + SCROLLBAR_WIDTH - 2, thumbY + thumbHeight, thumbColor);
            
            // Draw thumb border for better visibility
            int thumbBorderColor = (isDraggingScrollbar || hovering) ? 0xFFFFFFFF : 0xFFEEEEEE;
            context.fill(scrollbarX + 2, thumbY, scrollbarX + SCROLLBAR_WIDTH - 2, thumbY + 2, thumbBorderColor);
            context.fill(scrollbarX + 2, thumbY + thumbHeight - 2, scrollbarX + SCROLLBAR_WIDTH - 2, thumbY + thumbHeight, thumbBorderColor);
            context.fill(scrollbarX + 2, thumbY, scrollbarX + 4, thumbY + thumbHeight, thumbBorderColor);
            context.fill(scrollbarX + SCROLLBAR_WIDTH - 4, thumbY, scrollbarX + SCROLLBAR_WIDTH - 2, thumbY + thumbHeight, thumbBorderColor);
        } else {
            isHoveringScrollbar = false;
        }
    }

    private void drawCategoryWithDescription(DrawContext context, String label, String description, int y) {
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal(label),
            SIDE_MARGIN,
            y + 2,
            COLOR_WHITE
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal(description).formatted(Formatting.GRAY),
            SIDE_MARGIN,
            y + 12,
            COLOR_GRAY
        );
    }
    
    private void drawHorizontalSeparator(DrawContext context, int y) {
        // Draw a subtle horizontal line
        context.fill(SIDE_MARGIN, y, this.width - SIDE_MARGIN, y + 1, SEPARATOR_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // CRITICAL: Check buttons FIRST before text fields to prevent overlap issues
        // Buttons are at fixed positions and should always be clickable
        if (saveButton != null && saveButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (cancelButton != null && cancelButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // Check if clicking on scrollbar (before text fields)
        if (maxScroll > 0 && button == 0) {
            int scrollbarX = Math.max(SIDE_MARGIN, this.width - SCROLLBAR_X_OFFSET - SCROLLBAR_WIDTH);
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
            
            // Expanded click area for easier interaction
            if (mouseX >= scrollbarX - 3 && mouseX <= scrollbarX + SCROLLBAR_WIDTH + 3 &&
                mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight) {
                isDraggingScrollbar = true;
                dragStartY = mouseY;
                
                // If clicking on track (not thumb), jump to that position
                int thumbHeight = Math.max(30, (int)(scrollbarHeight * (scrollbarHeight / (double)(contentHeight + scrollbarHeight))));
                int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * (scrollOffset / maxScroll));
                
                if (mouseY < thumbY || mouseY > thumbY + thumbHeight) {
                    // Clicked on track - jump to position
                    double relativeY = mouseY - scrollbarY;
                    double scrollableHeight = scrollbarHeight - thumbHeight;
                    double scrollPercent = Math.max(0, Math.min(1, relativeY / scrollableHeight));
                    scrollOffset = scrollPercent * maxScroll;
                    updateWidgetPositions();
                }
                
                return true;
            }
        }
        
        // v3.0.0: Anti-AFK keybind handling REMOVED
        
        // Check if click is in button area - if so, don't process text fields
        int bottomY = this.height - 30;
        int buttonAreaTop = bottomY - 5; // Slightly above buttons to prevent overlap
        if (mouseY >= buttonAreaTop) {
            // Click is in button area - let super handle it (buttons are checked first above)
            return super.mouseClicked(mouseX, mouseY, button);
        }
        
        // Check text fields AFTER buttons and scrollbar (only if not in button area)
        if (whitelistField != null && whitelistField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(whitelistField);
            return true;
        }
        if (blacklistField != null && blacklistField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(blacklistField);
            return true;
        }
        if (blacklistCharField != null && blacklistCharField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(blacklistCharField);
            return true;
        }
        if (excludedWorldsField != null && excludedWorldsField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(excludedWorldsField);
            return true;
        }
        if (realmReturnCommandField != null && realmReturnCommandField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(realmReturnCommandField);
            return true;
        }
        if (regionX1Field != null && regionX1Field.mouseClicked(mouseX, mouseY, button)) {
            setFocused(regionX1Field);
            return true;
        }
        if (regionZ1Field != null && regionZ1Field.mouseClicked(mouseX, mouseY, button)) {
            setFocused(regionZ1Field);
            return true;
        }
        if (regionX2Field != null && regionX2Field.mouseClicked(mouseX, mouseY, button)) {
            setFocused(regionX2Field);
            return true;
        }
        if (regionZ2Field != null && regionZ2Field.mouseClicked(mouseX, mouseY, button)) {
            setFocused(regionZ2Field);
            return true;
        }
        if (playersToAvoidField != null && playersToAvoidField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(playersToAvoidField);
            return true;
        }
        if (minLongPauseField != null && minLongPauseField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(minLongPauseField);
            return true;
        }
        if (maxLongPauseField != null && maxLongPauseField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(maxLongPauseField);
            return true;
        }
        if (minBreakPauseField != null && minBreakPauseField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(minBreakPauseField);
            return true;
        }
        if (maxBreakPauseField != null && maxBreakPauseField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(maxBreakPauseField);
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScroll > 0) {
            int scrollbarX = Math.max(SIDE_MARGIN, this.width - SCROLLBAR_X_OFFSET - SCROLLBAR_WIDTH);
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
            double scrollRatio = scrollbarHeight / (double) Math.max(scrollbarHeight, contentHeight);
            int thumbHeight = Math.max(40, (int)(scrollbarHeight * scrollRatio));
            
            // Calculate new scroll position based on mouse Y
            double relativeY = mouseY - scrollbarY;
            double scrollableHeight = scrollbarHeight - thumbHeight;
            double scrollPercent = Math.max(0, Math.min(1, relativeY / scrollableHeight));
            
            double oldScroll = scrollOffset;
            scrollOffset = scrollPercent * maxScroll;
            
            // Update widget positions when scroll changes significantly
            if (Math.abs(oldScroll - scrollOffset) > 1.0) {
                updateWidgetPositions();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Check if content is scrollable
        if (maxScroll <= 0) {
            return false;
        }
        
        // Scroll anywhere on the screen (not just in specific area)
        double oldScroll = scrollOffset;
        scrollOffset = scrollOffset - verticalAmount * SCROLL_SPEED;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        
        // Update widget positions when scroll changes significantly
        // This ensures widgets stay aligned with text labels
        if (Math.abs(oldScroll - scrollOffset) > 1.0) {
            updateWidgetPositions();
        }
        return true;
    }
    
    /**
     * Update widget positions based on current scroll offset.
     * We need to recreate widgets with new positions to keep them aligned with text.
     */
    private void updateWidgetPositions() {
        // Preserve text field content before recreating
        String whitelistText = whitelistField != null ? whitelistField.getText() : "";
        String blacklistText = blacklistField != null ? blacklistField.getText() : "";
        String blacklistCharText = blacklistCharField != null ? blacklistCharField.getText() : "";
        String excludedWorldsText = excludedWorldsField != null ? excludedWorldsField.getText() : "";
        String realmReturnCommandText = realmReturnCommandField != null ? realmReturnCommandField.getText() : "";
        String regionX1Text = regionX1Field != null ? regionX1Field.getText() : "";
        String regionZ1Text = regionZ1Field != null ? regionZ1Field.getText() : "";
        String regionX2Text = regionX2Field != null ? regionX2Field.getText() : "";
        String regionZ2Text = regionZ2Field != null ? regionZ2Field.getText() : "";
        String playersToAvoidText = playersToAvoidField != null ? playersToAvoidField.getText() : "";
        String minLongPauseText = minLongPauseField != null ? minLongPauseField.getText() : "";
        String maxLongPauseText = maxLongPauseField != null ? maxLongPauseField.getText() : "";
        String minBreakPauseText = minBreakPauseField != null ? minBreakPauseField.getText() : "";
        String maxBreakPauseText = maxBreakPauseField != null ? maxBreakPauseField.getText() : "";
        String longPauseChanceText = longPauseChanceField != null ? longPauseChanceField.getText() : "";
        String breakPauseChanceText = breakPauseChanceField != null ? breakPauseChanceField.getText() : "";
        String backtrackChanceText = backtrackChanceField != null ? backtrackChanceField.getText() : "";
        String walkChanceText = walkChanceField != null ? walkChanceField.getText() : "";
        String hotbarSwitchChanceText = hotbarSwitchChanceField != null ? hotbarSwitchChanceField.getText() : "";
        String jumpWhileMovingChanceText = jumpWhileMovingChanceField != null ? jumpWhileMovingChanceField.getText() : "";
        String lookAroundChanceText = lookAroundChanceField != null ? lookAroundChanceField.getText() : "";
        
        // Recreate widgets with updated positions
        init();
        
        // Restore text field content after recreation
        if (whitelistField != null && !whitelistText.isEmpty()) whitelistField.setText(whitelistText);
        if (blacklistField != null && !blacklistText.isEmpty()) blacklistField.setText(blacklistText);
        if (blacklistCharField != null && !blacklistCharText.isEmpty()) blacklistCharField.setText(blacklistCharText);
        if (excludedWorldsField != null && !excludedWorldsText.isEmpty()) excludedWorldsField.setText(excludedWorldsText);
        if (realmReturnCommandField != null && !realmReturnCommandText.isEmpty()) realmReturnCommandField.setText(realmReturnCommandText);
        if (regionX1Field != null && !regionX1Text.isEmpty()) regionX1Field.setText(regionX1Text);
        if (regionZ1Field != null && !regionZ1Text.isEmpty()) regionZ1Field.setText(regionZ1Text);
        if (regionX2Field != null && !regionX2Text.isEmpty()) regionX2Field.setText(regionX2Text);
        if (regionZ2Field != null && !regionZ2Text.isEmpty()) regionZ2Field.setText(regionZ2Text);
        if (playersToAvoidField != null && !playersToAvoidText.isEmpty()) playersToAvoidField.setText(playersToAvoidText);
        if (minLongPauseField != null && !minLongPauseText.isEmpty()) minLongPauseField.setText(minLongPauseText);
        if (maxLongPauseField != null && !maxLongPauseText.isEmpty()) maxLongPauseField.setText(maxLongPauseText);
        if (minBreakPauseField != null && !minBreakPauseText.isEmpty()) minBreakPauseField.setText(minBreakPauseText);
        if (maxBreakPauseField != null && !maxBreakPauseText.isEmpty()) maxBreakPauseField.setText(maxBreakPauseText);
        if (longPauseChanceField != null && !longPauseChanceText.isEmpty()) longPauseChanceField.setText(longPauseChanceText);
        if (breakPauseChanceField != null && !breakPauseChanceText.isEmpty()) breakPauseChanceField.setText(breakPauseChanceText);
        if (backtrackChanceField != null && !backtrackChanceText.isEmpty()) backtrackChanceField.setText(backtrackChanceText);
        if (walkChanceField != null && !walkChanceText.isEmpty()) walkChanceField.setText(walkChanceText);
        if (hotbarSwitchChanceField != null && !hotbarSwitchChanceText.isEmpty()) hotbarSwitchChanceField.setText(hotbarSwitchChanceText);
        if (jumpWhileMovingChanceField != null && !jumpWhileMovingChanceText.isEmpty()) jumpWhileMovingChanceField.setText(jumpWhileMovingChanceText);
        if (lookAroundChanceField != null && !lookAroundChanceText.isEmpty()) lookAroundChanceField.setText(lookAroundChanceText);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle keybind setting for mod toggle
        if (waitingForKey) {
            // Don't rebind to Escape key
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                waitingForKey = false;
                keybindButton.setMessage(getKeybindText());
                return true;
            }
            
            // Update the keybinding
            PokeAlertClient.toggleModKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updateKeysByCode();
            waitingForKey = false;
            keybindButton.setMessage(getKeybindText());
            return true;
        }
        
        // Handle keybind setting for egg timer
        if (waitingForEggTimerKey) {
            // Don't rebind to Escape key
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                waitingForEggTimerKey = false;
                eggTimerKeybindButton.setMessage(getEggTimerKeybindText());
                return true;
            }
            
            // Update the egg timer keybinding
            PokeAlertClient.startEggTimerKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updateKeysByCode();
            waitingForEggTimerKey = false;
            eggTimerKeybindButton.setMessage(getEggTimerKeybindText());
            return true;
        }
        
        // Handle keybind setting for egg hatcher
        if (waitingForRealmReturnKey) {
            // Don't rebind to Escape key
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                waitingForRealmReturnKey = false;
                realmReturnKeybindButton.setMessage(getRealmReturnKeybindText());
                return true;
            }
            
            // Update the egg hatcher keybinding
            PokeAlertClient.eggHatcherKey.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
            KeyBinding.updateKeysByCode();
            waitingForRealmReturnKey = false;
            realmReturnKeybindButton.setMessage(getRealmReturnKeybindText());
            return true;
        }
        
        // v3.0.0: Anti-AFK keybind handling REMOVED
        
        // Allow escape to close the screen when not setting keybind
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !waitingForKey && !waitingForEggTimerKey && !waitingForRealmReturnKey) {
            this.close();
            return true;
        }
        
        // Handle text field input
        if (getFocused() instanceof TextFieldWidget field) {
            return field.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (getFocused() instanceof TextFieldWidget field) {
            return field.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }
    
    private Text getKeybindText() {
        String keyName = PokeAlertClient.toggleModKey.getBoundKeyLocalizedText().getString();
        return Text.literal("Key: ").formatted(Formatting.WHITE)
            .append(Text.literal(keyName).formatted(Formatting.YELLOW));
    }
    
    private Text getEggTimerKeybindText() {
        String keyName = PokeAlertClient.startEggTimerKey.getBoundKeyLocalizedText().getString();
        return Text.literal("Key: ").formatted(Formatting.WHITE)
            .append(Text.literal(keyName).formatted(Formatting.YELLOW));
    }
    
    private Text getRealmReturnKeybindText() {
        String keyName = PokeAlertClient.eggHatcherKey.getBoundKeyLocalizedText().getString();
        return Text.literal("Key: ").formatted(Formatting.WHITE)
            .append(Text.literal(keyName).formatted(Formatting.YELLOW));
    }
    
    // v3.0.0: getAntiAfkKeybindText() REMOVED - no longer using external Anti-AFK keybind
    
    private void updateHumanLikeBehaviorWidgetsEnabled(boolean enabled) {
        if (minLongPauseField != null) minLongPauseField.active = enabled;
        if (maxLongPauseField != null) maxLongPauseField.active = enabled;
        if (minBreakPauseField != null) minBreakPauseField.active = enabled;
        if (maxBreakPauseField != null) maxBreakPauseField.active = enabled;
        if (longPauseChanceField != null) longPauseChanceField.active = enabled;
        if (breakPauseChanceField != null) breakPauseChanceField.active = enabled;
        if (backtrackChanceField != null) backtrackChanceField.active = enabled;
        if (walkChanceField != null) walkChanceField.active = enabled;
        if (hotbarSwitchChanceField != null) hotbarSwitchChanceField.active = enabled;
        if (jumpWhileMovingChanceField != null) jumpWhileMovingChanceField.active = enabled;
        if (lookAroundChanceField != null) lookAroundChanceField.active = enabled;
    }
    
    private void updateDmNotificationWidgetsEnabled(boolean enabled) {
        if (dmTelegramNotificationButton != null) dmTelegramNotificationButton.active = enabled;
        if (dmInGameNotificationButton != null) dmInGameNotificationButton.active = enabled;
    }
    
    /**
     * Custom slider widget for volume control
     */
    private class VolumeSliderWidget extends SliderWidget {
        private final Text prefix;
        
        public VolumeSliderWidget(int x, int y, int width, int height, Text prefix, double value) {
            super(x, y, width, height, prefix.copy().append(Text.literal((int)(value * 100) + "%")), value);
            this.prefix = prefix;
        }
        
        @Override
        protected void updateMessage() {
            this.setMessage(prefix.copy().append(Text.literal((int)(this.value * 100) + "%")));
        }
        
        @Override
        protected void applyValue() {
            config.inGameSoundVolume = (float) this.value;
        }
    }
    
}