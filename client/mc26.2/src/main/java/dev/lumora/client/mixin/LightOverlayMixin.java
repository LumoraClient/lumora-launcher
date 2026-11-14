package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.LightOverlayRender;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Module "Light Block Overlay": its marks are submitted together with the block outline, every frame. */
@Mixin(LevelRenderer.class)
public abstract class LightOverlayMixin {
    @Inject(method = "submitBlockOutline", at = @At("HEAD"))
    private void lumora$lightOverlay(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        LightOverlayRender.submit(pose, collector, state);
    }
}
