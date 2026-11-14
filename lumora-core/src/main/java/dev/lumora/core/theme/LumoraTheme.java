package dev.lumora.core.theme;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Look of Lumora's menus: the Lumora theme with an accent colour, or the Vanilla theme (Minecraft's own menus,
 * buttons and font). Every Lumora view and the client's widgets read the accent from here, so a change applies at
 * once.
 */
public final class LumoraTheme {
    public static final String LUMORA = "lumora";
    public static final String VANILLA = "vanilla";

    /** A selectable accent colour. */
    public record Accent(String id, String german, String english, int color) {
    }

    /** Lumora's own accents, then those addons added (Lumora API 1.5, ids {@code <addon>:<id>}). */
    public static final List<Accent> ACCENTS = new CopyOnWriteArrayList<>(List.of(
            new Accent("blue", "Blau", "Blue", 0xFF3B82F6),
            new Accent("purple", "Lila", "Purple", 0xFF8B5CF6),
            new Accent("cyan", "Türkis", "Cyan", 0xFF06B6D4),
            new Accent("green", "Grün", "Green", 0xFF22C55E),
            new Accent("amber", "Orange", "Orange", 0xFFF59E0B),
            new Accent("red", "Rot", "Red", 0xFFEF4444),
            new Accent("pink", "Pink", "Pink", 0xFFEC4899)));

    public static final Accent DEFAULT = ACCENTS.get(0);
    /** How many of {@link #ACCENTS} are Lumora's own. */
    public static final int BUILT_IN = ACCENTS.size();

    private static volatile Accent accent = DEFAULT;
    /** The accent the player chose, also while it is not available (an addon's accent before the addon is on). */
    private static volatile String wanted = DEFAULT.id();
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private LumoraTheme() {
    }

    /** The accent with this id, or the default (Blau, same as the launcher; Lila until 0.3.42) for unknown ids. */
    public static Accent accent(String id) {
        for (Accent a : ACCENTS) {
            if (a.id().equals(id)) {
                return a;
            }
        }
        return DEFAULT;
    }

    public static void setAccent(String id) {
        wanted = id == null ? DEFAULT.id() : id;
        update();
    }

    /** Runs {@code listener} whenever the accent in use changes (the clients copy it into their own fields). */
    public static void onChange(Runnable listener) {
        listeners.add(listener);
    }

    /** Offers an addon's accent; chosen again at once if the player had picked it before. */
    public static void addAccent(Accent a) {
        ACCENTS.removeIf(x -> x.id().equals(a.id()));
        ACCENTS.add(a);
        update();
    }

    public static void removeAccent(String id) {
        if (ACCENTS.subList(0, BUILT_IN).stream().anyMatch(a -> a.id().equals(id))) {
            return; // Lumora's own stay
        }
        ACCENTS.removeIf(a -> a.id().equals(id));
        update();
    }

    private static void update() {
        Accent before = accent;
        accent = accent(wanted);
        if (accent != before) {
            for (Runnable l : listeners) {
                try {
                    l.run();
                } catch (RuntimeException ignored) {
                    // a broken listener must not stop the others
                }
            }
        }
    }

    public static Accent current() {
        return accent;
    }

    public static int accent() {
        return accent.color();
    }

    /** Lighter accent for hovered elements (the launcher's values for Blau, the original ones for Lila). */
    public static int accentHover() {
        Accent a = accent;
        return switch (a.id()) {
            case "blue" -> 0xFF5E9BF8;
            case "purple" -> 0xFFA27BFA;
            default -> mix(a.color(), 0xFFFFFFFF, 0.22f);
        };
    }

    /** Darker accent for pressed elements and shadows. */
    public static int accentDark() {
        Accent a = accent;
        return switch (a.id()) {
            case "blue" -> 0xFF306ACA;
            case "purple" -> 0xFF7243E0;
            default -> mix(a.color(), 0xFF000000, 0.2f);
        };
    }

    /** {@code color} with the given alpha (0..255). */
    public static int alpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    /** Linear mix of two ARGB colours ({@code t} = 0: {@code a}, 1: {@code b}). */
    public static int mix(int a, int b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        int r = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            r |= Math.round(ca + (cb - ca) * u) << shift;
        }
        return r;
    }

    /** The default accent recoloured to the current accent, for colours derived from it (keeps alpha). */
    public static int recolor(int argb) {
        return (argb & 0xFF000000) | (accent() & 0x00FFFFFF);
    }
}
