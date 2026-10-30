package dev.lumora.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Cross-fade between menus: when one screen replaces another, the last frame (old menu) is copied into a texture
 * and drawn over the new screen with falling opacity. Only screen-to-screen switches fade; opening or closing a
 * menu from the game itself stays instant. "menuTransitions": "off" disables it.
 */
public final class ScreenTransition {
    /** Fallback when no config is loaded. */
    private static final int DEFAULT_MILLIS = 220;

    private static GpuTexture texture;
    private static GpuTextureView view;
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
            RenderTarget target = mc.getMainRenderTarget();
            GpuTexture src = target.getColorTexture();
            if (src == null) {
                return;
            }
            int w = src.getWidth(0);
            int h = src.getHeight(0);
            GpuDevice device = RenderSystem.getDevice();
            if (texture == null || texture.getWidth(0) != w || texture.getHeight(0) != h || texture.getFormat() != src.getFormat()) {
                release();
                texture = device.createTexture("lumora:screen_transition",
                        GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, src.getFormat(), w, h, 1, 1);
                view = device.createTextureView(texture);
            }
            device.createCommandEncoder().copyTextureToTexture(src, texture, 0, 0, 0, 0, 0, w, h);
            start = System.nanoTime();
            active = true;
        } catch (RuntimeException e) {
            // never break menu navigation because of an effect
            LumoraClient.LOG.warn("Menu transitions disabled: {}", e.toString());
            failed = true;
            active = false;
            release();
        }
    }

    /** Called after the screen (including tooltips) has been extracted. */
    public static void draw(GuiGraphicsExtractor g, Screen screen) {
        draw(g, screen.width, screen.height);
    }

    /** Also used by the HUD layer when a menu was closed back into the game. */
    public static void draw(GuiGraphicsExtractor g, int width, int height) {
        if (!active || view == null) {
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
        g.nextStratum();
        // "slide": the old menu also moves up and shrinks a little while it fades
        boolean slide = c != null && "slide".equals(c.transitionStyle);
        int dy = slide ? Math.round(-height * 0.06f * t) : 0;
        int inset = slide ? Math.round(Math.min(width, height) * 0.03f * t) : 0;
        // framebuffer textures are stored bottom-up, hence v 1 -> 0 (same as vanilla's picture-in-picture blit)
        ((GuiGraphicsExtractorAccessor) g).lumora$innerBlit(RenderPipelines.GUI_TEXTURED, view,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                inset, inset + dy, width - inset, height - inset + dy, 0f, 1f, 1f, 0f, (alpha << 24) | 0xFFFFFF);
    }

    private static void release() {
        if (view != null) {
            view.close();
            view = null;
        }
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }
}
