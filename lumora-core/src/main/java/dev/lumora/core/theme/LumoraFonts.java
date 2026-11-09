package dev.lumora.core.theme;

import java.util.List;

/**
 * Fonts for Lumora's menus and the game's text. Each one (except Vanilla) is a folder {@code resourcepacks/<pack>} in
 * the client jar that replaces Minecraft's default font with a TrueType font; it is built into the client, not a resource
 * pack to pick ({@link ThemeLayers}). All fonts are under the SIL Open Font License 1.1; the license text is in
 * each folder.
 */
public final class LumoraFonts {
    /** Minecraft's own font (no font pack). */
    public static final String VANILLA = "vanilla";
    public static final String DEFAULT = "inter";

    /**
     * A font: {@code pack} is the folder in the client jar (null for Vanilla), {@code size} and {@code shift} go into the
     * pack's font definition (cap height matched to Inter at 8.5).
     */
    public record Font(String id, String name, String pack, String file, double size, double shift, String note) {
    }

    public static final List<Font> ALL = List.of(
            new Font(VANILLA, "Vanilla", null, null, 0, 0, "Minecraft"),
            new Font("inter", "Inter", "lumora_font", "inter-medium.ttf", 8.5, 0.5, "Lumora, LiquidBounce Nextgen"),
            new Font("outfit", "Outfit", "lumora_font_outfit", "outfit-medium.ttf", 8.8, 0.5, "LiquidBounce Legacy"),
            new Font("manrope", "Manrope", "lumora_font_manrope", "manrope-medium.ttf", 8.6, 0.5, "Lunar-Stil"),
            new Font("roboto", "Roboto", "lumora_font_roboto", "roboto-medium.ttf", 8.7, 0.5, ""),
            new Font("poppins", "Poppins", "lumora_font_poppins", "poppins-medium.ttf", 8.9, 0.5, ""),
            new Font("montserrat", "Montserrat", "lumora_font_montserrat", "montserrat-medium.ttf", 8.8, 0.5, ""),
            new Font("jetbrainsmono", "JetBrains Mono", "lumora_font_jetbrainsmono", "jetbrainsmono-medium.ttf", 8.5, 0.5, "Mono"),
            new Font("silkscreen", "Silkscreen", "lumora_font_silkscreen", "silkscreen-regular.ttf", 8.0, 1.0, "Pixel"));

    private LumoraFonts() {
    }

    /** The font with this id, or Inter for unknown ids. */
    public static Font get(String id) {
        for (Font f : ALL) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return ALL.get(1);
    }
}
