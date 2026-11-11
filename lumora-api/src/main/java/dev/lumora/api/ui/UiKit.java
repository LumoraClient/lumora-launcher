package dev.lumora.api.ui;

import dev.lumora.api.render.HudGraphics;

/**
 * Lumora's controls for addon menus and HUD widgets, drawn in the player's {@link Theme}: panels, cards, buttons,
 * switches, sliders, text fields, titles and hints. Only drawing - the addon keeps its own state and reacts to clicks
 * with {@link #inside}. All coordinates are GUI pixels.
 *
 * <pre>{@code
 * public void render(HudGraphics g, int w, int h, int mx, int my) {
 *     Theme t = context.theme();
 *     UiKit.panel(g, t, 0, 0, w, h);
 *     UiKit.title(g, t, 20, 16, "Mein Addon");
 *     UiKit.button(g, t, 20, 40, 120, 20, "Start", UiKit.inside(mx, my, 20, 40, 120, 20), true);
 *     UiKit.toggle(g, t, 150, 43, enabled, UiKit.inside(mx, my, 150, 43, UiKit.TOGGLE_W, UiKit.TOGGLE_H));
 * }
 * }</pre>
 *
 * @since 1.5.0
 */
public final class UiKit {
    public static final int TOGGLE_W = 24;
    public static final int TOGGLE_H = 12;

    private UiKit() {
    }

    /** Whether (mx, my) is inside the rectangle. */
    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /** {@code color} with a new alpha (0..255). */
    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    /** Full-screen background of a menu. */
    public static void panel(HudGraphics g, Theme t, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, alpha(t.background(), 0xF0));
    }

    /** A rounded card; {@code highlighted} draws its border in the accent colour. */
    public static void card(HudGraphics g, Theme t, int x, int y, int w, int h, boolean highlighted) {
        g.card(x, y, w, h, 4, t.surface(), highlighted ? t.accent() : t.border());
    }

    /** Title in the text colour with a short accent line under it. */
    public static void title(HudGraphics g, Theme t, int x, int y, String text) {
        g.text(text, x, y, t.text(), false);
        g.fill(x, y + 11, x + Math.min(24, g.textWidth(text)), y + 12, t.accent());
    }

    /** A line of text; {@code muted} for secondary text. */
    public static void label(HudGraphics g, Theme t, int x, int y, String text, boolean muted) {
        g.text(text, x, y, muted ? t.muted() : t.text(), false);
    }

    /** A small hint in the muted colour at 3/4 size. */
    public static void hint(HudGraphics g, Theme t, int x, int y, String text) {
        g.push();
        g.translate(x, y);
        g.scale(0.75f);
        g.text(text, 0, 0, t.muted(), false);
        g.pop();
    }

    /** A button filled with the accent colour (greyed out when disabled), label centered. */
    public static void button(HudGraphics g, Theme t, int x, int y, int w, int h, String label, boolean hovered, boolean enabled) {
        int fill = !enabled ? t.surface2() : hovered ? t.accentHover() : t.accent();
        g.roundedRect(x, y, w, h, 4, fill);
        g.centeredText(label, x + w / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : t.muted(), false);
    }

    /** A quiet button for secondary actions: surface colour, accent border while hovered. */
    public static void secondaryButton(HudGraphics g, Theme t, int x, int y, int w, int h, String label, boolean hovered) {
        g.card(x, y, w, h, 4, t.surface2(), hovered ? t.accent() : t.border());
        g.centeredText(label, x + w / 2, y + (h - 8) / 2, t.text(), false);
    }

    /** An on/off switch of {@link #TOGGLE_W} x {@link #TOGGLE_H}. */
    public static void toggle(HudGraphics g, Theme t, int x, int y, boolean on, boolean hovered) {
        int track = on ? (hovered ? t.accentHover() : t.accent()) : (hovered ? t.border() : t.surface2());
        g.roundedRect(x, y, TOGGLE_W, TOGGLE_H, TOGGLE_H / 2, track);
        int knob = TOGGLE_H - 4;
        int kx = on ? x + TOGGLE_W - knob - 2 : x + 2;
        g.roundedRect(kx, y + 2, knob, knob, knob / 2, 0xFFFFFFFF);
    }

    /** A slider; {@code value} from 0 to 1. Turn a click at {@code mx} into a value with {@link #sliderValue}. */
    public static void slider(HudGraphics g, Theme t, int x, int y, int w, double value, boolean hovered) {
        double v = Math.max(0, Math.min(1, value));
        int filled = (int) Math.round((w - 8) * v);
        g.roundedRect(x, y + 4, w, 4, 2, t.surface2());
        g.roundedRect(x, y + 4, filled + 4, 4, 2, t.accent());
        g.roundedRect(x + filled, y, 8, 12, 4, hovered ? 0xFFFFFFFF : 0xFFE6E2F0);
    }

    /** The slider value (0..1) for the mouse at {@code mx}. */
    public static double sliderValue(double mx, int x, int w) {
        return Math.max(0, Math.min(1, (mx - x - 4) / Math.max(1, w - 8)));
    }

    /** A text field showing {@code text} (or {@code placeholder} when empty) with a blinking cursor when focused. */
    public static void textField(HudGraphics g, Theme t, int x, int y, int w, int h, String text, String placeholder, boolean focused) {
        g.card(x, y, w, h, 3, t.surface2(), focused ? t.accent() : t.border());
        boolean empty = text == null || text.isEmpty();
        String shown = empty ? (placeholder == null ? "" : placeholder) : text;
        int max = w - 10;
        while (shown.length() > 1 && g.textWidth(shown) > max) {
            shown = empty ? shown.substring(0, shown.length() - 1) : shown.substring(1);
        }
        int ty = y + (h - 8) / 2;
        g.text(shown, x + 5, ty, empty ? t.muted() : t.text(), false);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cx = x + 5 + (empty ? 0 : g.textWidth(shown));
            g.fill(cx, ty - 1, cx + 1, ty + 9, t.accent());
        }
    }

    /** A row of a list (22 px high works well); {@code selected} draws the accent bar on the left. */
    public static void listRow(HudGraphics g, Theme t, int x, int y, int w, int h, boolean selected, boolean hovered) {
        if (selected || hovered) {
            g.roundedRect(x, y, w, h, 3, selected ? alpha(t.accent(), 0x40) : alpha(t.surface2(), 0xC0));
        }
        if (selected) {
            g.fill(x, y + 4, x + 2, y + h - 4, t.accent());
        }
    }

    /** A small coloured badge with a short text (e.g. a count or "NEU"). */
    public static void badge(HudGraphics g, Theme t, int x, int y, String text) {
        int w = g.textWidth(text) + 6;
        g.roundedRect(x, y, w, 10, 3, t.accent());
        g.text(text, x + 3, y + 1, 0xFFFFFFFF, false);
    }
}
