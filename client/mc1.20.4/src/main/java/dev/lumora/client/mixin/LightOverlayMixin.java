package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.LightOverlayRender;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Module "Light Block Overlay": its marks are drawn before the debug renderers, every frame. */
@Mixin(LevelRenderer.class)
public abstract class LightOverlayMixin {
    @Inject(method = "renderDebug", at = @At("HEAD"))
    private void lumora$lightOverlay(PoseStack pose, MultiBufferSource buffers, Camera camera, CallbackInfo ci) {
        LightOverlayRender.draw(pose, buffers, camera.getPosition());
    }
}
