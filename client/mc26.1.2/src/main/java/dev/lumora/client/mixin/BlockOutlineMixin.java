package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumora.core.extras.BlockOverlayModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora Block-Overlay: instead of the vanilla outline the box of {@link BlockOverlayModule#frame} (filled box and/or
 * outline, slide, fade, only the face looked at). The high-contrast pass (black outline below) is skipped.
 */
@Mixin(LevelRenderer.class)
public abstract class BlockOutlineMixin {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void lumora$overlay(PoseStack pose, VertexConsumer lines, double camX, double camY, double camZ,
                                BlockOutlineRenderState state, int color, float width, CallbackInfo ci) {
        BlockOverlayModule m = BlockOverlayModule.active();
        if (m == null) {
            return;
        }
        ci.cancel();
        VoxelShape shape = state.shape();
        if (color == 0xFF000000 || shape.isEmpty()) {
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
        float x1 = (float) (f.minX() - camX);
        float y1 = (float) (f.minY() - camY);
        float z1 = (float) (f.minZ() - camZ);
        float x2 = (float) (f.maxX() - camX);
        float y2 = (float) (f.maxY() - camY);
        float z2 = (float) (f.maxZ() - camZ);
        PoseStack.Pose p = pose.last();
        if (f.outline() != 0) {
            lumora$edges(p, lines, x1, y1, z1, x2, y2, z2, f.outline(), m.lineWidth());
        }
        if (f.fill() != 0) {
            lumora$faces(p, renderBuffers.bufferSource().getBuffer(RenderTypes.debugQuads()), x1, y1, z1, x2, y2, z2, f.fill());
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
