package dev.lumora.launcher.core.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lumora.launcher.core.content.ContentManifest;
import dev.lumora.launcher.core.content.InstalledContent;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.platform.ModPlatformProvider;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectFile;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.ProjectType;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports an instance:
 * <ul>
 *   <li><b>.mrpack</b> (Modrinth format): files that come from Modrinth's CDN are listed with their download address and
 *       hashes, every other file of the selected folders goes into {@code overrides/}. Worlds are not part of a modpack.</li>
 *   <li><b>Lumora archive</b> (.zip): {@code instance.json} and the whole game folder, for a 1:1 copy on another PC;
 *       "Importieren" reads it back as a new instance.</li>
 * </ul>
 * Logs, crash reports, locks and caches are never exported.
 */
public final class InstanceExporter {
    public static final String ARCHIVE_MARKER = "lumora-instance.json";
    private static final Pattern SKIP = Pattern.compile(
            "(.*/)?(logs|crash-reports|\\.natives)(/.*)?|.*\\.lumora-running[^/]*\\.lock|(.*/)?\\.lumora(/.*)?|.*\\.lumora-part|.*\\.disabled");

    /** Which folders go into a modpack's overrides besides the content folders. */
    public record MrpackOptions(boolean config, boolean options, boolean servers) {
    }

    public record Result(Path file, int downloads, int overrides) {
    }

    private InstanceExporter() {
    }

