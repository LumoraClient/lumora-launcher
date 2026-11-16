package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A block changed (1.12.2 notifyBlockUpdate): the culler reads the opaque blocks of its section again. */
@Mixin(WorldRenderer.class)
public abstract class SectionDirtyMixin {
    @Inject(method = "method_11493", at = @At("HEAD"))
    private void lumora$dirty(World world, BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        Culling.invalidate(pos.getX(), pos.getY(), pos.getZ());
    }
}
