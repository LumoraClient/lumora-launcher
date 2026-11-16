package dev.lumora.core.perf;

import dev.lumora.api.render.HudGraphics;

/**
 * Frame optimization for Lumora's rounded panels, cards and buttons: the same pixels as
 * {@link HudGraphics#roundedRect}, but the corner shape of each radius is computed once (not with a square root per
 * row every frame), and neighbouring corner rows with the same inset are drawn as one rectangle instead of one per
 * row. The rectangles never overlap, so translucent colours look exactly the same.
 */
public final class FastShapes {
    private static final int CACHED_RADII = 128;
    private static final int[][] INSETS = new int[CACHED_RADII][];

    private FastShapes() {
    }

    /** Corner insets of each row for a radius (row 0 = outermost), the same values the default implementation computes. */
    static int[] insets(int r) {
        int[] cached = r < CACHED_RADII ? INSETS[r] : null;
        if (cached != null) {
            return cached;
        }
        int[] rows = new int[r];
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            rows[i] = (int) Math.ceil(r - Math.sqrt(r * r - d * d));
        }
        if (r < CACHED_RADII) {
            INSETS[r] = rows; // racing threads compute the same array: harmless
        }
        return rows;
    }

    /** Draws a rounded rectangle like {@link HudGraphics#roundedRect} (pixel for pixel). */
    public static void roundedRect(HudGraphics g, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean batch = FillBatch.begin(g);
        try {
            drawRoundedRect(g, x, y, width, height, radius, color);
        } finally {
            FillBatch.end(g, batch);
        }
    }

    private static void drawRoundedRect(HudGraphics g, int x, int y, int width, int height, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        g.fill(x + r, y, x + width - r, y + height, color);
        int[] insets = insets(r);
        int i = 0;
        while (i < r) {
            int inset = insets[i];
            int j = i + 1;
            while (j < r && insets[j] == inset) {
                j++;
            }
            if (inset < r) {
                g.fill(x + inset, y + i, x + r, y + j, color);
                g.fill(x + width - r, y + i, x + width - inset, y + j, color);
                g.fill(x + inset, y + height - j, x + r, y + height - i, color);
                g.fill(x + width - r, y + height - j, x + width - inset, y + height - i, color);
            }
            i = j;
        }
        if (height > 2 * r) {
            g.fill(x, y + r, x + r, y + height - r, color);
            g.fill(x + width - r, y + r, x + width, y + height - r, color);
        }
    }

    /**
     * The one pixel border with rounded corners of the HUD widgets (Widget-Design), pixel for pixel, with the corner
     * shape of the radius computed once.
     */
    public static void roundedOutline(HudGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean batch = FillBatch.begin(g);
        try {
            drawRoundedOutline(g, x, y, w, h, radius, color);
        } finally {
            FillBatch.end(g, batch);
        }
    }

    private static void drawRoundedOutline(HudGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        int[] insets = insets(r);
        int previous = r;
        for (int i = 0; i < r; i++) {
            int inset = insets[i];
            int end = Math.max(inset + 1, previous);
            g.fill(x + inset, y + i, x + end, y + i + 1, color);
            g.fill(x + w - end, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + end, y + h - i, color);
            g.fill(x + w - end, y + h - 1 - i, x + w - inset, y + h - i, color);
            previous = inset;
        }
    }
}
