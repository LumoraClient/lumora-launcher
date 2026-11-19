package dev.lumora.client.mixin;

import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zoom, "Show hand in first person" off: the hand and the held item are not drawn while zoomed
 * ({@link dev.lumora.client.zoom.LumoraZoom#hidesHand}).
 */
@Mixin(HeldItemRenderer.class)
public abstract class ZoomHandMixin {
    @Inject(method = "renderArmHoldingItem", at = @At("HEAD"), cancellable = true)
    private void lumora$zoomHand(float tickDelta, CallbackInfo ci) {
        if (dev.lumora.client.zoom.LumoraZoom.hidesHand()) {
            ci.cancel();
        }
    }
}
