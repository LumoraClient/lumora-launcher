package dev.lumora.launcher.core.modrinth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Hashing;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mod browser on top of the official Modrinth API (api.modrinth.com/v2): search Fabric mods or resource packs
 * for one Minecraft version and install them into the version's instance. Every file is downloaded from the
 * Modrinth CDN and checked against the SHA-512 Modrinth publishes; required dependencies are installed too
 * (except those the launcher already manages, e.g. Fabric API or Sodium). What was installed this way is
 * recorded in {@code .lumora-modrinth.json} next to the files so the browser can show "installiert".
 */
public final class ModrinthBrowser {
    public static final String API = "https://api.modrinth.com/v2";
    public static final String MANIFEST = ".lumora-modrinth.json";
    private static final int MAX_DEPENDENCY_DEPTH = 6;

    public enum Kind {
        MOD("mod", "fabric", "mods"), RESOURCE_PACK("resourcepack", "minecraft", "resourcepacks");

        final String projectType;
        final String loader;
        final String folder;

        Kind(String projectType, String loader, String folder) {
            this.projectType = projectType;
            this.loader = loader;
            this.folder = folder;
        }
    }

    public enum Sort {
        RELEVANCE("relevance"), DOWNLOADS("downloads"), FOLLOWS("follows"), NEWEST("newest"), UPDATED("updated");

        final String index;

        Sort(String index) {
            this.index = index;
        }
    }

    public record Hit(String projectId, String slug, String title, String description, String author, long downloads,
                      String iconUrl, String license, List<String> categories) {
        public String pageUrl(Kind kind) {
            return "https://modrinth.com/" + (kind == Kind.MOD ? "mod" : "resourcepack") + "/" + slug;
        }
    }

    public record Page(List<Hit> hits, int offset, int total) {
    }

    /** One installable file version of a project. */
    public record Version(String projectId, String versionId, String versionNumber, String fileName, String url, long size,
                          String sha512, List<String> requiredProjects) {
    }

    /** Result of an install: what landed in the folder (including dependencies) and what was skipped. */
    public record Installed(List<String> files, List<String> skippedManaged, List<String> notes) {
    }

    static final class Manifest {
        /** project id -> file name */
        Map<String, String> projects = new LinkedHashMap<>();
    }

    private final Downloader downloader;
    private final String api;

    public ModrinthBrowser(Downloader downloader) {
        this(downloader, API);
    }

    /** @param api base URL of the Modrinth API (tests point this at a local server) */
    public ModrinthBrowser(Downloader downloader, String api) {
        this.downloader = downloader;
        this.api = api;
    }

    public Page search(String query, String minecraftVersion, Kind kind, Sort sort, int offset, int limit) throws IOException {
        return search(query, minecraftVersion, kind, sort, List.of(), offset, limit);
    }

