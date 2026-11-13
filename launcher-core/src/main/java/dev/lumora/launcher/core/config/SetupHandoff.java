package dev.lumora.launcher.core.config;

import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Choices from the Windows installer (LumoraClient-Setup.exe): it writes {@code config/setup.json} next to
 * {@code launcher.json}, the launcher (or its CLI, which the installer uses to download the versions) takes them over
 * on its next start and deletes the file. The installer never edits launcher.json itself.
 */
public final class SetupHandoff {
    public static final String FILE = "setup.json";

    /** File written by the installer (Gson POJO). */
    static final class Data {
        /** Game data directory, null = platform default. */
        String dataDir;
        /** Minecraft versions shown in the launcher, newest first. */
        List<String> profiles;
        /** Version selected on the first start. */
        String defaultProfile;
        /** "lumora" or "vanilla". */
        String theme;
        /** Id of this setup run (marks the instances the design was applied to). */
        String seed;
    }

    private SetupHandoff() {
    }

    public static Path file(Path configFile) {
        return configFile.resolveSibling(FILE);
    }

    /**
     * Applies a pending installer choice to {@code config}. Returns true if something was taken over (the caller then
     * saves the configuration).
     */
    public static boolean apply(Path configFile, LauncherConfig config, List<String> knownProfiles) {
        Path file = file(configFile);
        if (!Files.isRegularFile(file)) {
            return false;
        }
        Data data;
        try {
            data = Json.read(file, Data.class);
        } catch (IOException | RuntimeException e) {
            Log.warn("Ignoring invalid installer file " + file + ": " + e.getMessage());
            delete(file);
            return false;
        }
        delete(file);
        if (data == null) {
            return false;
        }
        apply(data, config, knownProfiles);
        Log.info("Installer choices taken over: data=" + config.installDir + ", versions=" + config.visibleProfiles
                + ", theme=" + config.gameTheme);
        return true;
    }

    static void apply(Data data, LauncherConfig config, List<String> knownProfiles) {
        if (data.dataDir != null) {
            String dir = data.dataDir.strip();
            config.installDir = dir.isEmpty() || dir.equals("default") ? null : dir;
        }
        if (data.profiles != null) {
            List<String> visible = new ArrayList<>(new LinkedHashSet<>(data.profiles));
            visible.removeIf(id -> !knownProfiles.contains(id));
            // all versions selected = no filter (new versions from later updates appear too)
            config.visibleProfiles = visible.size() >= knownProfiles.size() ? new ArrayList<>() : visible;
            if (data.defaultProfile != null && (visible.isEmpty() || visible.contains(data.defaultProfile))
                    && knownProfiles.contains(data.defaultProfile)) {
                config.selectedProfile = data.defaultProfile;
            } else if (!visible.isEmpty() && !visible.contains(config.selectedProfile)) {
                config.selectedProfile = visible.get(0);
            }
        }
        if ("lumora".equals(data.theme) || "vanilla".equals(data.theme)) {
            config.gameTheme = data.theme;
            config.gameThemeSeed = data.seed != null && data.seed.matches("[A-Za-z0-9_.-]{1,64}")
                    ? data.seed : Long.toString(System.currentTimeMillis());
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Log.warn("Could not delete " + file, e);
        }
    }
}