    /**
     * @param modrinth      used to find the download address of Modrinth files the launcher did not record (may be null)
     * @param loaderVersion the loader version the instance actually uses (null for Vanilla)
     */
    public static Result exportMrpack(Instance i, Path gameDir, String loaderVersion, String name, String version, MrpackOptions opt,
                                      ModPlatformProvider modrinth, Path target) throws IOException {
        if (i.kind() == InstanceKind.LUMORA) {
            throw new IOException(Lang.t("export.error.lumora"));
        }
        ContentManifest manifest = ContentManifest.load(gameDir);
        List<JsonObject> files = new ArrayList<>();
        Map<String, Path> overrides = new LinkedHashMap<>();
        List<InstalledContent.Item> items = new ArrayList<>();
        for (ProjectType t : List.of(ProjectType.MOD, ProjectType.RESOURCE_PACK, ProjectType.SHADER)) {
            for (InstalledContent.Item item : InstalledContent.scan(gameDir, t, manifest)) {
                if (item.enabled()) {
                    items.add(item);
                }
            }
        }
        // Modrinth files without a recorded address: one hash lookup for all
        Map<String, ProjectVersion> lookedUp = Map.of();
        List<String> missing = new ArrayList<>();
        Map<InstalledContent.Item, String> sha1s = new LinkedHashMap<>();
        for (InstalledContent.Item item : items) {
            String sha1 = item.origin() != null && item.origin().sha1 != null ? item.origin().sha1 : InstalledContent.sha1(item.file());
            sha1s.put(item, sha1);
            if (item.origin() == null || item.origin().url == null) {
                missing.add(sha1);
            }
        }
        if (modrinth != null && !missing.isEmpty()) {
            try {
                lookedUp = modrinth.versionsByHash(missing);
            } catch (IOException e) {
                Log.warn("Export: Modrinth lookup failed, files go into overrides: " + e.getMessage());
            }
        }
        for (InstalledContent.Item item : items) {
            String sha1 = sha1s.get(item);
            String url = item.origin() != null ? item.origin().url : null;
            String sha512 = item.origin() != null ? item.origin().sha512 : null;
            ProjectVersion v = lookedUp.get(sha1);
            if (url == null && v != null) {
                for (ProjectFile f : v.files()) {
                    if (sha1.equalsIgnoreCase(f.sha1())) {
                        url = f.url();
                        sha512 = f.sha512();
                    }
                }
            }
            boolean onModrinth = url != null && url.startsWith("https://cdn.modrinth.com/");
            String path = item.type().folder() + "/" + item.fileName();
            if (onModrinth) {
                if (sha512 == null) {
                    sha512 = Hashing.hash(item.file(), Hashing.Algorithm.SHA512);
                }
                JsonObject f = new JsonObject();
                f.addProperty("path", path);
                JsonObject hashes = new JsonObject();
                hashes.addProperty("sha1", sha1);
                hashes.addProperty("sha512", sha512);
                f.add("hashes", hashes);
                JsonObject env = new JsonObject();
                env.addProperty("client", "required");
                env.addProperty("server", "optional");
                f.add("env", env);
                JsonArray downloads = new JsonArray();
                downloads.add(url);
                f.add("downloads", downloads);
                f.addProperty("fileSize", Files.size(item.file()));
                files.add(f);
            } else {
                overrides.put(path, item.file());
            }
        }
        if (opt.config()) {
            for (String dir : List.of("config", "defaultconfigs", "kubejs")) {
                addTree(gameDir, dir, overrides);
            }
        }
        if (opt.options() && Files.isRegularFile(gameDir.resolve("options.txt"))) {
            overrides.put("options.txt", gameDir.resolve("options.txt"));
        }
        if (opt.servers() && Files.isRegularFile(gameDir.resolve("servers.dat"))) {
            overrides.put("servers.dat", gameDir.resolve("servers.dat"));
        }
        JsonObject index = new JsonObject();
        index.addProperty("formatVersion", 1);
        index.addProperty("game", "minecraft");
        index.addProperty("versionId", version == null || version.isBlank() ? "1.0.0" : version.strip());
        index.addProperty("name", name == null || name.isBlank() ? i.name : name.strip());
        JsonArray fileArray = new JsonArray();
        files.forEach(fileArray::add);
        index.add("files", fileArray);
        JsonObject deps = new JsonObject();
        deps.addProperty("minecraft", i.minecraftVersion);
        if (loaderVersion != null) {
            String key = switch (i.kind()) {
                case FABRIC -> "fabric-loader";
                case QUILT -> "quilt-loader";
                case FORGE -> "forge";
                case NEOFORGE -> "neoforge";
                default -> null;
            };
            String lv = i.kind() == InstanceKind.FORGE && loaderVersion.startsWith(i.minecraftVersion + "-")
                    ? loaderVersion.substring(i.minecraftVersion.length() + 1) : loaderVersion;
            if (key != null) {
                deps.addProperty(key, lv);
            }
        }
        index.add("dependencies", deps);
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry(MrpackImporter.INDEX));
            zip.write(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(index).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (i.iconFile() != null && Files.isRegularFile(i.iconFile()) && i.icon.endsWith(".png")) {
                zip.putNextEntry(new ZipEntry("overrides/icon.png"));
                Files.copy(i.iconFile(), zip);
                zip.closeEntry();
            }
            for (Map.Entry<String, Path> e : overrides.entrySet()) {
                zip.putNextEntry(new ZipEntry("overrides/" + e.getKey()));
                Files.copy(e.getValue(), zip);
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        FileUtil.moveReplacing(tmp, target);
        Log.info("Exported " + i.key() + " as .mrpack: " + files.size() + " downloads, " + overrides.size() + " overrides");
        return new Result(target, files.size(), overrides.size());
    }

    private static void addTree(Path gameDir, String dir, Map<String, Path> into) throws IOException {
        Path root = gameDir.resolve(dir);
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path p : walk.toList()) {
                if (Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    String rel = gameDir.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                    if (!SKIP.matcher(rel).matches()) {
                        into.put(rel, p);
                    }
                }
            }
        }
    }

    /** Lumora archive: the instance description plus the whole game folder (worlds included). */
    public static Result exportArchive(Instance i, Path gameDir, Path target) throws IOException {
        Path game = gameDir.toAbsolutePath().normalize();
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        int n = 0;
        try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(os)) {
            Instance meta = i.copy();
            meta.id = null;
            meta.javaPath = null; // a path of this PC
            zip.putNextEntry(new ZipEntry(ARCHIVE_MARKER));
            zip.write(Json.GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (i.iconFile() != null && Files.isRegularFile(i.iconFile())) {
                zip.putNextEntry(new ZipEntry(i.icon));
                Files.copy(i.iconFile(), zip);
                zip.closeEntry();
            }
            try (var walk = Files.walk(game)) {
                for (Path p : walk.toList()) {
                    if (!Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                        continue;
                    }
                    String rel = game.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                    if (rel.startsWith(".lumora/") ? !rel.equals(".lumora/content.json") : SKIP.matcher(rel).matches() && !rel.endsWith(".disabled")) {
                        continue;
                    }
                    zip.putNextEntry(new ZipEntry("minecraft/" + rel));
                    Files.copy(p, zip);
                    zip.closeEntry();
                    n++;
                }
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        FileUtil.moveReplacing(tmp, target);
        return new Result(target, 0, n);
    }

    /** Copies chosen files into a folder (export of selected mods). Existing files there are not overwritten. */
    public static List<Path> exportFiles(List<Path> files, Path folder) throws IOException {
        Files.createDirectories(folder);
        List<Path> out = new ArrayList<>();
        for (Path f : files) {
            Path dest = FileUtil.resolveInside(folder, f.getFileName().toString());
            if (!Files.exists(dest)) {
                Files.copy(f, dest);
                out.add(dest);
            }
        }
        return out;
    }
}
