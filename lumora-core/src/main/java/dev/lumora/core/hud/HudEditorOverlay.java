package dev.lumora.core.hud;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.theme.LumoraTheme;

/**
 * What the HUD editor draws while a widget is moved or resized: a fine grid, the 3x3 anchor zones with the active
 * anchor point, glowing snap lines, the widgets it snapped to, and its position ("x × y") or size next to it. The
 * editor screens of every Minecraft version call {@link #draw} after the widgets.
 */
public final class HudEditorOverlay {
    private static final int GRID = 0x10FFFFFF;
    private static final int ZONE_LINE = 0x22FFFFFF;
    private static final int DOT = 0x70FFFFFF;
    private static final int LABEL_BG = 0xF0141220;

    private HudEditorOverlay() {
    }

    public static void draw(HudGraphics g, HudEditorModel m, int width, int height) {
        HudEditorModel.Mode mode = m.mode();
        boolean moving = mode == HudEditorModel.Mode.MOVING;
        if (!moving && mode != HudEditorModel.Mode.RESIZING) {
            return;
        }
        int accent = LumoraTheme.accent();
        if (moving) {
            int step = Math.max(4, m.settings().gridSize);
            for (int x = step; x < width; x += step) {
                g.fill(x, 0, x + 1, height, GRID);
            }
            for (int y = step; y < height; y += step) {
                g.fill(0, y, width, y + 1, GRID);
            }
            zones(g, m.dragZone(), width, height, accent);
        }
        for (WidgetEntry e : m.magnets()) {
            Bounds b = e.bounds();
            if (b.valid) {
                g.fill(b.x, b.y, b.right(), b.bottom(), LumoraTheme.alpha(accent, 0x22));
                g.outline(b.x - 2, b.y - 2, b.width + 4, b.height + 4, LumoraTheme.alpha(accent, 0xE0));
            }
        }
        for (int gx : m.guidesX()) {
            g.fill(gx - 1, 0, gx + 2, height, LumoraTheme.alpha(accent, 0x40));
        }
        for (int gy : m.guidesY()) {
            g.fill(0, gy - 1, width, gy + 2, LumoraTheme.alpha(accent, 0x40));
        }
        WidgetEntry sel = m.selected();
        if (sel != null && sel.bounds().valid) {
            Bounds b = sel.bounds();
            String text = moving ? b.x + " × " + b.y : b.width + " × " + b.height + "  " + Math.round(sel.scale() * 100) + " %";
            label(g, text, b, width, height, accent);
        }
    }

    /** Thirds of the screen; the zone under the cursor is tinted and its anchor point grows with a ring. */
    static void zones(HudGraphics g, String active, int width, int height, int accent) {
        int w3 = width / 3;
        int h3 = height / 3;
        g.fill(w3, 0, w3 + 1, height, ZONE_LINE);
        g.fill(2 * w3, 0, 2 * w3 + 1, height, ZONE_LINE);
        g.fill(0, h3, width, h3 + 1, ZONE_LINE);
        g.fill(0, 2 * h3, width, 2 * h3 + 1, ZONE_LINE);
        for (String zone : Anchor.ZONES) {
            int[] p = Anchor.point(zone, width, height);
            int px = Math.max(4, Math.min(width - 4, p[0]));
            int py = Math.max(4, Math.min(height - 4, p[1]));
            if (zone.equals(active)) {
                int c = Anchor.column(zone);
                int r = Anchor.row(zone);
                int left = c * w3;
                int top = r * h3;
                int right = c == 2 ? width : left + w3;
                int bottom = r == 2 ? height : top + h3;
                g.fill(left, top, right, bottom, LumoraTheme.alpha(accent, 0x18));
                g.outline(left, top, right - left, bottom - top, LumoraTheme.alpha(accent, 0x70));
                g.outline(px - 6, py - 6, 12, 12, LumoraTheme.alpha(accent, 0x90));
                g.fill(px - 3, py - 3, px + 3, py + 3, accent);
            } else {
                g.fill(px - 1, py - 1, px + 2, py + 2, DOT);
            }
        }
    }

    /** Small card above the widget (below it if the widget is in the upper half), kept on screen. */
    static void label(HudGraphics g, String text, Bounds b, int width, int height, int accent) {
        int w = g.textWidth(text) + 8;
        int h = g.lineHeight() + 4;
        boolean below = b.y + b.height / 2 < height / 2;
        int x = Math.max(2, Math.min(width - w - 2, b.x + (b.width - w) / 2));
        int y = below ? b.bottom() + 4 : b.y - h - 4;
        y = Math.max(2, Math.min(height - h - 2, y));
        g.card(x, y, w, h, 3, LABEL_BG, LumoraTheme.alpha(accent, 0xA0));
        g.text(text, x + 4, y + 2, 0xFFFFFFFF, false);
    }
}
