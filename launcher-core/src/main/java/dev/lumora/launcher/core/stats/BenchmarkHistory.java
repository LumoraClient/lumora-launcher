package dev.lumora.launcher.core.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Results of the in-game F9 benchmark (diagnostics/benchmark-&lt;profile&gt;-&lt;epoch ms&gt;.json, written by the
 * companion mod) for the launcher's performance chart. Oldest first, at most the last 50 runs.
 */
public final class BenchmarkHistory {
    private static final Pattern FILE = Pattern.compile("^benchmark-(.+)-(\\d{10,15})\\.json$");
    private static final int MAX_RUNS = 50;

    public record Run(Instant time, double avgFps, double onePercentLowFps, double p99Ms, int frames, double seconds, String renderer) {
    }

    private BenchmarkHistory() {
    }

    public static List<Run> load(Path diagnosticsDir, String profileId) {
        List<Run> out = new ArrayList<>();
        if (!Files.isDirectory(diagnosticsDir)) {
            return out;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(diagnosticsDir)) {
            files = s.toList();
        } catch (IOException e) {
            return out;
        }
        for (Path f : files) {
            Matcher m = FILE.matcher(f.getFileName().toString());
            if (!m.matches() || !m.group(1).equals(profileId)) {
                continue;
            }
            try {
                if (Files.size(f) > 2 * 1024 * 1024) {
                    continue;
                }
                JsonObject o = Json.GSON.fromJson(Files.readString(f), JsonObject.class);
                JsonObject b = o.getAsJsonObject("benchmark");
                String renderer = null;
                if (o.has("renderer") && o.get("renderer").isJsonObject()) {
                    JsonObject r = o.getAsJsonObject("renderer");
                    if (r.has("gpuDevice") && r.get("gpuDevice").isJsonObject() && r.getAsJsonObject("gpuDevice").has("renderer")) {
                        renderer = r.getAsJsonObject("gpuDevice").get("renderer").getAsString();
                    }
                }
                out.add(new Run(Instant.ofEpochMilli(Long.parseLong(m.group(2))), b.get("avgFps").getAsDouble(),
                        b.get("onePercentLowFps").getAsDouble(), b.get("p99Ms").getAsDouble(), b.get("frames").getAsInt(),
                        b.get("seconds").getAsDouble(), renderer));
            } catch (IOException | JsonParseException | IllegalStateException | NullPointerException | ClassCastException
                     | NumberFormatException | UnsupportedOperationException ignored) {
                // not a benchmark file we understand
            }
        }
        out.sort(Comparator.comparing(Run::time));
        return out.size() > MAX_RUNS ? List.copyOf(out.subList(out.size() - MAX_RUNS, out.size())) : out;
    }
}
