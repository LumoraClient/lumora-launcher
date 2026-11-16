package dev.lumora.client.perf;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;

/**
 * Cloud optimization for Minecraft 1.8.9 and 1.12.2: the fancy clouds are built vertex by vertex in every frame there
 * (two passes of 64 cells, 128 draw calls). Here the same geometry is compiled once into an OpenGL display list and
 * drawn with two list calls; the movement within a cloud cell and the height of the camera become a translation.
 * The list is compiled again only when what it holds changes: the texture cell, which faces are visible (camera above,
 * in or below the clouds) or one of the vertex colours as Minecraft stores them (bytes), so the colours are exactly
 * the same. The same calculations as Minecraft's own code, the same GL state before and after.
 */
public final class LegacyClouds {
    private static int list = -1;
    private static final int[] key = new int[16];
    private static final int[] next = new int[16];
    private static boolean valid;
    private static int builds;

    private LegacyClouds() {
    }

    /** Number of times the cloud geometry was compiled (performance comparison). */
    public static int builds() {
        return builds;
    }

    private static int b(float c) {
        return (int) (c * 255.0F); // what BufferBuilder.color(float...) stores
    }

    /**
     * Draws the fancy clouds like Minecraft.
     *
     * @param camX         camera x (Minecraft's own interpolation of the camera entity)
     * @param camY         camera y
     * @param camZ         camera z
     * @param cloudHeight  the dimension's cloud height
     * @param anaglyph     Minecraft's anaglyph pass (2 = normal)
     */
    public static void render(float tickDelta, int anaglyph, double camX, double camY, double camZ, int ticks, float cloudHeight,
                              Vec3d color, TextureManager textures, Identifier texture) {
        GlStateManager.disableCull();
        double d = (double) ((float) ticks + tickDelta);
        double e = (camX + d * 0.03F) / 12.0;
        double i = camZ / 12.0 + 0.33F;
        float j = cloudHeight - (float) camY + 0.33F;
        int k = MathHelper.floor(e / 2048.0);
        int l = MathHelper.floor(i / 2048.0);
        e -= (double) (k * 2048);
        i -= (double) (l * 2048);
        textures.bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        float m = (float) color.x;
        float n = (float) color.y;
        float o = (float) color.z;
        if (anaglyph != 2) {
            float p = (m * 30.0F + n * 59.0F + o * 11.0F) / 100.0F;
            float q = (m * 30.0F + n * 70.0F) / 100.0F;
            float r = (m * 30.0F + o * 70.0F) / 100.0F;
            m = p;
            n = q;
            o = r;
        }
        float p = m * 0.9F;
        float q = n * 0.9F;
        float r = o * 0.9F;
        float s = m * 0.7F;
        float t = n * 0.7F;
        float u = o * 0.7F;
        float v = m * 0.8F;
        float w = n * 0.8F;
        float x = o * 0.8F;
        int fe = MathHelper.floor(e);
        int fi = MathHelper.floor(i);
        float z = (float) fe * 0.00390625F;
        float aa = (float) fi * 0.00390625F;
        float ab = (float) (e - (double) fe);
        float ac = (float) (i - (double) fi);
        GlStateManager.scale(12.0F, 1.0F, 12.0F);

        boolean below = j > -5.0F;
        boolean above = j <= 5.0F;
        int[] kk = next;
        kk[0] = fe;
        kk[1] = fi;
        kk[2] = below ? 1 : 0;
        kk[3] = above ? 1 : 0;
        kk[4] = b(s);
        kk[5] = b(t);
        kk[6] = b(u);
        kk[7] = b(m);
        kk[8] = b(n);
        kk[9] = b(o);
        kk[10] = b(p);
        kk[11] = b(q);
        kk[12] = b(r);
        kk[13] = b(v);
        kk[14] = b(w);
        kk[15] = b(x);
        if (!valid || !Arrays.equals(key, kk)) {
            if (list < 0) {
                list = GL11.glGenLists(1);
            }
            GL11.glNewList(list, GL11.GL_COMPILE);
            build(below, above, z, aa, m, n, o, p, q, r, s, t, u, v, w, x);
            GL11.glEndList();
            System.arraycopy(kk, 0, key, 0, kk.length);
            valid = true;
            builds++;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(-ab, j, -ac);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
                GlStateManager.colorMask(false, false, false, false);
            } else {
                switch (anaglyph) {
                    case 0 -> GlStateManager.colorMask(false, true, true, true);
                    case 1 -> GlStateManager.colorMask(true, false, false, true);
                    case 2 -> GlStateManager.colorMask(true, true, true, true);
                    default -> {
                    }
                }
            }
            GlStateManager.callList(list);
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
    }

    /** The resources were reloaded or the GL context changed: compile again on the next frame. */
    public static void invalidate() {
        valid = false;
    }

