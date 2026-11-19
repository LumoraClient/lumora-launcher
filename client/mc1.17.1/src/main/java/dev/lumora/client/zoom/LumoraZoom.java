package dev.lumora.client.zoom;

import net.minecraft.network.chat.TextComponent;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.config.LumoraConfig;
import dev.lumora.client.gui.LumoraUi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.compat.GuiGraphics;

import java.util.Locale;

/**
 * Lumora zoom (key C by default): narrows the field of view of the camera only - the same effect as OptiFine's
 * zoom, purely client-side. Optional: hold or toggle, transitions with curve and duration, scroll wheel to change the
 * factor while zoomed, lower mouse sensitivity, cinematic camera, hidden hand or HUD, dark border, cinema bars.
 */
public final class LumoraZoom {
    public static final double MIN = 1.5;
    public static final double MAX = 50;

    private static KeyMapping key;
    private static boolean toggled;
    private static boolean wasDown;
    /** Factor the player set with the scroll wheel during this zoom; reset when the zoom ends. */
    private static double scrollFactor = -1;
    /** The factor over time (transition curve and duration of zooming in or out). */
    private static final dev.lumora.core.zoom.ZoomMotion MOTION = new dev.lumora.core.zoom.ZoomMotion();
    private static Boolean savedHideGui;
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

    /** GLFW code of the zoom key, -1 if unbound or a mouse button. */
    public static int keyCode() {
        if (key == null) {
            return -1;
        }
        com.mojang.blaze3d.platform.InputConstants.Key k = com.mojang.blaze3d.platform.InputConstants.getKey(key.saveString());
        return k.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM ? k.getValue() : -1;
    }

    /** Binds the zoom key in Minecraft's controls (-1: unbound) and saves Minecraft's options. */
    public static void setKeyCode(int glfw) {
        if (key == null) {
            return;
        }
        key.setKey(glfw < 0 ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN
                : com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(glfw));
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    private static LumoraConfig cfg() {
        return LumoraClient.config();
    }

    /** Name of the bound key for hints ("C" by default). */
    public static net.minecraft.network.chat.Component keyName() {
        return key == null ? new net.minecraft.network.chat.TextComponent("C") : key.getTranslatedKeyMessage();
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
        long now = System.nanoTime();
        aim(c, now);
        // cinematic camera only while zoomed (or only during the zoom-in transition), then back to the player's own setting
        if (zooming && c.zoomCinematic && (!c.zoomCinematicTransition || MOTION.moving(now))) {
            if (savedSmoothCamera == null) {
                savedSmoothCamera = mc.options.smoothCamera;
            }
            mc.options.smoothCamera = true;
        } else if (savedSmoothCamera != null) {
            mc.options.smoothCamera = savedSmoothCamera;
            savedSmoothCamera = null;
        }
        // HUD and chat hidden like F1 while zoomed, then back to the player's own choice
        if (zooming && c.zoomHideHud) {
            if (savedHideGui == null) {
                savedHideGui = mc.options.hideGui;
            }
            mc.options.hideGui = true;
        } else if (savedHideGui != null) {
            mc.options.hideGui = savedHideGui;
            savedHideGui = null;
        }
    }

    /** Moves the factor towards the target with the curve and duration of zooming in or out. */
    private static void aim(LumoraConfig c, long now) {
        double t = target();
        boolean in = t > MOTION.target();
        MOTION.aim(t, now, c.zoomSmooth, in ? c.zoomInEase : c.zoomOutEase, in ? c.zoomInMs : c.zoomOutMs);
    }

    /** Factor for this frame (1 = no zoom), along the transition. */
    public static double factor(float partialTick) {
        LumoraConfig c = cfg();
        long now = System.nanoTime();
        if (c != null) {
            aim(c, now);
        }
        return MOTION.value(now);
    }

    /** "Show hand in first person" off: the hand and the held item are hidden while zoomed. */
    public static boolean hidesHand() {
        LumoraConfig c = cfg();
        return c != null && !c.zoomHand && (active() || MOTION.value(System.nanoTime()) > 1.01);
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

    /** How far the cinema bars are in (0 out, 1 in) and when that was last updated. */
    private static float bars;
    private static long barsAt;

    /** Black bars that slide in from the top and bottom while zoomed and out again afterwards (off by default). */
    private static void cinemaBars(GuiGraphics g, LumoraConfig c) {
        long now = System.nanoTime();
        float dt = barsAt == 0 ? 0 : Math.min(0.1f, (now - barsAt) / 1e9f);
        barsAt = now;
        float goal = c.zoomCinemaBars && active() ? 1 : 0;
        bars += (goal - bars) * Math.min(1, dt * 10);
        if (Math.abs(goal - bars) < 0.002f) {
            bars = goal;
        }
        if (bars <= 0) {
            return;
        }
        float eased = bars * bars * (3 - 2 * bars);
        int w = g.guiWidth();
        int h = g.guiHeight();
        int bh = Math.round(h * (c.zoomBarHeight / 100f) * eased);
        if (bh > 0) {
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
    }

    /** Dark border, cinema bars and the current factor after scrolling (HUD layer). */
    public static void overlay(GuiGraphics g) {
        LumoraConfig c = cfg();
        double f = factor(1f);
        if (c != null) {
            cinemaBars(g, c);
        }
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
