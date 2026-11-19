package dev.lumora.core.zoom;

/**
 * The zoom factor over time: when the target changes (zooming in or out, the scroll wheel), the factor moves from where
 * it is now to the new target along a curve in a set time in milliseconds ("Zoom In / Out Transition", "Duration").
 * Time based, so it is smooth at every frame rate. Version independent; the version's zoom asks it once per frame.
 */
public final class ZoomMotion {
    /** The transition curves, in the order of the settings. */
    public static final String[] EASES = {"linear", "ease_in", "ease_out", "ease_in_out"};
    public static final int MAX_MS = 1000;

    private double from = 1;
    private double to = 1;
    private long start;
    private long durationNanos;
    private String ease = "linear";

    /**
     * Sets the target. A new target starts a transition from the current value; the same target changes nothing.
     *
     * @param animate "Enable Zoom Transition": off jumps to the target at once
     * @param ms      duration of the transition, 0 = at once
     */
    public void aim(double target, long now, boolean animate, String curve, int ms) {
        if (target == to) {
            return;
        }
        from = value(now);
        to = target;
        start = now;
        durationNanos = animate ? Math.max(0, Math.min(MAX_MS, ms)) * 1_000_000L : 0;
        ease = curve;
    }

    /** The target the factor is moving to. */
    public double target() {
        return to;
    }

    /** The factor right now. */
    public double value(long now) {
        if (durationNanos <= 0) {
            return to;
        }
        double p = (now - start) / (double) durationNanos;
        if (p >= 1) {
            return to;
        }
        return from + (to - from) * ease(ease, Math.max(0, p));
    }

    /** Whether a transition is running. */
    public boolean moving(long now) {
        return durationNanos > 0 && now - start < durationNanos;
    }

    /** Progress 0..1 along a curve (unknown names: linear). */
    public static double ease(String curve, double p) {
        return switch (curve == null ? "" : curve) {
            case "ease_in" -> p * p * p;
            case "ease_out" -> 1 - Math.pow(1 - p, 3);
            case "ease_in_out" -> p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2;
            default -> p;
        };
    }

    /** A valid curve name, {@code fallback} for anything else. */
    public static String curve(String name, String fallback) {
        for (String e : EASES) {
            if (e.equals(name)) {
                return e;
            }
        }
        return fallback;
    }
}
