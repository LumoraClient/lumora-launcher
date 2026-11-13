package dev.lumora.core.hud;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.theme.LumoraTheme;

import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * One look for all HUD widgets (module "Widget-Design"): while it is on, background, border, corners, accent stripe,
 * label and value colours, text shadow, the format of label and value and the size of every widget come from here
 * instead of each widget's own settings. Lumora's widgets ask it where they draw; addons read it through
 * {@code AddonContext.theme().widgetStyle()}; the size applies to every widget (also addon widgets) in
 * {@link HudRenderer}. Off: everything returns the widget's own values.
 */
public final class WidgetDesign {
    /** Label and value: "FPS 60". */
    public static final String SPACE = "space";
    /** "FPS: 60". */
    public static final String COLON = "colon";
    /** "[FPS] 60". */
    public static final String BRACKETS = "brackets";
    /** "60 FPS". */
    public static final String VALUE_FIRST = "value_first";
    /** "60". */
    public static final String VALUE_ONLY = "value_only";
    /** Gap between label and value in GUI pixels (as the widgets had it). */
    public static final int GAP = 4;
    private static final long REFRESH_NANOS = 50_000_000L;

    /**
     * The RGB effect: the chosen parts cycle through the colours of the rainbow. {@code speed} 1 (one cycle in 12 s) to
     * 10 (1.2 s), {@code saturation} and {@code brightness} in percent; {@code wave}: each widget is a little further
     * along the rainbow than the one to its left and above, so the colours run across the screen.
     */
    public record Rgb(boolean on, boolean accent, boolean border, boolean label, boolean value, int speed,
                      int saturation, int brightness, boolean wave) {
        public static final Rgb OFF = new Rgb(false, false, false, false, false, 4, 100, 100, false);

        /** The rainbow colour now (with {@code alpha} of the colour it replaces). */
        int color(int alpha) {
            double hue = System.currentTimeMillis() * Math.max(1, speed) / 12000.0 + (wave ? waveOffset : 0);
            return (alpha & 0xFF000000) | (hsv((float) (hue % 1.0), saturation / 100f, brightness / 100f) & 0xFFFFFF);
        }
    }

    /**
     * The chosen look. {@code accentColor} and {@code labelColor} 0: the accent of the theme (follows a change of the
     * accent at once). The colour methods ({@link #accent()}, {@link #label()}, {@link #value()},
     * {@link #borderNow()}) include the RGB effect and change from frame to frame while it is on.
     */
    public record Look(boolean background, int backgroundColor, int radius, boolean border, int borderColor,
                       boolean accentBar, int accentColor, int labelColor, int valueColor, boolean shadow,
                       String format, boolean uppercase, float scale, Rgb rgb) {
        public Look(boolean background, int backgroundColor, int radius, boolean border, int borderColor,
                    boolean accentBar, int accentColor, int labelColor, int valueColor, boolean shadow,
                    String format, boolean uppercase, float scale) {
            this(background, backgroundColor, radius, border, borderColor, accentBar, accentColor, labelColor, valueColor,
                    shadow, format, uppercase, scale, Rgb.OFF);
        }

        public int accent() {
            if (rgb.on && rgb.accent) {
                return rgb.color(0xFF000000);
            }
            return accentColor == 0 ? LumoraTheme.accent() : accentColor;
        }

        public int label() {
            if (rgb.on && rgb.label) {
                return rgb.color(0xFF000000);
            }
            return labelColor == 0 ? LumoraTheme.accentHover() : labelColor;
        }

        public int value() {
            return rgb.on && rgb.value ? rgb.color(valueColor) : valueColor;
        }

        /** The border colour now (RGB keeps the alpha of the chosen border colour). */
        public int borderNow() {
            return rgb.on && rgb.border ? rgb.color(borderColor) : borderColor;
        }

        /** The label as drawn (capitals, colon, brackets); "" when the format shows no label. */
        public String label(String label) {
            if (label == null || label.isEmpty() || VALUE_ONLY.equals(format)) {
                return "";
            }
            String l = uppercase ? label.toUpperCase(Locale.ROOT) : label;
            return switch (format) {
                case COLON -> l.endsWith(":") ? l : l + ":";
                case BRACKETS -> "[" + l + "]";
                default -> l;
            };
        }
    }

