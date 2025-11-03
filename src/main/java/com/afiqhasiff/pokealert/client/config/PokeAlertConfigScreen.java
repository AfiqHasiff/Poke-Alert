package com.afiqhasiff.pokealert.client.config;

import com.afiqhasiff.pokealert.client.PokeAlertClient;
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
 * Configuration screen for PokéAlert v1.1.0.
 * Provides GUI controls for all mod settings with descriptions.
 */
public class PokeAlertConfigScreen extends Screen {
    private final Screen parent;
    private PokeAlertConfig config;
    
    // Master toggle
    private ButtonWidget modEnabledButton;
    private ButtonWidget keybindButton;
    private boolean waitingForKey = false;
    
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
    
    // Text fields
    private TextFieldWidget whitelistField;
    private TextFieldWidget blacklistField;
    private TextFieldWidget excludedWorldsField;
    
    // Bottom buttons
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    
    // Layout constants
    private static final int SIDE_MARGIN = 20;
    private static final int TOGGLE_WIDTH = 50;
    private static final int RESET_WIDTH = 55;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_HEIGHT = 30;  // Height for row with description
    private static final int SECTION_SPACING = 20;
    private static final int BUTTON_GAP = 5;
    
    // Scroll position for future use
    private int scrollOffset = 0;

    public PokeAlertConfigScreen(Screen parent) {
        super(Text.literal("PokéAlert v1.1.0 Configuration"));
        this.parent = parent;
        this.config = loadConfigCopy();
    }

    private PokeAlertConfig loadConfigCopy() {
        PokeAlertConfig original = ConfigManager.getConfig();
        PokeAlertConfig copy = new PokeAlertConfig();
        
        // Copy all fields
        copy.modEnabled = original.modEnabled;
        copy.broadcastAllLegendaries = original.broadcastAllLegendaries;
        copy.broadcastAllMythics = original.broadcastAllMythics;
        copy.broadcastAllStarter = original.broadcastAllStarter;
        copy.broadcastAllBabies = original.broadcastAllBabies;
        copy.broadcastAllUltraBeasts = original.broadcastAllUltraBeasts;
        copy.broadcastAllShinies = original.broadcastAllShinies;
        copy.broadcastAllParadox = original.broadcastAllParadox;
        copy.broadcastWhitelist = Arrays.copyOf(original.broadcastWhitelist, original.broadcastWhitelist.length);
        copy.broadcastBlacklist = Arrays.copyOf(original.broadcastBlacklist, original.broadcastBlacklist.length);
        copy.excludedWorlds = Arrays.copyOf(original.excludedWorlds, original.excludedWorlds.length);
        copy.inGameTextEnabled = original.inGameTextEnabled;
        copy.inGameSoundEnabled = original.inGameSoundEnabled;
        copy.inGameSoundVolume = original.inGameSoundVolume;
        copy.telegramEnabled = original.telegramEnabled;
        
        return copy;
    }

