package dev.lumora.core.extras;

import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrosshairEditorTest {
    @TempDir
    Path dir;
    LumoraCore core;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    @Test
    void theCrosshairSitsInTheRealMiddleOfTheWindow() {
        // 1920 x 1080 at GUI scale 3: the GUI is 640 x 360, its centre pixel would start at real pixel 960 (1.5 px off)
        double[] c = CrosshairModule.centre(640, 360, 1920, 1080);
        assertEquals(959, Math.round(c[0] * 3), "the 3 px wide centre pixel covers 959..961 (middle 960.5)");
        assertEquals(539, Math.round(c[1] * 3));
        // GUI scale 4: exactly in the middle (958..961)
        assertArrayEquals(new double[]{239.5, 134.5}, CrosshairModule.centre(480, 270, 1920, 1080), 1e-9);
        // a width that does not divide by the scale: 1366 / 3 is shown as 456 GUI pixels
        assertEquals(682, Math.round(CrosshairModule.centre(456, 256, 1366, 768)[0] * 3), "683 is the middle");
        // the smoke test window (854 x 480, scale 2): horizontally as before, vertically half a GUI pixel higher, so
        // the 2 px centre pixel covers 239..240 around the middle 240 (before: 240..241)
        assertArrayEquals(new double[]{213, 119.5}, CrosshairModule.centre(427, 240, 854, 480), 1e-9);
        // no framebuffer size: the GUI centre
        assertArrayEquals(new double[]{320, 180}, CrosshairModule.centre(640, 360, 0, 0), 1e-9);
    }

    @Test
    void linesHaveNoGaps() {
        List<int[]> l = CrosshairEditor.line(0, 0, 6, 2);
        assertEquals(7, l.size());
        for (int i = 1; i < l.size(); i++) {
            assertTrue(Math.abs(l.get(i)[0] - l.get(i - 1)[0]) <= 1 && Math.abs(l.get(i)[1] - l.get(i - 1)[1]) <= 1);
        }
        assertArrayEquals(new int[]{6, 2}, l.get(l.size() - 1));
        assertEquals(1, CrosshairEditor.line(3, 3, 3, 3).size());
    }

    @Test
    void aFastStrokePaintsAContinuousLineWithoutMirroring() {
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        assertTrue(core.addonManager().setWanted("lumora-crosshair", true).isEmpty());
        CrosshairModule m = CrosshairModule.active();
        m.mode.set("custom");
        m.setCustomPixels(new boolean[CrosshairPresets.GRID * CrosshairPresets.GRID]);
        CrosshairEditor e = new CrosshairEditor(m, true);
        e.render(new FakeGraphics(), 0, 0, 400, -1, -1);
        // 400 px wide: preview 112 + 12, the grid starts at x 124, y 26 with 10 px cells
        int cell = 10;
        int gx = 124;
        int gy = 26;
        assertTrue(e.mouseClicked(gx + 2 * cell + 5, gy + 7 * cell + 5, 0));
        // the mouse jumps ten cells to the right in one event (fast stroke)
        e.mouseDragged(gx + 12 * cell + 5, gy + 7 * cell + 5, 0);
        e.mouseReleased();
        boolean[] px = m.customPixels();
        int n = CrosshairPresets.GRID;
        for (int x = 2; x <= 12; x++) {
            assertTrue(px[7 * n + x], "cell " + x + " of the stroke is painted");
        }
        int painted = 0;
        for (boolean b : px) {
            painted += b ? 1 : 0;
        }
        assertEquals(11, painted, "mirroring is off by default: only the stroke itself");
        // a stroke that starts on a painted cell erases
        assertTrue(e.mouseClicked(gx + 5 * cell + 5, gy + 7 * cell + 5, 0));
        e.mouseReleased();
        assertFalse(m.customPixels()[7 * n + 5]);
    }
}
