package dev.lumora.client.zoom;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.config.LumoraConfig;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

/**
 * Lumora zoom (key C by default): narrows the field of view of the camera only - the same effect as OptiFine's
 * zoom, purely client-side. Optional: hold or toggle, smooth animation, scroll wheel to change the factor while
 * zoomed, lower mouse sensitivity, cinematic camera, dark border.
 */
public final class LumoraZoom {
    public static final double MIN = 1.5;
    public static final double MAX = 50;

    private static KeyMapping key;
    private static boolean toggled;
    private static boolean wasDown;
    /** Factor the player set with the scroll wheel during this zoom; reset when the zoom ends. */
    private static double scrollFactor = -1;
    private static double current = 1;
    private static double previous = 1;
    private static Boolean savedSmoothCamera;
    private static long lastScroll;

    private LumoraZoom() {
    }

    public static KeyMapping key() {
        return key;
    }

    public static void init(KeyMapping k) {
        key = k;
    }

    private static LumoraConfig cfg() {
        return LumoraClient.config();
    }

    /** Name of the bound key for hints ("C" by default). */
    public static net.minecraft.network.chat.Component keyName() {
        return key == null ? net.minecraft.network.chat.Component.literal("C") : key.getTranslatedKeyMessage();
    }

    public static boolean active() {
        LumoraConfig c = cfg();
        if (c == null || !c.zoomEnabled || key == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return "toggle".equals(c.zoomMode) && toggled && mc.player != null;
        }
        return "toggle".equals(c.zoomMode) ? toggled : key.isDown();
    }

    private static double target() {
        return active() ? (scrollFactor > 0 ? scrollFactor : Math.clamp(cfg().zoomFactor, MIN, MAX)) : 1.0;
    }

    /** Once per client tick: key handling, animation step, cinematic camera. */
    public static void tick(Minecraft mc) {
        LumoraConfig c = cfg();
        if (c == null || key == null) {
            return;
        }
        boolean down = key.isDown();
        if ("toggle".equals(c.zoomMode) && down && !wasDown && mc.screen == null) {
            toggled = !toggled;
        }
        if (!"toggle".equals(c.zoomMode)) {
            toggled = false;
        }
        wasDown = down;
        boolean zooming = active();
        if (!zooming) {
            scrollFactor = -1;
        }
        previous = current;
        double t = target();
        current = c.zoomSmooth ? current + (t - current) * 0.5 : t;
        if (Math.abs(current - t) < 0.01) {
            current = t;
        }
        // cinematic camera only while zoomed, then back to the player's own setting
        if (zooming && c.zoomCinematic) {
            if (savedSmoothCamera == null) {
                savedSmoothCamera = mc.options.smoothCamera;
            }
            mc.options.smoothCamera = true;
        } else if (savedSmoothCamera != null) {
            mc.options.smoothCamera = savedSmoothCamera;
            savedSmoothCamera = null;
        }
    }

    /** Factor for this frame (1 = no zoom), interpolated between ticks. */
    public static double factor(float partialTick) {
        return previous + (current - previous) * partialTick;
    }

    /** Applied to the camera's field of view. */
    public static float apply(float fov, float partialTick) {
        double f = factor(partialTick);
        return f <= 1.0001 ? fov : (float) (fov / f);
    }

    /** Mouse sensitivity while zoomed (lower, so aiming stays steady). */
    public static double sensitivity(double base) {
        LumoraConfig c = cfg();
        double f = factor(1f);
        if (c == null || !c.zoomSensitivity || f <= 1.0001) {
            return base;
        }
        // Minecraft's sensitivity is cubed internally; scaling it by 1/sqrt(f) feels natural across zoom levels
        return base / Math.sqrt(f);
    }

    /** Scroll wheel while zoomed: changes the factor, returns true if the scroll was used. */
    public static boolean scroll(double amount) {
        LumoraConfig c = cfg();
        if (c == null || !c.zoomScroll || !active() || amount == 0) {
            return false;
        }
        double base = scrollFactor > 0 ? scrollFactor : Math.clamp(c.zoomFactor, MIN, MAX);
        scrollFactor = Math.clamp(base * (amount > 0 ? 1.25 : 0.8), MIN, MAX);
        lastScroll = System.currentTimeMillis();
        return true;
    }

    /** Dark border and the current factor after scrolling (HUD layer). */
    public static void overlay(GuiGraphics g) {
        LumoraConfig c = cfg();
        double f = factor(1f);
        if (c == null || f <= 1.05) {
            return;
        }
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (c.zoomVignette) {
            float strength = (float) Math.min(1, (f - 1) / 2);
            int steps = 10;
            for (int i = 0; i < steps; i++) {
                int a = Math.round(strength * 40 * (steps - i) / steps);
                int ix = Math.round(w * 0.16f * i / steps);
                int iy = Math.round(h * 0.16f * i / steps);
                int col = a << 24;
                g.fill(0, iy, ix + 2, h - iy, col);
                g.fill(w - ix - 2, iy, w, h - iy, col);
                g.fill(ix, 0, w - ix, iy + 2, col);
                g.fill(ix, h - iy - 2, w - ix, h, col);
            }
        }
        long since = System.currentTimeMillis() - lastScroll;
        if (since < 1200) {
            int alpha = since < 900 ? 0xFF : (int) (0xFF * (1200 - since) / 300.0);
            String t = String.format(Locale.GERMANY, "%.1f×", target());
            var font = Minecraft.getInstance().font;
            int tw = font.width(t);
            int x = (w - tw) / 2;
            int y = h / 2 + 14;
            g.fill(x - 4, y - 3, x + tw + 4, y + 10, LumoraUi.alpha(LumoraUi.BG, Math.min(0xB0, alpha)));
            g.drawString(font, t, x, y, (alpha << 24) | (LumoraUi.ACCENT_HOVER & 0xFFFFFF), false);
        }
    }
}
