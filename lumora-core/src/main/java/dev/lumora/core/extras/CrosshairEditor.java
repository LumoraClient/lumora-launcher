package dev.lumora.core.extras;

import dev.lumora.api.config.Setting;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.hub.SettingsView;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Crosshair editor above the settings of the crosshair module: tabs Formen / Vorlagen / Eigene, a large preview on a
 * checkerboard, the shapes or the preset grid to click, a pixel editor (with mirroring) for an own crosshair and a
 * colour bar. The other settings (size, colour, outline, …) stay as rows below; rows that do not apply to the chosen
 * mode are hidden.
 */
final class CrosshairEditor implements SettingsView.Panel {
    private static final int TEXT = 0xFFECE8F4;
    private static final int MUTED = 0xFF9D95AE;
    private static final int DIM = 0xFF6B6580;
    private static final int FIELD = 0xFF0F0D16;
    private static final int BORDER = 0xFF2A2638;
    private static final int TILE = 0xFF1C1928;
    private static final int CHECK_A = 0xFF24212F;
    private static final int CHECK_B = 0xFF2C2839;
    private static final int PREVIEW = 112;
    private static final int TILE_SIZE = 34;
    private static final int CELL = 7;
    private static final Map<String, String[]> STYLE_NAMES = Map.of(
            "gap", new String[]{"Kreuz mit Lücke", "Cross with gap"},
            "cross", new String[]{"Kreuz", "Cross"},
            "t", new String[]{"T", "T"},
            "x", new String[]{"X", "X"},
            "circle", new String[]{"Kreis", "Circle"},
            "dot", new String[]{"Punkt", "Dot"});

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final CrosshairModule module;
    private final boolean de;
    private final List<Hit> hits = new ArrayList<>();
    private String tooltip;
    /** Mirror painting in all four quarters (kept while the game runs). */
    private static boolean mirror = true;
    /** Pixel grid on screen: x, y; -1 while not shown. */
    private int gridX = -1;
    private int gridY = -1;
    /** Colour bar on screen. */
    private int hueX = -1;
    private int hueY = -1;
    private int hueW;
    /** What a drag does: "paint" (with {@link #paintValue}), "hue" or null. */
    private String drag;
    private boolean paintValue;

    CrosshairEditor(CrosshairModule module, boolean german) {
        this.module = module;
        this.de = german;
    }

    private String t(String german, String english) {
        return de ? german : english;
    }

    @Override
    public boolean shows(Setting<?> s) {
        String m = module.mode.get();
        return switch (s.key()) {
            case "mode", "style", "preset", "pixels" -> false;
            case "size", "gap", "thickness", "dot" -> "shape".equals(m);
            case "scale" -> !"shape".equals(m);
            default -> true;
        };
    }

