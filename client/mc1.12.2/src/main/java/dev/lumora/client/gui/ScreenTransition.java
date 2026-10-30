package dev.lumora.client.gui;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.compat.GuiGraphics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Cross-fade between menus: when one screen replaces another, the last frame (old menu) is copied into a texture
 * and drawn over the new screen with falling opacity. Only screen-to-screen switches fade; opening or closing a
 * menu from the game itself stays instant. "menuTransitions": "off" disables it.
 *
 * <p>Minecraft 1.12.2: the main {@link Framebuffer} is copied with {@code glBlitFramebuffer} into an own framebuffer whose
 * colour texture is drawn as a textured quad. Without framebuffer support (old drivers, "fbo" off) there is no fade.
 */
public final class ScreenTransition {
    /** Fallback when no config is loaded. */
    private static final int DEFAULT_MILLIS = 220;

    private static Framebuffer copy;
    private static long start;
    private static boolean active;
    private static boolean failed;

    private ScreenTransition() {
    }

    private static boolean enabled() {
        var c = LumoraClient.config();
        return !failed && GLX.supportsFbo() && (c == null || (!"off".equals(c.menuTransitions) && !"off".equals(c.transitionStyle)));
    }

    /** Called from MinecraftClient#setScreen before the switch. */
    public static void capture(MinecraftClient mc, Screen from, Screen to) {
        boolean inGame = LumoraClient.config() != null && LumoraClient.config().transitionInGame;
        // game <-> menu (pause menu, inventory, chat) only fades when switched on: no delay for players who do not want it
        if (from == to || !enabled() || ((from == null || to == null) && !inGame) || mc.world == null && (from == null || to == null)) {
            active = false;
            return;
        }
        try {
            Framebuffer main = mc.getFramebuffer();
            if (main == null) {
                return;
            }
            int w = main.textureWidth;
            int h = main.textureHeight;
            if (w <= 0 || h <= 0) {
                return;
            }
            if (copy == null) {
                copy = new Framebuffer(w, h, false);
                copy.setTexFilter(GL11.GL_LINEAR);
            } else if (copy.textureWidth != w || copy.textureHeight != h) {
                copy.resize(w, h);
                copy.setTexFilter(GL11.GL_LINEAR);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.fbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.bind(true);
            start = System.nanoTime();
            active = true;
        } catch (RuntimeException | LinkageError e) {
            // never break menu navigation because of an effect (LinkageError: no GL 3.0 entry points)
            LumoraClient.LOG.warn("Menu transitions disabled: {}", e.toString());
            failed = true;
            active = false;
        }
    }

    /** Called after the screen (including tooltips) has been rendered. */
    public static void draw(GuiGraphics g, Screen screen) {
        draw(g, screen.width, screen.height);
    }

    /** Also used by the HUD layer when a menu was closed back into the game. */
    public static void draw(GuiGraphics g, int width, int height) {
        if (!active || copy == null) {
            return;
        }
        var c = LumoraClient.config();
        int millis = c == null ? DEFAULT_MILLIS : Math.clamp(c.transitionMillis, 80, 1000);
        float t = (System.nanoTime() - start) / (millis * 1_000_000f);
        if (t >= 1f) {
            active = false;
            return;
        }
        // ease-out: the new menu becomes readable quickly, the tail stays soft
        float k = 1f - t;
        float alpha = k * k;
        if (alpha <= 0.004f) {
            return;
        }
        // "slide": the old menu also moves up and shrinks a little while it fades
        boolean slide = c != null && "slide".equals(c.transitionStyle);
        float dy = slide ? -height * 0.06f * t : 0;
        float inset = slide ? Math.min(width, height) * 0.03f * t : 0;
        float x0 = inset;
        float y0 = inset + dy;
        float x1 = width - inset;
        float y1 = height - inset + dy;

        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.enableTexture();
        GlStateManager.disableAlphaTest();
        GlStateManager.bindTexture(copy.colorAttachment);
        GlStateManager.color(1f, 1f, 1f, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder b = tessellator.getBuffer();
        // framebuffer textures are stored bottom-up, hence v 1 at the top
        b.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
        b.vertex(x0, y1, 0).texture(0, 0).next();
        b.vertex(x1, y1, 0).texture(1, 0).next();
        b.vertex(x1, y0, 0).texture(1, 1).next();
        b.vertex(x0, y0, 0).texture(0, 1).next();
        tessellator.draw();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableAlphaTest();
    }
}
