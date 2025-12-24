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
    private static final int SEPARATOR_COLOR = 0x40FFFFFF; // Semi-transparent white
    private boolean isDraggingScrollbar = false;
    private double dragStartY = 0;
    
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
    
    // v3.0.0: Anti-AFK Region settings
    private TextFieldWidget regionX1Field;
    private TextFieldWidget regionZ1Field;
    private TextFieldWidget regionX2Field;
    private TextFieldWidget regionZ2Field;
    private TextFieldWidget playersToAvoidField;
    
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
        copy.locationsForStep6 = original.locationsForStep6;
        copy.maxConsecutiveTimeouts = original.maxConsecutiveTimeouts;
        
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
        currentY += 30 + SECTION_SPACING;
        
        // ========== v3.0.0: Anti-AFK Region Section ==========
        int regionFieldWidth = 60;
        int regionLabelWidth = this.textRenderer.getWidth("Region Corner 1 (X, Z):");
        int regionFieldX = SIDE_MARGIN + regionLabelWidth + 10;
        
        // Corner 1 (X1, Z1)
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
        addSelectableChild(regionX1Field);
        addDrawableChild(regionX1Field);
        
        regionZ1Field = new TextFieldWidget(
            this.textRenderer,
            regionFieldX + regionFieldWidth + 10,
            currentY - (int)scrollOffset,
            regionFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Z1")
        );
        regionZ1Field.setMaxLength(10);
        regionZ1Field.setText(String.valueOf(config.antiAfkRegionZ1));
        addSelectableChild(regionZ1Field);
        addDrawableChild(regionZ1Field);
        
        // Set Corner 1 to current position button
        ButtonWidget setCorner1Button = ButtonWidget.builder(
            Text.literal("Set Pos"),
            button -> {
                if (client != null && client.player != null) {
                    regionX1Field.setText(String.valueOf((int) client.player.getX()));
                    regionZ1Field.setText(String.valueOf((int) client.player.getZ()));
                }
            }
        ).dimensions(regionFieldX + (regionFieldWidth * 2) + 20, currentY - (int)scrollOffset, 50, BUTTON_HEIGHT).build();
        addDrawableChild(setCorner1Button);
        currentY += 30;
        
        // Corner 2 (X2, Z2)
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
        addSelectableChild(regionX2Field);
        addDrawableChild(regionX2Field);
        
        regionZ2Field = new TextFieldWidget(
            this.textRenderer,
            regionFieldX + regionFieldWidth + 10,
            currentY - (int)scrollOffset,
            regionFieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Z2")
        );
        regionZ2Field.setMaxLength(10);
        regionZ2Field.setText(String.valueOf(config.antiAfkRegionZ2));
        addSelectableChild(regionZ2Field);
        addDrawableChild(regionZ2Field);
        
        // Set Corner 2 to current position button
        ButtonWidget setCorner2Button = ButtonWidget.builder(
            Text.literal("Set Pos"),
            button -> {
                if (client != null && client.player != null) {
                    regionX2Field.setText(String.valueOf((int) client.player.getX()));
                    regionZ2Field.setText(String.valueOf((int) client.player.getZ()));
                }
            }
        ).dimensions(regionFieldX + (regionFieldWidth * 2) + 20, currentY - (int)scrollOffset, 50, BUTTON_HEIGHT).build();
        addDrawableChild(setCorner2Button);
        currentY += 30;
        
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
        return addOptionRow(scrolledY, label, description, enabled, onPress, false);
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
        return label.contains("Legendary") || label.contains("Mythical") || label.contains("Shiny");
    }

    private void resetCategory(String label, boolean value) {
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
        
        // Render background
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Title (always visible at top)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("PokéAlert v3.0.0").formatted(Formatting.GOLD),
            this.width / 2,
            15,
            0xFFFFFF
        );
        
        // Apply scroll offset to all content
        int currentY = 50 - (int)scrollOffset;
        
        // Master toggle section
        currentY += 10;
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Master Toggle").formatted(Formatting.YELLOW),
            SIDE_MARGIN,
            currentY + 5,
            0xFFFFFF
        );
        currentY += ROW_HEIGHT;
        
        // Draw keybind label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Toggle Keybind"),
            SIDE_MARGIN,
            currentY + 2,
            0xFFFFFF
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Quick toggle mod on/off").formatted(Formatting.GRAY),
            SIDE_MARGIN,
            currentY + 12,
            0x808080
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
            0xFFFFFF
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
            0xFFFFFF
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
                0xFFFFFF
            );
            context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Adjust notification sound volume").formatted(Formatting.GRAY),
                SIDE_MARGIN,
                currentY + 12,
                0x808080
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
            0xFFFFFF
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
            0xFFFFFF
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
            0xFFFFFF
        );
        currentY += 30 + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // v3.0.0: Anti-AFK Region header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Anti-AFK Region").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            0xFFFFFF
        );
        
        // Region Corner 1 label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Region Corner 1 (X, Z):"),
            SIDE_MARGIN,
            currentY + 2,
            0xFFFFFF
        );
        currentY += 30;
        
        // Region Corner 2 label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Region Corner 2 (X, Z):"),
            SIDE_MARGIN,
            currentY + 2,
            0xFFFFFF
        );
        currentY += 30;
        
        // Players to avoid label
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Players to Avoid:"),
            SIDE_MARGIN,
            currentY + 2,
            0xFFFFFF
        );
        currentY += 30 + SECTION_SPACING;
        
        // Draw separator line
        drawHorizontalSeparator(context, currentY - 10);
        
        // Custom Lists header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Custom Lists").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            0xFFFFFF
        );
        
        currentY += 5;
        
        // List labels - aligned with text fields on same line
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Whitelist:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            0xFFFFFF
        );
        currentY += 30;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Blacklist:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            0xFFFFFF
        );
        currentY += 30;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Name Filter:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            0xFFFFFF
        );
        currentY += 30;  // Move to next row
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Excluded:"),
            SIDE_MARGIN,
            currentY + 2,  // Vertically center with text field
            0xFFFFFF
        );
        
        // Render widgets
        super.render(context, mouseX, mouseY, delta);
        
        // Draw scroll indicator if content is scrollable
        if (maxScroll > 0) {
            int scrollbarX = this.width - 10;
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
            int thumbHeight = Math.max(20, (int)(scrollbarHeight * (scrollbarHeight / (double)(contentHeight + scrollbarHeight))));
            int thumbY = scrollbarY + (int)((scrollbarHeight - thumbHeight) * (scrollOffset / maxScroll));
            
            // Draw scrollbar track
            context.fill(scrollbarX, scrollbarY, scrollbarX + 5, scrollbarY + scrollbarHeight, 0x40FFFFFF);
            
            // Draw scrollbar thumb
            context.fill(scrollbarX, thumbY, scrollbarX + 5, thumbY + thumbHeight, 0x80FFFFFF);
        }
    }

    private void drawCategoryWithDescription(DrawContext context, String label, String description, int y) {
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal(label),
            SIDE_MARGIN,
            y + 2,
            0xFFFFFF
        );
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal(description).formatted(Formatting.GRAY),
            SIDE_MARGIN,
            y + 12,
            0x808080
        );
    }
    
    private void drawHorizontalSeparator(DrawContext context, int y) {
        // Draw a subtle horizontal line
        context.fill(SIDE_MARGIN, y, this.width - SIDE_MARGIN, y + 1, SEPARATOR_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // v3.0.0: Anti-AFK keybind handling REMOVED
        
        if (whitelistField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(whitelistField);
            return true;
        }
        if (blacklistField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(blacklistField);
            return true;
        }
        if (excludedWorldsField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(excludedWorldsField);
            return true;
        }
        
        // Check if clicking on scrollbar
        if (maxScroll > 0 && button == 0) {
            int scrollbarX = this.width - 10;
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
            
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + 5 &&
                mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight) {
                isDraggingScrollbar = true;
                dragStartY = mouseY;
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar && maxScroll > 0) {
            int scrollbarY = TOP_MARGIN;
            int scrollbarHeight = this.height - TOP_MARGIN - BOTTOM_MARGIN;
            int thumbHeight = Math.max(20, (int)(scrollbarHeight * (scrollbarHeight / (double)(contentHeight + scrollbarHeight))));
            
            // Calculate new scroll position based on mouse Y
            double relativeY = mouseY - scrollbarY;
            double scrollableHeight = scrollbarHeight - thumbHeight;
            double scrollPercent = Math.max(0, Math.min(1, relativeY / scrollableHeight));
            
            double oldScroll = scrollOffset;
            scrollOffset = scrollPercent * maxScroll;
            
            // Reinitialize if scroll changed significantly
            if (Math.abs(oldScroll - scrollOffset) > 1) {
                init();
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
        
        // Reinitialize widgets if scroll changed
        if (oldScroll != scrollOffset) {
            init();
        }
        return true;
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