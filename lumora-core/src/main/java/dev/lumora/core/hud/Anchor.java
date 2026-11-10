package dev.lumora.core.hud;

import java.util.List;

/**
 * Where a widget hangs on the screen: one of nine anchors (corners, edge middles, center). An anchored widget keeps
 * its distance in GUI pixels to that anchor when the window or the GUI scale changes - a widget in the bottom right
 * corner stays exactly there. The editor takes the anchor from the third of the screen the cursor is in when the
 * widget is dropped (3x3 zones).
 * <p>
 * Offsets: left/top anchors measure from the left/top edge to the widget, right/bottom anchors from the right/bottom
 * edge to the widget, center anchors from the screen center to the widget's center.
 */
public final class Anchor {
    public static final List<String> ZONES = List.of("top_left", "top_center", "top_right", "center_left", "center",
            "center_right", "bottom_left", "bottom_center", "bottom_right");

    private Anchor() {
    }

    /** 0 left, 1 center, 2 right. */
    public static int column(String anchor) {
        return anchor.endsWith("left") ? 0 : anchor.endsWith("right") ? 2 : 1;
    }

    /** 0 top, 1 center, 2 bottom. */
    public static int row(String anchor) {
        return anchor.startsWith("top") ? 0 : anchor.startsWith("bottom") ? 2 : 1;
    }

    public static String of(int column, int row) {
        return ZONES.get(Math.max(0, Math.min(2, row)) * 3 + Math.max(0, Math.min(2, column)));
    }

    public static boolean valid(String anchor) {
        return anchor != null && ZONES.contains(anchor);
    }

    /** The zone (third of the screen in each direction) a point is in. */
    public static String zone(double x, double y, int screenW, int screenH) {
        int c = x < screenW / 3.0 ? 0 : x > screenW * 2 / 3.0 ? 2 : 1;
        int r = y < screenH / 3.0 ? 0 : y > screenH * 2 / 3.0 ? 2 : 1;
        return of(c, r);
    }

    /** Top left pixel of a {@code w} x {@code h} widget at the given anchor and offsets, kept inside the screen. */
    public static int[] place(String anchor, int offsetX, int offsetY, int w, int h, int screenW, int screenH) {
        double x = switch (column(anchor)) {
            case 0 -> offsetX;
            case 2 -> screenW - offsetX - w;
            default -> screenW / 2.0 + offsetX - w / 2.0;
        };
        double y = switch (row(anchor)) {
            case 0 -> offsetY;
            case 2 -> screenH - offsetY - h;
            default -> screenH / 2.0 + offsetY - h / 2.0;
        };
        return new int[] {clamp((int) Math.round(x), 0, Math.max(0, screenW - w)),
                clamp((int) Math.round(y), 0, Math.max(0, screenH - h))};
    }

    /** Offsets that put the widget's top left pixel at (x, y) for the given anchor (inverse of {@link #place}). */
    public static int[] offsets(String anchor, double x, double y, int w, int h, int screenW, int screenH) {
        double ox = switch (column(anchor)) {
            case 0 -> x;
            case 2 -> screenW - x - w;
            default -> x + w / 2.0 - screenW / 2.0;
        };
        double oy = switch (row(anchor)) {
            case 0 -> y;
            case 2 -> screenH - y - h;
            default -> y + h / 2.0 - screenH / 2.0;
        };
        return new int[] {(int) Math.round(ox), (int) Math.round(oy)};
    }

    /** The anchor point of a zone on the screen (corner, edge middle or center of that third). */
    public static int[] point(String anchor, int screenW, int screenH) {
        int c = column(anchor);
        int r = row(anchor);
        int zoneW = screenW / 3;
        int zoneH = screenH / 3;
        int left = c * zoneW;
        int top = r * zoneH;
        int w = c == 2 ? screenW - left : zoneW;
        int h = r == 2 ? screenH - top : zoneH;
        return new int[] {c == 0 ? left : c == 2 ? left + w : left + w / 2, r == 0 ? top : r == 2 ? top + h : top + h / 2};
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
