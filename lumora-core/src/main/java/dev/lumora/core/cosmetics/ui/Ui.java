package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;

import java.util.ArrayList;
import java.util.List;

/** Shared look and small controls of the cosmetics views (colours as the other Lumora windows). */
final class Ui {
    static final int TEXT = 0xFFE8EEFA;
    static final int MUTED = 0xFF8D9BB8;
    static final int DIM = 0xFF5D6878;
    static final int PANEL = 0xF2131821;
    static final int CARD = 0xFF181D27;
    static final int ROW = 0xFF202633;
    static final int ROW_HOVER = 0xFF283041;
    static final int BORDER = 0xFF2C3444;
    static final int FIELD = 0xFF0B0F15;
    static final int ACCENT = 0xFF5B8CFF;
    static final int ACCENT_DIM = 0xFF2B4280;
    static final int GOOD = 0xFF5FD068;
    static final int STAR = 0xFFFFC94A;
    static final int DANGER = 0xFFD03755;
    static final int BACKDROP = 0xA0000000;

    private Ui() {
    }

    /** A clickable rectangle registered while drawing. */
    record Region(int x, int y, int w, int h, Runnable left, Runnable right) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** Regions of one frame; the last added (topmost) wins. */
    static final class Regions {
        private final List<Region> list = new ArrayList<>();

        void clear() {
            list.clear();
        }

        void add(int x, int y, int w, int h, Runnable left) {
            list.add(new Region(x, y, w, h, left, null));
        }

        void add(int x, int y, int w, int h, Runnable left, Runnable right) {
            list.add(new Region(x, y, w, h, left, right));
        }

        boolean click(double mx, double my, int button) {
            for (int i = list.size() - 1; i >= 0; i--) {
                Region r = list.get(i);
                if (r.contains(mx, my)) {
                    Runnable a = button == 1 ? r.right : r.left;
                    if (a != null) {
                        a.run();
                        return true;
                    }
                    return button == 0;
                }
            }
            return false;
        }
    }

    static boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Text cut with "…" to fit {@code width}. */
    static String fit(HudGraphics g, String s, int width) {
        if (s == null) {
            return "";
        }
        if (g.textWidth(s) <= width) {
            return s;
        }
        String dots = "…";
        int end = s.length();
        while (end > 0 && g.textWidth(s.substring(0, end) + dots) > width) {
            end--;
        }
        return s.substring(0, end) + dots;
    }

    /** Text broken into lines of at most {@code width}. */
    static List<String> wrap(HudGraphics g, String s, int width, int maxLines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty() || maxLines <= 0) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (g.textWidth(next) > width && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (!line.isEmpty()) {
            out.add(line.toString());
        }
        if (out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            cut.set(maxLines - 1, fit(g, cut.get(maxLines - 1) + " " + out.get(maxLines), width));
            return cut;
        }
        return out;
    }

    /** A button; returns its width. */
    static int button(HudGraphics g, Regions regions, int x, int y, int w, int h, String label, boolean primary, boolean enabled,
                      int mx, int my, Runnable action) {
        boolean hot = enabled && hover(mx, my, x, y, w, h);
        int fill = !enabled ? ROW : primary ? (hot ? 0xFF6E9BFF : ACCENT) : (hot ? ROW_HOVER : ROW);
        g.card(x, y, w, h, 4, fill, primary && enabled ? 0xFF7FA6FF : BORDER);
        g.centeredText(fit(g, label, w - 6), x + w / 2, y + (h - g.lineHeight()) / 2 + 1, enabled ? TEXT : DIM, false);
        if (enabled && action != null) {
            regions.add(x, y, w, h, action);
        }
        return w;
    }

    /** A tab / chip that can be selected. */
    static void chip(HudGraphics g, Regions regions, int x, int y, int w, int h, String label, boolean selected, int mx, int my,
                     Runnable action) {
        boolean hot = hover(mx, my, x, y, w, h);
        g.roundedRect(x, y, w, h, 3, selected ? ACCENT_DIM : hot ? ROW_HOVER : 0);
        if (selected) {
            g.fill(x, y + 2, x + 2, y + h - 2, ACCENT);
        }
        g.text(fit(g, label, w - 10), x + 7, y + (h - g.lineHeight()) / 2 + 1, selected ? TEXT : hot ? TEXT : MUTED, false);
        regions.add(x, y, w, h, action);
    }

    /** A one-line text field. */
    static final class TextBox {
        final int max;
        final StringBuilder text = new StringBuilder();

        TextBox(int max) {
            this.max = max;
        }

        String get() {
            return text.toString();
        }

        void append(String s) {
            for (char c : (s == null ? "" : s).toCharArray()) {
                if (text.length() < max && c >= 32 && c != 127 && !Character.isISOControl(c)) {
                    text.append(c);
                }
            }
        }

        boolean key(int key, boolean ctrl, String clipboard) {
            if (key == Keys.BACKSPACE) {
                if (ctrl) {
                    text.setLength(0);
                } else if (!text.isEmpty()) {
                    text.setLength(text.length() - 1);
                }
                return true;
            }
            if (ctrl && key == Keys.V) {
                append(clipboard == null ? "" : clipboard.replace('\n', ' '));
                return true;
            }
            return false;
        }

        void draw(HudGraphics g, int x, int y, int w, int h, String placeholder, boolean focused) {
            g.card(x, y, w, h, 4, FIELD, focused ? ACCENT : BORDER);
            String shown = text.isEmpty() ? placeholder : get();
            int ty = y + (h - g.lineHeight()) / 2 + 1;
            String clipped = shown;
            while (!clipped.isEmpty() && g.textWidth(clipped) > w - 12) {
                clipped = clipped.substring(1);
            }
            g.text(clipped, x + 6, ty, text.isEmpty() ? DIM : TEXT, false);
            if (focused && System.currentTimeMillis() / 500 % 2 == 0) {
                int cx = x + 6 + (text.isEmpty() ? 0 : g.textWidth(clipped));
                g.fill(cx, ty - 1, cx + 1, ty + g.lineHeight() - 1, TEXT);
            }
        }
    }

    /** A small star (favourite) drawn with rectangles, filled or as an outline colour. */
    static void star(HudGraphics g, int x, int y, boolean on) {
        int c = on ? STAR : DIM;
        // 7x7 pixel star
        g.fill(x + 3, y, x + 4, y + 2, c);
        g.fill(x, y + 2, x + 7, y + 3, c);
        g.fill(x + 1, y + 3, x + 6, y + 4, c);
        g.fill(x + 2, y + 4, x + 5, y + 5, c);
        g.fill(x + 1, y + 5, x + 3, y + 6, c);
        g.fill(x + 4, y + 5, x + 6, y + 6, c);
        g.fill(x + 1, y + 6, x + 2, y + 7, c);
        g.fill(x + 5, y + 6, x + 6, y + 7, c);
    }

    /** A check mark drawn with rectangles. */
    static void check(HudGraphics g, int x, int y, int color) {
        g.fill(x, y + 3, x + 2, y + 5, color);
        g.fill(x + 1, y + 4, x + 3, y + 6, color);
        g.fill(x + 3, y + 3, x + 4, y + 5, color);
        g.fill(x + 4, y + 2, x + 5, y + 4, color);
        g.fill(x + 5, y + 1, x + 6, y + 3, color);
        g.fill(x + 6, y, x + 7, y + 2, color);
    }
}
