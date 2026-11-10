package dev.lumora.client.mixin;

import dev.lumora.client.api.McHudGraphics;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.core.extras.CrosshairModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora crosshair module: method_12164 is 1.12.2's crosshair (with the attack indicator). While the module is on it
 * draws the Lumora crosshair in first person instead; the F3 debug crosshair stays vanilla.
 */
@Mixin(InGameHud.class)
public abstract class CrosshairMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Inject(method = "method_12164", at = @At("HEAD"), cancellable = true)
    private void lumora$crosshair(float tickDelta, Window window, CallbackInfo ci) {
        CrosshairModule c = CrosshairModule.active();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (c == null || mc.options.debugEnabled && !mc.options.hudHidden) {
            return;
        }
        ci.cancel();
        if (mc.options.perspective != 0) {
            return;
        }
        if (mc.interactionManager != null && mc.interactionManager.isSpectator() && mc.targetedEntity == null) {
            return;
        }
        GuiGraphics g = GuiGraphics.create();
        c.draw(LUMORA_G.with(g), g.guiWidth(), g.guiHeight(), net.minecraft.client.MinecraftClient.getInstance().width,
                    net.minecraft.client.MinecraftClient.getInstance().height);
    }
}
