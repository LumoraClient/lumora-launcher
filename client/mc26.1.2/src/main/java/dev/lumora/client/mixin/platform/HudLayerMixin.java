package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora's HUD layer on top of the vanilla HUD (26.1.x: the HUD is drawn by Gui). */
@Mixin(Gui.class)
public abstract class HudLayerMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"))
    private void lumora$hud(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        Hooks.fireHud(g, delta);
    }
}
