package dev.lumora.launcher.app.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.inter.FlatInterFont;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;

/**
 * Lumora design tokens and look-and-feel setup in a calm, minimal style: deep navy surfaces, small radii, a selectable accent (blue by default) with a
 * second, cooler accent for flowing gradients, Inter typeface, rounded or hard pixel corners. The tokens are set once
 * before the first window is built.
 */
public final class Theme {
    public static Color BG = new Color(0x070B14);
    public static Color SURFACE = new Color(0x0D1424);
    public static Color SURFACE_2 = new Color(0x16213A);
    public static Color BORDER = new Color(0x22314F);
    public static Color TEXT = new Color(0xE8EEFA);
    public static Color MUTED = new Color(0x8D9BB8);
    public static Color ACCENT = new Color(0x3B82F6);
    public static Color ACCENT_HOVER = new Color(0x5E9BF8);
    public static Color ACCENT_PRESSED = new Color(0x306ACA);
    /** Second accent for flowing gradients: the accent shifted towards ice blue / cyan. */
    public static Color ACCENT_2 = new Color(0x22C3EE);
    /** Third, deeper tone (indigo side of the accent) for backdrops and glows. */
    public static Color ACCENT_DEEP = new Color(0x2D4BD8);
    /** Light accent tint for text on accent-coloured chips. */
    public static Color ACCENT_TEXT = new Color(0xCFE0FD);
    public static final Color OK = new Color(0x3DD68C);
    public static final Color WARN = new Color(0xF5B342);
    public static final Color FAIL = new Color(0xF2555A);

    /** Card radius; 0 in pixel style. */
    public static int RADIUS = 12;
    /** Hard pixel corners instead of rounded ones. */
    public static boolean pixelStyle;
    /** No automatic animations (skin rotation, spinners). */
    public static boolean reduceMotion;

    /** Accent presets of the settings page: name and colour. */
    public record Preset(String name, String hex) {
    }

    public static final List<Preset> PRESETS = List.of(
            new Preset("Blau", "#3B82F6"), new Preset("Eisblau", "#38BDF8"), new Preset("Türkis", "#18B4CC"), new Preset("Indigo", "#6366F1"),
            new Preset("Lila", "#8B5CF6"), new Preset("Violett", "#A855F7"), new Preset("Rosa", "#EC4899"), new Preset("Grün", "#22C55E"),
            new Preset("Smaragd", "#10B981"), new Preset("Orange", "#F97316"), new Preset("Gelb", "#F59E0B"), new Preset("Rot", "#EF4444"),
            new Preset("Schiefer", "#64748B"), new Preset("Weiß", "#E5E7EB"));

    private static boolean installed;

    private Theme() {
    }

    /** Must run on the EDT before the first component is created. Idempotent. */
    public static synchronized void install() {
        install(null, false, false);
    }

