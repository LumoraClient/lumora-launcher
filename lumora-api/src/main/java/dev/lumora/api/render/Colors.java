package dev.lumora.api.render;

/** ARGB color helpers and the Lumora design colors (same tokens as the launcher). */
public final class Colors {
    public static final int BACKGROUND = 0xFF0F0C17;
    public static final int SURFACE = 0xFF17131F;
    public static final int SURFACE_2 = 0xFF221C2D;
    public static final int BORDER = 0xFF2F2741;
    public static final int TEXT = 0xFFECE8F4;
    public static final int MUTED = 0xFF9D95AE;
    public static final int ACCENT = 0xFF8B5CF6;
    public static final int ACCENT_HOVER = 0xFFA27BFA;
    public static final int OK = 0xFF3DD68C;
    public static final int WARNING = 0xFFF5B342;
    public static final int ERROR = 0xFFF2555A;
    public static final int WHITE = 0xFFFFFFFF;

    private Colors() {
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    /** Same color with a new alpha (0..255). */
    public static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    /** Multiplies the existing alpha by {@code factor} (0..1). */
    public static int fade(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        return withAlpha(argb, Math.round(a * Math.max(0f, Math.min(1f, factor))));
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    /** Linear blend, {@code t} = 0 gives {@code a}, 1 gives {@code b}. */
    public static int mix(int a, int b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        return argb(Math.round(((a >>> 24) & 0xFF) * (1 - u) + ((b >>> 24) & 0xFF) * u),
                Math.round(((a >> 16) & 0xFF) * (1 - u) + ((b >> 16) & 0xFF) * u),
                Math.round(((a >> 8) & 0xFF) * (1 - u) + ((b >> 8) & 0xFF) * u),
                Math.round((a & 0xFF) * (1 - u) + (b & 0xFF) * u));
    }

    /** {@code #AARRGGBB}. */
    public static String toHex(int argb) {
        return String.format("#%08X", argb);
    }

    /** Parses {@code #RRGGBB} or {@code #AARRGGBB} (also without '#' or with "0x"); {@code null} if invalid. */
    public static Integer parseHex(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim();
        if (v.startsWith("#")) {
            v = v.substring(1);
        } else if (v.startsWith("0x") || v.startsWith("0X")) {
            v = v.substring(2);
        }
        if (v.length() != 6 && v.length() != 8) {
            return null;
        }
        try {
            long n = Long.parseLong(v, 16);
            if (v.length() == 6) {
                n |= 0xFF000000L;
            }
            return (int) n;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
