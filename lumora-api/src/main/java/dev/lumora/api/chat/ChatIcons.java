package dev.lumora.api.chat;

/**
 * Inline icons in chat messages (Lumora's counterpart of LabyMod's {@code IconComponent} in chat): in every new
 * message Lumora looks up each {@code :name:} (letters, digits, {@code _ + -}) and shows the icon instead of the text.
 * The icon is a character of a font the addon ships ({@code assets/<namespace>/font/<path>.json}, typically a bitmap
 * provider), so Minecraft wraps, scales and fades it like text; clicks and hover texts of the message stay. Copying a
 * message gives back the {@code :name:} text. Only the own view changes, other players see what was sent.
 * <p>
 * Minecraft 1.8.9 and 1.12.2 have no custom fonts: there the text stays as it is.
 *
 * <pre>{@code
 * context.chat().addIcons(name -> name.equals("lumora")
 *         ? new ChatIcons.Icon("myaddon:icons", "") : null);
 * }</pre>
 *
 * @since 1.9.0
 */
@FunctionalInterface
public interface ChatIcons {
    /**
     * The icon for a shortcode, or null for none. Called on the client thread for every {@code :name:} in new
     * messages; keep it a map lookup.
     *
     * @param name the name between the colons, lower case
     */
    Icon icon(String name);

    /**
     * A character of an addon font.
     *
     * @param font  font id {@code namespace:path}
     * @param glyph the character(s), drawn white (a colored texture keeps its colors)
     */
    record Icon(String font, String glyph) {
        public Icon {
            if (font == null || !font.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("font: " + font);
            }
            if (glyph == null || glyph.isEmpty()) {
                throw new IllegalArgumentException("glyph");
            }
        }
    }
}
