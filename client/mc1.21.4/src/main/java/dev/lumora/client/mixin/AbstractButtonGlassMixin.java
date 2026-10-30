package dev.lumora.client.mixin;

import net.minecraft.client.renderer.RenderType;
import dev.lumora.client.gui.LumoraShaders;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Every vanilla-style button (Button, CycleButton, ...) draws its background with one blitSprite call in
 * AbstractButton#renderWidget (1.21.4 has no separate renderDefaultSprite). With Lumora shaders active that
 * background becomes a glass panel with a hover glow - the label is still drawn by vanilla, so layout, text and
 * behaviour stay untouched. Without shaders the "Lumora Theme" resource pack sprites are used instead.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonGlassMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Ljava/util/function/Function;Lnet/minecraft/resources/ResourceLocation;IIIII)V"))
    private void lumora$glassButton(GuiGraphics g, java.util.function.Function<ResourceLocation, RenderType> pipeline, ResourceLocation sprite, int x, int y, int w, int h, int color) {
        if (!LumoraShaders.enabled()) {
            g.blitSprite(pipeline, sprite, x, y, w, h, color);
            return;
        }
        AbstractButton self = (AbstractButton) (Object) this;
        boolean active = self.active;
        boolean hover = active && self.isHoveredOrFocused();
        if (hover) {
            LumoraShaders.glow(g, x, y, w, h, LumoraUi.ACCENT, 5);
        }
        LumoraShaders.glass(g, x, y, w, h, !active ? 0xB015111D : hover ? 0xF02E2645 : 0xDC1C1726);
        if (hover) {
            g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, LumoraUi.alpha(LumoraUi.ACCENT, 0xC0));
        }
    }
}
