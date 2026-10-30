package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceMigrationTest {
    @TempDir
    Path tmp;

    private static void instance(LauncherPaths paths, String id, long lastPlayedMillis) throws IOException {
        Path log = paths.instance(id).resolve("logs/latest.log");
        Files.createDirectories(log.getParent());
        Files.writeString(log, id);
        Files.writeString(paths.instance(id).resolve("saves.txt"), "world of " + id);
        Files.setLastModifiedTime(log, FileTime.fromMillis(lastPlayedMillis));
    }

    @Test
    void movesTheMostRecentlyPlayedLegacyInstanceAndKeepsTheOther() throws IOException {
        LauncherPaths paths = new LauncherPaths(tmp);
        instance(paths, "vulkan", 2_000_000_000_000L);
        instance(paths, "opengl", 1_000_000_000_000L);
        assertEquals(Optional.of("vulkan"), InstanceMigration.migrate(paths));
        assertEquals("world of vulkan", Files.readString(paths.instance("26.1.2").resolve("saves.txt")));
        assertTrue(Files.isDirectory(paths.instance("opengl")), "the other instance is never deleted");
        // runs only once
        assertEquals(Optional.empty(), InstanceMigration.migrate(paths));
    }

    @Test
    void doesNothingWithoutLegacyInstancesOrWhenTargetExists() throws IOException {
        LauncherPaths paths = new LauncherPaths(tmp);
        assertEquals(Optional.empty(), InstanceMigration.migrate(paths));
        instance(paths, "opengl", 1_000L);
        Files.createDirectories(paths.instance("26.1.2"));
        assertEquals(Optional.empty(), InstanceMigration.migrate(paths));
        assertTrue(Files.isDirectory(paths.instance("opengl")));
    }
}
