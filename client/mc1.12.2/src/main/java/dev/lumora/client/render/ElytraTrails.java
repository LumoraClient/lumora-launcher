package dev.lumora.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.core.extras.ElytraTrailsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.opengl.GL11;

/** Lumora module "Elytra-Streifen" (legacy GL): the trails of one player, drawn by ElytraTrailMixin. */
public final class ElytraTrails {
    private ElytraTrails() {
    }

    /**
     * Draws the trails of a player right after it was drawn.
     *
     * @param x camera-relative position of the player (as given to the renderer)
     */
    public static void draw(LivingEntity e, double x, double y, double z, float tickDelta) {
        ElytraTrailsModule m = ElytraTrailsModule.active();
        if (m == null) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        double px = e.prevTickX + (e.x - e.prevTickX) * tickDelta;
        double py = e.prevTickY + (e.y - e.prevTickY) * tickDelta;
        double pz = e.prevTickZ + (e.z - e.prevTickZ) * tickDelta;
        float yaw = e.prevYaw + (e.yaw - e.prevYaw) * tickDelta;
        float pitch = e.prevPitch + (e.pitch - e.prevPitch) * tickDelta;
        // the renderer got the position relative to the camera: camera = position - (x, y, z)
        ElytraTrailsModule.Mesh mesh = m.mesh(e.getEntityId(), e == mc.player, e.method_13055() /* isFallFlying */,
                px, py, pz, yaw, pitch, e.velocityX, e.velocityY, e.velocityZ, px - x, py - y, pz - z,
                System.currentTimeMillis());
        if (mesh.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        mc.gameRenderer.disableLightmap();
        GlStateManager.disableLighting();
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder vc = t.getBuffer();
        vc.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        float[] xyz = mesh.xyz();
        int[] col = mesh.colors();
        for (int i = 0; i < mesh.vertices(); i++) {
            int c = col[i];
            vc.vertex(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .color(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, (c >>> 24) / 255f).next();
        }
        t.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
        GlStateManager.enableLighting();
        mc.gameRenderer.enableLightmap();
        GlStateManager.popMatrix();
    }
}
