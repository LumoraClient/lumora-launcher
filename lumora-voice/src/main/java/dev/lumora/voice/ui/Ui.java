package dev.lumora.voice.ui;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;

/** Drawing helpers in the Lumora style on {@link HudGraphics}: rounded cards with a 1px border, soft accent glow. */
public final class Ui {
    public static final TextureRef BACKGROUND = new TextureRef("lumoravoice", "textures/gui/background.png");
    private static final int BG_W = 1920, BG_H = 1080;

    private Ui() {}

    public static int text() { return Theme.color("text"); }
    public static int muted() { return Theme.color("text_muted"); }
    public static int accent() { return Theme.color("accent"); }
    public static int border() { return Theme.color("border"); }

    public static int alpha(int argb, int alpha) { return (Math.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF); }

    /** Soft halo around an area (replaces the Lumora glow shader). */
    public static void glow(HudGraphics g, int x, int y, int w, int h, int r, int color, int size) {
        for (int i = size; i >= 1; i--) g.roundedRect(x - i, y - i, w + 2 * i, h + 2 * i, r + i, alpha(color, 0x48 / (size + 1)));
    }

    /**
     * Screen background like the Lumora hub: outside a world the landscape (center crop) under a dark glaze; in a
     * world the game stays visible, darkened.
     */
    public static void backdrop(HudGraphics g, int w, int h, boolean inWorld) {
        if (!inWorld) {
            g.fill(0, 0, w, h, Theme.color("background"));
            float screen = w / (float) h, tex = BG_W / (float) BG_H;
            int rw = BG_W, rh = BG_H;
            if (screen > tex) rh = Math.round(BG_W / screen); else rw = Math.round(BG_H * screen);
            g.texture(BACKGROUND, 0, 0, w, h, (BG_W - rw) / 2f, (BG_H - rh) / 2f, rw, rh, BG_W, BG_H);
        }
        g.fill(0, 0, w, h, Theme.color(inWorld ? "scrim_world" : "scrim_menu"));
    }

    /** Small badge like the launcher chips; returns its width. */
    public static int chip(HudGraphics g, String label, int x, int y, int color) {
        int w = g.textWidth(label) + 18;
        g.roundedRect(x, y, w, 14, 7, alpha(color, 0x38));
        g.roundedRect(x + 6, y + 5, 4, 4, 2, color);
        g.text(label, x + 13, y + 3, text(), false);
        return w;
    }

    /** Text scaled around its top-left corner (the Minecraft font has one size). */
    public static void bigText(HudGraphics g, String label, int x, int y, float scale, int color) {
        g.push(); g.translate(x, y); g.scale(scale);
        g.text(label, 0, 0, color, true);
        g.pop();
    }

    /** Shortens {@code value} with an ellipsis so it fits into {@code width} pixels. */
    public static String fit(HudGraphics g, String value, int width) {
        if (g.textWidth(value) <= width) return value;
        int start = 0, end = value.length();
        while (start < end) {
            int mid = (start + end + 1) / 2;
            if (g.textWidth(value.substring(0, mid) + "…") <= width) start = mid; else end = mid - 1;
        }
        return value.substring(0, start) + "…";
    }

    public static void label(HudGraphics g, String value, int x, int y, int color) { g.text(value, x, y, color, false); }
    public static void right(HudGraphics g, String value, int right, int y, int color) { g.rightText(value, right, y, color, false); }
    public static void centered(HudGraphics g, String value, int center, int y, int color) { g.centeredText(value, center, y, color, false); }
}
