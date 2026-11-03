package dev.lumora.client.mixin;

import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.20.1 slider: two nine-slice blits from widgets.png, the full-width track and the 8 px handle. Both become
 * Lumora cards (dark track, accent handle) - see {@link ButtonSkinMixin}.
 */
@Mixin(AbstractSliderButton.class)
public abstract class SliderSkinMixin {
    private static final int HANDLE_WIDTH = 8;

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitNineSliced(Lnet/minecraft/resources/ResourceLocation;IIIIIIIIII)V"))
    private void lumora$skin(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int border, int corner,
                             int texW, int texH, int u, int v) {
        if (!LumoraUi.vanillaSkin()) {
            g.blitNineSliced(texture, x, y, w, h, border, corner, texW, texH, u, v);
            return;
        }
        AbstractSliderButton self = (AbstractSliderButton) (Object) this;
        boolean hover = self.active && self.isHoveredOrFocused();
        if (w == HANDLE_WIDTH) {
            LumoraUi.card(g, x, y, w, h, hover ? dev.lumora.client.gui.LumoraUi.ACCENT_HOVER : dev.lumora.client.gui.LumoraUi.ACCENT, LumoraUi.alpha(LumoraUi.ACCENT, 0xFF));
        } else {
            LumoraUi.card(g, x, y, w, h, hover ? 0xE8241D31 : 0xDC13101A, LumoraUi.BORDER);
        }
    }
}
