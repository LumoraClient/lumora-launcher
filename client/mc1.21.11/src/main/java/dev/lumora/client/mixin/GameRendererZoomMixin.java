package dev.lumora.client.mixin;

import dev.lumora.client.zoom.LumoraZoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: narrows the world field of view (the hand keeps its own field of view, useFovSetting == false). */
@Mixin(GameRenderer.class)
public abstract class GameRendererZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lumora$zoom(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        if (useFovSetting) {
            cir.setReturnValue(LumoraZoom.apply(cir.getReturnValue(), partialTick));
        }
    }
}
