package dev.lumora.client.mixin.perf;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.perf.Culling;
import dev.lumora.core.perf.Cullable;
import dev.lumora.core.perf.OcclusionCuller;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block entity culling (1.16.5, where the distance check is done by the dispatcher itself): a block entity the
 * occlusion culler found completely hidden behind opaque blocks is not drawn. Block entities drawn regardless of where
 * they are (beacon beams, end gateways, structure blocks: {@code shouldRenderOffScreen}) are never culled. The box
 * covers the block and generously around and above it (standing banners reach two blocks up).
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullingMixin {
    @Shadow
    public abstract <E extends BlockEntity> BlockEntityRenderer<E> getRenderer(E blockEntity);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void lumora$occlusion(E blockEntity, float partialTicks, PoseStack pose, MultiBufferSource buffers,
                                                          CallbackInfo ci) {
        OcclusionCuller culler = Culling.active();
        if (culler == null) {
            return;
        }
        BlockEntityRenderer<E> renderer = getRenderer(blockEntity);
        if (renderer == null || renderer.shouldRenderOffScreen(blockEntity)) {
            return;
        }
        BlockPos p = blockEntity.getBlockPos();
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();
        if (culler.hidden((Cullable) blockEntity, x - 0.5, y - 0.5, z - 0.5, x + 1.5, y + 2.5, z + 1.5)) {
            ci.cancel();
        }
    }
}
