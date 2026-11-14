package dev.lumora.client.mixin;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.gui.LumoraUi;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menus outside a world draw the Lumora landscape instead of the vanilla dirt background. Up to Minecraft 1.20.4 (here 1.19.4) there
 * is no panorama behind menus (Screen#renderPanorama arrived in 1.20.5) - they tile the dirt texture in
 * {@code renderDirtBackground}; the landscape is darkened like the blurred 1.20.5+ menus so text stays readable.
 * Disable with "menuBackground": false in lumora-client.json.
 */
@Mixin(Screen.class)
public abstract class ScreenPanoramaMixin {
    /** Dims the landscape behind menu text (the newer versions blur it and lay the menu background on top). */
    private static final int DIM = 0x990C1016;

    @Shadow
    public int width;
    @Shadow
    public int height;

    @Inject(method = "renderDirtBackground", at = @At("HEAD"), cancellable = true)
    private void lumora$background(PoseStack pose, CallbackInfo ci) {
        GuiGraphics g = GuiGraphics.of(pose);
        if (LumoraClient.config() != null && LumoraClient.config().menuBackground) {
            LumoraUi.background(g, width, height, LumoraUi.BACKGROUND_MENU);
            g.fill(0, 0, width, height, DIM);
            ci.cancel();
        }
    }
}
