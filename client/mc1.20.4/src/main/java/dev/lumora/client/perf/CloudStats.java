package dev.lumora.client.perf;

/** Number of cloud geometry builds since start (performance comparison of the cloud optimization). */
public final class CloudStats {
    private static int builds;

    private CloudStats() {
    }

    public static void built() {
        builds++;
    }

    public static int builds() {
        return builds;
    }
}
