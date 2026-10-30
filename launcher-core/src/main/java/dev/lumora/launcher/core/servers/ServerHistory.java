package dev.lumora.launcher.core.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Player counts the launcher measured itself (one sample per server every 10 minutes at most while the server page
 * is used, kept 8 days) for the sparkline and the weekly trend. Nothing comes from a third-party statistics service,
 * so the history starts with the first measurement on this computer.
 */
public final class ServerHistory {
    static final long MIN_GAP_SEC = 600;
    static final long KEEP_SEC = 8 * 24 * 3600;
    private static final long DAY = 24 * 3600;

    private record Sample(long at, int online) {
    }

    private final Path file;
    private final Map<String, List<Sample>> samples = new HashMap<>();

    public ServerHistory(Path dir) {
        this.file = dir.resolve("server-history.json");
        load();
    }

    /** Records a measurement (ignored if the last one is younger than 10 minutes). */
    public synchronized void record(String address, int online, long nowSec) {
        List<Sample> l = samples.computeIfAbsent(address.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
        if (!l.isEmpty() && nowSec - l.get(l.size() - 1).at < MIN_GAP_SEC) {
            return;
        }
        l.add(new Sample(nowSec, Math.max(0, online)));
        l.removeIf(s -> nowSec - s.at > KEEP_SEC);
    }

    /**
     * Average players in {@code buckets} equal slots over the last {@code windowSec} seconds (oldest first); -1 for
     * slots without a measurement.
     */
    public synchronized int[] sparkline(String address, long nowSec, long windowSec, int buckets) {
        int[] out = new int[buckets];
        long[] sum = new long[buckets];
        int[] n = new int[buckets];
        for (Sample s : samples.getOrDefault(address.toLowerCase(Locale.ROOT), List.of())) {
            long age = nowSec - s.at;
            if (age < 0 || age >= windowSec) {
                continue;
            }
            int b = buckets - 1 - (int) (age * buckets / windowSec);
            sum[b] += s.online;
            n[b]++;
        }
        for (int i = 0; i < buckets; i++) {
            out[i] = n[i] == 0 ? -1 : (int) (sum[i] / n[i]);
        }
        return out;
    }

    /**
     * Change in percent compared with a week ago: current value against the average of the samples 6-8 days old.
     * Null while there is no measurement that old yet.
     */
    public synchronized Double weekChange(String address, int current, long nowSec) {
        long sum = 0;
        int n = 0;
        for (Sample s : samples.getOrDefault(address.toLowerCase(Locale.ROOT), List.of())) {
            long age = nowSec - s.at;
            if (age >= 6 * DAY && age <= 8 * DAY) {
                sum += s.online;
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        double before = (double) sum / n;
        if (before <= 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return (current - before) / before * 100.0;
    }

    /** Age in seconds of the oldest sample of this server (for "Verlauf seit …"), or -1. */
    public synchronized long trackedFor(String address, long nowSec) {
        List<Sample> l = samples.get(address.toLowerCase(Locale.ROOT));
        return l == null || l.isEmpty() ? -1 : nowSec - l.get(0).at;
    }

    public synchronized void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, List<Sample>> e : samples.entrySet()) {
            JsonArray a = new JsonArray();
            for (Sample s : e.getValue()) {
                JsonArray p = new JsonArray();
                p.add(s.at);
                p.add(s.online);
                a.add(p);
            }
            root.add(e.getKey(), a);
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.warn("Saving server history failed: " + e);
        }
    }

    private void load() {
        try {
            if (!Files.isRegularFile(file)) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                List<Sample> l = new ArrayList<>();
                for (JsonElement p : e.getValue().getAsJsonArray()) {
                    JsonArray a = p.getAsJsonArray();
                    l.add(new Sample(a.get(0).getAsLong(), a.get(1).getAsInt()));
                }
                samples.put(e.getKey(), l);
            }
        } catch (IOException | RuntimeException e) {
            Log.warn("Server history unreadable, starting empty: " + e);
        }
    }
}
