package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.18.2 slider handle: AbstractSliderButton#renderBg blits two 4 px halves from widgets.png. The first
 * becomes the 8 px accent card of the Lumora theme, the second is dropped (the track: {@link ButtonSkinMixin}).
 */
@Mixin(AbstractSliderButton.class)
public abstract class SliderSkinMixin {
    private static final String BLIT = "Lnet/minecraft/client/gui/components/AbstractSliderButton;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIII)V";
    private static final int HANDLE_WIDTH = 8;

    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void lumora$handle(AbstractSliderButton self, PoseStack pose, int x, int y, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.blit(pose, x, y, u, v, w, h);
            return;
        }
        boolean hover = self.active && self.isHoveredOrFocused();
        LumoraUi.card(GuiGraphics.of(pose), x, y, HANDLE_WIDTH, h, hover ? dev.lumora.client.gui.LumoraUi.ACCENT_HOVER : dev.lumora.client.gui.LumoraUi.ACCENT, LumoraUi.ACCENT);
    }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void lumora$handleRight(AbstractSliderButton self, PoseStack pose, int x, int y, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.blit(pose, x, y, u, v, w, h);
        }
    }
}
