package dev.lumora.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;
import dev.lumora.core.extras.light.LightMesh;
import dev.lumora.core.extras.light.LightOverlayModule;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the finished chunk meshes of the module "Light Block Overlay" right away (one draw of quads, one of lines per
 * frame). Positions are made camera-relative per chunk in double precision; nothing is allocated per vertex.
 */
public final class LightOverlayRender {
    private LightOverlayRender() {
    }

    /** Called while the level renderer draws the world (every frame, the pose holds the camera rotation). */
    public static void draw(PoseStack pose, Vec3 cam) {
        LightOverlayModule m = LightOverlayModule.active();
        if (m == null || cam == null) {
            return;
        }
        LightMesh[] meshes = m.meshes();
        if (meshes.length == 0) {
            return;
        }
        Matrix4f mat = pose.last().pose();
        boolean quads = false;
        boolean lines = false;
        for (LightMesh mesh : meshes) {
            quads |= mesh.quadCount > 0;
            lines |= mesh.lineCount > 0;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        BufferBuilder vc = Tesselator.getInstance().getBuilder();
        if (quads) {
            RenderSystem.depthMask(false);
            vc.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (LightMesh mesh : meshes) {
                float ox = (float) (mesh.originX - cam.x);
                float oy = (float) -cam.y;
                float oz = (float) (mesh.originZ - cam.z);
                float[] q = mesh.quads;
                int[] col = mesh.quadColors;
                for (int i = 0; i < mesh.quadCount; i++) {
                    int c = col[i];
                    int k = i * 12;
                    for (int v = 0; v < 12; v += 3) {
                        vc.vertex(mat, ox + q[k + v], oy + q[k + v + 1], oz + q[k + v + 2])
                                .color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, c >>> 24).endVertex();
                    }
                }
            }
            Tesselator.getInstance().end();
            RenderSystem.depthMask(true);
        }
        if (lines) {
            RenderSystem.lineWidth(2f);
            vc.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            for (LightMesh mesh : meshes) {
                float ox = (float) (mesh.originX - cam.x);
                float oy = (float) -cam.y;
                float oz = (float) (mesh.originZ - cam.z);
                float[] l = mesh.lines;
                int[] col = mesh.lineColors;
                for (int i = 0; i < mesh.lineCount; i++) {
                    int c = col[i];
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;
                    int k = i * 6;
                    vc.vertex(mat, ox + l[k], oy + l[k + 1], oz + l[k + 2]).color(r, g, b, 255).endVertex();
                    vc.vertex(mat, ox + l[k + 3], oy + l[k + 4], oz + l[k + 5]).color(r, g, b, 255).endVertex();
                }
            }
            Tesselator.getInstance().end();
            RenderSystem.lineWidth(1f);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
    }
}
