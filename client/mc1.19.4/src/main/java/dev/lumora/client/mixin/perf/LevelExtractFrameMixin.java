package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import dev.lumora.client.perf.FrameCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Start of the world part of a frame (the camera is already set up): hands the last frame's entities to the culler and
 * counts the frame.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelExtractFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void lumora$frame(CallbackInfo ci) {
        FrameCounter.next();
        Vec3 p = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Culling.frame(p.x, p.y, p.z);
    }
}
