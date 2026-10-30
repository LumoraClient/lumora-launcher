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
 * Lumora design tokens and look-and-feel setup: deep night violet surfaces, a selectable accent (violet by default),
 * Inter typeface, rounded or hard pixel corners. The tokens are set once before the first window is built.
 */
public final class Theme {
    public static Color BG = new Color(0x0F0C17);
    public static Color SURFACE = new Color(0x17131F);
    public static Color SURFACE_2 = new Color(0x221C2D);
    public static Color BORDER = new Color(0x2F2741);
    public static Color TEXT = new Color(0xECE8F4);
    public static Color MUTED = new Color(0x9D95AE);
    public static Color ACCENT = new Color(0x8B5CF6);
    public static Color ACCENT_HOVER = new Color(0xA27BFA);
    public static Color ACCENT_PRESSED = new Color(0x7243E0);
    /** Light accent tint for text on accent-coloured chips. */
    public static Color ACCENT_TEXT = new Color(0xDCCCFD);
    public static final Color OK = new Color(0x3DD68C);
    public static final Color WARN = new Color(0xF5B342);
    public static final Color FAIL = new Color(0xF2555A);

    /** Card radius; 0 in pixel style. */
    public static int RADIUS = 16;
    /** Hard pixel corners instead of rounded ones. */
    public static boolean pixelStyle;
    /** No automatic animations (skin rotation, spinners). */
    public static boolean reduceMotion;

    /** Accent presets of the settings page: name and colour. */
    public record Preset(String name, String hex) {
    }

    public static final List<Preset> PRESETS = List.of(
            new Preset("Lila", "#8B5CF6"), new Preset("Türkis", "#18B4CC"), new Preset("Blau", "#3B82F6"),
            new Preset("Rosa", "#EC4899"), new Preset("Grün", "#22C55E"), new Preset("Weiß", "#E5E7EB"));

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
        RADIUS = pixel ? 0 : 16;
        FlatInterFont.install();
        FlatLaf.setPreferredFontFamily(FlatInterFont.FAMILY);
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", hex(ACCENT),
                "@background", hex(BG),
                "@foreground", hex(TEXT)));
        FlatDarkLaf.setup();
        UIManager.put("Component.arc", arc(12));
        UIManager.put("Button.arc", arc(12));
        UIManager.put("TextComponent.arc", arc(10));
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

    /** Sets the accent and the colours derived from it. Invalid values keep the default violet. */
    static void setAccent(String accentHex) {
        Color c = parse(accentHex);
        if (c == null) {
            c = new Color(0x8B5CF6);
        }
        ACCENT = c;
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

    /** Text colour that stays readable on the accent (dark text on light accents such as white). */
    public static Color onAccent() {
        double l = (0.299 * ACCENT.getRed() + 0.587 * ACCENT.getGreen() + 0.114 * ACCENT.getBlue()) / 255;
        return l > 0.7 ? new Color(0x17131F) : Color.WHITE;
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Font font(int style, float size) {
        return new Font(FlatInterFont.FAMILY, style, Math.round(size)).deriveFont(size);
    }

    public static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
