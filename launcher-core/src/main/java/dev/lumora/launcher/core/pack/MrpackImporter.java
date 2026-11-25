package dev.lumora.launcher.core.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.content.ContentManifest;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.i18n.Lang;
import dev.lumora.launcher.core.instance.Instance;
import dev.lumora.launcher.core.instance.InstanceKind;
import dev.lumora.launcher.core.instance.InstanceManager;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Modrinth modpacks ({@code .mrpack}, format of support.modrinth.com "Modrinth Modpack Format"). Two steps:
 * <ol>
 *   <li>{@link #analyze}: reads {@code modrinth.index.json} and the archive listing only; returns a preview (name,
 *       Minecraft version, loader, files by folder, overrides, download size) and every reason it cannot be imported.</li>
 *   <li>{@link #importPack}: creates a new, independent instance, downloads every file from an allowed host with its
 *       SHA-512, unpacks {@code overrides/} and then {@code client-overrides/} (safe paths only, see {@link SafeZip}), and
 *       deletes the half-made instance again on any failure or cancel.</li>
 * </ol>
 * Files the pack marks as unsupported on the client are skipped; optional client files are installed.
 */
public final class MrpackImporter {
    public static final String INDEX = "modrinth.index.json";
    /** Hosts the format allows for downloads; each must also be on the launcher's download allow-list. */
    public static final Set<String> ALLOWED_HOSTS = Set.of("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com");

    /**
     * One file the pack downloads, checked with SHA-512 where the pack has one, else SHA-1 (CurseForge).
     *
     * @param platform  platform of the file when known ("modrinth", "curseforge"), else null
     */
    public record PackFile(String path, String url, String sha1, String sha512, long size, boolean optional,
                           String platform, String projectId, String versionId) {
        public PackFile(String path, String url, String sha1, String sha512, long size, boolean optional) {
            this(path, url, sha1, sha512, size, optional, null, null, null);
        }

        public Hashing.Expected expected() {
            return sha512 != null ? Hashing.Expected.sha512(sha512) : Hashing.Expected.sha1(sha1);
        }
    }

    /**
     * @param problems  readable reasons the pack cannot be imported (empty = importable)
     * @param folders   number of downloaded files per top folder ("mods" → 120, "resourcepacks" → 2, ...)
     * @param overrides folder in the archive that is copied into the game folder ("overrides")
     * @param manual    files the pack needs that may only be downloaded by hand (name and page), left out on import
     * @param format    "mrpack" or "curseforge"
     */
    public record Preview(Path file, String name, String version, String summary, String minecraftVersion, InstanceKind kind,
                          String loaderVersion, List<PackFile> files, Map<String, Integer> folders, int overrideFiles,
                          long downloadBytes, long overrideBytes, byte[] icon, List<String> problems, int skippedServerOnly,
                          String overrides, List<String> manual, String format) {
        public Preview(Path file, String name, String version, String summary, String minecraftVersion, InstanceKind kind,
                       String loaderVersion, List<PackFile> files, Map<String, Integer> folders, int overrideFiles,
                       long downloadBytes, long overrideBytes, byte[] icon, List<String> problems, int skippedServerOnly) {
            this(file, name, version, summary, minecraftVersion, kind, loaderVersion, files, folders, overrideFiles, downloadBytes,
                    overrideBytes, icon, problems, skippedServerOnly, "overrides", List.of(), "mrpack");
        }

        public boolean importable() {
            return problems.isEmpty();
        }

        public int count(String folder) {
            return folders.getOrDefault(folder, 0);
        }
    }

    private final Downloader downloader;

    public MrpackImporter(Downloader downloader) {
        this.downloader = downloader;
    }

    /** Whether a file looks like a Modrinth modpack (extension or an index at the archive root). */
    public static boolean isMrpack(Path file) {
        if (SafeZip.extension(file.getFileName().toString()).equals("mrpack")) {
            return true;
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.getEntry(INDEX) != null;
        } catch (IOException e) {
            return false;
        }
    }

    public static Preview analyze(Path file) throws IOException {
        return analyze(file, ALLOWED_HOSTS, false);
    }

    /** @param allowLocalHttp tests only: also accept http://127.0.0.1 download URLs */
    static Preview analyze(Path file, Set<String> hosts, boolean allowLocalHttp) throws IOException {
        List<String> problems = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            byte[] raw = SafeZip.read(zip, INDEX, 16 * 1024 * 1024);
            if (raw == null) {
                throw new IOException(Lang.t("mrpack.error.no_index"));
            }
            JsonObject o;
            try {
                o = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (JsonParseException | IllegalStateException e) {
                throw new IOException(Lang.t("mrpack.error.index", e.getMessage()), e);
            }
            int format = o.has("formatVersion") ? o.get("formatVersion").getAsInt() : -1;
            if (format != 1) {
                problems.add(Lang.t("mrpack.error.format", format));
            }
            if (!"minecraft".equals(str(o, "game"))) {
                problems.add(Lang.t("mrpack.error.game", String.valueOf(str(o, "game"))));
            }
            JsonObject deps = o.has("dependencies") && o.get("dependencies").isJsonObject() ? o.getAsJsonObject("dependencies") : new JsonObject();
            String mc = str(deps, "minecraft");
            if (mc == null || !mc.matches("[0-9A-Za-z._-]{1,32}")) {
                problems.add(Lang.t("mrpack.error.no_minecraft"));
            }
            InstanceKind kind = InstanceKind.VANILLA;
            String loaderVersion = null;
            int loaders = 0;
            for (Map.Entry<String, JsonElement> d : deps.entrySet()) {
                InstanceKind k = switch (d.getKey()) {
                    case "fabric-loader" -> InstanceKind.FABRIC;
                    case "quilt-loader" -> InstanceKind.QUILT;
                    case "forge" -> InstanceKind.FORGE;
                    case "neoforge" -> InstanceKind.NEOFORGE;
                    case "minecraft" -> null;
                    default -> {
                        problems.add(Lang.t("mrpack.error.dependency", d.getKey()));
                        yield null;
                    }
                };
                if (k != null) {
                    kind = k;
                    loaderVersion = d.getValue().getAsString();
                    loaders++;
                }
            }
            if (loaders > 1) {
                problems.add(Lang.t("mrpack.error.loaders"));
            }
            if (loaderVersion != null && !loaderVersion.matches("[A-Za-z0-9._+-]{1,80}")) {
                problems.add(Lang.t("mrpack.error.loader_version", loaderVersion));
                loaderVersion = null;
            }
            if (kind == InstanceKind.FORGE && loaderVersion != null && mc != null && !loaderVersion.startsWith(mc + "-")) {
                loaderVersion = mc + "-" + loaderVersion; // Forge's Maven version carries the Minecraft version
            }
            List<PackFile> files = new ArrayList<>();
            Map<String, Integer> folders = new LinkedHashMap<>();
            long bytes = 0;
            int serverOnly = 0;
            if (o.has("files") && o.get("files").isJsonArray()) {
                for (JsonElement fe : o.getAsJsonArray("files")) {
                    JsonObject f = fe.getAsJsonObject();
                    String path;
                    try {
                        path = SafeZip.checkedName(str(f, "path"));
                    } catch (IOException e) {
                        problems.add(Lang.t("mrpack.error.path", String.valueOf(str(f, "path"))));
                        continue;
                    }
                    boolean optional = false;
                    if (f.has("env") && f.get("env").isJsonObject()) {
                        String client = str(f.getAsJsonObject("env"), "client");
                        if ("unsupported".equals(client)) {
                            serverOnly++;
                            continue;
                        }
                        optional = "optional".equals(client);
                    }
                    JsonObject hashes = f.has("hashes") && f.get("hashes").isJsonObject() ? f.getAsJsonObject("hashes") : new JsonObject();
                    String sha1 = str(hashes, "sha1");
                    String sha512 = str(hashes, "sha512");
                    if (sha512 == null || !sha512.matches("[0-9a-fA-F]{128}")) {
                        problems.add(Lang.t("mrpack.error.hash", path));
                        continue;
                    }
                    String url = null;
                    if (f.has("downloads") && f.get("downloads").isJsonArray()) {
                        for (JsonElement u : f.getAsJsonArray("downloads")) {
                            if (u.isJsonPrimitive() && allowedUrl(u.getAsString(), hosts, allowLocalHttp)) {
                                url = u.getAsString();
                                break;
                            }
                        }
                    }
                    if (url == null) {
                        problems.add(Lang.t("mrpack.error.host", path));
                        continue;
                    }
                    long size = f.has("fileSize") ? f.get("fileSize").getAsLong() : 0;
                    files.add(new PackFile(path, url, sha1, sha512.toLowerCase(java.util.Locale.ROOT), size, optional));
                    bytes += Math.max(0, size);
                    String top = path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
                    folders.merge(top, 1, Integer::sum);
                }
            }
            int overrides = 0;
            long overrideBytes = 0;
            for (var en = zip.entries(); en.hasMoreElements(); ) {
                var e = en.nextElement();
                String n = SafeZip.checkedName(e.getName());
                if (!e.isDirectory() && (n.startsWith("overrides/") || n.startsWith("client-overrides/"))) {
                    overrides++;
                    overrideBytes += Math.max(0, e.getSize());
                }
            }
            byte[] icon = null;
            try {
                icon = SafeZip.read(zip, "overrides/icon.png", 1024 * 1024);
            } catch (IOException e) {
                // no or too large icon
            }
            return new Preview(file, nonBlank(str(o, "name"), file.getFileName().toString()), str(o, "versionId"), str(o, "summary"),
                    mc, kind, loaderVersion, files, folders, overrides, bytes, overrideBytes, icon, problems, serverOnly);
        } catch (java.util.zip.ZipException e) {
            throw new IOException(Lang.t("mrpack.error.zip"), e);
        } catch (IllegalStateException | ClassCastException | UnsupportedOperationException | NumberFormatException e) {
            throw new IOException(Lang.t("mrpack.error.index", e.getMessage()), e);
        }
    }

    static boolean allowedUrl(String url, Set<String> hosts, boolean allowLocalHttp) {
        try {
            URI u = new URI(url);
            if (allowLocalHttp && "http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost())) {
                return true;
            }
            return "https".equals(u.getScheme()) && u.getHost() != null && hosts.contains(u.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Imports the previewed pack as a new instance named {@code name}. {@code skipOptional} paths are left out.
     * Interrupting the thread cancels; the new instance is then removed again.
     */
    public Instance importPack(Preview p, String name, Set<String> skipOptional, InstanceManager instances, String source,
                               String sourceProject, String sourceVersion, Downloader.Progress progress) throws IOException {
        if (!p.importable()) {
            throw new IOException(String.join("\n", p.problems()));
        }
        Instance spec = new Instance();
        spec.name = name;
        spec.kind = p.kind().id();
        spec.minecraftVersion = p.minecraftVersion();
        spec.loaderVersion = p.loaderVersion();
        spec.loaderPinned = p.loaderVersion() != null;
        spec.source = source;
        spec.sourceProject = sourceProject;
        spec.sourceVersion = sourceVersion != null ? sourceVersion : p.version();
        Instance inst = instances.create(spec);
        try {
            Path game = inst.gameDir();
            List<Downloader.Task> tasks = new ArrayList<>();
            for (PackFile f : p.files()) {
                if (f.optional() && skipOptional != null && skipOptional.contains(f.path())) {
                    continue;
                }
                tasks.add(new Downloader.Task(URI.create(f.url()), FileUtil.resolveInside(game, f.path()), f.expected(), f.size()));
            }
            downloader.downloadAll(tasks, progress, Downloader.Verify.FULL);
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException(Lang.t("content.cancelled"));
            }
            try (ZipFile zip = new ZipFile(p.file().toFile())) {
                SafeZip.extract(zip, p.overrides() + "/", game, rel -> true);
                if (p.format().equals("mrpack")) {
                    SafeZip.extract(zip, "client-overrides/", game, rel -> true);
                }
            }
            // pack icon as instance icon
            Path icon = game.resolve("icon.png");
            if (Files.isRegularFile(icon) && Files.size(icon) <= 2 * 1024 * 1024) {
                inst = instances.setIcon(inst, icon);
            }
            recordOrigin(game, p, sourceProject);
            Log.info("Imported modpack " + p.name() + " as " + inst.id + " (" + tasks.size() + " files)");
            return inst;
        } catch (IOException | RuntimeException e) {
            try {
                instances.delete(inst);
            } catch (IOException cleanup) {
                Log.warn("Could not remove the incomplete instance " + inst.id + ": " + cleanup.getMessage());
            }
            throw e instanceof IOException io ? io : new IOException(e.getMessage(), e);
        }
    }

    /** Files from Modrinth's CDN get a manifest entry (platform and hashes) so updates and names work later. */
    private static void recordOrigin(Path game, Preview p, String packProject) throws IOException {
        ContentManifest manifest = ContentManifest.load(game);
        for (PackFile f : p.files()) {
            int slash = f.path().indexOf('/');
            if (slash < 0 || f.path().indexOf('/', slash + 1) >= 0) {
                continue;
            }
            String folder = f.path().substring(0, slash);
            if (!List.of("mods", "resourcepacks", "shaderpacks").contains(folder) || !Files.isRegularFile(game.resolve(f.path()))) {
                continue;
            }
            ContentManifest.Entry e = new ContentManifest.Entry();
            String[] ids = modrinthIds(f.url());
            if (f.platform() != null) {
                e.platform = f.platform();
                e.projectId = f.projectId();
                e.versionId = f.versionId();
            } else if (ids != null) {
                e.platform = "modrinth";
                e.projectId = ids[0];
                e.versionId = ids[1];
            }
            e.url = f.url();
            e.sha1 = f.sha1();
            e.sha512 = f.sha512();
            if (packProject != null) {
                e.requiredBy = new ArrayList<>(List.of(packProject));
            }
            e.installed = Instant.now().toString();
            manifest.put(f.path(), e);
        }
        manifest.save();
    }

    private static final java.util.regex.Pattern CDN_PATH = java.util.regex.Pattern.compile("/data/([A-Za-z0-9]{8})/versions/([A-Za-z0-9]{8})/[^/]+");

    /** Project and version id of a Modrinth CDN file URL (https://cdn.modrinth.com/data/&lt;project&gt;/versions/&lt;version&gt;/&lt;file&gt;), else null. */
    static String[] modrinthIds(String url) {
        try {
            URI u = new URI(url);
            java.util.regex.Matcher m = CDN_PATH.matcher(u.getPath() == null ? "" : u.getPath());
            if ("cdn.modrinth.com".equals(u.getHost()) && m.matches()) {
                return new String[]{m.group(1), m.group(2)};
            }
        } catch (URISyntaxException e) {
            // not a URL
        }
        return null;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String nonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.strip() : b;
    }
}
