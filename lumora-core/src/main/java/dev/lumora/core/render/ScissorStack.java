package dev.lumora.core.render;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The scissor stack of the Minecraft versions whose GUI has none that honours the current transformation (1.8.9 -
 * 1.18.2): {@code HudGraphics#enableScissor} promises a rectangle in transformed coordinates and nesting, so the
 * version's {@code GuiGraphics} transforms the rectangle through its matrices ({@link #transform}), pushes it here
 * (the intersection with the enclosing rectangle) and restores the enclosing rectangle on {@link #pop()}.
 * <p>
 * Minecraft-independent so it can be unit tested; the version module only reads its matrices and calls glScissor
 * with {@link Rect#toWindow}.
 */
public final class ScissorStack {
    /**
     * The stack of the GUI. Shared because the {@code GuiGraphics} wrappers of the old versions are created per frame
     * and call site while the GL scissor state is global. Render thread only.
     */
    public static final ScissorStack GUI = new ScissorStack();

    private final Deque<Rect> stack = new ArrayDeque<>();

    /**
     * An axis-aligned rectangle in GUI-scaled screen coordinates (after all transformations); always
     * {@code x1 <= x2} and {@code y1 <= y2}, an empty intersection has zero width or height.
     */
    public record Rect(float x1, float y1, float x2, float y2) {
        public Rect {
            if (x2 < x1) {
                x2 = x1;
            }
            if (y2 < y1) {
                y2 = y1;
            }
        }

        public Rect intersect(Rect other) {
            float nx1 = Math.max(x1, other.x1);
            float ny1 = Math.max(y1, other.y1);
            return new Rect(nx1, ny1, Math.max(nx1, Math.min(x2, other.x2)), Math.max(ny1, Math.min(y2, other.y2)));
        }

        /**
         * The rectangle in window pixels as {@code {x, y, width, height}} for glScissor (origin bottom left). Edges
         * are rounded to the nearest pixel, so integer GUI coordinates at an integer GUI scale map exactly.
         */
        public int[] toWindow(double guiScale, int windowHeight) {
            int left = (int) Math.round(x1 * guiScale);
            int right = (int) Math.round(x2 * guiScale);
            int top = (int) Math.round(y1 * guiScale);
            int bottom = (int) Math.round(y2 * guiScale);
            return new int[]{left, windowHeight - bottom, Math.max(0, right - left), Math.max(0, bottom - top)};
        }
    }

    /** Pushes {@code rect} clipped to the current innermost rectangle and returns the rectangle that now applies. */
    public Rect push(Rect rect) {
        Rect top = stack.peek();
        Rect applied = top == null ? rect : top.intersect(rect);
        stack.push(applied);
        return applied;
    }

    /**
     * Removes the innermost rectangle and returns the one that applies again, or {@code null} when scissoring ends
     * (an unbalanced pop on an empty stack also returns {@code null}).
     */
    public Rect pop() {
        stack.poll();
        return stack.peek();
    }

    /** The rectangle that currently applies, {@code null} without scissoring. */
    public Rect peek() {
        return stack.peek();
    }

    public int depth() {
        return stack.size();
    }

    public void clear() {
        stack.clear();
    }

    /**
     * The screen bounds of the rectangle {@code (x1, y1) - (x2, y2)} (z = 0) after the given 4x4 matrices in
     * column-major order (OpenGL / {@code Matrix4f#store} layout), applied in order: the first one is the innermost
     * (e.g. the pose), the last one the outermost (e.g. the model-view matrix). Rotations give the axis-aligned bounds.
     */
    public static Rect transform(float x1, float y1, float x2, float y2, float[]... matrices) {
        float[][] corners = {{x1, y1, 0, 1}, {x2, y1, 0, 1}, {x1, y2, 0, 1}, {x2, y2, 0, 1}};
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (float[] v : corners) {
            for (float[] m : matrices) {
                v = multiply(m, v);
            }
            float w = v[3] == 0 ? 1 : v[3];
            float x = v[0] / w;
            float y = v[1] / w;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return new Rect(minX, minY, maxX, maxY);
    }

    private static float[] multiply(float[] m, float[] v) {
        if (m.length != 16) {
            throw new IllegalArgumentException("4x4 matrix expected, got " + m.length + " values");
        }
        float[] r = new float[4];
        for (int row = 0; row < 4; row++) {
            r[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3];
        }
        return r;
    }
}
