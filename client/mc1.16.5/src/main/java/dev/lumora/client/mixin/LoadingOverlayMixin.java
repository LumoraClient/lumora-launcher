package dev.lumora.client.mixin;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.gui.LumoraLoading;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * Lumora loading screen: Minecraft's brand background color becomes the Lumora background, the Mojang Studios logo is
 * not drawn, and the progress bar is replaced by {@link LumoraLoading}. Fade-in/out and the reload logic stay vanilla.
 * Can be switched off with "customLoadingScreen": false.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    private float currentProgress;

    @Shadow
    @Final
    private static int BRAND_BACKGROUND;
    @Shadow
    @Final
    private static int BRAND_BACKGROUND_NO_ALPHA;

    private static boolean lumora$on() {
        return LumoraClient.config() == null || LumoraClient.config().customLoadingScreen;
    }

    // Minecraft 1.16.5: the brand colour is two constants (with and without alpha) instead of an IntSupplier
    @Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;BRAND_BACKGROUND:I"))
    private int lumora$background() {
        return lumora$on() ? LumoraLoading.BACKGROUND : BRAND_BACKGROUND;
    }

    @Redirect(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;BRAND_BACKGROUND_NO_ALPHA:I"))
    private int lumora$backgroundRgb() {
        return lumora$on() ? LumoraLoading.BACKGROUND & 0xFFFFFF : BRAND_BACKGROUND_NO_ALPHA;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;blit(Lcom/mojang/blaze3d/vertex/PoseStack;IIIIFFIIII)V"))
    private void lumora$logo(PoseStack pose, int x, int y, int w, int h, float u, float v,
                             int uw, int vh, int tw, int th) {
        if (!lumora$on()) {
            GuiComponent.blit(pose, x, y, w, h, u, v, uw, vh, tw, th);
        }
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
    private void lumora$progress(PoseStack pose, int x0, int y0, int x1, int y1, float alpha, CallbackInfo ci) {
        if (lumora$on()) {
            LumoraLoading.draw(GuiGraphics.of(pose), x0, y0, x1, y1, alpha, currentProgress);
            ci.cancel();
        }
    }
}
