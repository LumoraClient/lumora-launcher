package dev.lumora.client.mixin;

import dev.lumora.client.api.McHudGraphics;
import dev.lumora.client.compat.GuiGraphics;
import dev.lumora.core.extras.ButtonStyleModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora button design (legacy): background in the chosen style and the centred label; mouseDragged still runs, so
 * option sliders keep drawing their handle.
 */
@Mixin(ButtonWidget.class)
public abstract class ButtonStyleMixin {
    @Unique
    private static final McHudGraphics LUMORA_G = new McHudGraphics();

    @Shadow
    public int x;
    @Shadow
    public int y;
    @Shadow
    protected int width;
    @Shadow
    protected int height;
    @Shadow
    public String message;
    @Shadow
    public boolean active;
    @Shadow
    public boolean visible;
    @Shadow
    protected boolean hovered;

    @Shadow
    protected abstract void mouseDragged(MinecraftClient client, int mouseX, int mouseY);

    @Inject(method = "method_891", at = @At("HEAD"), cancellable = true)
    private void lumora$style(MinecraftClient client, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        ButtonStyleModule style = ButtonStyleModule.styled();
        if (style == null || !visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        McHudGraphics g = LUMORA_G.with(GuiGraphics.create());
        style.drawBackground(g, this, x, y, width, height, hovered, active);
        mouseDragged(client, mouseX, mouseY);
        g.centeredText(message, x + width / 2, y + (height - 8) / 2, ButtonStyleModule.textColor(active), true);
        ci.cancel();
    }
}
