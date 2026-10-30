package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.18.2 draws the background of vanilla buttons (and the slider track) in AbstractWidget#renderButton as two
 * halves from textures/gui/widgets.png. The first blit becomes one Lumora card over the whole widget, the second is
 * dropped; label, layout and behaviour stay vanilla. Off together with the "Lumora Theme" resource pack.
 */
@Mixin(AbstractWidget.class)
public abstract class ButtonSkinMixin {
    private static final String BLIT = "Lnet/minecraft/client/gui/components/AbstractWidget;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIII)V";

    @Redirect(method = "renderButton", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void lumora$leftHalf(AbstractWidget self, PoseStack pose, int x, int y, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.blit(pose, x, y, u, v, w, h);
            return;
        }
        GuiGraphics g = GuiGraphics.of(pose);
        if (self instanceof AbstractSliderButton) {
            boolean hover = self.active && self.isHoveredOrFocused();
            LumoraUi.card(g, self.x, self.y, self.getWidth(), self.getHeight(), hover ? 0xE8241D31 : 0xDC13101A, LumoraUi.BORDER);
        } else {
            LumoraUi.vanillaButton(g, self.x, self.y, self.getWidth(), self.getHeight(), self.active, self.active && self.isHoveredOrFocused());
        }
    }

    @Redirect(method = "renderButton", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void lumora$rightHalf(AbstractWidget self, PoseStack pose, int x, int y, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.blit(pose, x, y, u, v, w, h);
        }
    }
}
