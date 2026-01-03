package com.afiqhasiff.pokealert.mixin;

import com.afiqhasiff.pokealert.client.automation.EggManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to handle rendering for debug circle drag-and-drop
 * Injects into Screen class so it works for all screens, including PCGUI
 * This ensures debug circles render on top of everything and work independently of PC GUI
 * 
 * Note: Mouse events are handled via render method polling mouse state
 * since Screen class mouse method signatures vary by version
 */
@Mixin(Screen.class)
public class ScreenMixin {
    
    @Inject(method = "render", at = @At("TAIL"))
    private void injectDebugRendering(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Render debug indicators on top of all screens
        // Render at TAIL to ensure we draw on top of everything (highest layer)
        try {
            EggManager eggManager = EggManager.getInstance();
            if (eggManager != null) {
                eggManager.renderDebugIndicators(context, mouseX, mouseY);
            }
        } catch (Exception e) {
            // Log error for debugging but don't let it break screens
            org.slf4j.LoggerFactory.getLogger("pokealert").error("ScreenMixin: Error rendering debug indicators", e);
        }
    }
}

