package dev.lumora.client.mixin;

import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.20.1 draws vanilla buttons from textures/gui/widgets.png (the sprite atlas the "Lumora Theme" pack
 * re-skins arrived in 1.20.2). The nine-slice blit of the button background becomes a Lumora card instead - label,
 * layout and behaviour stay vanilla. Off together with the "Lumora Theme" resource pack (Options > Resource Packs).
 */
@Mixin(AbstractButton.class)
public abstract class ButtonSkinMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitNineSliced(Lnet/minecraft/resources/ResourceLocation;IIIIIIIIII)V"))
    private void lumora$skin(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h, int border, int corner,
                             int texW, int texH, int u, int v) {
        if (!LumoraUi.vanillaSkin()) {
            g.blitNineSliced(texture, x, y, w, h, border, corner, texW, texH, u, v);
            return;
        }
        AbstractButton self = (AbstractButton) (Object) this;
        LumoraUi.vanillaButton(g, x, y, w, h, self.active, self.active && self.isHoveredOrFocused());
    }
}
