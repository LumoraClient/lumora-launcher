package dev.lumora.launcher.core;

import dev.lumora.launcher.core.util.OsInfo;

import java.nio.file.Path;

/**
 * Directory layout below the install directory. Shared, content-addressed data (libraries, assets,
 * Java runtimes, vanilla versions) lives once; every runtime profile has its own game directory
 * because the mod sets differ.
 */
public record LauncherPaths(Path root) {
    public static final String APP_DIR_NAME = "LumoraClient";

    public static Path defaultRoot(OsInfo os) {
        String override = System.getenv("LUMORA_HOME");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String home = System.getProperty("user.home");
        return switch (os.name()) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield Path.of(appData != null ? appData : home, APP_DIR_NAME);
            }
            case OSX -> Path.of(home, "Library", "Application Support", APP_DIR_NAME);
            case LINUX -> {
                String xdg = System.getenv("XDG_DATA_HOME");
                yield (xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "share")).resolve("lumora-client");
            }
        };
    }

    public Path configFile() {
        return root.resolve("config").resolve("launcher.json");
    }

    public Path lockfileOverride() {
        return root.resolve("config").resolve("profiles.override.json");
    }

    /** Play time and last start per profile. */
    public Path playStats() {
        return root.resolve("config").resolve("playstats.json");
    }

    public Path authFile() {
        return root.resolve("auth").resolve("session.bin");
    }

    public Path logs() {
        return root.resolve("logs");
    }

    public Path libraries() {
        return root.resolve("libraries");
    }

    public Path assets() {
        return root.resolve("assets");
    }

    public Path versions() {
        return root.resolve("versions");
    }

    public Path runtimes() {
        return root.resolve("runtime");
    }

    public Path modCache() {
        return root.resolve("cache").resolve("mods");
    }

    public Path instance(String profileId) {
        return root.resolve("instances").resolve(profileId);
    }

    public Path natives(String profileId) {
        return instance(profileId).resolve(".natives");
    }

    public Path diagnostics() {
        return root.resolve("diagnostics");
    }
}
