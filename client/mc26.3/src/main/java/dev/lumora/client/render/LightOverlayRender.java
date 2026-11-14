package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumora.core.extras.light.LightMesh;
import dev.lumora.core.extras.light.LightOverlayModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the finished chunk meshes of the module "Light Block Overlay" (one batch of lines and one of quads per frame).
 * Positions are made camera-relative per chunk in double precision; nothing is allocated per vertex.
 */
public final class LightOverlayRender {
    static final float LINE_WIDTH = 2f;

    private LightOverlayRender() {
    }

    /** Called where the level renderer submits the block outline (every frame, the pose is camera-relative). */
    public static void submit(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state) {
        LightOverlayModule m = LightOverlayModule.active();
        if (m == null || state.cameraRenderState == null || state.cameraRenderState.pos == null) {
            return;
        }
        LightMesh[] meshes = m.meshes();
        if (meshes.length == 0) {
            return;
        }
        Vec3 cam = state.cameraRenderState.pos;
        boolean lines = false;
        boolean quads = false;
        for (LightMesh mesh : meshes) {
            lines |= mesh.lineCount > 0;
            quads |= mesh.quadCount > 0;
        }
        double cx = cam.x;
        double cy = cam.y;
        double cz = cam.z;
        if (quads) {
            collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, vc) -> quads(p, vc, meshes, cx, cy, cz));
        }
        if (lines) {
            collector.submitCustomGeometry(pose, RenderTypes.lines(), (p, vc) -> lines(p, vc, meshes, cx, cy, cz));
        }
    }

    static void lines(PoseStack.Pose p, VertexConsumer vc, LightMesh[] meshes, double cx, double cy, double cz) {
        for (LightMesh mesh : meshes) {
            float ox = (float) (mesh.originX - cx);
            float oy = (float) -cy;
            float oz = (float) (mesh.originZ - cz);
            float[] l = mesh.lines;
            int[] c = mesh.lineColors;
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
                vc.addVertex(p, x1, y1, z1).setColor(c[i]).setNormal(p, nx, ny, nz).setLineWidth(LINE_WIDTH);
                vc.addVertex(p, x2, y2, z2).setColor(c[i]).setNormal(p, nx, ny, nz).setLineWidth(LINE_WIDTH);
            }
        }
    }

    static void quads(PoseStack.Pose p, VertexConsumer vc, LightMesh[] meshes, double cx, double cy, double cz) {
        for (LightMesh mesh : meshes) {
            float ox = (float) (mesh.originX - cx);
            float oy = (float) -cy;
            float oz = (float) (mesh.originZ - cz);
            float[] q = mesh.quads;
            int[] c = mesh.quadColors;
            for (int i = 0; i < mesh.quadCount; i++) {
                int k = i * 12;
                for (int v = 0; v < 12; v += 3) {
                    vc.addVertex(p, ox + q[k + v], oy + q[k + v + 1], oz + q[k + v + 2]).setColor(c[i]);
                }
            }
        }
    }
}