    /**
     * @param categories Modrinth category slugs (e.g. "optimization"); every one must match (AND, as on modrinth.com).
     *                   Unknown characters are dropped, so nothing but a slug reaches the facet string.
     */
    public Page search(String query, String minecraftVersion, Kind kind, Sort sort, List<String> categories, int offset, int limit)
            throws IOException {
        StringBuilder facets = new StringBuilder("[[\"project_type:").append(kind.projectType).append("\"],[\"versions:")
                .append(minecraftVersion).append("\"]");
        if (kind == Kind.MOD) {
            facets.append(",[\"categories:fabric\"]");
        }
        for (String c : categories) {
            String slug = c.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9-]", "");
            if (!slug.isEmpty()) {
                facets.append(",[\"categories:").append(slug).append("\"]");
            }
        }
        facets.append(']');
        String url = api + "/search?limit=" + Math.clamp(limit, 1, 50) + "&offset=" + Math.max(0, offset)
                + "&index=" + sort.index + "&facets=" + enc(facets.toString())
                + (query == null || query.isBlank() ? "" : "&query=" + enc(query.strip()));
        return parseSearch(downloader.fetchString(URI.create(url)));
    }

    static Page parseSearch(String body) throws IOException {
        try {
            JsonObject o = Json.GSON.fromJson(body, JsonObject.class);
            List<Hit> hits = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("hits")) {
                JsonObject h = e.getAsJsonObject();
                List<String> cats = new ArrayList<>();
                if (h.has("categories") && h.get("categories").isJsonArray()) {
                    h.getAsJsonArray("categories").forEach(c -> cats.add(c.getAsString()));
                }
                hits.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "description"), str(h, "author"),
                        h.has("downloads") ? h.get("downloads").getAsLong() : 0, str(h, "icon_url"), str(h, "license"), cats));
            }
            return new Page(hits, o.get("offset").getAsInt(), o.get("total_hits").getAsInt());
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            throw new IOException("Unexpected search response from Modrinth: " + e.getMessage(), e);
        }
    }

    /** Newest release (otherwise newest beta/alpha) of the project for this version and loader, or null. */
    public Version latest(String projectId, String minecraftVersion, Kind kind) throws IOException {
        String url = api + "/project/" + enc(projectId) + "/version?loaders=" + enc("[\"" + kind.loader + "\"]")
                + "&game_versions=" + enc("[\"" + minecraftVersion + "\"]");
        return parseLatest(downloader.fetchString(URI.create(url)));
    }

    static Version parseLatest(String body) throws IOException {
        try {
            JsonArray versions = Json.GSON.fromJson(body, JsonArray.class);
            JsonObject chosen = null;
            for (JsonElement e : versions) {
                JsonObject v = e.getAsJsonObject();
                if ("release".equals(str(v, "version_type"))) {
                    chosen = v;
                    break;
                }
                if (chosen == null) {
                    chosen = v;
                }
            }
            if (chosen == null) {
                return null;
            }
            JsonObject file = null;
            for (JsonElement fe : chosen.getAsJsonArray("files")) {
                JsonObject f = fe.getAsJsonObject();
                if (file == null || (f.has("primary") && f.get("primary").getAsBoolean())) {
                    file = f;
                }
            }
            if (file == null) {
                return null;
            }
            List<String> required = new ArrayList<>();
            if (chosen.has("dependencies") && chosen.get("dependencies").isJsonArray()) {
                for (JsonElement de : chosen.getAsJsonArray("dependencies")) {
                    JsonObject d = de.getAsJsonObject();
                    if ("required".equals(str(d, "dependency_type")) && d.has("project_id") && !d.get("project_id").isJsonNull()) {
                        required.add(d.get("project_id").getAsString());
                    }
                }
            }
            return new Version(str(chosen, "project_id"), str(chosen, "id"), str(chosen, "version_number"), str(file, "filename"),
                    str(file, "url"), file.get("size").getAsLong(), file.getAsJsonObject("hashes").get("sha512").getAsString(),
                    List.copyOf(required));
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException e) {
            throw new IOException("Unexpected version response from Modrinth: " + e.getMessage(), e);
        }
    }

    /**
     * Installs the project (newest compatible version) and its required dependencies into {@code instance}.
     *
     * @param managedProjectIds Modrinth projects the launcher already installs for this profile (lockfile)
     */
    public Installed install(String projectId, String minecraftVersion, Kind kind, Path instance,
                             Set<String> managedProjectIds) throws IOException {
        Path dir = instance.resolve(kind.folder);
        Files.createDirectories(dir);
        Manifest manifest = readManifest(dir);
        List<String> files = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        installRecursive(projectId, minecraftVersion, kind, dir, managedProjectIds, manifest, files, skipped, notes, seen, 0);
        Json.write(dir.resolve(MANIFEST), manifest);
        return new Installed(files, skipped, notes);
    }

    private void installRecursive(String projectId, String mc, Kind kind, Path dir, Set<String> managed, Manifest manifest,
                                  List<String> files, List<String> skipped, List<String> notes, Set<String> seen, int depth) throws IOException {
        if (!seen.add(projectId) || depth > MAX_DEPENDENCY_DEPTH) {
            return;
        }
        if (depth > 0 && managed.contains(projectId)) {
            skipped.add(projectId);
            return;
        }
        Version v = latest(projectId, mc, kind);
        if (v == null) {
            if (depth == 0) {
                throw new IOException("Keine passende Version für Minecraft " + mc + " gefunden");
            }
            notes.add("Abhängigkeit " + projectId + " hat keine Version für Minecraft " + mc);
            return;
        }
        String safeName = v.fileName().replaceAll("[^A-Za-z0-9_.+\\-\\[\\]() ]", "_");
        if (!safeName.endsWith(kind == Kind.MOD ? ".jar" : ".zip")) {
            throw new IOException("Unerwarteter Dateityp: " + v.fileName());
        }
        Path target = FileUtil.resolveInside(dir, safeName);
        // a previous version of the same project is replaced
        String old = manifest.projects.get(projectId);
        downloader.download(new Downloader.Task(URI.create(v.url()), target, Hashing.Expected.sha512(v.sha512()), v.size()), null);
        if (old != null && !old.equals(safeName)) {
            Files.deleteIfExists(FileUtil.resolveInside(dir, old));
        }
        manifest.projects.put(projectId, safeName);
        files.add(safeName);
        Log.info("Installed " + safeName + " (Modrinth " + projectId + " " + v.versionNumber() + ") into " + dir);
        for (String dep : v.requiredProjects()) {
            if (kind == Kind.MOD) {
                installRecursive(dep, mc, Kind.MOD, dir, managed, manifest, files, skipped, notes, seen, depth + 1);
            } else if (!managed.contains(dep)) {
                // a resource pack that needs a mod (e.g. EMF): installed into mods/ with its own manifest
                Installed sub = install(dep, mc, Kind.MOD, dir.getParent(), managed);
                files.addAll(sub.files());
                skipped.addAll(sub.skippedManaged());
                notes.addAll(sub.notes());
            } else {
                skipped.add(dep);
            }
        }
    }

    /** Project ids installed through the browser in this folder (instance/mods or instance/resourcepacks). */
    public static Map<String, String> installed(Path instance, Kind kind) {
        return Map.copyOf(readManifest(instance.resolve(kind.folder)).projects);
    }

    /** Modrinth project ids the lockfile installs for this profile (never installed twice by the browser). */
    public static Set<String> managedProjects(ProfileCatalog catalog, ProfileCatalog.Profile profile) {
        Set<String> out = new HashSet<>();
        catalog.profileArtifacts(profile).forEach(a -> {
            if (a.modrinthProjectId != null) {
                out.add(a.modrinthProjectId);
            }
        });
        profile.resourcePacks.forEach(id -> {
            String p = catalog.artifact(id).modrinthProjectId;
            if (p != null) {
                out.add(p);
            }
        });
        return out;
    }

    static Manifest readManifest(Path dir) {
        Path f = dir.resolve(MANIFEST);
        if (!Files.isRegularFile(f)) {
            return new Manifest();
        }
        try {
            Manifest m = Json.read(f, Manifest.class);
            if (m == null || m.projects == null) {
                return new Manifest();
            }
            m.projects.values().removeIf(v -> v == null || v.contains("/") || v.contains("\\") || v.contains(".."));
            return m;
        } catch (IOException | RuntimeException e) {
            return new Manifest();
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
