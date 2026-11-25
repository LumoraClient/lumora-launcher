package dev.lumora.launcher.core.platform;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.platform.PlatformModel.Category;
import dev.lumora.launcher.core.platform.PlatformModel.Channel;
import dev.lumora.launcher.core.platform.PlatformModel.Dependency;
import dev.lumora.launcher.core.platform.PlatformModel.DependencyKind;
import dev.lumora.launcher.core.platform.PlatformModel.GalleryImage;
import dev.lumora.launcher.core.platform.PlatformModel.Project;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectFile;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectSummary;
import dev.lumora.launcher.core.platform.PlatformModel.ProjectVersion;
import dev.lumora.launcher.core.platform.PlatformModel.SearchPage;
import dev.lumora.launcher.core.platform.PlatformModel.SearchQuery;
import dev.lumora.launcher.core.platform.PlatformModel.Side;
import dev.lumora.launcher.core.util.Json;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Modrinth through its official API (api.modrinth.com/v2, see docs.modrinth.com). Read-only, no account or key.
 * <ul>
 *   <li>Requests go through the launcher's {@link Downloader} (HTTPS allow-list, unique User-Agent, retries with backoff,
 *       also on HTTP 429).</li>
 *   <li>A client-side limit keeps the launcher below Modrinth's 300 requests per minute.</li>
 *   <li>Answers are cached for a few minutes ({@link ApiCache}); categories for a day.</li>
 *   <li>Only ids, slugs and a fixed set of facet values reach the URL; free text is URL-encoded.</li>
 * </ul>
 */
public final class ModrinthProvider implements ModPlatformProvider {
    public static final String API = "https://api.modrinth.com/v2";
    /** Stay well below Modrinth's 300 requests per minute (shared by the whole launcher). */
    static final int REQUESTS_PER_MINUTE = 240;
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final java.util.regex.Pattern SHA1 = java.util.regex.Pattern.compile("[0-9a-f]{40}");

    /** URL parameters and request bodies without pretty printing. */
    private static final com.google.gson.Gson COMPACT = new com.google.gson.Gson();

    private final Downloader downloader;
    private final String api;
    private final ApiCache cache = new ApiCache(400);
    private final Deque<Long> recent = new ArrayDeque<>();

    public ModrinthProvider(Downloader downloader) {
        this(downloader, API);
    }

    /** @param api base URL (tests point this at a local server) */
    public ModrinthProvider(Downloader downloader, String api) {
        this.downloader = downloader;
        this.api = api;
    }

    @Override
    public Platform platform() {
        return Platform.MODRINTH;
    }

    @Override
    public Set<ProjectType> supportedTypes() {
        return EnumSet.of(ProjectType.MOD, ProjectType.MODPACK, ProjectType.RESOURCE_PACK, ProjectType.SHADER, ProjectType.DATAPACK);
    }

    @Override
    public String projectPage(ProjectType type, String idOrSlug) {
        return pageUrl(type, idOrSlug);
    }

    /** Project page on modrinth.com. */
    public static String pageUrl(ProjectType type, String slug) {
        return "https://modrinth.com/" + (type == null ? "project" : type.id()) + "/" + slug;
    }

    // ---------------------------------------------------------------- search

    @Override
    public SearchPage search(SearchQuery q) throws IOException {
        String url = api + "/search?limit=" + q.limit() + "&offset=" + q.offset() + "&index=" + index(q.sort())
                + "&facets=" + enc(facets(q)) + (q.text().isEmpty() ? "" : "&query=" + enc(q.text()));
        return parseSearch(get(url, Duration.ofMinutes(3)));
    }

