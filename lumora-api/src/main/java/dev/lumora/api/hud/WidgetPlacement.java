package dev.lumora.api.hud;

/**
 * Default position of a widget. {@code x}/{@code y} are fractions of the free screen space: 0 = left/top edge,
 * 1 = right/bottom edge, 0.5 = centered. This keeps widgets in place for every window size and GUI scale.
 */
public record WidgetPlacement(float x, float y, float scale, boolean enabled) {
    public WidgetPlacement {
        x = clamp01(x);
        y = clamp01(y);
        scale = Float.isFinite(scale) ? Math.max(0.25f, Math.min(4f, scale)) : 1f;
    }

    /** Disabled by default at the given position, scale 1. */
    public static WidgetPlacement at(float x, float y) {
        return new WidgetPlacement(x, y, 1f, false);
    }

    public WidgetPlacement scaled(float scale) {
        return new WidgetPlacement(x, y, scale, enabled);
    }

    /** Shown without the user adding it first. */
    public WidgetPlacement enabledByDefault() {
        return new WidgetPlacement(x, y, scale, true);
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0f, Math.min(1f, v)) : 0f;
    }
}
