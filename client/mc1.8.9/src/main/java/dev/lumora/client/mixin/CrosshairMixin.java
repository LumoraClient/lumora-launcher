package dev.lumora.client.mixin;

import dev.lumora.client.api.McHudGraphics;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.core.extras.CrosshairModule;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lumora crosshair module: 1.8.9 draws the crosshair inside render() behind showCrosshair(); while the module is on,
 * that check answers "no" and the Lumora crosshair is drawn wherever vanilla would have drawn one.
 */
@Mixin(InGameHud.class)
public abstract class CrosshairMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Shadow
    protected abstract boolean showCrosshair();

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;showCrosshair()Z"))
    private boolean lumora$crosshair(InGameHud hud) {
        CrosshairModule c = CrosshairModule.active();
        if (c == null) {
            return showCrosshair();
        }
        if (showCrosshair()) {
            GuiGraphics g = GuiGraphics.create();
            c.draw(LUMORA_G.with(g), g.guiWidth(), g.guiHeight());
        }
        return false;
    }
}
