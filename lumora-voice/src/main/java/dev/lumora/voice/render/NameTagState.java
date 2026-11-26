package dev.lumora.voice.render;

/**
 * Voice status shown next to a player's name (name tag and tab list): a glyph of the font
 * {@code lumoravoice:nametags} (pixel-art icons in their own colors, so the tint {@link #color()} is white).
 * Versions without custom fonts (1.8.9, 1.12.2) show {@link #fallback()} in the nearest chat color.
 */
public enum NameTagState {
    /** No voice chat (or blocked): nothing is drawn. */
    ABSENT("", "", 0),
    /** In voice, silent: speaker with one wave. */
    IDLE("", "♪", 0xFFFFFF),
    /** Talking: speaker with sound waves. */
    SPEAKING("", "♫", 0xFFFFFF),
    /** You muted this player (player menu): speaker with a red slash. */
    MUTED("", "×", 0xFFFFFF),
    /** Muted by the voice service (Laby Voice moderation): microphone with an orange slash. */
    SERVER_MUTED("", "×", 0xFFFFFF),
    /** In a group (Simple Voice Chat) or channel (Laby Voice) you are not in: speaker with a cyan slash. */
    GROUP("", "-", 0xFFFFFF),
    /** Voice connection lost (you, or a player who was in voice on this server): plug with a red slash. */
    DISCONNECTED("", "!", 0xFFFFFF);

    public static final String FONT = "lumoravoice:nametags";

    private final String glyph, fallback;
    private final int color;

    NameTagState(String glyph, String fallback, int color) { this.glyph = glyph; this.fallback = fallback; this.color = color; }

    /** Character of {@link #FONT}; empty for {@link #ABSENT}. */
    public String glyph() { return glyph; }

    /** Plain-text icon for versions without custom fonts. */
    public String fallback() { return fallback; }

    /** RGB tint. */
    public int color() { return color; }
}
