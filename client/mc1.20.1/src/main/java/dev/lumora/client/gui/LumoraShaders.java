package dev.lumora.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Menu effects for Minecraft 1.21. The aurora sky, glass and glow effects of the newer modules are RenderPipelines
 * (Minecraft 1.21.5+); 1.21 still renders through RenderTypes and JSON shader programs, so this module draws the
 * plain {@link LumoraUi} variants: flat cards instead of glass, no glow, the static background instead of the sky.
 * The API is the same as in the other modules, so the screens are unchanged.
 */
public final class LumoraShaders {
    private static final Logger LOG = LoggerFactory.getLogger("Lumora");
    private static boolean logged;

    private LumoraShaders() {
    }

    /** Render thread only. Always false on 1.21 (see class comment). */
    public static boolean enabled() {
        if (!logged) {
            logged = true;
            LOG.info("Lumora menu shaders are not available on Minecraft 1.20.1 - using plain menu rendering");
        }
        return false;
    }

    /** Animated sky: not drawn without shaders (callers draw the static background instead). */
    public static void sky(GuiGraphics g, int w, int h, float strength) {
    }

    /** Glass panel; falls back to a flat card. */
    public static void glass(GuiGraphics g, int x, int y, int w, int h, int argb) {
        LumoraUi.card(g, x, y, w, h, argb, LumoraUi.BORDER);
    }

    /** Soft glow around a rectangle (no-op without shaders). */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int rgb, int margin) {
    }
}
