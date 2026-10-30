package dev.lumora.client.mixin;

import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.widget.OptionSliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.12.2 slider handle (OptionSliderWidget#mouseDragged): two 4 px halves from widgets.png. The first becomes the 8 px
 * accent card of the Lumora look, the second is dropped (the track: {@link ButtonSkinMixin}).
 */
@Mixin(OptionSliderWidget.class)
public abstract class OptionSliderWidgetSkinMixin {
    private static final String BLIT = "Lnet/minecraft/client/gui/widget/OptionSliderWidget;drawTexture(IIIIII)V";
    private static final int HANDLE_WIDTH = 8;

    @Redirect(method = "mouseDragged", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void lumora$handle(OptionSliderWidget self, int x, int y, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.drawTexture(x, y, u, v, w, h);
            return;
        }
        LumoraUi.card(GuiGraphics.create(), x, y, HANDLE_WIDTH, h, self.isHovered() ? 0xFFA78BFA : 0xFF8B5CF6, LumoraUi.ACCENT);
    }

    @Redirect(method = "mouseDragged", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void lumora$handleRight(OptionSliderWidget self, int x, int y, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.drawTexture(x, y, u, v, w, h);
        }
    }
}
