package dev.lumora.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.core.extras.light.LightMesh;
import dev.lumora.core.extras.light.LightOverlayModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Draws the finished chunk meshes of the module "Light Block Overlay" right away (one draw of quads, one of lines per
 * frame). Positions are made camera-relative per chunk in double precision; nothing is allocated per vertex.
 */
public final class LightOverlayRender {
    private LightOverlayRender() {
    }

    /** Called while the world is drawn (every frame, the GL matrix holds the camera rotation). */
    public static void draw(float tickDelta) {
        LightOverlayModule m = LightOverlayModule.active();
        if (m == null) {
            return;
        }
        LightMesh[] meshes = m.meshes();
        Entity cam = MinecraftClient.getInstance().getCameraEntity();
        if (meshes.length == 0 || cam == null) {
            return;
        }
        double cx = cam.prevTickX + (cam.x - cam.prevTickX) * tickDelta;
        double cy = cam.prevTickY + (cam.y - cam.prevTickY) * tickDelta;
        double cz = cam.prevTickZ + (cam.z - cam.prevTickZ) * tickDelta;
        boolean quads = false;
        boolean lines = false;
        for (LightMesh mesh : meshes) {
            quads |= mesh.quadCount > 0;
            lines |= mesh.lineCount > 0;
        }
        GlStateManager.disableLighting();
        GlStateManager.enableDepthTest();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture();
        GlStateManager.disableCull();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder vc = t.getBuffer();
        if (quads) {
            GlStateManager.depthMask(false);
            vc.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            for (LightMesh mesh : meshes) {
                double ox = mesh.originX - cx;
                double oy = -cy;
                double oz = mesh.originZ - cz;
                float[] q = mesh.quads;
                int[] col = mesh.quadColors;
                for (int i = 0; i < mesh.quadCount; i++) {
                    int c = col[i];
                    float r = ((c >> 16) & 0xFF) / 255f;
                    float g = ((c >> 8) & 0xFF) / 255f;
                    float b = (c & 0xFF) / 255f;
                    float a = (c >>> 24) / 255f;
                    int k = i * 12;
                    for (int v = 0; v < 12; v += 3) {
                        vc.vertex(ox + q[k + v], oy + q[k + v + 1], oz + q[k + v + 2]).color(r, g, b, a).next();
                    }
                }
            }
            t.draw();
            GlStateManager.depthMask(true);
        }
        if (lines) {
            GL11.glLineWidth(2f);
            vc.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            for (LightMesh mesh : meshes) {
                double ox = mesh.originX - cx;
                double oy = -cy;
                double oz = mesh.originZ - cz;
                float[] l = mesh.lines;
                int[] col = mesh.lineColors;
                for (int i = 0; i < mesh.lineCount; i++) {
                    int c = col[i];
                    float r = ((c >> 16) & 0xFF) / 255f;
                    float g = ((c >> 8) & 0xFF) / 255f;
                    float b = (c & 0xFF) / 255f;
                    int k = i * 6;
                    vc.vertex(ox + l[k], oy + l[k + 1], oz + l[k + 2]).color(r, g, b, 1f).next();
                    vc.vertex(ox + l[k + 3], oy + l[k + 4], oz + l[k + 5]).color(r, g, b, 1f).next();
                }
            }
            t.draw();
            GL11.glLineWidth(1f);
        }
        GlStateManager.enableCull();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
    }
}
