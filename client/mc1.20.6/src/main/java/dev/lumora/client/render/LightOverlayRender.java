package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumora.core.extras.light.LightMesh;
import dev.lumora.core.extras.light.LightOverlayModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the finished chunk meshes of the module "Light Block Overlay" (one batch of lines and one of quads per frame).
 * Positions are made camera-relative per chunk in double precision; nothing is allocated per vertex.
 */
public final class LightOverlayRender {
    private LightOverlayRender() {
    }

    /** Called while the level renderer draws the world (every frame, the pose is camera-relative). */
    public static void draw(PoseStack pose, MultiBufferSource buffers, Vec3 cam) {
        LightOverlayModule m = LightOverlayModule.active();
        if (m == null || cam == null) {
            return;
        }
        LightMesh[] meshes = m.meshes();
        if (meshes.length == 0) {
            return;
        }
        PoseStack.Pose p = pose.last();
        boolean quads = false;
        boolean lines = false;
        for (LightMesh mesh : meshes) {
            quads |= mesh.quadCount > 0;
            lines |= mesh.lineCount > 0;
        }
        if (quads) {
            quads(p.pose(), buffers.getBuffer(RenderType.debugQuads()), meshes, cam.x, cam.y, cam.z);
            if (buffers instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(RenderType.debugQuads());
            }
        }
        if (lines) {
            lines(p.pose(), p, buffers.getBuffer(RenderType.lines()), meshes, cam.x, cam.y, cam.z);
            if (buffers instanceof MultiBufferSource.BufferSource bs) {
                bs.endBatch(RenderType.lines());
            }
        }
    }

    static void lines(Matrix4f m, PoseStack.Pose n, VertexConsumer vc, LightMesh[] meshes, double cx, double cy, double cz) {
        for (LightMesh mesh : meshes) {
            float ox = (float) (mesh.originX - cx);
            float oy = (float) -cy;
            float oz = (float) (mesh.originZ - cz);
            float[] l = mesh.lines;
            int[] col = mesh.lineColors;
            for (int i = 0; i < mesh.lineCount; i++) {
                int k = i * 6;
                float x1 = ox + l[k];
                float y1 = oy + l[k + 1];
                float z1 = oz + l[k + 2];
                float x2 = ox + l[k + 3];
                float y2 = oy + l[k + 4];
                float z2 = oz + l[k + 5];
                float nx = x2 - x1;
                float ny = y2 - y1;
                float nz = z2 - z1;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-6f) {
                    continue;
                }
                nx /= len;
                ny /= len;
                nz /= len;
                int c = col[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int a = c >>> 24;
                vc.vertex(m, x1, y1, z1).color(r, g, b, a).normal(n, nx, ny, nz).endVertex();
                vc.vertex(m, x2, y2, z2).color(r, g, b, a).normal(n, nx, ny, nz).endVertex();
            }
        }
    }

    static void quads(Matrix4f m, VertexConsumer vc, LightMesh[] meshes, double cx, double cy, double cz) {
        for (LightMesh mesh : meshes) {
            float ox = (float) (mesh.originX - cx);
            float oy = (float) -cy;
            float oz = (float) (mesh.originZ - cz);
            float[] q = mesh.quads;
            int[] col = mesh.quadColors;
            for (int i = 0; i < mesh.quadCount; i++) {
                int c = col[i];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int a = c >>> 24;
                int k = i * 12;
                for (int v = 0; v < 12; v += 3) {
                    vc.vertex(m, ox + q[k + v], oy + q[k + v + 1], oz + q[k + v + 2]).color(r, g, b, a).endVertex();
                }
            }
        }
    }
}
