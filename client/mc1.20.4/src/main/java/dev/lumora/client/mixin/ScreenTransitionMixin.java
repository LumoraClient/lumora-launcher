package dev.lumora.client.mixin;

import dev.lumora.client.gui.ScreenTransition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the fading previous menu on top of the current screen (see {@link ScreenTransition}). */
@Mixin(Screen.class)
public abstract class ScreenTransitionMixin {
    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void lumora$transition(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ScreenTransition.draw(g, (Screen) (Object) this);
    }
}
