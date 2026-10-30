package dev.lumora.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.gui.LumoraLoading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/**
 * Lumora loading screen: Minecraft's brand background color becomes the Lumora background, the Mojang Studios logo is
 * not drawn, and the progress bar is replaced by {@link LumoraLoading}. Fade-in/out and the reload logic stay vanilla.
 * Can be switched off with "customLoadingScreen": false.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    private float currentProgress;

    private static boolean lumora$on() {
        return LumoraClient.config() == null || LumoraClient.config().customLoadingScreen;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int lumora$background(IntSupplier brand) {
        return lumora$on() ? LumoraLoading.BACKGROUND : brand.getAsInt();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIFFIIIIIII)V"))
    private void lumora$logo(GuiGraphics g, RenderPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v,
                             int w, int h, int uw, int vh, int tw, int th, int color) {
        if (!lumora$on()) {
            g.blit(pipeline, texture, x, y, u, v, w, h, uw, vh, tw, th, color);
        }
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
    private void lumora$progress(GuiGraphics g, int x0, int y0, int x1, int y1, float alpha, CallbackInfo ci) {
        if (lumora$on()) {
            LumoraLoading.draw(g, x0, y0, x1, y1, alpha, currentProgress);
            ci.cancel();
        }
    }
}
