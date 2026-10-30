package dev.lumora.client.mixin;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.gui.LumoraShaders;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menus outside a world draw the Lumora landscape instead of the vanilla panorama (Minecraft still blurs it
 * and lays the themed menu background on top). Disable with "menuBackground": false in lumora-client.json.
 */
@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {
    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true)
    private void lumora$background(GuiGraphics g, float delta, CallbackInfo ci) {
        if (LumoraClient.config() != null && LumoraClient.config().menuBackground) {
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
            LumoraShaders.sky(g, width, height, 1f);
            ci.cancel();
        }
    }
}
