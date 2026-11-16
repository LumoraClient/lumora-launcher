package dev.lumora.client.render;

import dev.lumora.api.render.NameTags;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.api.LumoraRuntime;

import dev.lumora.client.perf.FrameCounter;
import dev.lumora.core.perf.PerformanceEngine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Addon icons next to player names (Lumora API 1.1 {@link NameTags}) on Minecraft 1.8.9: this version has no
 * custom fonts, so the icon's plain-text fallback is appended in the nearest chat color.
 */
public final class NameTagIcons {
    private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static boolean logged;
    private static final int CACHE = 256;
    /** With "Optimized Rendering": the coloured icon text per icon, the width per text, the addons' answers per frame. */
    private static final Map<NameTags.Icon, String> GLYPHS = lru();
    private static final Map<String, Integer> WIDTHS = lru();
    private static UUID memoPlayer;
    private static int memoFrame = -1;
    private static int fontEpoch;
    private static Optional<NameTags.Icon> memoIcon = Optional.empty();
    private static NameTags.Lines memoLines;

    private static <K, V> Map<K, V> lru() {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE;
            }
        };
    }

    /** Resources were reloaded (font): texts are measured again. */
    public static void clearCaches() {
        clearTexts();
        memoFrame = -1;
    }

    /** The font changed: texts are measured again. */
    private static void clearTexts() {
        GLYPHS.clear();
        WIDTHS.clear();
    }

    /** Width of a text drawn above players (kept with "Optimized Rendering"). */
    public static int width(net.minecraft.client.font.TextRenderer font, String text) {
        if (!PerformanceEngine.rendering) {
            return font.getStringWidth(text);
        }
        Integer w = WIDTHS.get(text);
        if (w == null) {
            w = font.getStringWidth(text);
            WIDTHS.put(text, w);
        }
        return w;
    }

    /** Asks the addons once per player and frame (fast path). */
    private static void memo(UUID player) {
        int frame = FrameCounter.current();
        if (frame != memoFrame) {
            dev.lumora.core.perf.TextWidths.validate(dev.lumora.client.api.McHudGraphics.fontState());
            if (fontEpoch != dev.lumora.core.perf.TextWidths.epoch()) {
                fontEpoch = dev.lumora.core.perf.TextWidths.epoch();
                clearTexts();
            }
        }
        if (frame == memoFrame && player.equals(memoPlayer)) {
            return;
        }
        var core = LumoraRuntime.core();
        boolean active = core != null && core.nameTags().active();
        memoIcon = active ? core.nameTags().icon(player, NameTags.Place.WORLD) : Optional.empty();
        memoLines = active ? core.nameTags().lines(player).orElse(null) : null;
        memoPlayer = player;
        memoFrame = frame;
    }

    /** The coloured icon text of an icon (fast path, kept). */
    private static String glyphText(NameTags.Icon icon) {
        if (icon.fallback().isEmpty()) {
            return null;
        }
        String g = GLYPHS.get(icon);
        if (g == null) {
            g = code(icon.color()) + icon.fallback() + "§r";
            GLYPHS.put(icon, g);
        }
        if (!logged) {
            logged = true;
            LumoraClient.LOG.info("LUMORA_NAMETAG_ICON first icon drawn (fallback text, {})", NameTags.Place.WORLD);
        }
        return g;
    }

    private NameTagIcons() {
    }

    public static Optional<NameTags.Icon> icon(UUID player, NameTags.Place place) {
        var core = LumoraRuntime.core();
        if (core == null || player == null || !core.nameTags().active()) {
            return Optional.empty();
        }
        return core.nameTags().icon(player, place);
    }

    /** Chat formatting code of the legacy color nearest to {@code rgb}. */
    static String code(int rgb) {
        int best = 15;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < COLORS.length; i++) {
            int dr = ((rgb >> 16) & 255) - ((COLORS[i] >> 16) & 255);
            int dg = ((rgb >> 8) & 255) - ((COLORS[i] >> 8) & 255);
            int db = (rgb & 255) - (COLORS[i] & 255);
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return "§" + "0123456789abcdef".charAt(best);
    }

    /** The fallback icon of {@code player} in its chat color, or empty. */
    public static Optional<String> glyph(UUID player, NameTags.Place place) {
        var found = icon(player, place);
        if (found.isEmpty() || found.get().fallback().isEmpty()) {
            return Optional.empty();
        }
        if (!logged) {
            logged = true;
            LumoraClient.LOG.info("LUMORA_NAMETAG_ICON first icon drawn (fallback text, {})", place);
        }
        return Optional.of(code(found.get().color()) + found.get().fallback() + "§r");
    }

    /** {@code name} with the fallback icon of {@code player} appended (tab list), or {@code name} unchanged. */
    public static String decorate(String name, UUID player, NameTags.Place place) {
        return glyph(player, place).map(icon -> name + " " + icon).orElse(name);
    }

    /** Gap between the end of the name and the icon: the name tag box reaches 1 px past the text. */
    public static final float GAP = 3;

    /** Icon of the player whose name tag is drawn right now: drawn next to the box, not inside it. */
    private static String current;

    /** Right before a player's name tag is drawn: remembers the icon for {@link #current()}. */
    public static void begin(UUID player) {
        if (PerformanceEngine.rendering) {
            memo(player);
            current = memoIcon.map(NameTagIcons::glyphText).orElse(null);
            NameTags.Lines l = memoLines;
            above = l == null || l.above().isEmpty() ? null : l.above();
            below = l == null || l.below().isEmpty() ? null : l.below();
            return;
        }
        current = glyph(player, NameTags.Place.WORLD).orElse(null);
        var lines = lines(player);
        above = lines == null || lines.above().isEmpty() ? null : lines.above();
        below = lines == null || lines.below().isEmpty() ? null : lines.below();
    }

    /** After the name tag: nothing pending any more. */
    public static void end() {
        current = null;
        above = null;
        below = null;
    }

    /** The icon to draw next to the name tag being drawn, or {@code null}. */
    public static String current() {
        return current;
    }

    /** Whether an icon was drawn since start (smoke test). */
    public static boolean drawn() {
        return logged;
    }
    /** Distance of the lines above and below the name from the name, in text pixels. */
    public static final float LINE = 10;

    /** Lines above and below the name tag being drawn (Lumora API 1.7 NameTags.Lines), or {@code null}. */
    private static String above;
    private static String below;

    private static NameTags.Lines lines(UUID player) {
        var core = LumoraRuntime.core();
        if (core == null || player == null || !core.nameTags().active()) {
            return null;
        }
        return core.nameTags().lines(player).orElse(null);
    }

    /** The line to draw above the name tag being drawn, or {@code null}. */
    public static String above() {
        return above;
    }

    /** The line to draw below the name tag being drawn, or {@code null}. */
    public static String below() {
        return below;
    }
}
