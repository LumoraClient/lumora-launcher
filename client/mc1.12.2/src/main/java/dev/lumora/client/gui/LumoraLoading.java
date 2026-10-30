package dev.lumora.client.gui;

import dev.lumora.client.compat.GuiGraphics;

/**
 * Lumora version of Minecraft's resource-loading screen (startup, F3+T, resource pack changes): dark Lumora
 * background, pixel-art moon emblem and "LUMORA" wordmark, accent progress bar. Drawn only with filled rectangles,
 * because during the first load neither textures nor fonts are available yet.
 */
public final class LumoraLoading {
    public static final int BACKGROUND = 0xFF0C0A12;

    /** 5x7 pixel glyphs of the wordmark. */
    private static final String[][] GLYPHS = {
            {"L", "10000", "10000", "10000", "10000", "10000", "10000", "11111"},
            {"U", "10001", "10001", "10001", "10001", "10001", "10001", "01110"},
            {"M", "10001", "11011", "10101", "10101", "10001", "10001", "10001"},
            {"O", "01110", "10001", "10001", "10001", "10001", "10001", "01110"},
            {"R", "11110", "10001", "10001", "11110", "10100", "10010", "10001"},
            {"A", "01110", "10001", "10001", "11111", "10001", "10001", "10001"},
    };

    /** 11x11 yin-yang like the logo: L day half (teal to violet), D night half, S the Lumora star, E dark eye. */
    private static final String[] MOON = {
            "000DDDDD000",
            "00DDDDDDD00",
            "0LDDSDDDDD0",
            "LLDSSSDDDDD",
            "LLDDSDDDDDD",
            "LLLDDDLLDDD",
            "LLLLLLLLLDD",
            "LLLLLLEELDD",
            "0LLLLLEELD0",
            "00LLLLLLL00",
            "000LLLLL000",
    };

    private LumoraLoading() {
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.clamp(alpha, 0f, 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Draws emblem, wordmark and bar; {@code x0..x1 / y0..y1} is where Minecraft would draw its progress bar. */
    public static void draw(GuiGraphics g, int x0, int y0, int x1, int y1, float alpha, float progress) {
        int screenW = g.guiWidth();
        int barW = x1 - x0;
        int cx = screenW / 2;
        // wordmark size follows the bar width (= Minecraft's logo width)
        int px = Math.max(2, barW / 60);
        int wordW = GLYPHS.length * 6 * px - px;
        int wordH = 7 * px;
        int moonPx = Math.max(2, px * 5 / 4);
        int moonW = 11 * moonPx;
        int gap = px * 4;
        int totalW = moonW + gap + wordW;
        int top = y0 - Math.max(wordH, moonW) - px * 9;
        int left = cx - totalW / 2;

        // soft accent glow behind the emblem
        int glowCx = left + moonW / 2;
        int glowCy = top + moonW / 2;
        for (int r = 6; r >= 1; r--) {
            int rr = moonW / 2 + r * px * 2;
            g.fill(glowCx - rr, glowCy - rr, glowCx + rr, glowCy + rr, withAlpha(0x088B5CF6, alpha));
        }
        for (int y = 0; y < MOON.length; y++) {
            for (int x = 0; x < MOON[y].length(); x++) {
                char p = MOON[y].charAt(x);
                if (p != '0') {
                    int color = switch (p) {
                        case 'S' -> 0xFFFFFFFF;
                        case 'E' -> 0xFF14122A;
                        case 'D' -> 0xFF2A1F5C;
                        default -> y + x < 10 ? 0xFF6EE6F7 : 0xFF8B9CF6;
                    };
                    g.fill(left + x * moonPx, top + y * moonPx, left + (x + 1) * moonPx, top + (y + 1) * moonPx, withAlpha(color, alpha));
                }
            }
        }
        int wx = left + moonW + gap;
        int wy = top + (moonW - wordH) / 2;
        for (String[] glyph : GLYPHS) {
            for (int y = 0; y < 7; y++) {
                String row = glyph[y + 1];
                for (int x = 0; x < 5; x++) {
                    if (row.charAt(x) == '1') {
                        // shadow first, then the letter
                        g.fill(wx + x * px + px / 2, wy + y * px + px / 2, wx + (x + 1) * px + px / 2, wy + (y + 1) * px + px / 2,
                                withAlpha(0xFF3B1F7A, alpha));
                        g.fill(wx + x * px, wy + y * px, wx + (x + 1) * px, wy + (y + 1) * px, withAlpha(0xFFECE8F4, alpha));
                    }
                }
            }
            wx += 6 * px;
        }
        // progress bar: thin rounded track with accent fill
        int h = Math.max(4, (y1 - y0) / 2);
        int by = y0 + (y1 - y0 - h) / 2;
        g.fill(x0 + 1, by, x1 - 1, by + h, withAlpha(0xFF221C2D, alpha));
        g.fill(x0, by + 1, x1, by + h - 1, withAlpha(0xFF221C2D, alpha));
        int fill = Math.round(barW * Math.clamp(progress, 0f, 1f));
        if (fill > 2) {
            g.fill(x0 + 1, by, x0 + fill - 1, by + h, withAlpha(0xFF8B5CF6, alpha));
            g.fill(x0, by + 1, x0 + fill, by + h - 1, withAlpha(0xFF8B5CF6, alpha));
            g.fill(x0 + 1, by, x0 + fill - 1, by + 1, withAlpha(0xFFC4B5FD, alpha));
        }
    }
}
