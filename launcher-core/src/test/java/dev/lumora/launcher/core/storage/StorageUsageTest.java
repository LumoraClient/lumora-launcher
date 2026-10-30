package dev.lumora.launcher.core.storage;

import dev.lumora.launcher.core.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageUsageTest {
    @TempDir
    Path tmp;

    private static Path file(Path p, int bytes) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[bytes]);
        return p;
    }

    @Test
    void categoriesAddUp() throws IOException {
        Path root = tmp.resolve("data");
        LauncherPaths paths = new LauncherPaths(root);
        file(paths.runtimes().resolve("java/bin/java.exe"), 100);
        file(root.resolve("cache/mods/a/x.jar"), 40);
        file(paths.libraries().resolve("org/lib.jar"), 30);
        file(paths.instance("26.1.2").resolve("saves/World/level.dat"), 20);
        file(paths.instance("26.1.2-vanilla").resolve("saves/Other/level.dat"), 5);
        file(paths.instance("26.1.2").resolve("mods/own.jar"), 10);
        file(paths.instance("26.1.2").resolve("options.txt"), 7);
        StorageUsage.Report r = new StorageUsage(paths).scan();
        assertEquals(100, r.get(StorageUsage.Kind.RUNTIMES).bytes());
        assertEquals(40, r.get(StorageUsage.Kind.CACHE).bytes());
        assertEquals(25, r.get(StorageUsage.Kind.WORLDS).bytes(), "worlds of every instance (also Vanilla/Fabric)");
        assertEquals(2, r.get(StorageUsage.Kind.WORLDS).files());
        assertEquals(10, r.get(StorageUsage.Kind.MODS).bytes());
        assertEquals(7, r.get(StorageUsage.Kind.OTHER).bytes());
        assertEquals(212, r.total());
        assertEquals(40, r.unusedBytes(), "the mod download cache is not needed by any instance");
    }

    @Test
    void clearOnlyWhatTheLauncherCanRestore() throws IOException {
        Path root = tmp.resolve("data");
        LauncherPaths paths = new LauncherPaths(root);
        Path rt = file(paths.runtimes().resolve("java/bin/java.exe"), 100);
        Path world = file(paths.instance("p").resolve("saves/W/level.dat"), 20);
        StorageUsage s = new StorageUsage(paths);
        assertEquals(100, s.clear(StorageUsage.Kind.RUNTIMES));
        assertFalse(Files.exists(rt));
        assertTrue(Files.isDirectory(paths.runtimes()), "the folder itself stays");
        assertThrows(IllegalArgumentException.class, () -> s.clear(StorageUsage.Kind.WORLDS));
        assertTrue(Files.exists(world));
    }

    @Test
    void unusedKeepsRecentLogs() throws IOException {
        Path root = tmp.resolve("data");
        LauncherPaths paths = new LauncherPaths(root);
        Path oldLog = file(paths.logs().resolve("launcher-old.log"), 10);
        Files.setLastModifiedTime(oldLog, FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)));
        Path newLog = file(paths.logs().resolve("launcher.log"), 10);
        Path natives = file(paths.natives("p").resolve("lwjgl.dll"), 10);
        StorageUsage s = new StorageUsage(paths);
        assertEquals(20, s.clearUnused());
        assertFalse(Files.exists(oldLog));
        assertFalse(Files.exists(natives));
        assertTrue(Files.exists(newLog));
    }

    @Test
    void formats() {
        assertEquals("0 B", StorageUsage.format(0));
        assertEquals("993 kB", StorageUsage.format(993_000));
        assertEquals("710.1 MB", StorageUsage.format(710_100_000));
        assertEquals("9.00 GB", StorageUsage.format(9_000_000_000L));
    }
}
