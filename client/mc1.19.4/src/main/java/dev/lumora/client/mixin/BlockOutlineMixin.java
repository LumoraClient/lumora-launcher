package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumora.core.extras.BlockOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora Block-Overlay: instead of the vanilla outline the box of {@link BlockOverlayModule#frame} (filled box and/or
 * outline, slide, fade, only the face looked at).
 */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void lumora$overlay(PoseStack pose, VertexConsumer lines, Entity entity, double camX, double camY, double camZ,
                                BlockPos pos, BlockState state, CallbackInfo ci) {
        BlockOverlayModule m = BlockOverlayModule.active();
        if (m == null) {
            return;
        }
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        VoxelShape shape = state.getShape(mc.level, pos, CollisionContext.of(entity));
        if (shape.isEmpty()) {
            return;
        }
        AABB b = shape.bounds().move(pos);
        int side = mc.hitResult instanceof BlockHitResult hit ? hit.getDirection().ordinal() : -1;
        BlockOverlayModule.Frame f = m.frame(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, side,
                mc.options.getCameraType().isFirstPerson());
        if (f == null) {
            return;
        }
        AABB box = new AABB(f.minX() - camX, f.minY() - camY, f.minZ() - camZ, f.maxX() - camX, f.maxY() - camY, f.maxZ() - camZ);
        int o = f.outline();
        if (o != 0) {
            LevelRenderer.renderLineBox(pose, lines, box, ((o >> 16) & 0xFF) / 255f, ((o >> 8) & 0xFF) / 255f, (o & 0xFF) / 255f, (o >>> 24) / 255f);
        }
        if (f.fill() != 0) {
            lumora$faces(pose, renderBuffers.bufferSource().getBuffer(RenderType.debugQuads()), box, f.fill());
        }
    }

    /** The 6 faces of the box as quads (a flat box gives its face twice, seen from both sides). */
    @Unique
    private static void lumora$faces(PoseStack pose, VertexConsumer vc, AABB b, int c) {
        float x1 = (float) b.minX;
        float y1 = (float) b.minY;
        float z1 = (float) b.minZ;
        float x2 = (float) b.maxX;
        float y2 = (float) b.maxY;
        float z2 = (float) b.maxZ;
        float[][] q = {
                {x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2}, {x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1},
                {x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2},
                {x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1}, {x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2}};
        PoseStack.Pose p = pose.last();
        float r = ((c >> 16) & 0xFF) / 255f;
        float g = ((c >> 8) & 0xFF) / 255f;
        float bl = (c & 0xFF) / 255f;
        float a = (c >>> 24) / 255f;
        for (float[] f : q) {
            for (int i = 0; i < 12; i += 3) {
                vc.vertex(p.pose(), f[i], f[i + 1], f[i + 2]).color(r, g, bl, a).endVertex();
            }
        }
    }
}
