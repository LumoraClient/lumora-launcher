package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Up to 0.1.0 Minecraft 26.1.2 had two profiles, "vulkan" (VulkanMod) and "opengl" (Sodium). Both are now the
 * single profile "26.1.2". On first start the more recently played old instance (worlds, options, screenshots)
 * is renamed to instances/26.1.2; the other one stays untouched on disk. Nothing is deleted.
 */
public final class InstanceMigration {
    public static final String TARGET = "26.1.2";
    public static final List<String> LEGACY = List.of("vulkan", "opengl");
    /** Old selectedProfile values and the profile that replaces them. */
    public static final Map<String, String> LEGACY_PROFILE_IDS = Map.of("vulkan", TARGET, "opengl", TARGET);

    private InstanceMigration() {
    }

    /** Returns the migrated legacy instance id, if a migration happened. */
    public static Optional<String> migrate(LauncherPaths paths) {
        Path target = paths.instance(TARGET);
        if (Files.exists(target)) {
            return Optional.empty();
        }
        String best = null;
        FileTime bestTime = null;
        for (String id : LEGACY) {
            Path dir = paths.instance(id);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            FileTime t = lastPlayed(dir);
            if (best == null || t.compareTo(bestTime) > 0) {
                best = id;
                bestTime = t;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        try {
            Files.move(paths.instance(best), target);
            Log.info("Migrated instance '" + best + "' to '" + TARGET + "' (Vulkan profile removed)");
            return Optional.of(best);
        } catch (IOException e) {
            Log.warn("Could not migrate instance '" + best + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Last game start: the game log if present, otherwise the options file or the folder itself. */
    static FileTime lastPlayed(Path instance) {
        for (Path p : List.of(instance.resolve("logs/latest.log"), instance.resolve("options.txt"), instance)) {
            try {
                if (Files.exists(p)) {
                    return Files.getLastModifiedTime(p);
                }
            } catch (IOException ignored) {
                // try the next candidate
            }
        }
        return FileTime.fromMillis(0);
    }
}
