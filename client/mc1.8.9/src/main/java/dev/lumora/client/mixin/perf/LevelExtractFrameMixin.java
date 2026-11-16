package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import dev.lumora.client.perf.FrameCounter;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Start of the entity pass of a frame: hands the last frame's entities to the culler and counts the frame. */
@Mixin(WorldRenderer.class)
public abstract class LevelExtractFrameMixin {
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void lumora$frame(Entity camera, CameraView view, float tickDelta, CallbackInfo ci) {
        FrameCounter.next();
        Culling.frame(camera, tickDelta);
    }
}
