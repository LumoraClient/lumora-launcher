package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a screen built its widgets (first open and every resize) and after it was drawn. */
@Mixin(Screen.class)
public abstract class ScreenHooksMixin {
    @Inject(method = "init(II)V", at = @At("TAIL"))
    private void lumora$afterInit(int width, int height, CallbackInfo ci) {
        Hooks.fireScreenInit((Screen) (Object) this, width, height);
    }

    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("TAIL"))
    private void lumora$afterExtract(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hooks.fireScreenExtract((Screen) (Object) this, g, mouseX, mouseY, delta);
    }
}