    /** Minecraft's fancy cloud cells with the cell offset and the cloud height left out (they become a translation). */
    private static void build(boolean below, boolean above, float z, float aa, float m, float n, float o, float p, float q, float r,
                              float s, float t, float u, float v, float w, float x) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bb = tessellator.getBuffer();
        float j = 0.0F;
        for (int ah = -3; ah <= 4; ah++) {
            for (int ai = -3; ai <= 4; ai++) {
                bb.begin(7, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
                float aj = (float) (ah * 8);
                float ak = (float) (ai * 8);
                float al = aj;
                float am = ak;
                if (below) {
                    bb.vertex(al + 0.0F, j + 0.0F, am + 8.0F).texture((aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(s, t, u, 0.8F).normal(0.0F, -1.0F, 0.0F).next();
                    bb.vertex(al + 8.0F, j + 0.0F, am + 8.0F).texture((aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(s, t, u, 0.8F).normal(0.0F, -1.0F, 0.0F).next();
                    bb.vertex(al + 8.0F, j + 0.0F, am + 0.0F).texture((aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(s, t, u, 0.8F).normal(0.0F, -1.0F, 0.0F).next();
                    bb.vertex(al + 0.0F, j + 0.0F, am + 0.0F).texture((aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(s, t, u, 0.8F).normal(0.0F, -1.0F, 0.0F).next();
                }
                if (above) {
                    bb.vertex(al + 0.0F, j + 4.0F - 9.765625E-4F, am + 8.0F).texture((aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(m, n, o, 0.8F).normal(0.0F, 1.0F, 0.0F).next();
                    bb.vertex(al + 8.0F, j + 4.0F - 9.765625E-4F, am + 8.0F).texture((aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(m, n, o, 0.8F).normal(0.0F, 1.0F, 0.0F).next();
                    bb.vertex(al + 8.0F, j + 4.0F - 9.765625E-4F, am + 0.0F).texture((aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(m, n, o, 0.8F).normal(0.0F, 1.0F, 0.0F).next();
                    bb.vertex(al + 0.0F, j + 4.0F - 9.765625E-4F, am + 0.0F).texture((aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(m, n, o, 0.8F).normal(0.0F, 1.0F, 0.0F).next();
                }
                if (ah > -1) {
                    for (int an = 0; an < 8; an++) {
                        bb.vertex(al + (float) an + 0.0F, j + 0.0F, am + 8.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(-1.0F, 0.0F, 0.0F).next();
                        bb.vertex(al + (float) an + 0.0F, j + 4.0F, am + 8.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(-1.0F, 0.0F, 0.0F).next();
                        bb.vertex(al + (float) an + 0.0F, j + 4.0F, am + 0.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(-1.0F, 0.0F, 0.0F).next();
                        bb.vertex(al + (float) an + 0.0F, j + 0.0F, am + 0.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(-1.0F, 0.0F, 0.0F).next();
                    }
                }
                if (ah <= 1) {
                    for (int an = 0; an < 8; an++) {
                        bb.vertex(al + (float) an + 1.0F - 9.765625E-4F, j + 0.0F, am + 8.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(1.0F, 0.0F, 0.0F).next();
                        bb.vertex(al + (float) an + 1.0F - 9.765625E-4F, j + 4.0F, am + 8.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(1.0F, 0.0F, 0.0F).next();
                        bb.vertex(al + (float) an + 1.0F - 9.765625E-4F, j + 4.0F, am + 0.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(1.0F, 0.0F, 0.0F).next();
                        bb.vertex(al + (float) an + 1.0F - 9.765625E-4F, j + 0.0F, am + 0.0F).texture((aj + (float) an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa).color(p, q, r, 0.8F).normal(1.0F, 0.0F, 0.0F).next();
                    }
                }
                if (ai > -1) {
                    for (int an = 0; an < 8; an++) {
                        bb.vertex(al + 0.0F, j + 4.0F, am + (float) an + 0.0F).texture((aj + 0.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, -1.0F).next();
                        bb.vertex(al + 8.0F, j + 4.0F, am + (float) an + 0.0F).texture((aj + 8.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, -1.0F).next();
                        bb.vertex(al + 8.0F, j + 0.0F, am + (float) an + 0.0F).texture((aj + 8.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, -1.0F).next();
                        bb.vertex(al + 0.0F, j + 0.0F, am + (float) an + 0.0F).texture((aj + 0.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, -1.0F).next();
                    }
                }
                if (ai <= 1) {
                    for (int an = 0; an < 8; an++) {
                        bb.vertex(al + 0.0F, j + 4.0F, am + (float) an + 1.0F - 9.765625E-4F).texture((aj + 0.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, 1.0F).next();
                        bb.vertex(al + 8.0F, j + 4.0F, am + (float) an + 1.0F - 9.765625E-4F).texture((aj + 8.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, 1.0F).next();
                        bb.vertex(al + 8.0F, j + 0.0F, am + (float) an + 1.0F - 9.765625E-4F).texture((aj + 8.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, 1.0F).next();
                        bb.vertex(al + 0.0F, j + 0.0F, am + (float) an + 1.0F - 9.765625E-4F).texture((aj + 0.0F) * 0.00390625F + z, (ak + (float) an + 0.5F) * 0.00390625F + aa).color(v, w, x, 0.8F).normal(0.0F, 0.0F, 1.0F).next();
                    }
                }
                tessellator.draw();
            }
        }
    }
}
