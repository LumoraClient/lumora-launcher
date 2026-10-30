package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.api.McHudGraphics;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.core.extras.ButtonStyleModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora button design (1.16.5 - 1.18.2): AbstractWidget#renderButton draws plain buttons; buttons with their own
 * drawing (checkboxes, image buttons) override it and stay vanilla.
 */
@Mixin(AbstractWidget.class)
public abstract class ButtonStyleMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Inject(method = "renderButton", at = @At("HEAD"), cancellable = true)
    private void lumora$style(PoseStack pose, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ButtonStyleModule style = ButtonStyleModule.styled();
        if (style == null || !((Object) this instanceof AbstractButton)) {
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        boolean active = self.active;
        int w = self.getWidth();
        int h = self.getHeight();
        style.drawBackground(LUMORA_G.with(GuiGraphics.of(pose)), self, self.x, self.y, w, h, self.isHoveredOrFocused(), active);
        GuiComponent.drawCenteredString(pose, Minecraft.getInstance().font, self.getMessage(), self.x + w / 2, self.y + (h - 8) / 2,
                ButtonStyleModule.textColor(active));
        ci.cancel();
    }
}
