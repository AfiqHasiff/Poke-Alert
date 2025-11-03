package com.afiqhasiff.pokealert.client.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;

/**
 * Configuration screen for PokéAlert.
 * Provides GUI controls for all detection settings.
 */
public class PokeAlertConfigScreen extends Screen {
    private final Screen parent;
    private PokeAlertConfig config;
    
    // UI Elements
    private ButtonWidget legendariesButton;
    private ButtonWidget mythicsButton;
    private ButtonWidget starterButton;
    private ButtonWidget babiesButton;
    private ButtonWidget ultraBeastsButton;
    private ButtonWidget shiniesButton;
    private ButtonWidget paradoxButton;
    private TextFieldWidget allowlistField;
    
    private ButtonWidget saveButton;
    private ButtonWidget cancelButton;
    
    // Layout constants
    private static final int SIDE_MARGIN = 20;  // Margin from screen edges
    private static final int TOGGLE_WIDTH = 50;
    private static final int RESET_WIDTH = 55;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_SPACING = 22;  // Space between rows
    private static final int SECTION_SPACING = 15;  // Space between sections
    private static final int BUTTON_GAP = 5;  // Gap between toggle and reset buttons

    public PokeAlertConfigScreen(Screen parent) {
        super(Text.literal("PokéAlert Configuration"));
        this.parent = parent;
        // Load a copy of the current config
        this.config = loadConfigCopy();
    }

    private PokeAlertConfig loadConfigCopy() {
        PokeAlertConfig original = ConfigManager.getConfig();
        PokeAlertConfig copy = new PokeAlertConfig();
        
        copy.broadcastAllLegendaries = original.broadcastAllLegendaries;
        copy.broadcastAllMythics = original.broadcastAllMythics;
        copy.broadcastAllStarter = original.broadcastAllStarter;
        copy.broadcastAllBabies = original.broadcastAllBabies;
        copy.broadcastAllUltraBeasts = original.broadcastAllUltraBeasts;
        copy.broadcastAllShinies = original.broadcastAllShinies;
        copy.broadcastAllParadox = original.broadcastAllParadox;
        copy.broadcastAllowlist = Arrays.copyOf(original.broadcastAllowlist, original.broadcastAllowlist.length);
        
        return copy;
    }

