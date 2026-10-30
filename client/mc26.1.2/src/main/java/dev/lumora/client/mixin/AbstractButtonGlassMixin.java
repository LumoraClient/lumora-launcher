package dev.lumora.client.mixin;

import dev.lumora.client.gui.LumoraShaders;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every vanilla-style button (Button, CycleButton, icon buttons, ...) draws its background through
 * AbstractButton#extractDefaultSprite. With Lumora shaders active that background becomes a glass panel with
 * a hover glow - the label is still drawn by vanilla, so layout, text and behaviour stay untouched.
 * Without shaders the "Lumora Theme" resource pack sprites are used instead.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonGlassMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final dev.lumora.client.api.McHudGraphics LUMORA_G = new dev.lumora.client.api.McHudGraphics();

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void lumora$glassButton(GuiGraphicsExtractor g, CallbackInfo ci) {
        AbstractButton self = (AbstractButton) (Object) this;
        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();
        boolean active = self.active;
        boolean hover = active && self.isHoveredOrFocused();
        dev.lumora.core.extras.ButtonStyleModule style = dev.lumora.core.extras.ButtonStyleModule.styled();
        if (style != null) {
            // button design chosen in the hub: background in that style, the label stays vanilla
            style.drawBackground(LUMORA_G.with(g), self, x, y, w, h, hover, active);
            ci.cancel();
            return;
        }
        if (!LumoraShaders.enabled()) {
            return;
        }
        if (hover) {
            LumoraShaders.glow(g, x, y, w, h, LumoraUi.ACCENT, 5);
        }
        LumoraShaders.glass(g, x, y, w, h, !active ? 0xB015111D : hover ? 0xF02E2645 : 0xDC1C1726);
        if (hover) {
            g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, LumoraUi.alpha(LumoraUi.ACCENT, 0xC0));
        }
        ci.cancel();
    }
}
