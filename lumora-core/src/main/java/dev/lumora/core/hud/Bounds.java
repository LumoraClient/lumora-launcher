package dev.lumora.core.hud;

/** Screen rectangle of a widget in GUI pixels from the last layout pass (mutable, reused every frame). */
public final class Bounds {
    public int x;
    public int y;
    public int width;
    public int height;
    /** Unscaled size reported by the widget. */
    public int rawWidth;
    public int rawHeight;
    public boolean valid;

    public boolean contains(double px, double py) {
        return valid && px >= x && px < x + width && py >= y && py < y + height;
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }
}
