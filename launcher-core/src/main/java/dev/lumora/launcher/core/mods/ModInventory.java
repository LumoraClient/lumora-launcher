package dev.lumora.launcher.core.mods;

import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Everything that belongs to the mods folder of one profile, for the launcher's mod list:
 * <ul>
 *   <li>CORE: the profile's required mods (always loaded),</li>
 *   <li>EXTENSION: the profile's optional mods (switched by key, for all versions),</li>
 *   <li>USER: jars the player put into {@code mods/} themselves. They can be switched off without deleting
 *       them: {@code name.jar} becomes {@code name.jar.disabled}, which Fabric Loader ignores.</li>
 * </ul>
 */
public final class ModInventory {
    public static final String DISABLED_SUFFIX = ".disabled";

    /** DEPENDENCY: helper mod of a resource pack (e.g. EMF for Fresh Animations), RESOURCE_PACK: built-in pack. */
    public enum Kind { CORE, EXTENSION, DEPENDENCY, RESOURCE_PACK, USER }

    /**
     * @param artifact  lockfile entry (null for user mods)
     * @param info      metadata read from the jar, null if the jar is not available yet (downloaded on next start)
     * @param file      the jar in {@code mods/} (or the disabled file), null if not installed yet
     * @param enabled   loaded on the next start
     * @param installed present in {@code mods/} right now
     */
    public record Entry(Kind kind, ProfileCatalog.Artifact artifact, ModInfo info, Path file, boolean enabled, boolean installed) {
        public String displayName() {
            if (info != null && info.name() != null) {
                return info.name();
            }
            if (artifact != null) {
                int cut = artifact.name.indexOf(" (");
                return cut > 0 ? artifact.name.substring(0, cut) : artifact.name;
            }
            return file != null ? file.getFileName().toString() : "?";
        }

        public String version() {
            if (info != null && info.version() != null) {
                return info.version();
            }
            return artifact != null ? artifact.version : "";
        }
    }

    private ModInventory() {
    }

    /**
     * @param locate where a not-yet-installed artifact's jar may already exist (download cache, bundled folder);
     *               used only to read its metadata. May return null.
     */
    public static List<Entry> scan(ProfileCatalog catalog, ProfileCatalog.Profile profile, List<String> enabledExtensions,
                                   Path modsDir, Function<ProfileCatalog.Artifact, Path> locate) {
        return scan(catalog, profile, enabledExtensions, List.of(), modsDir, locate);
    }

    /** Like above, plus the built-in resource packs (on unless in {@code disabledPacks}) and their helper mods. */
    public static List<Entry> scan(ProfileCatalog catalog, ProfileCatalog.Profile profile, List<String> enabledExtensions,
                                   List<String> disabledPacks, Path modsDir, Function<ProfileCatalog.Artifact, Path> locate) {
        List<Entry> out = new ArrayList<>();
        Set<String> loaded = new HashSet<>();
        catalog.resolveMods(profile, enabledExtensions, disabledPacks).forEach(a -> loaded.add(a.key()));
        Path packDir = modsDir.resolveSibling("resourcepacks");
        Set<String> known = new HashSet<>();
        for (String id : profile.mods) {
            ProfileCatalog.Artifact a = catalog.artifact(id);
            known.add(a.fileName);
            out.add(artifactEntry(Kind.CORE, a, true, modsDir, locate));
        }
        for (String id : profile.extensions) {
            ProfileCatalog.Artifact a = catalog.artifact(id);
            known.add(a.fileName);
            out.add(a.dependencyOnly ? artifactEntry(Kind.DEPENDENCY, a, loaded.contains(a.key()), modsDir, locate)
                    : artifactEntry(Kind.EXTENSION, a, enabledExtensions.contains(a.key()), modsDir, locate));
        }
        for (String id : profile.resourcePacks) {
            ProfileCatalog.Artifact a = catalog.artifact(id);
            Path file = packDir.resolve(a.fileName);
            boolean present = Files.isRegularFile(file);
            Path source = present ? file : locate != null ? locate.apply(a) : null;
            out.add(new Entry(Kind.RESOURCE_PACK, a, source != null && Files.isRegularFile(source) ? PackInfo.read(source, a) : null,
                    present ? file : null, !disabledPacks.contains(a.key()), present));
        }
        // files of other versions' builds that the installer still has to clean up are not user mods
        catalog.artifacts().forEach(a -> known.add(a.fileName));
        known.addAll(managedFiles(modsDir));
        List<Entry> user = new ArrayList<>();
        if (Files.isDirectory(modsDir)) {
            try (Stream<Path> files = Files.list(modsDir)) {
                for (Path p : files.toList()) {
                    String name = p.getFileName().toString();
                    boolean jar = name.endsWith(".jar");
                    boolean disabled = name.endsWith(".jar" + DISABLED_SUFFIX);
                    if (!Files.isRegularFile(p) || (!jar && !disabled) || (jar && known.contains(name))) {
                        continue;
                    }
                    user.add(new Entry(Kind.USER, null, readQuietly(p), p, jar, true));
                }
            } catch (IOException e) {
                Log.warn("Could not list " + modsDir + ": " + e.getMessage());
            }
        }
        user.sort(Comparator.comparing(e -> e.displayName().toLowerCase(java.util.Locale.ROOT)));
        out.addAll(user);
        return out;
    }

    private static Entry artifactEntry(Kind kind, ProfileCatalog.Artifact a, boolean enabled, Path modsDir,
                                       Function<ProfileCatalog.Artifact, Path> locate) {
        Path installed = modsDir.resolve(a.fileName);
        boolean present = Files.isRegularFile(installed);
        Path source = present ? installed : locate != null ? locate.apply(a) : null;
        ModInfo info = source != null && Files.isRegularFile(source) ? readQuietly(source) : null;
        return new Entry(kind, a, info, present ? installed : null, enabled, present);
    }

    private static ModInfo readQuietly(Path jar) {
        try {
            return ModInfo.read(jar);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static List<String> managedFiles(Path modsDir) {
        Path managed = modsDir.resolve(ModInstaller.MANAGED_LIST);
        if (!Files.isRegularFile(managed)) {
            return List.of();
        }
        try {
            ModInstaller.Managed m = dev.lumora.launcher.core.util.Json.read(managed, ModInstaller.Managed.class);
            return m == null || m.files == null ? List.of() : m.files;
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** Switches a user mod on or off by renaming it; returns the new path. */
    public static Path setUserModEnabled(Path file, boolean enabled) throws IOException {
        String name = file.getFileName().toString();
        boolean isDisabled = name.endsWith(DISABLED_SUFFIX);
        if (enabled == !isDisabled) {
            return file;
        }
        String target = enabled ? name.substring(0, name.length() - DISABLED_SUFFIX.length()) : name + DISABLED_SUFFIX;
        Path dest = FileUtil.resolveInside(file.getParent(), target);
        if (Files.exists(dest)) {
            throw new IOException(target + " existiert bereits");
        }
        return Files.move(file, dest);
    }
}
