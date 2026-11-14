package dev.lumora.launcher.core;

import dev.lumora.launcher.core.util.OsInfo;

import java.nio.file.Path;

/**
 * Directory layout below the install directory. Shared, content-addressed data (libraries, assets,
 * Java runtimes, vanilla versions) lives once; the game directories of the instances are described by
 * {@link InstanceLayout}.
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

    /** Local sign-in and server history (no tokens). */
    public Path connectionHistory() {
        return root.resolve("config").resolve("connections.json");
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

    /**
     * What only Lumora needs of an instance that plays in the standard folder (client jars, natives), and with
     * {@link InstanceLayout#SHARED} the Lumora settings shared by all versions. See {@link InstanceLayout}.
     */
    public Path versionData(String id) {
        return root.resolve("data").resolve(id);
    }

    public Path diagnostics() {
        return root.resolve("diagnostics");
    }

    /** Addon library: Lumora addon jars offered in the hub (Addons) and installed into an instance from there. */
    public Path addonLibrary() {
        return root.resolve("addons");
    }
}
