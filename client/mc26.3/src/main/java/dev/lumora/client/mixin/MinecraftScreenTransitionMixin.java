package dev.lumora.client.mixin;

import dev.lumora.client.gui.ScreenTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the outgoing menu frame for the cross-fade (see {@link ScreenTransition}). Since 26.2 the current
 * screen lives in {@link Gui} ({@code Minecraft.gui.setScreen}), not in Minecraft itself.
 */
@Mixin(Gui.class)
public abstract class MinecraftScreenTransitionMixin {
    @Shadow
    private Screen screen;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void lumora$captureTransition(Screen next, CallbackInfo ci) {
        ScreenTransition.capture(minecraft, screen, next);
    }
}
