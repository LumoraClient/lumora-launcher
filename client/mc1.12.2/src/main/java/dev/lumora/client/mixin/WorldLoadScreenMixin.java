package dev.lumora.client.mixin;

import dev.lumora.client.api.McWorldLoad;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora's loading screen for singleplayer worlds: Minecraft's terrain screen keeps running underneath it and does
 * the work; any error keeps Minecraft's own screen.
 */
@Mixin(MinecraftClient.class)
public abstract class WorldLoadScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void lumora$worldLoad(Screen screen, CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (McWorldLoad.intercept(mc.currentScreen, screen, mc::setScreen)) {
            ci.cancel();
        }
    }
}