    /** Facets: OR inside an inner array, AND between them (docs.modrinth.com, Search projects). */
    static String facets(SearchQuery q) {
        List<List<String>> and = new ArrayList<>();
        if (q.type() != null) {
            and.add(List.of("project_type:" + q.type().id()));
        }
        if (q.gameVersion() != null && ID.matcher(q.gameVersion()).matches()) {
            and.add(List.of("versions:" + q.gameVersion()));
        }
        List<String> loaders = new ArrayList<>();
        for (String l : q.loaders()) {
            String slug = slug(l);
            if (!slug.isEmpty()) {
                loaders.add("categories:" + slug);
            }
        }
        if (!loaders.isEmpty()) {
            and.add(loaders);
        }
        List<String> cats = new ArrayList<>();
        for (String c : q.categories()) {
            String slug = slug(c);
            if (!slug.isEmpty()) {
                cats.add("categories:" + slug);
            }
        }
        if (!cats.isEmpty()) {
            and.add(cats);
        }
        switch (q.environment()) {
            case CLIENT -> and.add(List.of("client_side:required", "client_side:optional"));
            case SERVER -> and.add(List.of("server_side:required", "server_side:optional"));
            case BOTH -> {
                and.add(List.of("client_side:required", "client_side:optional"));
                and.add(List.of("server_side:required", "server_side:optional"));
            }
            default -> {
            }
        }
        if (q.openSourceOnly()) {
            and.add(List.of("open_source:true"));
        }
        return COMPACT.toJson(and);
    }

    private static String index(PlatformModel.Sort s) {
        return switch (s) {
            case DOWNLOADS -> "downloads";
            case FOLLOWS -> "follows";
            case NEWEST -> "newest";
            case UPDATED -> "updated";
            default -> "relevance";
        };
    }

