package dev.lumora.api.hud;

import java.util.List;

/**
 * Default position of a widget. {@code x}/{@code y} are fractions of the free screen space: 0 = left/top edge,
 * 1 = right/bottom edge, 0.5 = centered. This keeps widgets in place for every window size and GUI scale.
 * <p>
 * Since 1.5.0 a widget can instead hang on one of nine {@linkplain #ANCHORS anchor zones}, like a widget the player
 * dropped there in the HUD editor ({@link #anchored}): it keeps its distance in GUI pixels to that corner, edge or the
 * center of the screen.
 */
public record WidgetPlacement(float x, float y, float scale, boolean enabled, String anchor, int offsetX, int offsetY) {
    /**
     * The anchor zones: {@code top_left}, {@code top_center}, {@code top_right}, {@code center_left}, {@code center},
     * {@code center_right}, {@code bottom_left}, {@code bottom_center}, {@code bottom_right}.
     *
     * @since 1.5.0
     */
    public static final List<String> ANCHORS = List.of("top_left", "top_center", "top_right", "center_left", "center",
            "center_right", "bottom_left", "bottom_center", "bottom_right");

    public WidgetPlacement {
        x = clamp01(x);
        y = clamp01(y);
        scale = Float.isFinite(scale) ? Math.max(0.25f, Math.min(4f, scale)) : 1f;
        if (anchor != null && !ANCHORS.contains(anchor)) {
            throw new IllegalArgumentException("anchor: " + anchor);
        }
        if (anchor == null) {
            offsetX = 0;
            offsetY = 0;
        }
    }

    /** A placement by fractions of the screen (as before 1.5.0). */
    public WidgetPlacement(float x, float y, float scale, boolean enabled) {
        this(x, y, scale, enabled, null, 0, 0);
    }

    /** Disabled by default at the given position, scale 1. */
    public static WidgetPlacement at(float x, float y) {
        return new WidgetPlacement(x, y, 1f, false);
    }

    /**
     * Disabled by default, hanging on {@code anchor} (one of {@link #ANCHORS}). The offsets are GUI pixels: for an
     * anchor on the left/top the distance of the widget's left/top edge to the screen edge, on the right/bottom the
     * distance of its right/bottom edge, in the center the shift from the centered position.
     *
     * <pre>{@code
     * WidgetPlacement.anchored("bottom_right", 4, 30).enabledByDefault()   // 4 px from the right, 30 px from the bottom
     * }</pre>
     *
     * @since 1.5.0
     */
    public static WidgetPlacement anchored(String anchor, int offsetX, int offsetY) {
        if (anchor == null) {
            throw new IllegalArgumentException("anchor");
        }
        float fx = anchor.endsWith("left") ? 0f : anchor.endsWith("right") ? 1f : 0.5f;
        float fy = anchor.startsWith("top") ? 0f : anchor.startsWith("bottom") ? 1f : 0.5f;
        return new WidgetPlacement(fx, fy, 1f, false, anchor, offsetX, offsetY);
    }

    /** Whether the widget hangs on an anchor zone. @since 1.5.0 */
    public boolean anchoredPlacement() {
        return anchor != null;
    }

    public WidgetPlacement scaled(float scale) {
        return new WidgetPlacement(x, y, scale, enabled, anchor, offsetX, offsetY);
    }

    /** Shown without the user adding it first. */
    public WidgetPlacement enabledByDefault() {
        return new WidgetPlacement(x, y, scale, true, anchor, offsetX, offsetY);
    }

    private static float clamp01(float v) {
        return Float.isFinite(v) ? Math.max(0f, Math.min(1f, v)) : 0f;
    }
}
