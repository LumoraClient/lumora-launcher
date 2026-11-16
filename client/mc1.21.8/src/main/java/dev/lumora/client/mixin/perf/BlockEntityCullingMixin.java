package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import dev.lumora.core.perf.Cullable;
import dev.lumora.core.perf.OcclusionCuller;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Block entity culling: a block entity Minecraft would draw (its renderer's distance check passed) is skipped when the
 * occlusion culler found it completely hidden behind opaque blocks. Block entities drawn regardless of where they are
 * (beacon beams, end gateways, structure blocks: {@code shouldRenderOffScreen}) are never culled. The box covers the
 * block and generously around and above it (standing banners reach two blocks up).
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCullingMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    private boolean lumora$occlusion(BlockEntityRenderer renderer, BlockEntity blockEntity, Vec3 camera) {
        if (!renderer.shouldRender(blockEntity, camera)) {
            return false;
        }
        OcclusionCuller culler = Culling.active();
        if (culler == null || renderer.shouldRenderOffScreen()) {
            return true;
        }
        BlockPos p = blockEntity.getBlockPos();
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();
        return !culler.hidden((Cullable) blockEntity, x - 0.5, y - 0.5, z - 0.5, x + 1.5, y + 2.5, z + 1.5);
    }
}
