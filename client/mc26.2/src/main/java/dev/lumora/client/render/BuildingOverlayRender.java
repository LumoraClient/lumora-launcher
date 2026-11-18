package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.lumora.client.api.LumoraRuntime;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Outlines that addons put around blocks (Lumora API 1.11 {@code Building#highlight}, e.g. the start and end point of
 * the hotbar pool gradient): one batch of lines per frame, submitted with the block outline. Nothing happens while
 * no addon highlights a block.
 */
public final class BuildingOverlayRender {
    static final float LINE_WIDTH = 2.5f;

    private BuildingOverlayRender() {
    }

    public static void submit(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state) {
        var core = LumoraRuntime.core();
        if (core == null || state.cameraRenderState == null || state.cameraRenderState.pos == null) {
            return;
        }
        List<int[]> boxes = core.building().highlights();
        if (boxes.isEmpty()) {
            return;
        }
        Vec3 cam = state.cameraRenderState.pos;
        List<int[]> copy = List.copyOf(boxes);
        double cx = cam.x;
        double cy = cam.y;
        double cz = cam.z;
        collector.submitCustomGeometry(pose, RenderTypes.lines(), (p, vc) -> boxes(p, vc, copy, cx, cy, cz));
    }

    static void boxes(PoseStack.Pose p, VertexConsumer vc, List<int[]> boxes, double cx, double cy, double cz) {
        for (int[] b : boxes) {
            float x0 = (float) (b[0] - cx) - 0.003f;
            float y0 = (float) (b[1] - cy) - 0.003f;
            float z0 = (float) (b[2] - cz) - 0.003f;
            float x1 = x0 + 1.006f;
            float y1 = y0 + 1.006f;
            float z1 = z0 + 1.006f;
            int c = b[3];
            line(p, vc, x0, y0, z0, x1, y0, z0, c);
            line(p, vc, x0, y0, z1, x1, y0, z1, c);
            line(p, vc, x0, y1, z0, x1, y1, z0, c);
            line(p, vc, x0, y1, z1, x1, y1, z1, c);
            line(p, vc, x0, y0, z0, x0, y1, z0, c);
            line(p, vc, x1, y0, z0, x1, y1, z0, c);
            line(p, vc, x0, y0, z1, x0, y1, z1, c);
            line(p, vc, x1, y0, z1, x1, y1, z1, c);
            line(p, vc, x0, y0, z0, x0, y0, z1, c);
            line(p, vc, x1, y0, z0, x1, y0, z1, c);
            line(p, vc, x0, y1, z0, x0, y1, z1, c);
            line(p, vc, x1, y1, z0, x1, y1, z1, c);
        }
    }

    private static void line(PoseStack.Pose p, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(p, x1, y1, z1).setColor(c).setNormal(p, nx, ny, nz).setLineWidth(LINE_WIDTH);
        vc.addVertex(p, x2, y2, z2).setColor(c).setNormal(p, nx, ny, nz).setLineWidth(LINE_WIDTH);
    }
}