    @Override
    protected void init() {
        int currentY = 60;  // Starting Y position after title
        int centerX = this.width / 2;

        // ========== Detection Categories Section ==========
        
        // Legendaries
        legendariesButton = addOptionRow(currentY, 
            "Legendary Pokémon", config.broadcastAllLegendaries,
            button -> {
                config.broadcastAllLegendaries = !config.broadcastAllLegendaries;
                updateToggleButton(legendariesButton, config.broadcastAllLegendaries);
            });
        currentY += ROW_SPACING;

        // Mythics
        mythicsButton = addOptionRow(currentY,
            "Mythical Pokémon", config.broadcastAllMythics,
            button -> {
                config.broadcastAllMythics = !config.broadcastAllMythics;
                updateToggleButton(mythicsButton, config.broadcastAllMythics);
            });
        currentY += ROW_SPACING;

        // Starters
        starterButton = addOptionRow(currentY,
            "Starter Pokémon", config.broadcastAllStarter,
            button -> {
                config.broadcastAllStarter = !config.broadcastAllStarter;
                updateToggleButton(starterButton, config.broadcastAllStarter);
            });
        currentY += ROW_SPACING;

        // Babies
        babiesButton = addOptionRow(currentY,
            "Baby Pokémon", config.broadcastAllBabies,
            button -> {
                config.broadcastAllBabies = !config.broadcastAllBabies;
                updateToggleButton(babiesButton, config.broadcastAllBabies);
            });
        currentY += ROW_SPACING;

        // Ultra Beasts
        ultraBeastsButton = addOptionRow(currentY,
            "Ultra Beasts", config.broadcastAllUltraBeasts,
            button -> {
                config.broadcastAllUltraBeasts = !config.broadcastAllUltraBeasts;
                updateToggleButton(ultraBeastsButton, config.broadcastAllUltraBeasts);
            });
        currentY += ROW_SPACING;

        // Shinies
        shiniesButton = addOptionRow(currentY,
            "All Shiny Pokémon", config.broadcastAllShinies,
            button -> {
                config.broadcastAllShinies = !config.broadcastAllShinies;
                updateToggleButton(shiniesButton, config.broadcastAllShinies);
            });
        currentY += ROW_SPACING;

        // Paradox
        paradoxButton = addOptionRow(currentY,
            "Paradox Pokémon", config.broadcastAllParadox,
            button -> {
                config.broadcastAllParadox = !config.broadcastAllParadox;
                updateToggleButton(paradoxButton, config.broadcastAllParadox);
            });
        currentY += ROW_SPACING + SECTION_SPACING;

        // ========== Custom Allowlist Section ==========
        
        // Add space for separator line
        currentY += 1;  // Separator line height
        currentY += SECTION_SPACING;  // Space after separator
        
        // Add space for "Custom Allowlist" header
        currentY += 15;  // Space for header text
        
        // Add space for description text
        currentY += 15;  // Space for description
        currentY += 5;   // Additional padding before text field
        
        // Allowlist text field with proper width calculation
        int fieldWidth = this.width - (SIDE_MARGIN * 2);
        allowlistField = new TextFieldWidget(
            this.textRenderer,
            SIDE_MARGIN,
            currentY,
            fieldWidth,
            BUTTON_HEIGHT,
            Text.literal("Custom Allowlist")
        );
        allowlistField.setMaxLength(2000);
        allowlistField.setText(String.join(", ", config.broadcastAllowlist));
        allowlistField.setPlaceholder(Text.literal("Enter Pokémon names separated by commas...").formatted(Formatting.GRAY));
        addSelectableChild(allowlistField);
        addDrawableChild(allowlistField);

        // Bottom buttons (like Seed Cracker: Cancel and Save & Quit)
        int bottomY = this.height - 30;
        int buttonWidth = 150;
        int buttonSpacing = 10;
        
        cancelButton = ButtonWidget.builder(
            Text.literal("Cancel"),
            button -> close()
        )
        .dimensions(centerX - buttonWidth - buttonSpacing / 2, bottomY, buttonWidth, BUTTON_HEIGHT)
        .build();
        addDrawableChild(cancelButton);

        saveButton = ButtonWidget.builder(
            Text.literal("Save & Quit"),
            button -> saveAndClose()
        )
        .dimensions(centerX + buttonSpacing / 2, bottomY, buttonWidth, BUTTON_HEIGHT)
        .build();
        addDrawableChild(saveButton);
    }

