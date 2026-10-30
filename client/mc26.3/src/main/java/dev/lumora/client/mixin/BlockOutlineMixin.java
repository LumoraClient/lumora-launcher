package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumora.core.extras.BlockOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora Block-Overlay: instead of the vanilla outline the box of {@link BlockOverlayModule#frame} (filled box and/or
 * outline, slide, fade, only the face looked at), submitted as custom geometry. The high-contrast pass (black
 * outline below) is skipped.
 */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
    @Inject(method = "submitHitOutline", at = @At("HEAD"), cancellable = true)
    private void lumora$overlay(PoseStack pose, SubmitNodeCollector collector, RenderType lines, BlockOutlineRenderState state,
                                int color, float width, boolean translucent, CallbackInfo ci) {
        BlockOverlayModule m = BlockOverlayModule.active();
        if (m == null) {
            return;
        }
        ci.cancel();
        VoxelShape shape = state.shape();
        if (lines == RenderTypes.secondaryBlockOutline() || shape.isEmpty()) {
            return; // high-contrast pass, or nothing to outline
        }
        Minecraft mc = Minecraft.getInstance();
        AABB b = shape.bounds().move(state.pos());
        int side = mc.hitResult instanceof BlockHitResult hit ? hit.getDirection().ordinal() : -1;
        BlockOverlayModule.Frame f = m.frame(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, side,
                mc.options.getCameraType().isFirstPerson());
        if (f == null) {
            return;
        }
        // the pose is already moved to the block (block position minus camera)
        BlockPos o = state.pos();
        float x1 = (float) (f.minX() - o.getX());
        float y1 = (float) (f.minY() - o.getY());
        float z1 = (float) (f.minZ() - o.getZ());
        float x2 = (float) (f.maxX() - o.getX());
        float y2 = (float) (f.maxY() - o.getY());
        float z2 = (float) (f.maxZ() - o.getZ());
        int outline = f.outline();
        int fill = f.fill();
        float w = m.lineWidth();
        if (fill != 0) {
            collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, vc) -> lumora$faces(p, vc, x1, y1, z1, x2, y2, z2, fill));
        }
        if (outline != 0) {
            collector.submitCustomGeometry(pose, lines, (p, vc) -> lumora$edges(p, vc, x1, y1, z1, x2, y2, z2, outline, w));
        }
    }

    /** The 12 edges of the box (zero-length edges of a flat face are left out). */
    @Unique
    private static void lumora$edges(PoseStack.Pose p, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int c, float w) {
        float[][] e = {
                {x1, y1, z1, x2, y1, z1}, {x1, y2, z1, x2, y2, z1}, {x1, y1, z2, x2, y1, z2}, {x1, y2, z2, x2, y2, z2},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x1, y1, z2, x1, y2, z2}, {x2, y1, z2, x2, y2, z2},
                {x1, y1, z1, x1, y1, z2}, {x2, y1, z1, x2, y1, z2}, {x1, y2, z1, x1, y2, z2}, {x2, y2, z1, x2, y2, z2}};
        for (float[] l : e) {
            Vector3f n = new Vector3f(l[3] - l[0], l[4] - l[1], l[5] - l[2]);
            if (n.lengthSquared() < 1e-12f) {
                continue;
            }
            n.normalize();
            vc.addVertex(p, l[0], l[1], l[2]).setColor(c).setNormal(p, n).setLineWidth(w);
            vc.addVertex(p, l[3], l[4], l[5]).setColor(c).setNormal(p, n).setLineWidth(w);
        }
    }

    /** The 6 faces of the box as quads (a flat box gives its face twice, seen from both sides). */
    @Unique
    private static void lumora$faces(PoseStack.Pose p, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
        float[][] q = {
                {x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2}, {x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1},
                {x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1}, {x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2},
                {x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1}, {x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2}};
        for (float[] f : q) {
            for (int i = 0; i < 12; i += 3) {
                vc.addVertex(p, f[i], f[i + 1], f[i + 2]).setColor(c);
            }
        }
    }
}
