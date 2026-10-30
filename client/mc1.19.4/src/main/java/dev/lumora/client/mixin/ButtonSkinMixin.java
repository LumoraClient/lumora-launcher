package dev.lumora.client.mixin;

import dev.lumora.client.gui.LumoraUi;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.GuiComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 1.19.4 draws vanilla buttons from textures/gui/widgets.png (the sprite atlas the "Lumora Theme" pack
 * re-skins arrived in 1.20.2). The nine-slice blit of the button background becomes a Lumora card instead - label,
 * layout and behaviour stay vanilla. Off together with the "Lumora Theme" resource pack (Options > Resource Packs).
 */
@Mixin(AbstractButton.class)
public abstract class ButtonSkinMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/AbstractButton;blitNineSliced(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIIIIIII)V"))
    private void lumora$skin(PoseStack pose, int x, int y, int w, int h, int border, int corner,
                             int texW, int texH, int u, int v) {
        if (!LumoraUi.vanillaSkin()) {
            GuiComponent.blitNineSliced(pose, x, y, w, h, border, corner, texW, texH, u, v);
            return;
        }
        AbstractButton self = (AbstractButton) (Object) this;
        LumoraUi.vanillaButton(GuiGraphics.of(pose), x, y, w, h, self.active, self.active && self.isHoveredOrFocused());
    }
}
