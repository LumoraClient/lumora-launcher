package dev.lumora.core.theme;

import java.util.ArrayList;
import java.util.List;

/**
 * The look built into the client: the Lumora theme (buttons, sliders, menus) and the chosen font. Both are folders
 * {@code resourcepacks/<name>} in the client jar, but not resource packs the player picks: the client lays them over
 * the selected packs whenever the resources load, following the hub (theme Lumora/Vanilla, font).
 */
public final class ThemeLayers {
    /** Folder of the Lumora theme. */
    public static final String THEME = "lumora_theme";

    /** Font shown instead of the configured one (smoke test), null: the one chosen in the hub. */
    private static volatile String fontOverride;
    /** State of the last resource load ("lumora|inter"), null before the first. */
    private static volatile String applied;
    private static volatile boolean themeApplied = true;

    private ThemeLayers() {
    }

    /** Shows this font (LumoraFonts id, "vanilla": Minecraft's) instead of the configured one; null follows the hub. */
    public static void overrideFont(String font) {
        fontOverride = font;
    }

    /**
     * The layer folders for the hub state, lowest first, remembered as loaded.
     *
     * @param theme      "lumora" or "vanilla" (null: Lumora)
     * @param lumoraFont whether a Lumora font is on
     * @param font       LumoraFonts id of that font
     */
    public static List<String> load(String theme, boolean lumoraFont, String font) {
        boolean lumora = themeOn(theme);
        String f = font(lumoraFont, font);
        applied = key(lumora, f);
        themeApplied = lumora;
        List<String> out = new ArrayList<>();
        if (lumora) {
            out.add(THEME);
        }
        String pack = LumoraFonts.get(f).pack();
        if (!LumoraFonts.VANILLA.equals(f) && pack != null) {
            out.add(pack);
        }
        return out;
    }

    /** Whether the hub state differs from the loaded resources (a resource reload shows it). */
    public static boolean changed(String theme, boolean lumoraFont, String font) {
        return !key(themeOn(theme), font(lumoraFont, font)).equals(applied);
    }

    /** Whether the Lumora theme was part of the last resource load (true before the first). */
    public static boolean themeActive() {
        return themeApplied;
    }

    /** Back to the start state (tests). */
    static void reset() {
        fontOverride = null;
        applied = null;
        themeApplied = true;
    }

    private static boolean themeOn(String theme) {
        return !LumoraTheme.VANILLA.equals(theme);
    }

    private static String font(boolean lumoraFont, String font) {
        String f = fontOverride;
        if (f == null) {
            f = lumoraFont ? font : LumoraFonts.VANILLA;
        }
        return LumoraFonts.VANILLA.equals(f) ? f : LumoraFonts.get(f).id();
    }

    private static String key(boolean theme, String font) {
        return (theme ? "lumora" : "vanilla") + "|" + font;
    }
}
