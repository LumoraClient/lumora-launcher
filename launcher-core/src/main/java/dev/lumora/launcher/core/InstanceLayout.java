package dev.lumora.launcher.core;

import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.profile.Flavor;
import dev.lumora.launcher.core.util.Log;
import dev.lumora.launcher.core.util.OsInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Where one instance (a Minecraft version with its start type, see {@link Flavor#instanceId}) keeps its files.
 * <ul>
 *   <li><b>Standard</b> (default): the game runs in the standard folder ({@code .minecraft}, shared with Vanilla
 *       Minecraft and all other versions: worlds, resource packs, screenshots, options.txt). What only Lumora needs
 *       of this version (client jars, natives) lives in {@code <data>/data/<id>}.</li>
 *   <li><b>Own instance</b>: everything in {@code <data>/instances/<id>}, as before 0.3.42.</li>
 * </ul>
 * The Lumora settings (HUD, modules, addons: {@code config/}, {@code addons/}) are either shared by all versions
 * ({@code <data>/data/shared}) or the instance's own (its version folder). {@code <data>} is the data directory,
 * {@code .minecraft/LumoraClient} for new installations.
 *
 * @param gameDir   Minecraft's {@code --gameDir}
 * @param versionDir the instance's own folder: client jars ({@code .lumora/client}), natives, status file
 * @param lumoraDir parent of the Lumora settings ({@code config/}) and addons ({@code addons/})
 */
public record InstanceLayout(String instanceId, boolean own, boolean sharedSettings, Path gameDir, Path versionDir, Path lumoraDir) {
    /** Name of the standard folder's Lumora settings, shared by all versions. */
    public static final String SHARED = "shared";

    public static InstanceLayout of(LauncherPaths paths, LauncherConfig config, Flavor flavor, String profileId) {
        return of(paths, config, flavor.instanceId(profileId));
    }

    public static InstanceLayout of(LauncherPaths paths, LauncherConfig config, String instanceId) {
        LauncherConfig.InstanceChoice c = choice(config, instanceId);
        Path standard = standardGameDir(config);
        Path versionDir = c.own ? paths.instance(instanceId) : paths.versionData(instanceId);
        Path gameDir = c.own ? versionDir : standard;
        Path lumoraDir = c.sharedSettings ? paths.versionData(SHARED) : versionDir;
        return new InstanceLayout(instanceId, c.own, c.sharedSettings, gameDir, versionDir, lumoraDir);
    }

    /** The instance's choice; without one: standard folder, shared settings. */
    public static LauncherConfig.InstanceChoice choice(LauncherConfig config, String instanceId) {
        LauncherConfig.InstanceChoice c = config.instances == null ? null : config.instances.get(instanceId);
        return c != null ? c : new LauncherConfig.InstanceChoice(false, true);
    }

    public Path configDir() {
        return lumoraDir.resolve("config");
    }

    public Path addonsDir() {
        return lumoraDir.resolve("addons");
    }

    /** Lumora jars the Lumora Loader starts. */
    public Path clientDir() {
        return versionDir.resolve(".lumora").resolve("client");
    }

    public Path nativesDir() {
        return versionDir.resolve(".natives");
    }

    /** The game folder is shared with other versions (and Vanilla Minecraft). */
    public boolean standard() {
        return !own;
    }

    /**
     * The standard game folder: the configured one, else Minecraft's own folder ({@code %APPDATA%\.minecraft},
     * {@code ~/Library/Application Support/minecraft}, {@code ~/.minecraft}). With {@code LUMORA_HOME} set (tests,
     * a second installation) it is {@code $LUMORA_HOME/minecraft}, so a test never touches the real {@code .minecraft}.
     */
    public static Path standardGameDir(LauncherConfig config) {
        if (config.standardGameDir != null && !config.standardGameDir.isBlank()) {
            return Path.of(config.standardGameDir);
        }
        return minecraftDir(OsInfo.current());
    }

    /** Minecraft's own folder (or {@code $LUMORA_HOME/minecraft}). */
    public static Path minecraftDir(OsInfo os) {
        String override = System.getenv("LUMORA_HOME");
        if (override != null && !override.isBlank()) {
            return Path.of(override, "minecraft");
        }
        String home = System.getProperty("user.home");
        return switch (os.name()) {
            case WINDOWS -> {
                String appData = System.getenv("APPDATA");
                yield Path.of(appData != null ? appData : home, ".minecraft");
            }
            case OSX -> Path.of(home, "Library", "Application Support", "minecraft");
            case LINUX -> Path.of(home, ".minecraft");
        };
    }

    /**
     * After switching an instance (standard folder / own, shared / own settings): its Lumora settings and addons come
     * along into the new Lumora folder if that has none yet ({@code config/}, {@code addons/} are copied, never
     * overwritten; the old ones stay). Worlds and options stay where they are. Returns the copied folder names.
     */
    public static java.util.List<String> carrySettings(InstanceLayout from, InstanceLayout to) throws IOException {
        java.util.List<String> copied = new java.util.ArrayList<>();
        if (from.lumoraDir().toAbsolutePath().normalize().equals(to.lumoraDir().toAbsolutePath().normalize())) {
            return copied;
        }
        for (String name : java.util.List.of("config", "addons")) {
            Path src = from.lumoraDir().resolve(name);
            Path dst = to.lumoraDir().resolve(name);
            if (!Files.isDirectory(src) || Files.exists(dst)) {
                continue;
            }
            copyTree(src, dst);
            copied.add(name);
        }
        return copied;
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        Path tmp = dst.resolveSibling(dst.getFileName() + ".copying");
        if (Files.exists(tmp)) {
            // left over from an interrupted copy
            try (Stream<Path> s = Files.walk(tmp)) {
                for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        Files.createDirectories(dst.getParent());
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (Files.isSymbolicLink(p)) {
                    continue;
                }
                Path target = tmp.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(p, target, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        Files.move(tmp, dst);
    }

    /**
     * Once (0.3.42): every instance folder that already exists becomes an own instance with its own settings, so
     * nothing changes for existing players; versions without a folder use the standard folder from now on. Returns
     * true if the configuration changed (the caller saves it).
     */
    public static boolean adoptExisting(LauncherPaths paths, LauncherConfig config) {
        if (config.instanceLayout >= 1) {
            return false;
        }
        Path dir = paths.root().resolve("instances");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                        .filter(id -> id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))
                        .forEach(id -> config.instances.putIfAbsent(id, new LauncherConfig.InstanceChoice(true, false)));
            } catch (IOException e) {
                Log.warn("Could not list the instance folders: " + e.getMessage());
                return false;
            }
        }
        config.instanceLayout = 1;
        Log.info("Instance layout: existing own instances " + config.instances.keySet());
        return true;
    }
}
