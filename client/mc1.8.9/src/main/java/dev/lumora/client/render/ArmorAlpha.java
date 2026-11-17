package dev.lumora.client.render;

/**
 * Module "Hidden Armor": opacity of the armor piece Minecraft is drawing right now (1 outside of one), set by
 * HiddenArmorMixin around each piece and read where the piece picks its render type, colour and glint. Render
 * thread only.
 */
public final class ArmorAlpha {
    public static float current = 1f;

    private ArmorAlpha() {
    }

    /** {@code argb} with its alpha scaled by the current opacity. */
    public static int color(int argb) {
        if (current >= 1f) {
            return argb;
        }
        int alpha = Math.round(((argb >>> 24) & 0xFF) * current);
        return alpha << 24 | (argb & 0xFFFFFF);
    }
}
