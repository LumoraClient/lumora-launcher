package dev.lumora.client.mixin;

import dev.lumora.client.api.McHudGraphics;
import dev.lumora.core.extras.ButtonStyleModule;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora button design: background in the chosen style, label drawn by Minecraft's own renderString. */
@Mixin(AbstractButton.class)
public abstract class ButtonStyleMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void lumora$style(PoseStack pose, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ButtonStyleModule style = ButtonStyleModule.styled();
        if (style == null) {
            return;
        }
        AbstractButton self = (AbstractButton) (Object) this;
        boolean active = self.active;
        style.drawBackground(LUMORA_G.with(GuiGraphics.of(pose)), self, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                self.isHoveredOrFocused(), active);
        self.renderString(pose, Minecraft.getInstance().font, ButtonStyleModule.textColor(active));
        ci.cancel();
    }
}
