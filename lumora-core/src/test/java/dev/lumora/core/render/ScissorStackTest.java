package dev.lumora.core.render;

import dev.lumora.core.render.ScissorStack.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScissorStackTest {
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    /** Column-major translate(tx, ty) * scale(sx, sy), like PoseStack.translate followed by PoseStack.scale. */
    private static float[] translateScale(float tx, float ty, float sx, float sy) {
        return new float[]{sx, 0, 0, 0, 0, sy, 0, 0, 0, 0, 1, 0, tx, ty, 0, 1};
    }

    @Test
    void nestedRectangleIsClippedToTheOuterOneAndPopRestoresIt() {
        ScissorStack stack = new ScissorStack();
        Rect outer = stack.push(new Rect(10, 10, 100, 60));
        assertEquals(new Rect(10, 10, 100, 60), outer);

        Rect inner = stack.push(new Rect(50, 0, 200, 40));
        assertEquals(new Rect(50, 10, 100, 40), inner);

        assertEquals(outer, stack.pop());
        assertNull(stack.pop());
        assertNull(stack.pop(), "an unbalanced pop must not fail");
        assertEquals(0, stack.depth());
    }

    @Test
    void disjointRectanglesGiveAnEmptyRectangleThatStaysEmpty() {
        ScissorStack stack = new ScissorStack();
        stack.push(new Rect(0, 0, 10, 10));
        Rect empty = stack.push(new Rect(20, 20, 30, 30));
        assertEquals(0, empty.x2() - empty.x1());
        assertEquals(0, empty.y2() - empty.y1());
        Rect deeper = stack.push(new Rect(0, 0, 100, 100));
        assertEquals(0, deeper.x2() - deeper.x1(), "nothing nested in an empty rectangle may become visible again");
        assertArrayEquals(new int[]{0, 0}, new int[]{deeper.toWindow(2, 100)[2], deeper.toWindow(2, 100)[3]});
    }

    @Test
    void transformAppliesTranslationAndScale() {
        // a ChatPanel drawn at (300, 150) with its own coordinates, clipping (5, 5) - (45, 25) of itself
        Rect r = ScissorStack.transform(5, 5, 45, 25, translateScale(300, 150, 1, 1));
        assertEquals(new Rect(305, 155, 345, 175), r);

        Rect scaled = ScissorStack.transform(0, 0, 10, 20, translateScale(4, 6, 0.5f, 2));
        assertEquals(new Rect(4, 6, 9, 46), scaled);
    }

    @Test
    void matricesApplyInnermostFirst() {
        // pose translate(10, 0), then a model-view scale(2): (0..5) -> (10..15) -> (20..30)
        Rect r = ScissorStack.transform(0, 0, 5, 5, translateScale(10, 0, 1, 1), translateScale(0, 0, 2, 2));
        assertEquals(new Rect(20, 0, 30, 10), r);
        assertEquals(new Rect(1, 2, 3, 4), ScissorStack.transform(1, 2, 3, 4, IDENTITY));
        assertEquals(new Rect(1, 2, 3, 4), ScissorStack.transform(1, 2, 3, 4));
    }

    @Test
    void rotationGivesTheAxisAlignedBounds() {
        float[] rot90 = {0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; // (x, y) -> (-y, x)
        Rect r = ScissorStack.transform(0, 0, 10, 4, rot90);
        assertEquals(-4, r.x1(), 1e-6);
        assertEquals(0, r.y1(), 1e-6);
        assertEquals(0, r.x2(), 1e-6);
        assertEquals(10, r.y2(), 1e-6);
    }

    @Test
    void windowPixelsUseABottomLeftOrigin() {
        // GUI scale 2 on a 480 px high window: GUI (10, 20) - (110, 70) -> pixels x 20..220, top 40, bottom 140
        assertArrayEquals(new int[]{20, 340, 200, 100}, new Rect(10, 20, 110, 70).toWindow(2, 480));
        // a fractional HUD scale rounds the edges instead of truncating the size
        assertArrayEquals(new int[]{8, 89, 7, 9}, new Rect(2.6f, 0.8f, 4.9f, 3.6f).toWindow(3, 100));
    }
}
