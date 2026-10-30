package dev.lumora.launcher.core.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkHistoryTest {
    @TempDir
    Path tmp;

    static String run(double fps) {
        return """
                {"renderer":{"gpuDevice":{"renderer":"Radeon RX Vega"}},
                 "benchmark":{"frames":3600,"seconds":30.0,"avgFps":%s,"p50Ms":8.1,"p95Ms":10.2,"p99Ms":14.5,"maxMs":30.0,
                              "onePercentLowFps":%s,"pointOnePercentLowFps":40.0}}""".formatted(fps, fps * 0.6);
    }

    @Test
    void readsRunsOfOneProfileOldestFirst() throws IOException {
        Files.writeString(tmp.resolve("benchmark-1.21.11-1760000002000.json"), run(140));
        Files.writeString(tmp.resolve("benchmark-1.21.11-1760000001000.json"), run(120));
        Files.writeString(tmp.resolve("benchmark-26.1.2-1760000003000.json"), run(90));
        Files.writeString(tmp.resolve("benchmark-1.21.11-1760000004000.json"), "{broken");
        Files.writeString(tmp.resolve("game-1.21.11-latest.json"), "{}");
        List<BenchmarkHistory.Run> runs = BenchmarkHistory.load(tmp, "1.21.11");
        assertEquals(List.of(120.0, 140.0), runs.stream().map(BenchmarkHistory.Run::avgFps).toList());
        assertEquals("Radeon RX Vega", runs.getFirst().renderer());
        assertEquals(72.0, runs.getFirst().onePercentLowFps(), 1e-9);
        assertEquals(1, BenchmarkHistory.load(tmp, "26.1.2").size());
    }
}
