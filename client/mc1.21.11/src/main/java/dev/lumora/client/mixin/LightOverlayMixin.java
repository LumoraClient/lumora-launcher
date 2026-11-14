package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.LightOverlayRender;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Module "Light Block Overlay": its marks are drawn where the block outline is drawn (opaque pass), every frame. */
@Mixin(LevelRenderer.class)
public abstract class LightOverlayMixin {
    @Inject(method = "renderBlockOutline", at = @At("HEAD"))
    private void lumora$lightOverlay(MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucent,
                                     LevelRenderState state, CallbackInfo ci) {
        if (!translucent && state.cameraRenderState != null) {
            LightOverlayRender.draw(pose, buffers, state.cameraRenderState.pos);
        }
    }
}
