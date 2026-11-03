package dev.lumora.client.mixin;

import dev.lumora.client.api.McWorldLoad;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora's loading screen for singleplayer worlds where Minecraft opens one of its loading screens (reading the
 * world, data packs, preparing a new world, server start, spawn area, terrain). Minecraft's screen keeps running
 * underneath and does the work; any error keeps Minecraft's own screen.
 */
@Mixin(net.minecraft.client.gui.Gui.class)
public abstract class WorldLoadScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void lumora$worldLoad(Screen screen, CallbackInfo ci) {
        net.minecraft.client.gui.Gui gui = (net.minecraft.client.gui.Gui) (Object) this;
        if (McWorldLoad.intercept(gui.screen(), screen, gui::setScreen)) {
            ci.cancel();
        }
    }
}