    @Override
    public String tooltip() {
        return tooltip;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public int render(HudGraphics g, int x, int y, int w, int mx, int my) {
        hits.clear();
        tooltip = null;
        gridX = -1;
        hueX = -1;
        int accent = LumoraTheme.accent();
        String mode = module.mode.get();

        // tabs
        String[] labels = {t("Formen", "Shapes"), t("Vorlagen", "Presets"), t("Eigene", "Custom")};
        int tabW = w / 3;
        for (int i = 0; i < 3; i++) {
            int tx = x + i * tabW;
            int tw = i == 2 ? w - 2 * tabW : tabW;
            boolean sel = CrosshairModule.MODES[i].equals(mode);
            boolean hover = in(mx, my, tx, y, tw, 18);
            int fill = sel ? accent : hover ? 0xFF2C2840 : 0xFF1F1C2B; // opaque: 1.8.9 drops translucent fills here
            g.roundedRect(tx + (i == 0 ? 0 : 1), y, tw - 1, 18, 4, fill);
            g.centeredText(labels[i], tx + tw / 2, y + 5, sel ? 0xFFFFFFFF : hover ? TEXT : MUTED, false);
            String id = CrosshairModule.MODES[i];
            hits.add(new Hit(tx, y, tw, 18, () -> module.mode.set(id)));
        }
        int by = y + 26;

        // preview (left, or on top when narrow)
        boolean wide = w >= 360;
        int pw = wide ? PREVIEW : w;
        int ph = wide ? PREVIEW : 84;
        preview(g, x, by, pw, ph);
        int rx = wide ? x + pw + 12 : x;
        int ry = wide ? by : by + ph + 10;
        int rw = wide ? w - pw - 12 : w;

        int used = switch (mode) {
            case "preset" -> presets(g, rx, ry, rw, mx, my);
            case "custom" -> custom(g, rx, ry, rw, mx, my);
            default -> shapes(g, rx, ry, rw, mx, my);
        };
        int bottom = Math.max(wide ? by + ph : by + ph + 10 + used, wide ? ry + used : 0);
        // colour bar under everything
        int cy = bottom + 10;
        hueBar(g, x, cy, w, mx, my);
        return cy + 22 - y;
    }

    private void preview(HudGraphics g, int x, int y, int w, int h) {
        g.roundedRect(x - 1, y - 1, w + 2, h + 2, 4, BORDER);
        for (int yy = 0; yy < h; yy += 8) {
            for (int xx = 0; xx < w; xx += 8) {
                g.fill(x + xx, y + yy, x + Math.min(w, xx + 8), y + Math.min(h, yy + 8),
                        ((xx + yy) / 8) % 2 == 0 ? CHECK_A : CHECK_B);
            }
        }
        // zoom so the crosshair fills the box without leaving it
        float half = "shape".equals(module.mode.get())
                ? CrosshairModule.extent(CrosshairModule.shape(module.style.get(), module.size.get(), module.gap.get(),
                module.thickness.get(), module.dot.get())) + 1
                : (CrosshairPresets.GRID / 2 + 1) * (float) module.scale.get().doubleValue();
        int zoom = Math.max(1, Math.min(4, (int) ((Math.min(w, h) / 2f - 6) / half)));
        g.push();
        g.translate(x + w / 2f, y + h / 2f);
        g.scale(zoom);
        module.render(g, 0, 0);
        g.pop();
        g.text("×" + zoom, x + 4, y + h - 11, DIM, false);
    }

    private int shapes(HudGraphics g, int x, int y, int w, int mx, int my) {
        int gapPx = 6;
        int cols = Math.max(1, (w + gapPx) / (TILE_SIZE + gapPx));
        String current = module.style.get();
        for (int i = 0; i < CrosshairModule.STYLES.length; i++) {
            String id = CrosshairModule.STYLES[i];
            int tx = x + (i % cols) * (TILE_SIZE + gapPx);
            int ty = y + (i / cols) * (TILE_SIZE + gapPx);
            boolean hover = in(mx, my, tx, ty, TILE_SIZE, TILE_SIZE);
            tile(g, tx, ty, id.equals(current), hover);
            List<int[]> parts = CrosshairModule.shape(id, 4, 2, 1, false);
            int c = 0xFFFFFFFF;
            g.push();
            g.translate(tx + TILE_SIZE / 2f, ty + TILE_SIZE / 2f);
            g.scale(2);
            for (int[] r : parts) {
                g.fill(r[0], r[1], r[2], r[3], c);
            }
            g.pop();
            if (hover) {
                tooltip = STYLE_NAMES.get(id)[de ? 0 : 1];
            }
            hits.add(new Hit(tx, ty, TILE_SIZE, TILE_SIZE, () -> module.style.set(id)));
        }
        int rows = (CrosshairModule.STYLES.length + cols - 1) / cols;
        int ty = y + rows * (TILE_SIZE + gapPx) + 2;
        g.text(t("Größe, Abstand und Dicke stellst du unten ein.", "Set size, gap and thickness below."), x, ty, DIM, false);
        return ty + 10 - y;
    }

    private int presets(HudGraphics g, int x, int y, int w, int mx, int my) {
        int gapPx = 4;
        int cols = Math.max(1, (w + gapPx) / (TILE_SIZE + gapPx));
        String current = module.preset.get();
        List<CrosshairPresets.Preset> all = CrosshairPresets.all();
        for (int i = 0; i < all.size(); i++) {
            CrosshairPresets.Preset p = all.get(i);
            int tx = x + (i % cols) * (TILE_SIZE + gapPx);
            int ty = y + (i / cols) * (TILE_SIZE + gapPx);
            boolean hover = in(mx, my, tx, ty, TILE_SIZE, TILE_SIZE);
            tile(g, tx, ty, p.id().equals(current), hover);
            g.push();
            // two screen pixels per grid pixel, centred: the 15 px grid takes 30 of the 34 px
            g.translate(tx + TILE_SIZE / 2f - 1, ty + TILE_SIZE / 2f - 1);
            g.scale(2);
            CrosshairModule.bitmap(g, p.pixels(), 0xFFFFFFFF, 0);
            g.pop();
            if (hover) {
                tooltip = de ? p.nameDe() : p.nameEn();
            }
            hits.add(new Hit(tx, ty, TILE_SIZE, TILE_SIZE, () -> module.preset.set(p.id())));
        }
        int rows = (all.size() + cols - 1) / cols;
        int ty = y + rows * (TILE_SIZE + gapPx) + 2;
        String label = t("Als eigenes Fadenkreuz bearbeiten", "Edit as custom crosshair");
        button(g, x, ty, g.textWidth(label) + 16, label, mx, my, () -> {
            module.setCustomPixels(CrosshairPresets.get(module.preset.get()).pixels().clone());
            module.mode.set("custom");
        });
        return ty + 16 - y;
    }

    private int custom(HudGraphics g, int x, int y, int w, int mx, int my) {
        int n = CrosshairPresets.GRID;
        int size = n * CELL;
        gridX = x;
        gridY = y;
        boolean[] px = module.customPixels();
        g.roundedRect(x - 1, y - 1, size + 2, size + 2, 2, BORDER);
        for (int cy = 0; cy < n; cy++) {
            for (int cx = 0; cx < n; cx++) {
                int x1 = x + cx * CELL;
                int y1 = y + cy * CELL;
                boolean centre = cx == n / 2 && cy == n / 2;
                int bg = ((cx + cy) % 2 == 0) ? CHECK_A : CHECK_B;
                g.fill(x1, y1, x1 + CELL, y1 + CELL, centre ? LumoraTheme.mix(bg, LumoraTheme.accent(), 0.35f) : bg);
                if (px[cy * n + cx]) {
                    g.fill(x1, y1, x1 + CELL, y1 + CELL, module.color.argb() | 0xFF000000);
                }
            }
        }
        int cell = cellAt(mx, my);
        if (cell >= 0) {
            int x1 = x + (cell % n) * CELL;
            int y1 = y + (cell / n) * CELL;
            g.outline(x1, y1, CELL, CELL, LumoraTheme.accentHover());
            tooltip = t("Linksklick malt, Rechtsklick radiert", "Left click paints, right click erases");
        }
        // buttons right of the grid (below when narrow)
        int bx = x + size + 12;
        int by = y;
        int bw = Math.min(150, w - size - 12);
        if (bw < 90) {
            bx = x;
            by = y + size + 8;
            bw = Math.min(150, w);
        }
        button(g, bx, by, bw, t("Spiegeln: ", "Mirror: ") + (mirror ? t("AN", "ON") : t("AUS", "OFF")), mx, my,
                () -> mirror = !mirror);
        button(g, bx, by + 20, bw, t("Leeren", "Clear"), mx, my,
                () -> module.setCustomPixels(new boolean[n * n]));
        button(g, bx, by + 40, bw, t("Umkehren", "Invert"), mx, my, () -> {
            boolean[] inv = module.customPixels().clone();
            for (int i = 0; i < inv.length; i++) {
                inv[i] = !inv[i];
            }
            module.setCustomPixels(inv);
        });
        button(g, bx, by + 60, bw, t("Vorlage übernehmen", "Load preset"), mx, my,
                () -> module.setCustomPixels(CrosshairPresets.get(module.preset.get()).pixels().clone()));
        int count = 0;
        for (boolean b : px) {
            count += b ? 1 : 0;
        }
        g.text(count + t(" Pixel", " pixels"), bx, by + 84, DIM, false);
        return Math.max(size, by + 94 - y);
    }

    private void hueBar(HudGraphics g, int x, int y, int w, int mx, int my) {
        g.text(t("Farbe", "Colour"), x, y + 3, MUTED, false);
        int lx = x + 44;
        int lw = w - 44 - 22;
        hueX = lx;
        hueY = y;
        hueW = lw;
        g.roundedRect(lx - 1, y - 1, lw + 2, 14, 3, BORDER);
        for (int i = 0; i < lw; i++) {
            g.fill(lx + i, y, lx + i + 1, y + 12, hsv(i / (float) Math.max(1, lw - 1)));
        }
        int c = module.color.argb();
        float hue = hue(c);
        if (hue >= 0) {
            int kx = lx + Math.round(hue * (lw - 1));
            g.fill(kx - 2, y - 2, kx + 2, y + 14, 0xFF000000);
            g.fill(kx - 1, y - 1, kx + 1, y + 13, 0xFFFFFFFF);
        }
        // swatch with the colour (white/black stay in the colour row below)
        g.roundedRect(x + w - 16, y - 1, 16, 14, 3, BORDER);
        g.fill(x + w - 15, y, x + w - 1, y + 12, c | 0xFF000000);
        if (in(mx, my, lx, y - 2, lw, 16)) {
            tooltip = t("Farbe wählen (Weiß, Schwarz und Deckkraft unten bei „Farbe“)",
                    "Pick a colour (white, black and opacity below at \"Colour\")");
        }
    }

    private void tile(HudGraphics g, int x, int y, boolean selected, boolean hover) {
        int border = selected ? LumoraTheme.accent() : hover ? 0xFF4A4458 : BORDER;
        g.roundedRect(x, y, TILE_SIZE, TILE_SIZE, 4, border);
        g.roundedRect(x + 1, y + 1, TILE_SIZE - 2, TILE_SIZE - 2, 3, selected ? LumoraTheme.mix(TILE, LumoraTheme.accent(), 0.18f) : TILE);
    }

    private void button(HudGraphics g, int x, int y, int w, String label, int mx, int my, Runnable action) {
        boolean hover = in(mx, my, x, y, w, 16);
        g.card(x, y, w, 16, 4, hover ? LumoraTheme.alpha(LumoraTheme.accent(), 0x50) : FIELD,
                hover ? LumoraTheme.accent() : 0xFF3A3450);
        g.centeredText(label, x + w / 2, y + 4, hover ? 0xFFFFFFFF : TEXT, false);
        hits.add(new Hit(x, y, w, 16, action));
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    private int cellAt(double mx, double my) {
        if (gridX < 0) {
            return -1;
        }
        int n = CrosshairPresets.GRID;
        int cx = (int) Math.floor((mx - gridX) / CELL);
        int cy = (int) Math.floor((my - gridY) / CELL);
        return cx >= 0 && cy >= 0 && cx < n && cy < n ? cy * n + cx : -1;
    }

    private void paint(int cell) {
        int n = CrosshairPresets.GRID;
        boolean[] px = module.customPixels().clone();
        int cx = cell % n;
        int cy = cell / n;
        px[cy * n + cx] = paintValue;
        if (mirror) {
            px[cy * n + (n - 1 - cx)] = paintValue;
            px[(n - 1 - cy) * n + cx] = paintValue;
            px[(n - 1 - cy) * n + (n - 1 - cx)] = paintValue;
        }
        module.setCustomPixels(px);
    }

    private void pickHue(double mx) {
        float f = (float) Math.max(0, Math.min(1, (mx - hueX) / Math.max(1, hueW - 1)));
        int alpha = module.color.argb() & 0xFF000000;
        module.color.set(alpha | (hsv(f) & 0xFFFFFF));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        int cell = cellAt(x, y);
        if (cell >= 0 && (button == 0 || button == 1)) {
            paintValue = button == 0 && !module.customPixels()[cell];
            drag = "paint";
            paint(cell);
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (hueX >= 0 && x >= hueX - 2 && x < hueX + hueW + 2 && y >= hueY - 2 && y < hueY + 14) {
            drag = "hue";
            pickHue(x);
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(x, y)) {
                drag = null;
                hits.get(i).action().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseDragged(double x, double y, int button) {
        if ("paint".equals(drag)) {
            int cell = cellAt(x, y);
            if (cell >= 0) {
                paint(cell);
            }
        } else if ("hue".equals(drag)) {
            pickHue(x);
        }
    }

    @Override
    public void mouseReleased() {
        drag = null;
    }

    // ------------------------------------------------------------------ colours

    /** Fully saturated colour of the hue {@code f} (0..1). */
    static int hsv(float f) {
        float h = (f % 1f) * 6f;
        int i = (int) h;
        float r = h - i;
        int up = Math.round(255 * r);
        int down = 255 - up;
        int rgb = switch (i) {
            case 0 -> 0xFF0000 | up << 8;
            case 1 -> down << 16 | 0xFF00;
            case 2 -> 0xFF00 | up;
            case 3 -> down << 8 | 0xFF;
            case 4 -> up << 16 | 0xFF;
            default -> 0xFF0000 | down;
        };
        return 0xFF000000 | rgb;
    }

    /** Hue (0..1) of a colour, or -1 for grey, white and black. */
    static float hue(int argb) {
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min < 16) {
            return -1;
        }
        float d = max - min;
        float h;
        if (max == r) {
            h = ((g - b) / d) % 6f;
        } else if (max == g) {
            h = (b - r) / d + 2f;
        } else {
            h = (r - g) / d + 4f;
        }
        h /= 6f;
        return h < 0 ? h + 1 : h;
    }
}
