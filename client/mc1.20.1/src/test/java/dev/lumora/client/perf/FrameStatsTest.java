package dev.lumora.client.perf;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameStatsTest {
    @Test
    void summaryOfConstantFrameTimes() {
        long[] t = new long[1000];
        Arrays.fill(t, 10_000_000L); // 10 ms -> 100 FPS
        FrameStats.Summary s = FrameStats.summarize(t);
        assertEquals(1000, s.frames());
        assertEquals(100.0, s.avgFps(), 1e-9);
        assertEquals(10.0, s.p99Ms(), 1e-9);
        assertEquals(100.0, s.onePercentLowFps(), 1e-9);
    }

    @Test
    void stutterShowsUpInLowsAndPercentiles() {
        long[] t = new long[1000];
        Arrays.fill(t, 10_000_000L);
        for (int i = 0; i < 10; i++) {
            t[i * 100] = 100_000_000L; // ten 100 ms hitches
        }
        FrameStats.Summary s = FrameStats.summarize(t);
        assertEquals(100.0, s.maxMs(), 1e-9);
        assertEquals(10.0, s.onePercentLowFps(), 1e-9, "slowest 1% = the ten 100 ms frames");
        assertEquals(10.0, s.p95Ms(), 1e-9);
        assertTrue(s.avgFps() < 100);
    }

    @Test
    void recordingLifecycle() throws Exception {
        FrameStats f = new FrameStats();
        assertFalse(f.isRecording());
        f.start(0.05);
        long now = System.nanoTime();
        f.onFrame(now);
        f.onFrame(now + 5_000_000);
        f.onFrame(now + 10_000_000);
        Thread.sleep(80);
        assertTrue(f.isFinished());
        FrameStats.Summary s = f.finish();
        assertEquals(2, s.frames());
        assertNull(f.finish(), "finish resets the recorder");
    }

    @Test
    void emptyInput() {
        assertNull(FrameStats.summarize(new long[0]));
    }
}
