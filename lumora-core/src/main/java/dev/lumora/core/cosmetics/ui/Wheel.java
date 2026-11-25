package dev.lumora.core.cosmetics.ui;

import dev.lumora.api.render.HudGraphics;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * A ring of slots around a centre, slot 0 at the top, drawn from horizontal runs of one slot each (HudGraphics has no
 * polygons). The runs are computed once per position, size and slot count; separators and the inner and outer edge
 * get their own colour.
 */
final class Wheel {
    static final int LINE = -1;
    private int cx;
    private int cy;
    private int outer;
    private int inner;
    private int slots;
    /** Runs: y, x1, x2, slot (-1 = separator line), four ints each. */
    private int[] runs = new int[0];
    private int runCount;

    /** Places the ring; recomputes the runs only when something changed. */
    void layout(int cx, int cy, int outer, float innerRatio, int slots) {
        int in = Math.round(outer * innerRatio);
        if (cx == this.cx && cy == this.cy && outer == this.outer && in == inner && slots == this.slots) {
            return;
        }
        this.cx = cx;
        this.cy = cy;
        this.outer = outer;
        this.inner = in;
        this.slots = slots;
        build();
    }

    int cx() {
        return cx;
    }

    int cy() {
        return cy;
    }

    int outer() {
        return outer;
    }

    int inner() {
        return inner;
    }

    int slots() {
        return slots;
    }

    /** Draws the ring; {@code color} gives the colour of a slot index, {@code line} the separators. */
    void draw(HudGraphics g, IntUnaryOperator color, int line) {
        for (int i = 0; i < runCount; i++) {
            int o = i * 4;
            int slot = runs[o + 3];
            g.fill(runs[o + 1], runs[o], runs[o + 2] + 1, runs[o] + 1, slot < 0 ? line : color.applyAsInt(slot));
        }
    }

    /** Centre of slot {@code i}'s label at the middle radius. */
    int[] labelPos(int i) {
        double a = -Math.PI / 2 + i * 2 * Math.PI / slots;
        int r = (inner + outer) / 2;
        return new int[] {cx + (int) Math.round(Math.cos(a) * r), cy + (int) Math.round(Math.sin(a) * r)};
    }

    /** Width a label of one slot may use. */
    int labelWidth() {
        double step = 2 * Math.PI / slots;
        int r = (inner + outer) / 2;
        return (int) Math.max(20, Math.min(90, 2 * r * Math.sin(step / 2) - 6));
    }

    boolean inCenter(double mx, double my) {
        double dx = mx + 0.5 - cx;
        double dy = my + 0.5 - cy;
        return dx * dx + dy * dy < inner * inner;
    }

    /** Slot under the point (a little beyond the outer edge counts), -1 if none. */
    int slotAt(double mx, double my) {
        double dx = mx + 0.5 - cx;
        double dy = my + 0.5 - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (slots <= 0 || d < inner || d > outer + 6) {
            return -1;
        }
        return slotOfAngle(Math.atan2(dy, dx));
    }

    /** Slot in the direction of the point from the centre, whatever the distance (for "release to play"). */
    int slotToward(double mx, double my, double minDistance) {
        double dx = mx + 0.5 - cx;
        double dy = my + 0.5 - cy;
        if (slots <= 0 || dx * dx + dy * dy < minDistance * minDistance) {
            return -1;
        }
        return slotOfAngle(Math.atan2(dy, dx));
    }

    private int slotOfAngle(double angle) {
        double step = 2 * Math.PI / slots;
        double rel = angle + Math.PI / 2 + step / 2;
        rel = ((rel % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
        return Math.min(slots - 1, (int) (rel / step));
    }

    private void build() {
        int[] out = new int[1024];
        int count = 0;
        double step = 2 * Math.PI / slots;
        for (int y = -outer; y <= outer; y++) {
            int runStart = Integer.MIN_VALUE;
            int runSlot = -2;
            for (int x = -outer; x <= outer + 1; x++) {
                int slot = -2;
                if (x <= outer) {
                    double px = x + 0.5;
                    double py = y + 0.5;
                    double d = Math.sqrt(px * px + py * py);
                    if (d >= inner && d <= outer) {
                        double angle = Math.atan2(py, px);
                        slot = slotOfAngle(angle);
                        double rel = angle + Math.PI / 2 + step / 2;
                        rel = ((rel % step) + step) % step;
                        double edge = Math.min(rel, step - rel) * d;
                        if (edge < 0.7 || d - inner < 0.8 || outer - d < 0.8) {
                            slot = LINE;
                        }
                    }
                }
                if (slot != runSlot) {
                    if (runSlot != -2) {
                        if (count + 4 > out.length) {
                            out = Arrays.copyOf(out, out.length * 2);
                        }
                        out[count++] = cy + y;
                        out[count++] = cx + runStart;
                        out[count++] = cx + x - 1;
                        out[count++] = runSlot;
                    }
                    runStart = x;
                    runSlot = slot;
                }
            }
        }
        runs = out;
        runCount = count / 4;
    }
}
