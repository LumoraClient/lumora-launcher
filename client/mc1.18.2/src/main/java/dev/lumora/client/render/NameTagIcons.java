package dev.lumora.client.render;

import dev.lumora.api.render.NameTags;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.api.LumoraRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;
import java.util.UUID;

/**
 * Addon icons next to player names (Lumora API 1.1 {@link NameTags}): the icon is a glyph of a font the addon ships,
 * appended to the name, so vanilla text rendering handles position, see-through and the tab list.
 */
public final class NameTagIcons {
    private static boolean logged;

    private NameTagIcons() {
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
        var icon = found.get();
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
        return Optional.of(new net.minecraft.network.chat.TextComponent(icon.glyph()).withStyle(style));
    }

    /** {@code name} with the icon of {@code player} appended (tab list), or {@code name} unchanged. */
    public static Component decorate(Component name, UUID player, NameTags.Place place) {
        return glyph(player, place).<Component>map(icon -> name.copy().append(" ").append(icon)).orElse(name);
    }

    /** Gap between the end of the name and the icon: the name tag box reaches 1 px past the text. */
    public static final float GAP = 3;

    /** Icon of the player whose name tag is drawn right now: drawn next to the box, not inside it. */
    private static Component current;

    /** Right before a player's name tag is drawn: remembers the icon for {@link #current()}. */
    public static void begin(UUID player) {
        current = glyph(player, NameTags.Place.WORLD).orElse(null);
    }

    /** After the name tag: nothing pending any more. */
    public static void end() {
        current = null;
    }

    /** The icon to draw next to the name tag being drawn, or {@code null}. */
    public static Component current() {
        return current;
    }

    /** Whether an icon was drawn since start (smoke test). */
    public static boolean drawn() {
        return logged;
    }

    /** Small line under the name tag, or empty. */
    public static String subtitle(UUID player) {
        return icon(player, NameTags.Place.WORLD).map(NameTags.Icon::subtitle).orElse("");
    }
}
