package dev.lumora.client.perf;

import java.util.Arrays;

/**
 * Frame-time recorder. Called once per frame from the render thread (see MinecraftFrameMixin).
 * Keeps no per-frame allocations: frame times go into a preallocated long[] during a benchmark.
 */
public final class FrameStats {
    public static final FrameStats INSTANCE = new FrameStats();
    private static final int MAX_SAMPLES = 1 << 20;

    public record Summary(int frames, double seconds, double avgFps, double p50Ms, double p95Ms, double p99Ms,
                          double maxMs, double onePercentLowFps, double pointOnePercentLowFps) {
        public String format() {
            return String.format(java.util.Locale.ROOT,
                    "frames=%d duration=%.1fs avgFPS=%.1f 1%%low=%.1f 0.1%%low=%.1f frametime p50=%.2fms p95=%.2fms p99=%.2fms max=%.2fms",
                    frames, seconds, avgFps, onePercentLowFps, pointOnePercentLowFps, p50Ms, p95Ms, p99Ms, maxMs);
        }
    }

    private long lastFrameNs;
    private long[] samples;
    private int count;
    private long recordingUntilNs;

    FrameStats() {
    }

    /** Render thread only. */
    public void onFrame(long nowNs) {
        long last = lastFrameNs;
        lastFrameNs = nowNs;
        long[] s = samples;
        if (s == null || last == 0) {
            return;
        }
        if (nowNs > recordingUntilNs || count >= s.length) {
            return;
        }
        s[count++] = nowNs - last;
    }

    public synchronized void start(double seconds) {
        samples = new long[MAX_SAMPLES];
        count = 0;
        recordingUntilNs = System.nanoTime() + (long) (seconds * 1e9);
    }

    public boolean isRecording() {
        return samples != null && System.nanoTime() <= recordingUntilNs;
    }

    public boolean isFinished() {
        return samples != null && System.nanoTime() > recordingUntilNs;
    }

    /** Stops the recording and returns the summary (null if nothing was recorded). */
    public synchronized Summary finish() {
        long[] s = samples;
        int n = count;
        samples = null;
        if (s == null || n == 0) {
            return null;
        }
        return summarize(Arrays.copyOf(s, n));
    }

    public static Summary summarize(long[] frameTimesNs) {
        int n = frameTimesNs.length;
        if (n == 0) {
            return null;
        }
        long[] sorted = frameTimesNs.clone();
        Arrays.sort(sorted);
        long total = 0;
        for (long v : sorted) {
            total += v;
        }
        double seconds = total / 1e9;
        return new Summary(n, seconds, n / seconds,
                percentileMs(sorted, 0.50), percentileMs(sorted, 0.95), percentileMs(sorted, 0.99),
                sorted[n - 1] / 1e6, lowFps(sorted, 0.01), lowFps(sorted, 0.001));
    }

    /** Nearest-rank percentile. */
    static double percentileMs(long[] sorted, double p) {
        int idx = (int) Math.ceil(p * sorted.length) - 1;
        return sorted[Math.clamp(idx, 0, sorted.length - 1)] / 1e6;
    }

    /** "x% low": average FPS over the slowest x% of frames (at least one frame). */
    static double lowFps(long[] sorted, double fraction) {
        int k = Math.max(1, (int) Math.floor(sorted.length * fraction));
        long sum = 0;
        for (int i = sorted.length - k; i < sorted.length; i++) {
            sum += sorted[i];
        }
        return k / (sum / 1e9);
    }
}
