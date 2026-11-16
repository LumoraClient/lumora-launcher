package dev.lumora.core.perf;

import dev.lumora.api.render.HudGraphics;

/**
 * Frame Optimization: graphics that can draw a run of rectangles (a rounded panel, its border, the accent stripe) with
 * one draw call instead of one per rectangle. Only rectangles may be drawn between {@link #beginFills()} and
 * {@link #endFills()}; they are drawn in the same order as before, so overlapping translucent rectangles blend exactly
 * as they did one by one. Versions that already collect their GUI draws do not implement it.
 */
public interface FillBatch {
    /** Rectangles from here on are collected (calls may nest). */
    void beginFills();

    /** Draws the collected rectangles (when the outermost run ends). */
    void endFills();

    /** Starts a run on {@code g} if it supports it and Frame Optimization is on; pass the result to {@link #end}. */
    static boolean begin(HudGraphics g) {
        if (PerformanceEngine.frame && g instanceof FillBatch b) {
            b.beginFills();
            return true;
        }
        return false;
    }

    static void end(HudGraphics g, boolean begun) {
        if (begun) {
            ((FillBatch) g).endFills();
        }
    }
}
