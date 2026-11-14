package dev.lumora.launcher.core.storage;

import dev.lumora.launcher.core.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HousekeepingTest {
    @TempDir
    Path tmp;

    private static Path file(Path p, int bytes, Instant modified) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[bytes]);
        Files.setLastModifiedTime(p, FileTime.from(modified));
        return p;
    }

    @Test
    void cacheLosesItsOldestFilesUntilBelowTheLimit() throws Exception {
        Instant now = Instant.now();
        Path old = file(tmp.resolve("cache/a.jar"), 600, now.minus(Duration.ofDays(3)));
        Path mid = file(tmp.resolve("cache/sub/b.jar"), 600, now.minus(Duration.ofDays(2)));
        Path young = file(tmp.resolve("cache/c.jar"), 600, now);
        assertEquals(600, Housekeeping.pruneCache(tmp.resolve("cache"), 1300));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(mid));
        assertTrue(Files.exists(young));
        assertEquals(0, Housekeeping.pruneCache(tmp.resolve("cache"), 0), "0 = no limit");
    }

    @Test
    void oldArchivedLogsOfOwnInstancesGo() throws Exception {
        LauncherPaths paths = new LauncherPaths(tmp.resolve("data"));
        Instant now = Instant.now();
        Path logs = paths.instance("26.3").resolve("logs");
        Path old = file(logs.resolve("2026-10-01-1.log.gz"), 10, now.minus(Duration.ofDays(10)));
        Path recent = file(logs.resolve("2026-11-13-1.log.gz"), 10, now.minus(Duration.ofDays(1)));
        Path latest = file(logs.resolve("latest.log"), 10, now.minus(Duration.ofDays(30)));
        Path output = file(logs.resolve("lumora-game-output.log"), 10, now.minus(Duration.ofDays(30)));
        assertEquals(0, Housekeeping.deleteOldLogs(paths, 0, now), "0 days = keep");
        assertEquals(1, Housekeeping.deleteOldLogs(paths, 7, now));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
        assertTrue(Files.exists(latest));
        assertTrue(Files.exists(output));
    }
}
