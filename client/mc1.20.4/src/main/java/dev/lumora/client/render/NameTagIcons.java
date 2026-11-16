package dev.lumora.client.render;

import dev.lumora.api.render.NameTags;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.api.LumoraRuntime;
import dev.lumora.client.perf.FrameCounter;
import dev.lumora.core.perf.PerformanceEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Addon icons next to player names (Lumora API 1.1 {@link NameTags}): the icon is a glyph of a font the addon ships,
 * appended to the name, so vanilla text rendering handles position, see-through and the tab list.
 * <p>
 * With "Optimized Rendering" ({@link PerformanceEngine#rendering}) the addons are asked once per player and frame
 * (not once for the icon, once for the lines and again for the subtitle), and the texts built from their answers are
 * kept (glyph component, line components, their visual order and width) instead of being built again every frame.
 * The answers themselves are still read every frame, so a changed icon or line shows in the very next frame.
 */
public final class NameTagIcons {
    private static boolean logged;
    private static final int CACHE = 256;

    /** A text drawn above players, with what drawing it needs, computed on first use. */
    public static final class Text {
        public final Component component;
        private FormattedCharSequence order;
        private int width = -1;

        Text(Component component) {
            this.component = component;
        }

        public FormattedCharSequence order() {
            FormattedCharSequence o = order;
            if (o == null) {
                o = component.getVisualOrderText();
                order = o;
            }
            return o;
        }

        public int width(Font font) {
            int w = width;
            if (w < 0) {
                w = font.width(component);
                width = w;
            }
            return w;
        }
    }

    private static final Map<NameTags.Icon, Optional<Text>> GLYPHS = lru();
    private static final Map<String, Text> LINES = lru();
    private static final Map<String, Component> SUBTITLES = lru();

    // answers of the addons for one player in the current frame (render thread)
    private static UUID memoPlayer;
    private static int memoFrame = -1;
    private static Optional<NameTags.Icon> memoIcon = Optional.empty();
    private static NameTags.Lines memoLines;

    private NameTagIcons() {
    }

    private static <K, V> Map<K, V> lru() {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > CACHE;
            }
        };
    }

    /** Resources were reloaded (fonts, language): texts are measured and ordered again. */
    public static void clearCaches() {
        GLYPHS.clear();
        LINES.clear();
        SUBTITLES.clear();
        memoFrame = -1;
    }

    /** Asks the addons once per player and frame (fast path). */
    private static void memo(UUID player) {
        int frame = FrameCounter.current();
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

    public static Optional<NameTags.Icon> icon(UUID player, NameTags.Place place) {
        var core = LumoraRuntime.core();
        if (core == null || player == null || !core.nameTags().active()) {
            return Optional.empty();
        }
        return core.nameTags().icon(player, place);
    }

    /** The icon of {@code player} as a styled glyph, or empty. */
    public static Optional<Component> glyph(UUID player, NameTags.Place place) {
        var found = icon(player, place);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return build(found.get(), place).map(t -> t.component);
    }

    private static Optional<Text> build(NameTags.Icon icon, NameTags.Place place) {
        Style style;
        try {
            style = Style.EMPTY.withFont(new ResourceLocation(icon.font())).withColor(TextColor.fromRgb(icon.color())).withBold(false).withItalic(false);
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
        if (!logged) {
            logged = true;
            LumoraClient.LOG.info("LUMORA_NAMETAG_ICON first icon drawn ({}, {})", icon.font(), place);
        }
        return Optional.of(new Text(Component.literal(icon.glyph()).withStyle(style)));
    }

    /** The world icon as a kept text (fast path). */
    private static Text glyphText(NameTags.Icon icon) {
        Optional<Text> t = GLYPHS.get(icon);
        if (t == null) {
            t = build(icon, NameTags.Place.WORLD);
            GLYPHS.put(icon, t);
        }
        return t.orElse(null);
    }

    private static Text lineText(String line) {
        Text t = LINES.get(line);
        if (t == null) {
            t = new Text(Component.literal(line));
            LINES.put(line, t);
        }
        return t;
    }

    /** {@code name} with the icon of {@code player} appended (tab list), or {@code name} unchanged. */
    public static Component decorate(Component name, UUID player, NameTags.Place place) {
        return glyph(player, place).<Component>map(icon -> name.copy().append(" ").append(icon)).orElse(name);
    }

    /** Gap between the end of the name and the icon: the name tag box reaches 1 px past the text. */
    public static final float GAP = 3;

    /** Icon of the player whose name tag is drawn right now: drawn next to the box, not inside it. */
    private static Text current;

    /** Right before a player's name tag is drawn: remembers the icon for {@link #current()}. */
    public static void begin(UUID player) {
        if (PerformanceEngine.rendering) {
            memo(player);
            current = memoIcon.map(NameTagIcons::glyphText).orElse(null);
            NameTags.Lines lines = memoLines;
            above = lines == null || lines.above().isEmpty() ? null : lineText(lines.above());
            below = lines == null || lines.below().isEmpty() ? null : lineText(lines.below());
            return;
        }
        current = glyph(player, NameTags.Place.WORLD).map(Text::new).orElse(null);
        var lines = lines(player);
        above = lines == null || lines.above().isEmpty() ? null : new Text(Component.literal(lines.above()));
        below = lines == null || lines.below().isEmpty() ? null : new Text(Component.literal(lines.below()));
    }

    /** After the name tag: nothing pending any more. */
    public static void end() {
        current = null;
        above = null;
        below = null;
    }

    /** The icon to draw next to the name tag being drawn, or {@code null}. */
    public static Text current() {
        return current;
    }

    /** Whether an icon was drawn since start (smoke test). */
    public static boolean drawn() {
        return logged;
    }

    /** Small line under the name tag, or empty. */
    public static String subtitle(UUID player) {
        if (PerformanceEngine.rendering) {
            memo(player);
            return memoIcon.map(NameTags.Icon::subtitle).orElse("");
        }
        return icon(player, NameTags.Place.WORLD).map(NameTags.Icon::subtitle).orElse("");
    }

    /** The grey subtitle line as a component (kept with "Optimized Rendering"). */
    public static Component subtitleComponent(String line) {
        if (!PerformanceEngine.rendering) {
            return Component.literal(line).withStyle(ChatFormatting.GRAY);
        }
        Component c = SUBTITLES.get(line);
        if (c == null) {
            c = Component.literal(line).withStyle(ChatFormatting.GRAY);
            SUBTITLES.put(line, c);
        }
        return c;
    }

    /** Distance of the lines above and below the name from the name, in text pixels. */
    public static final float LINE = 10;

    /** Lines above and below the name tag being drawn (Lumora API 1.7 NameTags.Lines), or {@code null}. */
    private static Text above;
    private static Text below;

    private static NameTags.Lines lines(UUID player) {
        var core = LumoraRuntime.core();
        if (core == null || player == null || !core.nameTags().active()) {
            return null;
        }
        return core.nameTags().lines(player).orElse(null);
    }

    /** Whether the player has a line below the name: the subtitle then moves one line further down. */
    public static boolean hasBelow(UUID player) {
        if (PerformanceEngine.rendering) {
            memo(player);
            return memoLines != null && !memoLines.below().isEmpty();
        }
        var lines = lines(player);
        return lines != null && !lines.below().isEmpty();
    }

    /** The line to draw above the name tag being drawn, or {@code null}. */
    public static Text above() {
        return above;
    }

    /** The line to draw below the name tag being drawn, or {@code null}. */
    public static Text below() {
        return below;
    }
}
