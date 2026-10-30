package dev.lumora.client.mixin;

import dev.lumora.client.zoom.LumoraZoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: narrows the world field of view (the hand keeps its own HUD field of view). */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void lumora$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(LumoraZoom.apply(cir.getReturnValue(), partialTick));
    }
}
