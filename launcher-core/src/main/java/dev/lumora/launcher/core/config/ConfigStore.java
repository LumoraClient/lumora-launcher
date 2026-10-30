package dev.lumora.launcher.core.config;

import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and saves {@link LauncherConfig}. A corrupt file is never silently overwritten:
 * it is moved aside to {@code launcher.json.corrupt-<timestamp>} and defaults are used.
 */
public final class ConfigStore {
    public record LoadResult(LauncherConfig config, boolean recoveredFromCorruption, Path backup, List<String> repairs) {
    }

    private final Path file;
    private final List<String> knownProfiles;
    private final List<String> knownExtensions;
    private final String defaultProfile;

    public ConfigStore(Path file, List<String> knownProfiles, List<String> knownExtensions, String defaultProfile) {
        this.file = file;
        this.knownProfiles = List.copyOf(knownProfiles);
        this.knownExtensions = List.copyOf(knownExtensions);
        this.defaultProfile = defaultProfile;
    }

    public Path file() {
        return file;
    }

    public LoadResult load() {
        LauncherConfig config;
        boolean recovered = false;
        Path backup = null;
        List<String> repairs = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            try {
                config = Json.read(file, LauncherConfig.class);
            } catch (IOException | RuntimeException e) {
                recovered = true;
                backup = file.resolveSibling(file.getFileName() + ".corrupt-"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                try {
                    FileUtil.moveReplacing(file, backup);
                } catch (IOException moveError) {
                    Log.warn("Could not back up corrupt config", moveError);
                    backup = null;
                }
                Log.warn("Configuration " + file + " was invalid (" + e.getMessage() + "); defaults restored"
                        + (backup != null ? ", backup: " + backup : ""));
                config = new LauncherConfig();
            }
        } else {
            config = new LauncherConfig();
        }
        repairs.addAll(config.validateAndRepair(knownProfiles, knownExtensions, defaultProfile));
        for (String r : repairs) {
            Log.warn("Config repaired: " + r);
        }
        if (recovered || !repairs.isEmpty() || !Files.exists(file)) {
            try {
                save(config);
            } catch (IOException e) {
                Log.warn("Could not write repaired configuration", e);
            }
        }
        return new LoadResult(config, recovered, backup, repairs);
    }

    public void save(LauncherConfig config) throws IOException {
        List<String> notes = config.validateAndRepair(knownProfiles, knownExtensions, defaultProfile);
        for (String n : notes) {
            Log.warn("Config repaired before save: " + n);
        }
        Json.write(file, config);
    }
}
