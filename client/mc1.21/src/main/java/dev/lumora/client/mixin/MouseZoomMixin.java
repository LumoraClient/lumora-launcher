package dev.lumora.client.mixin;

import dev.lumora.client.zoom.LumoraZoom;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: lower mouse sensitivity while zoomed, scroll wheel changes the zoom factor instead of the hotbar slot. */
@Mixin(MouseHandler.class)
public abstract class MouseZoomMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object lumora$zoomSensitivity(OptionInstance<?> sensitivity) {
        Object value = sensitivity.get();
        return value instanceof Double d ? LumoraZoom.sensitivity(d) : value;
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void lumora$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (LumoraZoom.scroll(yOffset)) {
            ci.cancel();
        }
    }
}
