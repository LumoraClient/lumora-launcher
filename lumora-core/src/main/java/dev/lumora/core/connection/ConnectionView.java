package dev.lumora.core.connection;

import dev.lumora.core.theme.LumoraTheme;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared look of the Lumora connection screens (same colours, background and buttons as the server list), plus the
 * click areas of the frame just drawn.
 */
abstract class ConnectionView implements AddonView {
    static final int TEXT = 0xFFE8EEFA;
    static final int MUTED = 0xFF8D9BB8;
    static final int DIM = 0xFF6B7280;
    static final int SURFACE = 0xE00D1424;
    static final int SURFACE_2 = 0xFF16213A;
    static final int BORDER = 0xFF22314F;
    static final int OK = 0xFF3DD68C;
    static final int WARN = 0xFFF5B342;
    static final int FAIL = 0xFFF2555A;
    static final TextureRef BACKGROUND = new TextureRef("lumora", "textures/gui/background_menu.png");
    static final TextureRef LOGO = new TextureRef("lumora", "icon.png");

    enum Style { PRIMARY, SECONDARY, GHOST }

    private record Hit(int x1, int y1, int x2, int y2, Runnable action) {
        boolean contains(double x, double y) {
            return x >= x1 && x < x2 && y >= y1 && y < y2;
        }
    }

    final Platform platform;
    final ConnectionModule module;
    private final boolean de;
    private final List<Hit> hits = new ArrayList<>();

    ConnectionView(Platform platform, ConnectionModule module) {
        this.platform = platform;
        this.module = module;
        String lang = platform.translations() == null ? "" : platform.translations().language();
        this.de = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("de");
    }

    final String t(String german, String english) {
        return de ? german : english;
    }

    abstract void draw(HudGraphics g, int width, int height, int mx, int my);

    @Override
    public final void render(HudGraphics g, int width, int height, int mouseX, int mouseY) {
        hits.clear();
        background(g, width, height);
        draw(g, width, height, mouseX, mouseY);
    }

    static void background(HudGraphics g, int w, int h) {
        float s = Math.max(w / 1920f, h / 1080f);
        int rw = Math.min(1920, Math.round(w / s));
        int rh = Math.min(1080, Math.round(h / s));
        g.texture(BACKGROUND, 0, 0, w, h, (1920 - rw) / 2f, (1080 - rh) / 2f, rw, rh, 1920, 1080);
        g.fill(0, 0, w, h, 0x90070B14);
    }

    /** Logo and "LUMORA" with a subtitle, centred above {@code y}. */
    static void brand(HudGraphics g, int cx, int y, String subtitle) {
        int w = 22 + 6 + g.textWidth("LUMORA") * 3 / 2;
        int x = cx - w / 2;
        g.texture(LOGO, x, y, 22, 22, 128, 128);
        g.push();
        g.translate(x + 28, y + 4);
        g.scale(1.5f);
        g.text("§lLUMORA", 0, 0, TEXT, true);
        g.pop();
        g.centeredText(subtitle, cx, y + 27, LumoraTheme.accentHover(), false);
    }

    final boolean button(HudGraphics g, int x, int y, int w, int h, String label, Style style, int mx, int my, Runnable action) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        // the button design chosen in the hub (Themes) also applies here; ghost buttons stay plain
        boolean styled = (style == Style.PRIMARY || style == Style.SECONDARY)
                && dev.lumora.core.extras.ButtonStyleModule.drawViewButton(g, this, label, x, y, w, h, hover);
        if (!styled) switch (style) {
            case PRIMARY -> g.roundedRect(x, y, w, h, 4, hover ? LumoraTheme.accentHover() : LumoraTheme.accent());
            case SECONDARY -> g.card(x, y, w, h, 4, hover ? 0xF0232F40 : 0xE016213A, hover ? LumoraTheme.accent() : BORDER);
            case GHOST -> {
                if (hover) {
                    g.roundedRect(x, y, w, h, 3, 0x40FFFFFF);
                }
            }
        }
        int fg = style == Style.GHOST ? (hover ? TEXT : MUTED) : TEXT;
        g.text(label, x + (w - g.textWidth(label)) / 2 + 1, y + (h - 8) / 2, fg, false);
        hits.add(new Hit(x, y, x + w, y + h, action));
        return hover;
    }

    /** A small switch with a label to its left; returns the x where the label starts. */
    final int toggle(HudGraphics g, int right, int y, String label, boolean on, int mx, int my, Runnable action) {
        int sw = 20;
        int lw = g.textWidth(label);
        int x = right - sw;
        boolean hover = mx >= x - lw - 6 && mx < right && my >= y && my < y + 10;
        g.roundedRect(x, y, sw, 10, 5, on ? LumoraTheme.accent() : 0xFF313D50);
        g.roundedRect(on ? x + sw - 9 : x + 1, y + 1, 8, 8, 4, 0xFFFFFFFF);
        g.text(label, x - lw - 6, y + 1, hover ? TEXT : MUTED, false);
        hits.add(new Hit(x - lw - 6, y - 2, right, y + 12, action));
        return x - lw - 6;
    }

    /** Animated three-dot spinner centred at (cx, cy). */
    static void spinner(HudGraphics g, int cx, int cy, int color) {
        long t = System.currentTimeMillis() / 160;
        for (int i = 0; i < 3; i++) {
            boolean lit = (t % 3) == i;
            int c = lit ? color : (color & 0x00FFFFFF) | 0x50000000;
            g.roundedRect(cx - 7 + i * 5, cy - 1, 3, 3, 1, c);
        }
    }

    static List<String> wrap(HudGraphics g, String text, int max) {
        List<String> out = new ArrayList<>();
        String carry = "";
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder(carry);
            for (String word : para.split(" ")) {
                String next = line.length() == carry.length() ? line + word : line + " " + word;
                if (g.textWidth(next) > max && line.length() > carry.length()) {
                    out.add(line.toString());
                    line = new StringBuilder(lastFormat(line.toString()) + word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            out.add(line.toString());
            carry = lastFormat(line.toString());
        }
        return out;
    }

    /** The colour / format codes still active at the end of {@code s} (so wrapped lines keep their colour). */
    static String lastFormat(String s) {
        String color = "";
        StringBuilder fmt = new StringBuilder();
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) == '§') {
                char c = Character.toLowerCase(s.charAt(i + 1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || c == 'r') {
                    color = c == 'r' ? "" : "§" + c;
                    fmt.setLength(0);
                } else if (c >= 'k' && c <= 'o') {
                    fmt.append('§').append(c);
                }
            }
        }
        return color + fmt;
    }

    /** "1,4 s" / "320 ms". */
    final String duration(long millis) {
        if (millis < 0) {
            return "";
        }
        if (millis < 1000) {
            return millis + " ms";
        }
        if (millis < 60_000) {
            String s = String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
            return de ? s.replace('.', ',') : s;
        }
        long m = millis / 60_000;
        long sec = millis / 1000 % 60;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min " + sec + " s";
    }

    /** What the address line shows: the address, or a placeholder when "hide addresses" (stream mode) is on. */
    final String shown(String address) {
        if (address == null || address.isEmpty()) {
            return t("unbekannt", "unknown");
        }
        return module.hideAddress() ? t("verborgen", "hidden") : address;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) {
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(x, y)) {
                hits.get(i).action().run();
                return true;
            }
        }
        return false;
    }
}
