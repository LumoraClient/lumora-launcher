package dev.lumora.launcher.core.content;

import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The content of one game folder: mods, resource packs, shader packs and data packs, each with what the file says about
 * itself and where it came from ({@link ContentManifest}). Switching off renames {@code x.jar} to {@code x.jar.disabled},
 * which no loader reads; removing deletes the file. Only regular files directly in the folder are considered.
 */
public final class InstalledContent {
    public static final String DISABLED = ".disabled";

    /**
     * One file.
     *
     * @param key      manifest key ({@code mods/x.jar})
     * @param mod      metadata of a mod jar (null for other types or jars without metadata)
     * @param pack     pack.mcmeta data of a resource/data pack zip (null otherwise)
     * @param origin   manifest entry, null if the launcher did not install it
     */
    public record Item(ProjectType type, String key, Path file, String fileName, boolean enabled, long size,
                       ModMetadata mod, PackMeta pack, ContentManifest.Entry origin) {
        /** Name to show: platform title, mod name, pack name, else the file name. */
        public String displayName() {
            if (origin != null && origin.title != null) {
                return origin.title;
            }
            if (mod != null && mod.name() != null && !mod.name().isBlank()) {
                return mod.name();
            }
            String n = fileName.endsWith(DISABLED) ? fileName.substring(0, fileName.length() - DISABLED.length()) : fileName;
            int dot = n.lastIndexOf('.');
            return dot > 0 ? n.substring(0, dot) : n;
        }

        public String version() {
            if (origin != null && origin.versionNumber != null) {
                return origin.versionNumber;
            }
            return mod != null ? mod.version() : null;
        }
    }

    private InstalledContent() {
    }

    /** Folders the instance page shows, in order. */
    public static final List<ProjectType> TYPES = List.of(ProjectType.MOD, ProjectType.RESOURCE_PACK, ProjectType.SHADER, ProjectType.DATAPACK);

    /** All files of {@code type} in {@code gameDir}, by name; metadata is read from each jar or zip. */
    public static List<Item> scan(Path gameDir, ProjectType type, ContentManifest manifest) {
        List<Item> out = new ArrayList<>();
        Path dir = gameDir.resolve(type.folder());
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                if (!Files.isRegularFile(f, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                String name = f.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                boolean enabled = !lower.endsWith(DISABLED);
                String base = enabled ? lower : lower.substring(0, lower.length() - DISABLED.length());
                if (!accepts(type, base)) {
                    continue;
                }
                ModMetadata mod = null;
                PackMeta pack = null;
                try {
                    if (type == ProjectType.MOD) {
                        mod = ModMetadata.read(f);
                    } else if (base.endsWith(".zip") && type != ProjectType.SHADER) {
                        pack = PackMeta.read(f);
                    }
                } catch (IOException | RuntimeException e) {
                    Log.warn("Could not read " + name + ": " + e.getMessage());
                }
                String key = ContentManifest.key(type.folder(), name);
                out.add(new Item(type, key, f, name, enabled, Files.size(f), mod, pack, manifest == null ? null : manifest.get(key).orElse(null)));
            }
        } catch (IOException e) {
            Log.warn("Could not list " + dir + ": " + e.getMessage());
        }
        out.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return out;
    }

    static boolean accepts(ProjectType type, String lowerName) {
        return switch (type) {
            case MOD -> lowerName.endsWith(".jar");
            case RESOURCE_PACK, SHADER, DATAPACK -> lowerName.endsWith(".zip");
            default -> false;
        };
    }

    /** Switches a file on or off by renaming it; returns the new path. */
    public static Path setEnabled(Item item, boolean enabled) throws IOException {
        if (item.enabled() == enabled) {
            return item.file();
        }
        String name = item.fileName();
        String target = enabled ? name.substring(0, name.length() - DISABLED.length()) : name + DISABLED;
        Path dest = item.file().resolveSibling(target);
        if (Files.exists(dest)) {
            throw new IOException(Lang.t("content.error.exists", target));
        }
        Files.move(item.file(), dest);
        return dest;
    }

    /** Deletes the file and its manifest entry. */
    public static void remove(Path gameDir, Item item, ContentManifest manifest) throws IOException {
        Path file = FileUtil.resolveInside(gameDir, item.type().folder() + "/" + item.fileName());
        Files.deleteIfExists(file);
        if (manifest != null) {
            manifest.remove(item.key());
            manifest.save();
        }
    }

    /** SHA-1 of a file (platform lookups identify files by it). */
    public static String sha1(Path file) throws IOException {
        return Hashing.hash(file, Hashing.Algorithm.SHA1);
    }
}
