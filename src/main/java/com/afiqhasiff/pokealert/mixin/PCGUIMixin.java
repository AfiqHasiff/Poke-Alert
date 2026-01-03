package com.afiqhasiff.pokealert.mixin;

import com.afiqhasiff.pokealert.client.automation.EggManager;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to inject visual debugging indicators into PC GUI rendering
 * This ensures debug circles render on top when PC GUI is open
 * 
 * Note: ScreenMixin handles rendering for all screens, but this ensures
 * PC GUI specifically renders debug indicators on top layer
 */
@Mixin(targets = "com.cobblemon.mod.common.client.gui.pc.PCGUI")
public class PCGUIMixin {
    
    @Inject(method = "render", at = @At("TAIL"))
    private void injectDebugRendering(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // Render debug indicators on top of PC GUI
        // ScreenMixin also renders, but this ensures PC GUI specifically gets top-layer rendering
        try {
            EggManager eggManager = EggManager.getInstance();
            if (eggManager != null) {
                eggManager.renderDebugIndicators(context, mouseX, mouseY);
            }
        } catch (Exception e) {
            // Log error for debugging but don't let it break PC GUI
            org.slf4j.LoggerFactory.getLogger("pokealert").error("PCGUIMixin: Error rendering debug indicators", e);
        }
    }
}