    /**
     * @param accentHex "#RRGGBB" or null for the default violet
     * @param pixel     hard pixel corners
     * @param calm      reduce motion
     */
    public static synchronized void install(String accentHex, boolean pixel, boolean calm) {
        if (installed) {
            return;
        }
        installed = true;
        setAccent(accentHex);
        pixelStyle = pixel;
        reduceMotion = calm;
        RADIUS = pixel ? 0 : 12;
        FlatInterFont.install();
        FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", hex(ACCENT),
                "@background", hex(BG),
                "@foreground", hex(TEXT)));
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", arc(10));
        UIManager.put("Button.arc", arc(10));
        UIManager.put("TextComponent.arc", arc(10));
        UIManager.put("Component.focusColor", alpha(ACCENT, 160));
        UIManager.put("Button.hoverBackground", mix(SURFACE_2, ACCENT, 0.18));
        UIManager.put("Button.pressedBackground", mix(SURFACE_2, ACCENT, 0.30));
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("Slider.thumbColor", ACCENT);
        UIManager.put("Slider.trackValueColor", ACCENT);
        UIManager.put("CheckBox.icon.selectedBackground", ACCENT);
        UIManager.put("List.selectionBackground", alpha(ACCENT, 90));
        UIManager.put("Table.selectionBackground", alpha(ACCENT, 90));
        UIManager.put("TabbedPane.underlineColor", ACCENT);
        UIManager.put("TabbedPane.selectedBackground", SURFACE_2);
        UIManager.put("CheckBox.arc", arc(4));
        UIManager.put("ProgressBar.arc", pixel ? 0 : 999);
        UIManager.put("ProgressBar.horizontalSize", new java.awt.Dimension(146, 6));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", pixel ? 0 : 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.track", BG);
        UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("TitlePane.background", BG);
        UIManager.put("Panel.background", BG);
        UIManager.put("TextField.background", SURFACE_2);
        UIManager.put("ComboBox.background", SURFACE_2);
        UIManager.put("Button.background", SURFACE_2);
        UIManager.put("Button.borderColor", BORDER);
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Slider.trackWidth", 4);
        UIManager.put("Slider.thumbSize", new java.awt.Dimension(16, 16));
        UIManager.put("ToolTip.background", SURFACE_2);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("PopupMenu.background", SURFACE);
        UIManager.put("MenuItem.background", SURFACE);
        UIManager.put("MenuItem.selectionBackground", alpha(ACCENT, 90));
        UIManager.put("RadioButtonMenuItem.selectionBackground", alpha(ACCENT, 90));
        // segmented controls: the chosen option is filled with the accent (like the reference launchers)
        UIManager.put("ToggleButton.background", SURFACE_2);
        UIManager.put("ToggleButton.selectedBackground", ACCENT);
        UIManager.put("ToggleButton.selectedForeground", onAccent());
        UIManager.put("ToggleButton.toolbar.selectedBackground", ACCENT);
    }

    /** Sets the accent and the colours derived from it. Invalid values keep the default blue. */
    static void setAccent(String accentHex) {
        Color c = parse(accentHex);
        if (c == null) {
            c = new Color(0x3B82F6);
        }
        ACCENT = c;
        ACCENT_2 = mix(c, new Color(0x22D3EE), 0.55);
        ACCENT_DEEP = mix(c, new Color(0x312E81), 0.45);
        ACCENT_HOVER = mix(c, Color.WHITE, 0.18);
        ACCENT_PRESSED = mix(c, Color.BLACK, 0.18);
        ACCENT_TEXT = mix(c, Color.WHITE, 0.72);
    }

    /** "#RRGGBB" -> colour, null if invalid. */
    public static Color parse(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        return new Color(Integer.parseInt(hex.substring(1), 16));
    }

    /** Corner radius for custom painted components: 0 in pixel style. */
    public static int arc(int rounded) {
        return pixelStyle ? 0 : rounded;
    }

    /** The flowing accent gradient (accent -> second accent), shifted by {@code phase} (0..1, repeats). */
    public static java.awt.LinearGradientPaint flow(float x0, float y0, float x1, float y1, double phase, int alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float shift = (float) (phase - Math.floor(phase));
        return new java.awt.LinearGradientPaint(x0 - dx * shift, y0 - dy * shift, x1 - dx * shift, y1 - dy * shift,
                new float[]{0f, 0.5f, 1f}, new Color[]{alpha(ACCENT, alpha), alpha(ACCENT_2, alpha), alpha(ACCENT, alpha)},
                java.awt.MultipleGradientPaint.CycleMethod.REPEAT);
    }

    /** Text colour that stays readable on the accent (dark text on light accents such as white). */
    public static Color onAccent() {
        double l = (0.299 * ACCENT.getRed() + 0.587 * ACCENT.getGreen() + 0.114 * ACCENT.getBlue()) / 255;
        return l > 0.7 ? new Color(0x0D1424) : Color.WHITE;
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Font font(int style, float size) {
        return new Font(FlatInterFont.FAMILY, style, Math.round(size)).deriveFont(size);
    }

    private static Font pixelFont;

    /**
     * Font for titles, navigation and buttons: in the Lumora pixel design (as in the game's Lumora menus) Silkscreen
     * (OFL, the pixel font Lumora also offers in the game) at {@code pixelSize} (8, 16, 24, 32 stay sharp), otherwise
     * Inter with {@code style} and {@code size}.
     */
    public static Font display(float pixelSize, int style, float size) {
        Font f = pixelStyle ? silkscreen() : null;
        return f != null ? f.deriveFont(pixelSize) : font(style, size);
    }

    private static synchronized Font silkscreen() {
        if (pixelFont == null) {
            try (java.io.InputStream in = Theme.class.getResourceAsStream("/lumora/fonts/silkscreen-regular.ttf")) {
                pixelFont = in == null ? null : Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (java.io.IOException | java.awt.FontFormatException e) {
                pixelFont = null;
            }
            if (pixelFont == null) {
                pixelStyle = false; // without the font the modern design stays consistent
                return null;
            }
        }
        return pixelFont;
    }

    /** Size of one "pixel" step of the pixel design's corners. */
    private static final int STEP = 2;

    /**
     * Fills a rounded rectangle; in the pixel design with stepped corners like the game's Lumora menus (two steps on
     * larger shapes, one on small ones) instead of round arcs.
     */
    public static void fillRound(java.awt.Graphics g, int x, int y, int w, int h, int arcW, int arcH) {
        if (!pixelStyle) {
            g.fillRoundRect(x, y, w, h, arcW, arcH);
            return;
        }
        int steps = Math.min(w, h) >= 20 ? 2 : Math.min(w, h) >= 6 ? 1 : 0;
        if (steps == 0) {
            g.fillRect(x, y, w, h);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int inset = (steps - i) * STEP;
            int top = i * STEP;
            g.fillRect(x + inset, y + top, w - 2 * inset, h - 2 * top);
        }
    }

    /**
     * Rounded rectangle as a shape (clips, gradients, outlines); in the pixel design with stepped corners like
     * {@link #fillRound}.
     */
    public static java.awt.Shape roundShape(double x, double y, double w, double h, double arcW, double arcH) {
        if (!pixelStyle) {
            return new java.awt.geom.RoundRectangle2D.Double(x, y, w, h, arcW, arcH);
        }
        int steps = Math.min(w, h) >= 20 ? 2 : Math.min(w, h) >= 6 ? 1 : 0;
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        int x2 = (int) Math.round(x + w);
        int y2 = (int) Math.round(y + h);
        return steps == 0 ? new java.awt.Rectangle(ix, iy, x2 - ix, y2 - iy) : stepped(ix, iy, x2, y2, steps);
    }

    /** Outline matching {@link #fillRound} (1 px). */
    public static void drawRound(java.awt.Graphics g, int x, int y, int w, int h, int arcW, int arcH) {
        if (!pixelStyle) {
            g.drawRoundRect(x, y, w, h, arcW, arcH);
            return;
        }
        int x2 = x + w;
        int y2 = y + h;
        int steps = Math.min(w, h) >= 20 ? 2 : Math.min(w, h) >= 6 ? 1 : 0;
        if (steps == 0) {
            g.drawRect(x, y, w, h);
            return;
        }
        g.drawPolygon(stepped(x, y, x2, y2, steps));
    }

    /** Outline of a rectangle with {@code n} pixel steps per corner, clockwise from the top left. */
    static java.awt.Polygon stepped(int x, int y, int x2, int y2, int n) {
        java.awt.Polygon p = new java.awt.Polygon();
        int s = STEP;
        p.addPoint(x + n * s, y);
        p.addPoint(x2 - n * s, y);
        for (int k = 1; k <= n; k++) {
            p.addPoint(x2 - (n - k + 1) * s, y + k * s);
            p.addPoint(x2 - (n - k) * s, y + k * s);
        }
        p.addPoint(x2, y2 - n * s);
        for (int k = 1; k <= n; k++) {
            p.addPoint(x2 - k * s, y2 - (n - k + 1) * s);
            p.addPoint(x2 - k * s, y2 - (n - k) * s);
        }
        p.addPoint(x + n * s, y2);
        for (int k = 1; k <= n; k++) {
            p.addPoint(x + (n - k + 1) * s, y2 - k * s);
            p.addPoint(x + (n - k) * s, y2 - k * s);
        }
        p.addPoint(x, y + n * s);
        for (int k = 1; k <= n; k++) {
            p.addPoint(x + k * s, y + (n - k + 1) * s);
            p.addPoint(x + k * s, y + (n - k) * s);
        }
        return p;
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