    @Override
    protected void init() {
        int currentY = 50;
        int centerX = this.width / 2;

        // ========== Master Toggle Section ==========
        currentY += 10;
        modEnabledButton = addMasterToggle(currentY);
        currentY += ROW_HEIGHT;
        
        // Keybind button
        keybindButton = ButtonWidget.builder(
                getKeybindText(),
                button -> {
                    waitingForKey = true;
                    keybindButton.setMessage(Text.literal("Press any key...").formatted(Formatting.YELLOW));
                })
            .dimensions(this.width - SIDE_MARGIN - 150, currentY, 150, 20)
            .build();
        addDrawableChild(keybindButton);
        currentY += ROW_HEIGHT + SECTION_SPACING;

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
            sliderX, currentY, 150, 20,
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

        // ========== Custom Lists Section ==========
        // Whitelist text field
        int fieldWidth = this.width - (SIDE_MARGIN * 2) - 100; // Leave space for label
        
        currentY += 20;
        whitelistField = new TextFieldWidget(
            this.textRenderer,
            SIDE_MARGIN + 100,
            currentY,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Whitelist")
        );
        whitelistField.setMaxLength(2000);
        whitelistField.setText(String.join(", ", config.broadcastWhitelist));
        whitelistField.setPlaceholder(Text.literal("Additional Pokémon to track (comma-separated)").formatted(Formatting.GRAY));
        addSelectableChild(whitelistField);
        addDrawableChild(whitelistField);
        currentY += 25;

        // Blacklist text field
        blacklistField = new TextFieldWidget(
            this.textRenderer,
            SIDE_MARGIN + 100,
            currentY,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Blacklist")
        );
        blacklistField.setMaxLength(2000);
        blacklistField.setText(String.join(", ", config.broadcastBlacklist));
        blacklistField.setPlaceholder(Text.literal("Pokémon to exclude from notifications").formatted(Formatting.GRAY));
        addSelectableChild(blacklistField);
        addDrawableChild(blacklistField);
        currentY += 25;

        // Excluded worlds text field
        excludedWorldsField = new TextFieldWidget(
            this.textRenderer,
            SIDE_MARGIN + 100,
            currentY,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Excluded Worlds")
        );
        excludedWorldsField.setMaxLength(500);
        excludedWorldsField.setText(String.join(", ", config.excludedWorlds));
        excludedWorldsField.setPlaceholder(Text.literal("Worlds to exclude (e.g., spawn, the_end, the_nether)").formatted(Formatting.GRAY));
        addSelectableChild(excludedWorldsField);
        addDrawableChild(excludedWorldsField);
        currentY += 30;

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
    }

    private ButtonWidget addMasterToggle(int y) {
        int rightEdge = this.width - SIDE_MARGIN;
        int buttonX = rightEdge - TOGGLE_WIDTH - RESET_WIDTH - BUTTON_GAP;
        
        ButtonWidget toggleButton = ButtonWidget.builder(
            getMasterToggleText(config.modEnabled),
            button -> {
                config.modEnabled = !config.modEnabled;
                updateMasterToggleButton(modEnabledButton, config.modEnabled);
            }
        )
        .dimensions(buttonX, y, TOGGLE_WIDTH * 2, BUTTON_HEIGHT)
        .build();
        addDrawableChild(toggleButton);
        
        return toggleButton;
    }

    private ButtonWidget addCategoryRow(int y, String label, String description, boolean enabled, ButtonWidget.PressAction onPress) {
        return addOptionRow(y, label, description, enabled, onPress, true);
    }

    private ButtonWidget addNotificationRow(int y, String label, String description, boolean enabled, ButtonWidget.PressAction onPress) {
        return addOptionRow(y, label, description, enabled, onPress, false);
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
        // Parse whitelist
        String whitelistText = whitelistField.getText().trim();
        config.broadcastWhitelist = parseList(whitelistText);
        
        // Parse blacklist
        String blacklistText = blacklistField.getText().trim();
        config.broadcastBlacklist = parseList(blacklistText);
        
        // Parse excluded worlds
        String worldsText = excludedWorldsField.getText().trim();
        config.excludedWorlds = parseList(worldsText);

        // Save configuration
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
        // Render background
        this.renderBackground(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("PokéAlert v1.1.0").formatted(Formatting.GOLD),
            this.width / 2,
            15,
            0xFFFFFF
        );
        
        int currentY = 50;
        
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
        
        // Custom Lists header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Custom Lists").formatted(Formatting.AQUA),
            SIDE_MARGIN,
            currentY - 15,
            0xFFFFFF
        );
        
        currentY += 5;
        
        // List labels
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Whitelist:"),
            SIDE_MARGIN,
            currentY + 5,
            0xFFFFFF
        );
        currentY += 25;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Blacklist:"),
            SIDE_MARGIN,
            currentY + 5,
            0xFFFFFF
        );
        currentY += 25;
        
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Excluded:"),
            SIDE_MARGIN,
            currentY + 5,
            0xFFFFFF
        );
        
        // Render widgets
        super.render(context, mouseX, mouseY, delta);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle keybind setting
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
        
        // Allow escape to close the screen when not setting keybind
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !waitingForKey) {
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