    /**
     * Adds an option row with label, toggle button, and reset button
     */
    private ButtonWidget addOptionRow(int y, String label, boolean enabled, ButtonWidget.PressAction onPress) {
        // Calculate positions for right-aligned buttons
        int rightEdge = this.width - SIDE_MARGIN;
        int resetX = rightEdge - RESET_WIDTH;
        int toggleX = resetX - TOGGLE_WIDTH - BUTTON_GAP;
        
        // Toggle button (Yes/No)
        ButtonWidget toggleButton = ButtonWidget.builder(
            getToggleText(enabled),
            onPress
        )
        .dimensions(toggleX, y, TOGGLE_WIDTH, BUTTON_HEIGHT)
        .build();
        addDrawableChild(toggleButton);
        
        // Reset button
        ButtonWidget resetButton = ButtonWidget.builder(
            Text.literal("Reset").formatted(Formatting.GRAY),
            button -> {
                // Reset to default (all false except legendaries, mythics and shinies which default to true)
                boolean defaultValue = label.contains("Legendary") || label.contains("Mythical") || label.contains("Shiny");
                
                if (label.contains("Legendary")) {
                    config.broadcastAllLegendaries = defaultValue;
                    updateToggleButton(legendariesButton, defaultValue);
                } else if (label.contains("Mythical")) {
                    config.broadcastAllMythics = defaultValue;
                    updateToggleButton(mythicsButton, defaultValue);
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
        )
        .dimensions(resetX, y, RESET_WIDTH, BUTTON_HEIGHT)
        .build();
        addDrawableChild(resetButton);
        
        return toggleButton;
    }

    private void updateToggleButton(ButtonWidget button, boolean enabled) {
        button.setMessage(getToggleText(enabled));
    }

    private Text getToggleText(boolean enabled) {
        if (enabled) {
            return Text.literal("Yes").formatted(Formatting.GREEN);
        } else {
            return Text.literal("No").formatted(Formatting.RED);
        }
    }

    private void saveAndClose() {
        // Parse allowlist from text field
        String allowlistText = allowlistField.getText().trim();
        if (!allowlistText.isEmpty()) {
            String[] entries = allowlistText.split(",");
            config.broadcastAllowlist = Arrays.stream(entries)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        } else {
            config.broadcastAllowlist = new String[0];
        }

        // Save configuration
        ConfigManager.updateConfig(config);
        
        // Reload config in the client to apply changes immediately
        if (com.afiqhasiff.pokealert.client.PokeAlertClient.getInstance() != null) {
            com.afiqhasiff.pokealert.client.PokeAlertClient.getInstance().reloadConfig();
        }
        
        // Show confirmation message
        if (client != null && client.player != null) {
            client.player.sendMessage(
                Text.literal("PokéAlert settings saved and applied!")
                    .formatted(Formatting.GREEN),
                false
            );
        }
        
        close();
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
        
        // Render title (centered at top)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("PokéAlert"),
            this.width / 2,
            20,
            0xFFFFFF
        );
        
        // Section header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Detection Categories"),
            SIDE_MARGIN,
            45,
            0xFFFFFF
        );
        
        // Draw labels for each category (aligned with buttons)
        int currentY = 60;
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("Legendary Pokémon"), 
            SIDE_MARGIN, currentY + 5, 0xFFFFFF);
        currentY += ROW_SPACING;
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("Mythical Pokémon"), 
            SIDE_MARGIN, currentY + 5, 0xFFFFFF);
        currentY += ROW_SPACING;
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("Starter Pokémon"), 
            SIDE_MARGIN, currentY + 5, 0xFFFFFF);
        currentY += ROW_SPACING;
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("Baby Pokémon"), 
            SIDE_MARGIN, currentY + 5, 0xFFFFFF);
        currentY += ROW_SPACING;
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("Ultra Beasts"), 
            SIDE_MARGIN, currentY + 5, 0xFFFFFF);
        currentY += ROW_SPACING;
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("All Shiny Pokémon"), 
            SIDE_MARGIN, currentY + 5, 0xFFFFFF);
        currentY += ROW_SPACING;
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("Paradox Pokémon"), 
            SIDE_MARGIN, currentY + 5, 0xFFFFFF);
        currentY += ROW_SPACING + SECTION_SPACING;
        
        // Draw separator line
        int separatorWidth = this.width - (SIDE_MARGIN * 2);
        context.fill(SIDE_MARGIN, currentY, SIDE_MARGIN + separatorWidth, currentY + 1, 0xFF808080);
        currentY += SECTION_SPACING;
        
        // Custom Allowlist header
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Custom Allowlist"),
            SIDE_MARGIN,
            currentY,
            0xFFFFFF
        );
        currentY += 15;
        
        // Allowlist description
        context.drawTextWithShadow(
            this.textRenderer,
            Text.literal("Comma-separated Pokémon names (e.g., Pikachu, Charizard, Mewtwo)"),
            SIDE_MARGIN,
            currentY,
            0xA0A0A0
        );
        
        // Render all widgets (including the text field which is now a drawable child)
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (allowlistField.mouseClicked(mouseX, mouseY, button)) {
            setFocused(allowlistField);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (allowlistField.isFocused()) {
            return allowlistField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (allowlistField.isFocused()) {
            return allowlistField.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }
}

