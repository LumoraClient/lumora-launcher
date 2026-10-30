package dev.lumora.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.client.LumoraClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Cross-fade between menus: when one screen replaces another, the last frame (old menu) is copied into a texture
 * and drawn over the new screen with falling opacity. Only screen-to-screen switches fade; opening or closing a
 * menu from the game itself stays instant. "menuTransitions": "off" disables it.
 *
 * <p>Minecraft 1.21.4 (OpenGL render targets): the main framebuffer is copied with {@code glBlitFramebuffer} into an
 * own {@link TextureTarget}, whose colour texture is registered under {@link #TEXTURE} so the normal GUI blit can draw it.
 */
public final class ScreenTransition {
    /** Fallback when no config is loaded. */
    private static final int DEFAULT_MILLIS = 220;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("lumora", "screen_transition");

    private static TextureTarget copy;
    private static FrameTexture frame;
    private static long start;
    private static boolean active;
    private static boolean failed;

    private ScreenTransition() {
    }

    private static boolean enabled() {
        var c = LumoraClient.config();
        return !failed && (c == null || (!"off".equals(c.menuTransitions) && !"off".equals(c.transitionStyle)));
    }

    /** Called from Minecraft#setScreen before the switch. */
    public static void capture(Minecraft mc, Screen from, Screen to) {
        boolean inGame = LumoraClient.config() != null && LumoraClient.config().transitionInGame;
        // game <-> menu (pause menu, inventory, chat) only fades when switched on: no delay for players who do not want it
        if (from == to || !enabled() || ((from == null || to == null) && !inGame) || mc.level == null && (from == null || to == null)) {
            active = false;
            return;
        }
        try {
            RenderTarget main = mc.getMainRenderTarget();
            int w = main.width;
            int h = main.height;
            if (w <= 0 || h <= 0) {
                return;
            }
            if (copy == null) {
                copy = new TextureTarget(w, h, false);
                copy.setFilterMode(GL11.GL_LINEAR);
                frame = new FrameTexture();
                mc.getTextureManager().register(TEXTURE, frame);
            } else if (copy.width != w || copy.height != h) {
                copy.resize(w, h);
                copy.setFilterMode(GL11.GL_LINEAR);
            }
            frame.wrap(copy.getColorTextureId());
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            main.bindWrite(true);
            start = System.nanoTime();
            active = true;
        } catch (RuntimeException e) {
            // never break menu navigation because of an effect
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
        int alpha = Math.round(255 * k * k);
        if (alpha <= 0) {
            return;
        }
        // everything of the new screen first, then the old frame on top
        g.flush();
        // "slide": the old menu also moves up and shrinks a little while it fades
        boolean slide = c != null && "slide".equals(c.transitionStyle);
        int dy = slide ? Math.round(-height * 0.06f * t) : 0;
        int inset = slide ? Math.round(Math.min(width, height) * 0.03f * t) : 0;
        int tw = copy.width;
        int th = copy.height;
        // framebuffer textures are stored bottom-up: start at v = th and walk a negative height
        g.blit(RenderType::guiTextured, TEXTURE, inset, inset + dy, 0f, th, width - 2 * inset, height - 2 * inset,
                tw, -th, tw, th, (alpha << 24) | 0xFFFFFF);
        g.flush();
    }

    /** The copy target's colour texture under a ResourceLocation; the target owns (and deletes) the GL texture. */
    private static final class FrameTexture extends AbstractTexture {
        void wrap(int glId) {
            id = glId;
        }

        @Override
        public void releaseId() {
            id = -1;
        }

        @Override
        public void close() {
        }
    }
}
