package dev.lumora.client.perf;

/** Number of the current world frame (render thread): lets per-frame caches know when a new frame began. */
public final class FrameCounter {
    private static int frame;

    private FrameCounter() {
    }

    public static void next() {
        frame++;
    }

    public static int current() {
        return frame;
    }
}
