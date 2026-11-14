package dev.lumora.api.ui;

import dev.lumora.api.render.HudGraphics;

/**
 * One look for all HUD widgets, set by the player in the hub (module "Widget-Design"), read with
 * {@code AddonContext.theme().widgetStyle()}. While {@link #active()}, a widget should draw with these values instead
 * of its own look settings (and may hide them, e.g. with {@code visibleWhen(() -> !style.active())}); the easiest way
 * is {@link #panel} for the background and {@link #line} for label and value. Values are read fresh on every call.
 * The size of all widgets ({@link #scale()}) is applied by Lumora itself, also to addon widgets.
 * <pre>{@code
 * WidgetStyle style = context.theme().widgetStyle();
 * if (style.active()) {
 *     int w = style.lineWidth(g, "Session", text) + 12;
 *     style.panel(g, w, 17);
 *     style.line(g, 6, 5, "Session", text);
 * } else {
 *     // the widget's own look
 * }
 * }</pre>
 *
 * @since 1.6.0
 */
public interface WidgetStyle {
    /** Label and value: "FPS 60". */
    String SPACE = "space";
    /** "FPS: 60". */
    String COLON = "colon";
    /** "[FPS] 60". */
    String BRACKETS = "brackets";
    /** "60 FPS". */
    String VALUE_FIRST = "value_first";
    /** Only the value: "60". */
    String VALUE_ONLY = "value_only";

    /** Whether the player set one look for all widgets; while false, use the widget's own settings. */
    boolean active();

    /** Whether widgets have a background. */
    boolean background();

    /** Background colour (ARGB, usually translucent). */
    int backgroundColor();

    /** Corner radius of backgrounds and borders in GUI pixels (0: square). */
    int cornerRadius();

    boolean border();

    int borderColor();

    /** The thin stripe in the accent colour on the left. */
    boolean accentBar();

    /** Colour of the stripe (and of highlights such as pressed keys). */
    int accentColor();

    int labelColor();

    int valueColor();

    boolean textShadow();

    /** How label and value are shown: {@link #SPACE}, {@link #COLON}, {@link #BRACKETS}, {@link #VALUE_FIRST} or {@link #VALUE_ONLY}. */
    String format();

    boolean uppercaseLabels();

    /** Size of all widgets (applied by Lumora; 1 while not active). */
    float scale();

    /** {@code label} as the format shows it ("FPS:", "[FPS]", capitals); "" when the format shows no label. */
    String label(String label);

    /** Background, border and accent stripe at (0, 0) with the given size; draws nothing while not {@link #active()}. */
    void panel(HudGraphics g, int width, int height);

    /** Width of one line of label and value as {@link #line} draws it. */
    int lineWidth(HudGraphics g, String label, String value);

    /** Label and value in the chosen format, colours and shadow ({@code label} may be empty). */
    void line(HudGraphics g, int x, int y, String label, String value);

    /** Not active: Lumora's default look (for clients before 1.6 and tests). */
    WidgetStyle NONE = new WidgetStyle() {
        @Override public boolean active() { return false; }
        @Override public boolean background() { return true; }
        @Override public int backgroundColor() { return 0x9C070B14; }
        @Override public int cornerRadius() { return 0; }
        @Override public boolean border() { return false; }
        @Override public int borderColor() { return 0x40FFFFFF; }
        @Override public boolean accentBar() { return true; }
        @Override public int accentColor() { return 0xFF3B82F6; }
        @Override public int labelColor() { return 0xFF5E9BF8; }
        @Override public int valueColor() { return 0xFFE8EEFA; }
        @Override public boolean textShadow() { return true; }
        @Override public String format() { return SPACE; }
        @Override public boolean uppercaseLabels() { return false; }
        @Override public float scale() { return 1f; }
        @Override public String label(String label) { return label == null ? "" : label; }
        @Override public void panel(HudGraphics g, int width, int height) { }

        @Override
        public int lineWidth(HudGraphics g, String label, String value) {
            return label == null || label.isEmpty() ? g.textWidth(value) : g.textWidth(label) + 4 + g.textWidth(value);
        }

        @Override
        public void line(HudGraphics g, int x, int y, String label, String value) {
            if (label != null && !label.isEmpty()) {
                g.text(label, x, y, labelColor(), true);
                x += g.textWidth(label) + 4;
            }
            g.text(value, x, y, valueColor(), true);
        }
    };
}
