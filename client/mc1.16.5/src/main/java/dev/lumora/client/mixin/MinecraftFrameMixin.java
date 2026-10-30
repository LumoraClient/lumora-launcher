package dev.lumora.client.mixin;

import dev.lumora.client.perf.FrameStats;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the wall-clock time between two frames. Minecraft#runTick(boolean) runs once per rendered frame
 * regardless of the renderer (vanilla OpenGL or Sodium), so the measurement is comparable
 * between both profiles. The injection is read-only and does not alter game behaviour.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void lumora$onFrame(boolean advanceGameTime, CallbackInfo ci) {
        FrameStats.INSTANCE.onFrame(System.nanoTime());
    }
}