    /** Position of the widget being drawn along the rainbow ("wave"), set by the HUD before each widget. */
    private static volatile double waveOffset;
    private static volatile Supplier<Look> source;
    private static volatile Look cached;
    private static volatile long cachedAt;

    private WidgetDesign() {
    }

    /** The module switches the design on ({@code look} gives the current settings) or off ({@code null}). */
    public static void use(Supplier<Look> look) {
        source = look;
        cached = null;
    }

    /**
     * Where the widget about to be drawn sits (screen GUI pixels): with the RGB "wave", widgets further right and
     * further down are further along the rainbow (one cycle per 400 pixels).
     */
    public static void position(int x, int y) {
        waveOffset = (x + y) / 400.0;
    }

    /** Colour of hue {@code h} (0..1) at {@code s} saturation and {@code v} brightness (0..1), opaque. */
    static int hsv(float h, float s, float v) {
        float x = (h % 1f + 1f) % 1f * 6f;
        int i = (int) x;
        float f = x - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    /** A setting of the design changed: the next call reads the look again. */
    public static void changed() {
        cached = null;
    }

    /** The current look, or null while the design is off. Read again at most every 50 ms (settings are cheap to change). */
    public static Look look() {
        Supplier<Look> s = source;
        if (s == null) {
            return null;
        }
        Look l = cached;
        long now = System.nanoTime();
        if (l == null || now - cachedAt > REFRESH_NANOS) {
            l = s.get();
            cached = l;
            cachedAt = now;
        }
        return l;
    }

    /** Heading of a widget's own settings ("Anzeigen"). */
    public static final String SECTION_SHOW = "lumora.widget.section.show";
    /** Heading of the shared look settings of a widget (background, accent stripe, colours). */
    public static final String SECTION_LOOK = "lumora.widget.section.look";
    /** Heading of the shared look settings when the widget has its own sections (Keystrokes: "Widget"). */
    public static final String SECTION_PANEL = "lumora.widget.section.panel";

    /**
     * Sorts a widget's settings: its own settings (added since {@code before}) come first under "Anzeigen" unless they
     * have their own sections; returns the heading for the shared look settings that follow.
     */
    public static String ownSettingsFirst(dev.lumora.api.config.SettingsGroup s, int before) {
        java.util.List<dev.lumora.api.config.Setting<?>> all = s.all();
        if (all.size() <= before) {
            return SECTION_LOOK;
        }
        for (int i = before; i < all.size(); i++) {
            if (all.get(i).sectionKey() != null) {
                return SECTION_PANEL;
            }
        }
        all.get(before).section(SECTION_SHOW);
        return SECTION_LOOK;
    }

    public static boolean active() {
        return source != null;
    }

    /** Size of all widgets (1 while off), multiplied with each widget's own size. */
    public static float scale() {
        Look l = look();
        return l == null ? 1f : l.scale;
    }

    /** Whether the widget draws a background: the design's choice, else its own. */
    public static boolean background(boolean own) {
        Look l = look();
        return l == null ? own : l.background;
    }

    public static boolean shadow(boolean own) {
        Look l = look();
        return l == null ? own : l.shadow;
    }

    public static int labelColor(int own) {
        Look l = look();
        return l == null ? own : l.label();
    }

    public static int valueColor(int own) {
        Look l = look();
        return l == null ? own : l.value();
    }

    /**
     * Background, border and accent stripe of a panel widget in the design. Returns false while the design is off
     * (nothing drawn: the widget draws its own panel).
     */
    public static boolean panel(HudGraphics g, int w, int h) {
        Look l = look();
        if (l == null) {
            return false;
        }
        box(g, l, 0, 0, w, h);
        if (l.accentBar) {
            int r = Math.min(l.radius, h / 2);
            g.fill(0, r, 2, h - r, l.accent());
        }
        return true;
    }

    /**
     * The box behind a widget (status bars, keys): the design's background and border, else a rounded box of the
     * widget's own colour.
     */
    public static void box(HudGraphics g, int x, int y, int w, int h, int ownRadius, int ownColor) {
        Look l = look();
        if (l == null) {
            g.roundedRect(x, y, w, h, ownRadius, ownColor);
        } else {
            box(g, l, x, y, w, h);
        }
    }

    /**
     * The track behind a bar (always drawn, a bar needs it): the design's background colour (half as strong when its
     * background is off), corners and border, else the bar's own.
     */
    public static void track(HudGraphics g, int x, int y, int w, int h, int ownRadius, int ownColor) {
        Look l = look();
        if (l == null) {
            g.roundedRect(x, y, w, h, ownRadius, ownColor);
            return;
        }
        int c = l.backgroundColor;
        if (!l.background) {
            c = (c & 0xFFFFFF) | ((c >>> 24) / 2) << 24;
        }
        g.roundedRect(x, y, w, h, l.radius, c);
        if (l.border) {
            outline(g, x, y, w, h, l.radius, l.borderNow());
        }
    }

    private static void box(HudGraphics g, Look l, int x, int y, int w, int h) {
        if (l.background) {
            g.roundedRect(x, y, w, h, l.radius, l.backgroundColor);
        }
        if (l.border) {
            outline(g, x, y, w, h, l.radius, l.borderNow());
        }
    }

    /** A one pixel border with rounded corners (the inside stays free, so a translucent background stays even). */
    static void outline(HudGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        int previous = r;
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - d * d));
            int end = Math.max(inset + 1, previous);
            g.fill(x + inset, y + i, x + end, y + i + 1, color);
            g.fill(x + w - end, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + end, y + h - i, color);
            g.fill(x + w - end, y + h - 1 - i, x + w - inset, y + h - i, color);
            previous = inset;
        }
    }

    // ------------------------------------------------------------------ label and value

    /** Width of one line of label and value as {@link #line} draws it ({@code width}: the font's text width). */
    public static int lineWidth(ToIntFunction<String> width, String label, String value) {
        Look l = look();
        if (l == null) {
            return label.isEmpty() ? width.applyAsInt(value) : width.applyAsInt(label) + GAP + width.applyAsInt(value);
        }
        String lb = l.label(label);
        return lb.isEmpty() ? width.applyAsInt(value) : width.applyAsInt(lb) + GAP + width.applyAsInt(value);
    }

    /**
     * Draws label and value: in the design's format, colours and shadow, else as the widgets always did (label, gap,
     * value) with their own colours. {@code valueColor} is used even with the design when {@code fixedValue} (a value
     * whose colour means something, e.g. a green or red ping).
     */
    public static void line(HudGraphics g, int x, int y, String label, String value, int labelColor, int valueColor,
                            boolean shadow, boolean fixedValue) {
        Look l = look();
        if (l == null) {
            if (!label.isEmpty()) {
                g.text(label, x, y, labelColor, shadow);
                x += g.textWidth(label) + GAP;
            }
            g.text(value, x, y, valueColor, shadow);
            return;
        }
        String lb = l.label(label);
        int vc = fixedValue ? valueColor : l.value();
        if (lb.isEmpty()) {
            g.text(value, x, y, vc, l.shadow);
        } else if (VALUE_FIRST.equals(l.format)) {
            g.text(value, x, y, vc, l.shadow);
            g.text(lb, x + g.textWidth(value) + GAP, y, l.label(), l.shadow);
        } else {
            g.text(lb, x, y, l.label(), l.shadow);
            g.text(value, x + g.textWidth(lb) + GAP, y, vc, l.shadow);
        }
    }
}