    static SearchPage parseSearch(String body) throws IOException {
        try {
            JsonObject o = Json.GSON.fromJson(body, JsonObject.class);
            List<ProjectSummary> hits = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("hits")) {
                JsonObject h = e.getAsJsonObject();
                List<String> cats = strings(h, "display_categories");
                if (cats.isEmpty()) {
                    cats = strings(h, "categories");
                }
                List<String> loaders = new ArrayList<>();
                for (String c : strings(h, "categories")) {
                    if (LOADERS.contains(c)) {
                        loaders.add(c);
                    }
                }
                List<String> featured = strings(h, "gallery");
                String feat = str(h, "featured_gallery");
                hits.add(new ProjectSummary(Platform.MODRINTH, str(h, "project_id"), str(h, "slug"), str(h, "title"),
                        str(h, "description"), str(h, "author"), num(h, "downloads"), num(h, "follows"), str(h, "icon_url"),
                        withoutLoaders(cats), loaders, strings(h, "versions"), time(h, "date_modified"), time(h, "date_created"),
                        str(h, "license"), ProjectType.of(str(h, "project_type")), feat != null ? feat : featured.isEmpty() ? null : featured.get(0),
                        Side.of(str(h, "client_side")), Side.of(str(h, "server_side"))));
            }
            return new SearchPage(hits, o.get("offset").getAsInt(), o.get("total_hits").getAsInt());
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
            throw new IOException("Unexpected search response from Modrinth: " + e.getMessage(), e);
        }
    }

    /** Category values that are loaders (Modrinth lists them among the categories). */
    static final Set<String> LOADERS = Set.of("fabric", "quilt", "forge", "neoforge", "liteloader", "modloader", "rift",
            "minecraft", "iris", "optifine", "canvas", "vanilla", "datapack", "babric", "bta-babric", "legacy-fabric", "ornithe", "nilloader", "java-agent", "bukkit", "paper", "spigot", "purpur", "folia",
            "sponge", "velocity", "bungeecord", "waterfall");

    private static List<String> withoutLoaders(List<String> cats) {
        List<String> out = new ArrayList<>();
        for (String c : cats) {
            if (!LOADERS.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- projects

    @Override
    public Project project(String idOrSlug) throws IOException {
        checkId(idOrSlug);
        return parseProject(get(api + "/project/" + enc(idOrSlug), Duration.ofMinutes(10)));
    }

    static Project parseProject(String body) throws IOException {
        try {
            JsonObject p = Json.GSON.fromJson(body, JsonObject.class);
            ProjectSummary s = summary(p);
            List<GalleryImage> gallery = new ArrayList<>();
            if (p.has("gallery") && p.get("gallery").isJsonArray()) {
                List<JsonObject> items = new ArrayList<>();
                p.getAsJsonArray("gallery").forEach(g -> items.add(g.getAsJsonObject()));
                items.sort((a, b) -> Long.compare(num(a, "ordering"), num(b, "ordering")));
                for (JsonObject g : items) {
                    gallery.add(new GalleryImage(str(g, "url"), str(g, "raw_url"), str(g, "title"), str(g, "description"),
                            g.has("featured") && g.get("featured").getAsBoolean()));
                }
            }
            Map<String, String> links = new LinkedHashMap<>();
            putLink(links, "source", str(p, "source_url"));
            putLink(links, "issues", str(p, "issues_url"));
            putLink(links, "wiki", str(p, "wiki_url"));
            putLink(links, "discord", str(p, "discord_url"));
            List<String> donations = new ArrayList<>();
            if (p.has("donation_urls") && p.get("donation_urls").isJsonArray()) {
                for (JsonElement d : p.getAsJsonArray("donation_urls")) {
                    String u = str(d.getAsJsonObject(), "url");
                    if (u != null && u.startsWith("https://")) {
                        donations.add(u);
                    }
                }
            }
            String licenseName = null;
            String licenseUrl = null;
            if (p.has("license") && p.get("license").isJsonObject()) {
                JsonObject l = p.getAsJsonObject("license");
                licenseName = str(l, "name") != null && !str(l, "name").isBlank() ? str(l, "name") : str(l, "id");
                licenseUrl = str(l, "url");
            }
            return new Project(s, str(p, "body"), gallery, links, donations, licenseName, licenseUrl, pageUrl(s.type(), s.slug()));
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
            throw new IOException("Unexpected project response from Modrinth: " + e.getMessage(), e);
        }
    }

    private static void putLink(Map<String, String> links, String key, String url) {
        if (url != null && url.startsWith("https://")) {
            links.put(key, url);
        }
    }

    /** A project object (GET /project, GET /projects) as a summary. */
    static ProjectSummary summary(JsonObject p) {
        String license = null;
        if (p.has("license") && p.get("license").isJsonObject()) {
            license = str(p.getAsJsonObject("license"), "id");
        }
        List<String> gallery = new ArrayList<>();
        if (p.has("gallery") && p.get("gallery").isJsonArray()) {
            for (JsonElement g : p.getAsJsonArray("gallery")) {
                JsonObject go = g.getAsJsonObject();
                if (go.has("featured") && go.get("featured").getAsBoolean()) {
                    gallery.add(0, str(go, "url"));
                } else {
                    gallery.add(str(go, "url"));
                }
            }
        }
        List<String> cats = new ArrayList<>(strings(p, "categories"));
        cats.addAll(strings(p, "additional_categories"));
        return new ProjectSummary(Platform.MODRINTH, str(p, "id"), str(p, "slug"), str(p, "title"), str(p, "description"), null,
                num(p, "downloads"), num(p, "followers"), str(p, "icon_url"), withoutLoaders(cats), strings(p, "loaders"),
                strings(p, "game_versions"), time(p, "updated"), time(p, "published"), license, ProjectType.of(str(p, "project_type")),
                gallery.isEmpty() ? null : gallery.get(0), Side.of(str(p, "client_side")), Side.of(str(p, "server_side")));
    }

    @Override
    public List<ProjectSummary> projects(List<String> ids) throws IOException {
        List<ProjectSummary> out = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += 100) {
            List<String> chunk = ids.subList(from, Math.min(ids.size(), from + 100));
            chunk.forEach(ModrinthProvider::checkId);
            String body = get(api + "/projects?ids=" + enc(COMPACT.toJson(chunk)), Duration.ofMinutes(10));
            try {
                for (JsonElement e : Json.GSON.fromJson(body, JsonArray.class)) {
                    out.add(summary(e.getAsJsonObject()));
                }
            } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
                throw new IOException("Unexpected projects response from Modrinth: " + e.getMessage(), e);
            }
        }
        return out;
    }

    /** Authors (team members) of a project, owner first. */
    public List<String> authors(String projectId) throws IOException {
        checkId(projectId);
        String body = get(api + "/project/" + enc(projectId) + "/members", Duration.ofMinutes(30));
        List<String> out = new ArrayList<>();
        try {
            for (JsonElement e : Json.GSON.fromJson(body, JsonArray.class)) {
                JsonObject m = e.getAsJsonObject();
                JsonObject user = m.getAsJsonObject("user");
                String name = user == null ? null : str(user, "username");
                if (name != null) {
                    if ("Owner".equalsIgnoreCase(str(m, "role"))) {
                        out.add(0, name);
                    } else {
                        out.add(name);
                    }
                }
            }
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
            throw new IOException("Unexpected members response from Modrinth: " + e.getMessage(), e);
        }
        return out;
    }

    // ---------------------------------------------------------------- versions

    @Override
    public List<ProjectVersion> versions(String projectId, List<String> loaders, List<String> gameVersions) throws IOException {
        checkId(projectId);
        StringBuilder url = new StringBuilder(api).append("/project/").append(enc(projectId)).append("/version?include_changelog=true");
        if (loaders != null && !loaders.isEmpty()) {
            loaders.forEach(ModrinthProvider::checkId);
            url.append("&loaders=").append(enc(COMPACT.toJson(loaders)));
        }
        if (gameVersions != null && !gameVersions.isEmpty()) {
            gameVersions.forEach(ModrinthProvider::checkId);
            url.append("&game_versions=").append(enc(COMPACT.toJson(gameVersions)));
        }
        String body = get(url.toString(), Duration.ofMinutes(5));
        try {
            List<ProjectVersion> out = new ArrayList<>();
            for (JsonElement e : Json.GSON.fromJson(body, JsonArray.class)) {
                ProjectVersion v = parseVersion(e.getAsJsonObject());
                if (v != null) {
                    out.add(v);
                }
            }
            out.sort((a, b) -> b.published() == null || a.published() == null ? 0 : b.published().compareTo(a.published()));
            return out;
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
            throw new IOException("Unexpected version list from Modrinth: " + e.getMessage(), e);
        }
    }

    @Override
    public ProjectVersion version(String versionId) throws IOException {
        checkId(versionId);
        String body = get(api + "/version/" + enc(versionId), Duration.ofMinutes(30));
        try {
            ProjectVersion v = parseVersion(Json.GSON.fromJson(body, JsonObject.class));
            if (v == null) {
                throw new IOException("Version " + versionId + " has no usable file");
            }
            return v;
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
            throw new IOException("Unexpected version from Modrinth: " + e.getMessage(), e);
        }
    }

    /** Version object; null when it has no file with both hashes (nothing could be verified). */
    static ProjectVersion parseVersion(JsonObject v) {
        List<ProjectFile> files = new ArrayList<>();
        for (JsonElement fe : v.getAsJsonArray("files")) {
            JsonObject f = fe.getAsJsonObject();
            JsonObject hashes = f.getAsJsonObject("hashes");
            String sha1 = hashes == null ? null : str(hashes, "sha1");
            String sha512 = hashes == null ? null : str(hashes, "sha512");
            String type = str(f, "file_type");
            if (sha512 == null || str(f, "url") == null || str(f, "filename") == null
                    || (type != null && !type.endsWith("resource-pack") && !"unknown".equals(type))) {
                continue; // sources/dev/javadoc jars and files without a hash are never installed
            }
            files.add(new ProjectFile(str(f, "url"), str(f, "filename"), num(f, "size"), sha1, sha512,
                    f.has("primary") && f.get("primary").getAsBoolean()));
        }
        if (files.isEmpty()) {
            return null;
        }
        List<Dependency> deps = new ArrayList<>();
        if (v.has("dependencies") && v.get("dependencies").isJsonArray()) {
            for (JsonElement de : v.getAsJsonArray("dependencies")) {
                JsonObject d = de.getAsJsonObject();
                DependencyKind kind = switch (String.valueOf(str(d, "dependency_type"))) {
                    case "required" -> DependencyKind.REQUIRED;
                    case "incompatible" -> DependencyKind.INCOMPATIBLE;
                    case "embedded" -> DependencyKind.EMBEDDED;
                    default -> DependencyKind.OPTIONAL;
                };
                deps.add(new Dependency(str(d, "project_id"), str(d, "version_id"), str(d, "file_name"), kind));
            }
        }
        return new ProjectVersion(Platform.MODRINTH, str(v, "project_id"), str(v, "id"), str(v, "name"), str(v, "version_number"),
                Channel.of(str(v, "version_type")), time(v, "date_published"), strings(v, "game_versions"), strings(v, "loaders"),
                files, deps, num(v, "downloads"), str(v, "changelog"));
    }

    @Override
    public Map<String, ProjectVersion> versionsByHash(List<String> sha1) throws IOException {
        List<String> hashes = validHashes(sha1);
        if (hashes.isEmpty()) {
            return Map.of();
        }
        JsonObject req = new JsonObject();
        req.add("hashes", Json.GSON.toJsonTree(hashes));
        req.addProperty("algorithm", "sha1");
        return parseHashMap(post(api + "/version_files", COMPACT.toJson(req)));
    }

    @Override
    public Map<String, ProjectVersion> latestByHash(List<String> sha1, List<String> loaders, List<String> gameVersions) throws IOException {
        List<String> hashes = validHashes(sha1);
        if (hashes.isEmpty()) {
            return Map.of();
        }
        JsonObject req = new JsonObject();
        req.add("hashes", Json.GSON.toJsonTree(hashes));
        req.addProperty("algorithm", "sha1");
        req.add("loaders", Json.GSON.toJsonTree(loaders == null ? List.of() : loaders));
        req.add("game_versions", Json.GSON.toJsonTree(gameVersions == null ? List.of() : gameVersions));
        // update offers: stable releases only (a beta is never pushed onto a player who installed a release)
        req.add("version_types", Json.GSON.toJsonTree(List.of("release")));
        return parseHashMap(post(api + "/version_files/update", COMPACT.toJson(req)));
    }

    private static List<String> validHashes(List<String> sha1) {
        List<String> out = new ArrayList<>();
        for (String h : sha1) {
            String l = h == null ? "" : h.toLowerCase(Locale.ROOT);
            if (SHA1.matcher(l).matches() && !out.contains(l)) {
                out.add(l);
            }
        }
        return out;
    }

    static Map<String, ProjectVersion> parseHashMap(String body) throws IOException {
        try {
            Map<String, ProjectVersion> out = new LinkedHashMap<>();
            JsonObject o = Json.GSON.fromJson(body, JsonObject.class);
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                ProjectVersion v = parseVersion(e.getValue().getAsJsonObject());
                if (v != null) {
                    out.put(e.getKey().toLowerCase(Locale.ROOT), v);
                }
            }
            return out;
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
            throw new IOException("Unexpected hash lookup response from Modrinth: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Category> categories() throws IOException {
        String body = get(api + "/tag/category", Duration.ofHours(24));
        try {
            List<Category> out = new ArrayList<>();
            for (JsonElement e : Json.GSON.fromJson(body, JsonArray.class)) {
                JsonObject c = e.getAsJsonObject();
                out.add(new Category(str(c, "name"), str(c, "name"), ProjectType.of(str(c, "project_type")), str(c, "header")));
            }
            return out;
        } catch (JsonParseException | IllegalStateException | NullPointerException | UnsupportedOperationException | ClassCastException e) {
            throw new IOException("Unexpected category list from Modrinth: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- HTTP

    private String get(String url, Duration ttl) throws IOException {
        return cache.get("GET " + url, ttl, () -> {
            throttle();
            return downloader.fetchString(URI.create(url));
        });
    }

    private String post(String url, String json) throws IOException {
        return cache.get("POST " + url + " " + json, Duration.ofMinutes(5), () -> {
            throttle();
            return downloader.postJson(URI.create(url), json);
        });
    }

    /** Waits when the launcher made {@link #REQUESTS_PER_MINUTE} requests within the last minute. */
    private void throttle() throws InterruptedIOException {
        long wait;
        synchronized (recent) {
            long now = System.currentTimeMillis();
            while (!recent.isEmpty() && now - recent.peekFirst() > 60_000) {
                recent.pollFirst();
            }
            wait = recent.size() >= REQUESTS_PER_MINUTE ? 60_000 - (now - recent.peekFirst()) : 0;
            recent.addLast(now + Math.max(0, wait));
        }
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while waiting for the Modrinth rate limit");
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void checkId(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("invalid id: " + id);
        }
    }

    private static String slug(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static long num(JsonObject o, String k) {
        JsonElement e = o.get(k);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static Instant time(JsonObject o, String k) {
        String s = str(o, k);
        if (s == null) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            try {
                return java.time.OffsetDateTime.parse(s).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    static List<String> strings(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(k);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonPrimitive()) {
                    out.add(x.getAsString());
                }
            }
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
