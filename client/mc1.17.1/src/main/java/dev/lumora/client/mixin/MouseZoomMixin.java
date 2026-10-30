package dev.lumora.client.mixin;

import dev.lumora.client.zoom.LumoraZoom;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: lower mouse sensitivity while zoomed, scroll wheel changes the zoom factor instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseZoomMixin {
    // Minecraft 1.17.1: the sensitivity is a plain double field (OptionInstance arrived in 1.19)
    @Redirect(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;sensitivity:D", ordinal = 0))
    private double lumora$zoomSensitivity(Options options) {
        return LumoraZoom.sensitivity(options.sensitivity);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void lumora$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (LumoraZoom.scroll(yOffset)) {
            ci.cancel();
        }
    }
}
