package dev.lumora.client.mixin;

import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionSliderWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.8.9 draws vanilla buttons (and the slider track) in ButtonWidget#render as two halves from
 * textures/gui/widgets.png. The first blit becomes one Lumora card over the whole button, the second is dropped; label,
 * layout and behaviour stay vanilla. Off with "vanillaSkin": false in lumora-client.json.
 */
@Mixin(ButtonWidget.class)
public abstract class ButtonSkinMixin {
    private static final String BLIT = "Lnet/minecraft/client/gui/widget/ButtonWidget;drawTexture(IIIIII)V";

    @Shadow
    protected int width;
    @Shadow
    protected int height;
    @Shadow
    public int x;
    @Shadow
    public int y;
    @Shadow
    public boolean active;
    @Shadow
    protected boolean hovered;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
    private void lumora$leftHalf(ButtonWidget self, int bx, int by, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.drawTexture(bx, by, u, v, w, h);
            return;
        }
        GuiGraphics g = GuiGraphics.create();
        if ((Object) this instanceof SliderWidget || (Object) this instanceof OptionSliderWidget) {
            LumoraUi.card(g, x, y, width, height, hovered ? 0xE81D2431 : 0xDC10141A, LumoraUi.BORDER);
        } else {
            LumoraUi.vanillaButton(g, x, y, width, height, active, active && hovered);
        }
        // the texture stays bound for the vanilla label shadow etc.; fills leave the colour white again
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
    private void lumora$rightHalf(ButtonWidget self, int bx, int by, int u, int v, int w, int h) {
        if (!LumoraUi.vanillaSkin()) {
            self.drawTexture(bx, by, u, v, w, h);
        }
    }
}
