package dev.lumora.core.zoom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Zoom transition: curves, duration in milliseconds, jump without transition, new target from where it is. */
class ZoomMotionTest {
    private static final long MS = 1_000_000L;

    @Test
    void movesAlongTheCurveInTheSetTime() {
        ZoomMotion m = new ZoomMotion();
        assertEquals(1, m.value(0));
        m.aim(4, 0, true, "linear", 100);
        assertEquals(1, m.value(0), 1e-9);
        assertEquals(2.5, m.value(50 * MS), 1e-9, "linear: half way after half the time");
        assertTrue(m.moving(50 * MS));
        assertEquals(4, m.value(100 * MS), 1e-9);
        assertFalse(m.moving(100 * MS));

        // ease out: fast at the start, ease in: slow at the start
        m.aim(1, 200 * MS, true, "ease_out", 100);
        assertTrue(m.value(250 * MS) < 2.5, "ease out has covered more than half: " + m.value(250 * MS));
        m.aim(4, 400 * MS, true, "ease_in", 100);
        assertTrue(m.value(450 * MS) < 2.5, "ease in has covered less than half: " + m.value(450 * MS));
        assertEquals(4, m.value(500 * MS), 1e-9);
    }

    @Test
    void withoutTransitionAndFromTheMiddle() {
        ZoomMotion m = new ZoomMotion();
        m.aim(4, 0, false, "ease_out", 100);
        assertEquals(4, m.value(0), "transition off: at once");
        m.aim(4, 10 * MS, true, "linear", 100);
        assertFalse(m.moving(10 * MS), "the same target starts nothing");
        m.aim(1, 0, true, "linear", 0);
        assertEquals(1, m.value(0), "0 ms: at once");

        m.aim(5, 0, true, "linear", 100);
        double middle = m.value(50 * MS);
        m.aim(1, 50 * MS, true, "linear", 100);
        assertEquals(middle, m.value(50 * MS), 1e-9, "a new target starts where the factor is, no jump");
        assertEquals("ease_out", ZoomMotion.curve("bogus", "ease_out"));
        assertEquals(0.5, ZoomMotion.ease("ease_in_out", 0.5), 1e-9);
    }
}
