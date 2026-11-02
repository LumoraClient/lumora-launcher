package dev.lumora.launcher.app.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * One animation clock for the whole launcher. Components ask for repaints either for a short time (a transition:
 * {@link #during}) or continuously while they are visible (a flowing effect: {@link #loop}). A single Swing timer
 * drives everything and stops when nothing is animated; hidden or minimised components cost nothing. With "Weniger
 * Bewegung" (reduce motion) loops never start and transitions finish at once.
 */
public final class Motion {
    /** About 30 frames per second. */
    private static final int FRAME_MS = 33;
    private static final long START = System.nanoTime();
    /** Component -> repaint until (nanoTime); Long.MAX_VALUE = loop. Weak: removed components are forgotten. */
    private static final Map<JComponent, Long> ACTIVE = new WeakHashMap<>();
    private static Timer timer;

    private Motion() {
    }

    /** Seconds since start (for phases of flowing effects). */
    public static double seconds() {
        return (System.nanoTime() - START) / 1e9;
    }

    /** Repaints {@code c} every frame for {@code millis} (transitions). Must run on the Swing thread. */
    public static void during(JComponent c, int millis) {
        if (Theme.reduceMotion) {
            c.repaint();
            return;
        }
        long until = System.nanoTime() + millis * 1_000_000L;
        ACTIVE.merge(c, until, Math::max);
        ensureTimer();
    }

    /** Repaints {@code c} every frame while it is showing (flowing effects); no-op with reduce motion. */
    public static void loop(JComponent c) {
        if (Theme.reduceMotion) {
            return;
        }
        ACTIVE.put(c, Long.MAX_VALUE);
        ensureTimer();
    }

    public static void stop(JComponent c) {
        ACTIVE.remove(c);
    }

    private static void ensureTimer() {
        if (timer == null) {
            timer = new Timer(FRAME_MS, e -> tick());
            timer.setCoalesce(true);
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private static void tick() {
        long now = System.nanoTime();
        boolean any = false;
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<JComponent, Long> e = it.next();
            JComponent c = e.getKey();
            if (c == null) {
                continue;
            }
            if (e.getValue() != Long.MAX_VALUE && e.getValue() < now) {
                it.remove();
                c.repaint();
                continue;
            }
            any = true;
            if (c.isShowing()) {
                c.repaint();
            }
        }
        if (!any) {
            timer.stop();
        }
    }

    /** Ease-out cubic (fast start, soft landing). */
    public static double easeOut(double t) {
        double u = 1 - Math.clamp(t, 0, 1);
        return 1 - u * u * u;
    }

    /** Ease-in-out sine (for back-and-forth glows). */
    public static double easeInOut(double t) {
        return 0.5 - 0.5 * Math.cos(Math.PI * Math.clamp(t, 0, 1));
    }

    /**
     * A value that glides to its target: {@link #set} starts the glide, {@link #value} reads the current position.
     * The owner calls {@link Motion#during} with {@link #millis()} so it is repainted while gliding.
     */
    public static final class Tween {
        private final int millis;
        private double from;
        private double to;
        private long start;

        public Tween(double initial, int millis) {
            this.from = initial;
            this.to = initial;
            this.millis = millis;
        }

        public int millis() {
            return millis;
        }

        /** @return true if the target changed */
        public boolean set(double target) {
            if (target == to) {
                return false;
            }
            from = value();
            to = target;
            start = System.nanoTime();
            if (Theme.reduceMotion) {
                from = target;
            }
            return true;
        }

        /** Jumps without animation. */
        public void jump(double target) {
            from = target;
            to = target;
        }

        public double value() {
            if (from == to) {
                return to;
            }
            double t = (System.nanoTime() - start) / (millis * 1_000_000.0);
            if (t >= 1) {
                from = to;
                return to;
            }
            return from + (to - from) * easeOut(t);
        }

        public double target() {
            return to;
        }
    }
}
