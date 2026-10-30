package dev.lumora.api.render;

import dev.lumora.api.Registration;
import java.util.Optional;
import java.util.UUID;

/**
 * Icons next to player names (permission {@code nametags}): appended to the name tag above a player and to the name
 * in the tab list. Lumora asks the decorators every frame; keep {@link Decorator#icon} cheap.
 *
 * @since 1.1.0
 */
public interface NameTags {
    Registration register(Decorator decorator);

    enum Place { WORLD, TAB_LIST }

    interface Decorator {
        /** Icon for {@code player} at {@code place}, or empty for none. Called on the render thread. */
        Optional<Icon> icon(UUID player, Place place);
    }

    /**
     * A character of a font the addon ships ({@code assets/<namespace>/font/<path>.json}, typically a bitmap provider
     * that maps the character to an icon texture), appended to the name after a space. Minecraft 1.8.9 and 1.12.2
     * have no custom fonts; there Lumora appends {@code fallback} in the nearest chat color instead.
     *
     * @param font     font id {@code namespace:path}
     * @param glyph    the character(s) to draw with that font
     * @param color    RGB tint of the glyph (white keeps a colored texture as it is) and color of the fallback
     * @param fallback plain text for versions without custom fonts, e.g. {@code "♪"}
     * @param subtitle small line under the name tag (WORLD only), or empty
     */
    record Icon(String font, String glyph, int color, String fallback, String subtitle) {
        public Icon {
            if (font == null || !font.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("font: " + font);
            if (glyph == null || glyph.isEmpty()) throw new IllegalArgumentException("glyph");
            if (fallback == null) fallback = "";
            if (subtitle == null) subtitle = "";
        }
    }
}
