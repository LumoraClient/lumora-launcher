package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A block changed: the culler reads the opaque blocks of its section again. */
@Mixin(WorldRenderer.class)
public abstract class SectionDirtyMixin {
    @Inject(method = "onBlockUpdate", at = @At("HEAD"))
    private void lumora$dirty(BlockPos pos, CallbackInfo ci) {
        Culling.invalidate(pos.getX(), pos.getY(), pos.getZ());
    }
}
