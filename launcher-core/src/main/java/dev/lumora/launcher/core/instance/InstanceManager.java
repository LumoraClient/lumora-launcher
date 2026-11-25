package dev.lumora.launcher.core.instance;

import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.InstanceLayout;
import dev.lumora.launcher.core.LauncherPaths;
import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.profile.Flavor;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * All instances of the launcher: the built-in Lumora instances (one per Lumora version, plus Vanilla where the player
 * used it) and the player's own instances below {@code custom-instances/}. Own instances are independent folders: an
 * action on one never touches another. Every write of {@code instance.json} is atomic.
 */
public final class InstanceManager {
    public static final String FILE = "instance.json";
    public static final int MAX_NAME = 64;
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,47}");
    private static final Pattern MC_VERSION = Pattern.compile("[0-9A-Za-z._-]{1,32}");
    private static final Pattern ICON = Pattern.compile("icon\\.(png|jpg|jpeg)");
    /** Left behind when an instance is copied: locks, logs and crash reports belong to the original's runs. */
    private static final Pattern COPY_SKIP = Pattern.compile(
            "(minecraft/)?(logs|crash-reports)(/.*)?|.*\\.lumora-running[^/]*\\.lock|(minecraft/)?\\.lumora/status[^/]*\\.json|\\.natives(/.*)?");

    private final LauncherPaths paths;
    private final ProfileCatalog catalog;

    public InstanceManager(LauncherPaths paths, ProfileCatalog catalog) {
        this.paths = paths;
        this.catalog = catalog;
    }

    public LauncherPaths paths() {
        return paths;
    }

    // ---------------------------------------------------------------- listing

    /** Built-in instances first (in the order of the Lumora versions), then own instances by name. */
    public List<Instance> list(LauncherConfig config) {
        List<Instance> out = new ArrayList<>(builtIns(config));
        out.addAll(own());
        return out;
    }

    /** The Lumora instance of every shown Lumora version, and its Vanilla instance where the player used one. */
    public List<Instance> builtIns(LauncherConfig config) {
        List<Instance> out = new ArrayList<>();
        for (ProfileCatalog.Profile p : catalog.profiles()) {
            boolean visible = config.visibleProfiles == null || config.visibleProfiles.isEmpty()
                    || config.visibleProfiles.contains(p.id) || p.id.equals(config.selectedProfile);
            if (visible) {
                out.add(builtIn(p, Flavor.LUMORA));
            }
            String vanillaId = Flavor.VANILLA.instanceId(p.id);
            boolean usedVanilla = (config.instances != null && config.instances.containsKey(vanillaId))
                    || Files.isDirectory(paths.instance(vanillaId))
                    || vanillaId.equals(config.selectedInstance)
                    || (p.id.equals(config.selectedProfile) && Flavor.VANILLA.id().equals(config.selectedFlavor) && config.selectedInstance == null);
            if (usedVanilla) {
                out.add(builtIn(p, Flavor.VANILLA));
            }
        }
        return out;
    }

    Instance builtIn(ProfileCatalog.Profile p, Flavor flavor) {
        Instance i = new Instance();
        i.builtIn = true;
        i.profileId = p.id;
        i.id = flavor.instanceId(p.id);
        i.kind = flavor == Flavor.VANILLA ? InstanceKind.VANILLA.id() : InstanceKind.LUMORA.id();
        i.minecraftVersion = p.minecraftVersion;
        i.name = (flavor == Flavor.VANILLA ? "Vanilla " : "Lumora ") + p.minecraftVersion;
        i.source = "lumora";
        return i;
    }

    /** Own instances, sorted by name. Unreadable folders are skipped with a log entry, never deleted. */
    public List<Instance> own() {
        List<Instance> out = new ArrayList<>();
        Path root = paths.customInstances();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (var dirs = Files.list(root)) {
            for (Path d : dirs.sorted().toList()) {
                if (!Files.isRegularFile(d.resolve(FILE)) || !ID.matcher(d.getFileName().toString()).matches()) {
                    continue;
                }
                try {
                    out.add(read(d));
                } catch (IOException e) {
                    Log.warn("Instance " + d.getFileName() + " unreadable: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.warn("Could not list instances: " + e.getMessage());
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    private Instance read(Path dir) throws IOException {
        Instance i;
        try {
            i = Json.GSON.fromJson(Files.readString(dir.resolve(FILE), StandardCharsets.UTF_8), Instance.class);
        } catch (JsonParseException e) {
            throw new IOException(e.getMessage(), e);
        }
        if (i == null) {
            throw new IOException("empty");
        }
        i.id = dir.getFileName().toString();
        i.dir = dir;
        i.builtIn = false;
        if (i.name == null || i.name.isBlank()) {
            i.name = i.id;
        }
        if (i.jvmArgs == null) {
            i.jvmArgs = new ArrayList<>();
        }
        if (InstanceKind.of(i.kind) == null || i.minecraftVersion == null || !MC_VERSION.matcher(i.minecraftVersion).matches()) {
            throw new IOException("invalid kind or Minecraft version");
        }
        if (i.icon != null && !ICON.matcher(i.icon).matches()) {
            i.icon = null;
        }
        return i;
    }

    public Optional<Instance> find(LauncherConfig config, String key) {
        if (key == null) {
            return Optional.empty();
        }
        if (key.startsWith(Instance.CUSTOM_PREFIX)) {
            String id = key.substring(Instance.CUSTOM_PREFIX.length());
            if (!ID.matcher(id).matches()) {
                return Optional.empty();
            }
            Path dir = paths.customInstances().resolve(id);
            if (!Files.isRegularFile(dir.resolve(FILE))) {
                return Optional.empty();
            }
            try {
                return Optional.of(read(dir));
            } catch (IOException e) {
                Log.warn("Instance " + id + " unreadable: " + e.getMessage());
                return Optional.empty();
            }
        }
        for (ProfileCatalog.Profile p : catalog.profiles()) {
            for (Flavor f : Flavor.values()) {
                if (f.instanceId(p.id).equals(key)) {
                    return Optional.of(builtIn(p, f));
                }
            }
        }
        return Optional.empty();
    }

    /** The selected instance: {@link LauncherConfig#selectedInstance}, else the built-in one of the selected version. */
    public Instance selected(LauncherConfig config) {
        Optional<Instance> chosen = find(config, config.selectedInstance);
        if (chosen.isPresent()) {
            return chosen.get();
        }
        ProfileCatalog.Profile p = catalog.profile(config.selectedProfile).orElseGet(() -> catalog.profile(catalog.defaultProfile()).orElseThrow());
        return builtIn(p, Flavor.of(config.selectedFlavor));
    }

    /**
     * Makes {@code instance} the selected one. For a built-in instance the version and start type are set as well, so
     * everything that still works per version (library, performance, version menu) follows.
     */
    public static void select(LauncherConfig config, Instance instance) {
        config.selectedInstance = instance.key();
        if (instance.builtIn) {
            config.selectedProfile = instance.profileId;
            config.selectedFlavor = instance.kind() == InstanceKind.VANILLA ? Flavor.VANILLA.id() : Flavor.LUMORA.id();
        }
    }

    /** Folders of an instance: the built-in layout, or the own instance's folder with its own Lumora settings. */
    public InstanceLayout layout(LauncherConfig config, Instance i) {
        if (i.builtIn) {
            return InstanceLayout.of(paths, config, i.id);
        }
        return new InstanceLayout(i.key(), true, false, i.gameDir(), i.dir, i.dir.resolve("lumora"));
    }

    // ---------------------------------------------------------------- changes

    /** Checks the fields a player can set; throws with a readable reason. */
    public void validate(Instance i) throws IOException {
        if (i.name == null || i.name.isBlank()) {
            throw new IOException(Lang.t("instances.error.name_empty"));
        }
        if (i.name.strip().length() > MAX_NAME || i.name.chars().anyMatch(Character::isISOControl)) {
            throw new IOException(Lang.t("instances.error.name_invalid", MAX_NAME));
        }
        if (InstanceKind.of(i.kind) == null) {
            throw new IOException(Lang.t("instances.error.kind"));
        }
        if (i.minecraftVersion == null || !MC_VERSION.matcher(i.minecraftVersion).matches()) {
            throw new IOException(Lang.t("instances.error.version"));
        }
        if (i.kind() == InstanceKind.LUMORA && catalog.profiles().stream().noneMatch(p -> p.minecraftVersion.equals(i.minecraftVersion))) {
            throw new IOException(Lang.t("instances.error.lumora_version", i.minecraftVersion));
        }
        if (i.loaderVersion != null && !i.loaderVersion.matches("[A-Za-z0-9._+-]{1,80}")) {
            throw new IOException(Lang.t("instances.error.loader_version"));
        }
        if (i.maxMemoryMb != null && (i.maxMemoryMb < LauncherConfig.MIN_MEMORY_MB || i.maxMemoryMb > LauncherConfig.MAX_MEMORY_MB)) {
            throw new IOException(Lang.t("instances.error.memory", LauncherConfig.MIN_MEMORY_MB, LauncherConfig.MAX_MEMORY_MB));
        }
        if (i.minMemoryMb != null && (i.minMemoryMb < 256 || (i.maxMemoryMb != null && i.minMemoryMb > i.maxMemoryMb))) {
            throw new IOException(Lang.t("instances.error.memory", 256, i.maxMemoryMb == null ? LauncherConfig.MAX_MEMORY_MB : i.maxMemoryMb));
        }
        if (i.javaPath != null && !i.javaPath.isBlank() && !Files.isRegularFile(Path.of(i.javaPath))) {
            throw new IOException(Lang.t("instances.error.java", i.javaPath));
        }
        if (i.icon != null && !ICON.matcher(i.icon).matches()) {
            throw new IOException(Lang.t("instances.error.icon"));
        }
    }

    /** Creates a new own instance from {@code spec} (name, kind, version, optional settings); returns it with its folder. */
    public synchronized Instance create(Instance spec) throws IOException {
        Instance i = spec.copy();
        i.builtIn = false;
        i.profileId = null;
        i.name = spec.name == null ? null : spec.name.strip();
        validate(i);
        i.id = freeId(i.name);
        i.dir = paths.customInstances().resolve(i.id);
        i.created = Instant.now().toString();
        i.format = Instance.FORMAT;
        Files.createDirectories(i.gameDir());
        save(i);
        Log.info("Instance created: " + i);
        return i;
    }

    /** Writes {@code instance.json} of an own instance (atomic). */
    public void save(Instance i) throws IOException {
        if (i.builtIn || i.dir == null) {
            throw new IOException(Lang.t("instances.error.builtin"));
        }
        validate(i);
        FileUtil.writeAtomically(i.dir.resolve(FILE), Json.GSON.toJson(i).getBytes(StandardCharsets.UTF_8));
    }

    public Instance rename(Instance i, String name) throws IOException {
        Instance c = i.copy();
        c.name = name == null ? null : name.strip();
        save(c);
        return c;
    }

    /**
     * Copies an instance into a new, independent own instance named {@code name}: mods, settings, resource packs,
     * worlds and the Lumora settings. A built-in instance in the shared standard folder only takes its own parts
     * (options, server list, mod configs, Lumora settings), not the whole shared folder.
     */
    public synchronized Instance duplicate(LauncherConfig config, Instance source, String name) throws IOException {
        Instance spec = source.copy();
        spec.name = name;
        spec.builtIn = false;
        spec.profileId = null;
        spec.source = source.builtIn ? null : source.source;
        spec.icon = null;
        validate(spec);
        String id = freeId(spec.name.strip());
        Path target = paths.customInstances().resolve(id);
        Path work = paths.customInstances().resolve("." + id + ".copying");
        FileUtil.deleteTree(work);
        try {
            if (!source.builtIn) {
                FileUtil.copyTree(source.dir, work, rel -> COPY_SKIP.matcher(rel).matches() || rel.equals(FILE));
                if (source.icon != null && Files.isRegularFile(work.resolve(source.icon))) {
                    spec.icon = source.icon;
                }
            } else {
                InstanceLayout l = layout(config, source);
                Path game = work.resolve("minecraft");
                if (l.own()) {
                    FileUtil.copyTree(l.gameDir(), game, rel -> COPY_SKIP.matcher(rel).matches() || rel.startsWith(".lumora/client")
                            || rel.startsWith(".lumora/packs") || rel.startsWith(".lumora/addon-assets"));
                } else {
                    Files.createDirectories(game);
                    for (String f : List.of("options.txt", "servers.dat")) {
                        if (Files.isRegularFile(l.gameDir().resolve(f))) {
                            Files.copy(l.gameDir().resolve(f), game.resolve(f));
                        }
                    }
                }
                for (String sub : List.of("config", "addons")) {
                    Path from = l.lumoraDir().resolve(sub);
                    if (Files.isDirectory(from)) {
                        FileUtil.copyTree(from, work.resolve("lumora").resolve(sub), null);
                    }
                }
            }
            Files.createDirectories(work.resolve("minecraft"));
            spec.id = id;
            spec.dir = target;
            spec.created = Instant.now().toString();
            FileUtil.writeAtomically(work.resolve(FILE), Json.GSON.toJson(spec).getBytes(StandardCharsets.UTF_8));
            Files.move(work, target);
        } catch (IOException | RuntimeException e) {
            FileUtil.deleteTree(work);
            throw e;
        }
        Log.info("Instance copied: " + source + " -> " + id);
        return read(target);
    }

    /**
     * Deletes an own instance folder for good (worlds included). The caller asks first and makes sure the instance is
     * not running; built-in instances cannot be deleted.
     */
    public synchronized void delete(Instance i) throws IOException {
        if (i.builtIn || i.dir == null) {
            throw new IOException(Lang.t("instances.error.builtin"));
        }
        Path dir = FileUtil.resolveInside(paths.customInstances(), i.id);
        if (Files.exists(dir.resolve("minecraft").resolve(".lumora-running.lock"))) {
            try (var ch = java.nio.channels.FileChannel.open(dir.resolve("minecraft").resolve(".lumora-running.lock"),
                    java.nio.file.StandardOpenOption.WRITE); var lock = ch.tryLock()) {
                if (lock == null) {
                    throw new IOException(Lang.t("instances.error.running"));
                }
            } catch (java.nio.channels.OverlappingFileLockException e) {
                throw new IOException(Lang.t("instances.error.running"));
            }
        }
        // move aside first so a half-deleted folder never looks like an instance
        Path trash = paths.customInstances().resolve("." + i.id + ".deleting-" + System.currentTimeMillis());
        Files.move(dir, trash);
        FileUtil.deleteTree(trash);
        Log.info("Instance deleted: " + i.id);
    }

    /** Sets a PNG/JPEG file as the instance icon (copied into the folder, at most 2 MB). */
    public Instance setIcon(Instance i, Path image) throws IOException {
        if (i.builtIn) {
            throw new IOException(Lang.t("instances.error.builtin"));
        }
        String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = name.endsWith(".png") ? "png" : name.endsWith(".jpg") ? "jpg" : name.endsWith(".jpeg") ? "jpeg" : null;
        if (ext == null || Files.size(image) > 2 * 1024 * 1024) {
            throw new IOException(Lang.t("instances.error.icon"));
        }
        Instance c = i.copy();
        if (c.icon != null) {
            Files.deleteIfExists(c.dir.resolve(c.icon));
        }
        c.icon = "icon." + ext;
        FileUtil.writeAtomically(c.dir.resolve(c.icon), Files.readAllBytes(image));
        save(c);
        return c;
    }

    /** Folder name for a new instance: the name in lower case letters, digits and dashes, unique. */
    String freeId(String name) {
        String base = java.text.Normalizer.normalize(name.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue"), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace("ß", "ss").replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (base.length() > 40) {
            base = base.substring(0, 40).replaceAll("-+$", "");
        }
        if (base.isEmpty()) {
            base = "instance";
        }
        String id = base;
        for (int n = 2; Files.exists(paths.customInstances().resolve(id)) || Files.exists(paths.customInstances().resolve("." + id + ".copying")); n++) {
            id = base + "-" + n;
        }
        return id;
    }
}
