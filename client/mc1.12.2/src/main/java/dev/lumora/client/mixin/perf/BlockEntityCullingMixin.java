package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import dev.lumora.core.perf.Cullable;
import dev.lumora.core.perf.OcclusionCuller;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block entity culling: a block entity the occlusion culler found completely hidden behind opaque blocks is not drawn.
 * Block entities drawn regardless of where they are (the beacon beam: {@code isGlobalRenderer}) are never culled. The box covers the
 * block and generously around and above it (standing banners reach two blocks up).
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullingMixin {
    @Shadow
    public abstract <T extends BlockEntity> BlockEntityRenderer<T> getRenderer(BlockEntity blockEntity);

    @Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;FI)V", at = @At("HEAD"), cancellable = true)
    private void lumora$occlusion(BlockEntity blockEntity, float tickDelta, int breakStage, CallbackInfo ci) {
        OcclusionCuller culler = Culling.active();
        if (culler == null) {
            return;
        }
        BlockEntityRenderer<BlockEntity> renderer = getRenderer(blockEntity);
        if (renderer == null || renderer.method_12410(blockEntity)) {
            return;
        }
        BlockPos p = blockEntity.getPos();
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();
        if (culler.hidden((Cullable) blockEntity, x - 0.5, y - 0.5, z - 0.5, x + 1.5, y + 2.5, z + 1.5)) {
            ci.cancel();
        }
    }
}
