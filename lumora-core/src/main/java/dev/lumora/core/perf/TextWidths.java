package dev.lumora.core.perf;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Smart HUD &amp; Menus: the width of each text Lumora draws, measured once instead of every frame (render thread).
 * <p>
 * A width only depends on the text and the font. The font changes on a resource reload ({@link #clear()}) and when the
 * player switches "Force Unicode Font" (no reload in most versions); for that, every drawing pass measures one fixed
 * probe text first ({@link #validate}) and drops all widths when its width changed. Other caches that hold widths
 * (name tags) compare {@link #epoch()} to notice the same.
 */
public final class TextWidths {
    /** Characters whose widths differ between Minecraft's fonts (default, Unicode, resource packs). */
    public static final String PROBE = "Lumora WMmil1.:0Äg";
    private static final int CACHE = 1024;
    private static final Map<String, Integer> WIDTHS = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
            return size() > CACHE;
        }
    };
    private static int probe = -1;
    private static int epoch;

    private TextWidths() {
    }

    /** The kept width of {@code text}, or -1 if it was not measured since the font last changed. */
    public static int get(String text) {
        Integer w = WIDTHS.get(text);
        return w == null ? -1 : w;
    }

    public static void put(String text, int width) {
        WIDTHS.put(text, width);
    }

    /** {@code probeWidth}: the current font's width of {@link #PROBE}. A different width than before drops all widths. */
    public static void validate(int probeWidth) {
        if (probeWidth != probe) {
            if (probe >= 0) {
                clear();
            }
            probe = probeWidth;
        }
    }

    /** Resources were reloaded (fonts, language): everything is measured again. */
    public static void clear() {
        WIDTHS.clear();
        epoch++;
    }

    /** Changes whenever the kept widths were dropped. */
    public static int epoch() {
        return epoch;
    }

    static int size() {
        return WIDTHS.size();
    }
}
