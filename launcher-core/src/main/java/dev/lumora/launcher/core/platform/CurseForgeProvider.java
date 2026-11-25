package dev.lumora.launcher.core.platform;

import com.google.gson.Gson;
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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * CurseForge through the official CurseForge for Studios API (api.curseforge.com, docs.curseforge.com). It needs an API
 * key: Lumora ships none (no built-in credentials), the player enters their own under Optionen › Integrationen. Without a
 * key the provider is not {@link #available()} and is never asked.
 * <ul>
 *   <li>The key travels only in the {@code x-api-key} header and is registered with the log redactor.</li>
 *   <li>Files whose author does not allow third-party downloads come without a download URL; they are shown with the
 *       reason and a link to their page, never fetched another way.</li>
 *   <li>Files are verified with the SHA-1 CurseForge publishes (it offers no SHA-512).</li>
 * </ul>
 */
public final class CurseForgeProvider implements ModPlatformProvider {
    public static final String API = "https://api.curseforge.com";
    public static final int MINECRAFT = 432;
    private static final Gson COMPACT = new Gson();
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("\\d{1,10}");
    private static final java.util.regex.Pattern SLUG = java.util.regex.Pattern.compile("[a-z0-9-]{1,80}");

    private final Downloader downloader;
    private final String api;
    private final Supplier<String> key;
    private final ApiCache cache = new ApiCache(300);
    private volatile Map<Integer, List<Category>> categories;

    public CurseForgeProvider(Downloader downloader, Supplier<String> key) {
        this(downloader, API, key);
    }

    /** @param api base URL (tests point this at a local server) */
    public CurseForgeProvider(Downloader downloader, String api, Supplier<String> key) {
        this.downloader = downloader;
        this.api = api;
        this.key = key;
    }

    @Override
    public Platform platform() {
        return Platform.CURSEFORGE;
    }

    @Override
    public boolean available() {
        String k = key.get();
        return k != null && !k.isBlank();
    }

    @Override
    public Set<ProjectType> supportedTypes() {
        return EnumSet.of(ProjectType.MOD, ProjectType.MODPACK, ProjectType.RESOURCE_PACK, ProjectType.SHADER, ProjectType.DATAPACK);
    }

    /** CurseForge class ids of the project types. */
    public static int classId(ProjectType t) {
        return switch (t) {
            case MOD -> 6;
            case MODPACK -> 4471;
            case RESOURCE_PACK -> 12;
            case SHADER -> 6552;
            case DATAPACK -> 6945;
        };
    }

    static ProjectType typeOf(int classId) {
        return switch (classId) {
            case 4471 -> ProjectType.MODPACK;
            case 12 -> ProjectType.RESOURCE_PACK;
            case 6552 -> ProjectType.SHADER;
            case 6945 -> ProjectType.DATAPACK;
            default -> ProjectType.MOD;
        };
    }

    /** CurseForge mod loader type ids (1 Forge, 4 Fabric, 5 Quilt, 6 NeoForge); 0 = any. */
    static int loaderType(String loader) {
        return switch (loader == null ? "" : loader) {
            case "forge" -> 1;
            case "fabric" -> 4;
            case "quilt" -> 5;
            case "neoforge" -> 6;
            default -> 0;
        };
    }

    static String loaderName(int type) {
        return switch (type) {
            case 1 -> "forge";
            case 4 -> "fabric";
            case 5 -> "quilt";
            case 6 -> "neoforge";
            default -> null;
        };
    }

    @Override
    public String projectPage(ProjectType type, String idOrSlug) {
        String section = switch (type == null ? ProjectType.MOD : type) {
            case MODPACK -> "modpacks";
            case RESOURCE_PACK -> "texture-packs";
            case SHADER -> "shaders";
            case DATAPACK -> "data-packs";
            default -> "mc-mods";
        };
        return "https://www.curseforge.com/minecraft/" + section + "/" + idOrSlug;
    }

    // ---------------------------------------------------------------- search

    @Override
    public SearchPage search(SearchQuery q) throws IOException {
        ProjectType type = q.type() == null ? ProjectType.MOD : q.type();
        int pageSize = Math.min(50, q.limit());
        int index = Math.min(q.offset(), 10_000 - pageSize);
        StringBuilder url = new StringBuilder(api).append("/v1/mods/search?gameId=").append(MINECRAFT)
                .append("&classId=").append(classId(type)).append("&index=").append(Math.max(0, index)).append("&pageSize=").append(pageSize);
        if (!q.text().isEmpty()) {
            url.append("&searchFilter=").append(enc(q.text()));
        }
        int sort = switch (q.sort()) {
            case DOWNLOADS -> 6;
            case FOLLOWS -> 2;
            case NEWEST -> 11;
            case UPDATED -> 3;
            default -> q.text().isEmpty() ? 2 : 0;
        };
        if (sort > 0) {
            url.append("&sortField=").append(sort).append("&sortOrder=desc");
        }
        if (q.gameVersion() != null && q.gameVersion().matches("[0-9A-Za-z._-]{1,32}")) {
            url.append("&gameVersion=").append(enc(q.gameVersion()));
            // a loader filter needs a game version (API rule); the best match decides
            int lt = q.loaders().isEmpty() ? 0 : loaderType(q.loaders().get(0));
            if (lt > 0) {
                url.append("&modLoaderType=").append(lt);
            }
        }
        List<Integer> cats = categoryIds(type, q.categories());
        if (!cats.isEmpty()) {
            url.append("&categoryIds=").append(enc(COMPACT.toJson(cats.subList(0, Math.min(10, cats.size())))));
        }
        JsonObject o = get(url.toString(), Duration.ofMinutes(3));
        try {
            List<ProjectSummary> hits = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("data")) {
                hits.add(summary(e.getAsJsonObject()));
            }
            JsonObject p = o.getAsJsonObject("pagination");
            int total = p == null ? hits.size() : Math.min(10_000, p.get("totalCount").getAsInt());
            return new SearchPage(hits, q.offset(), total);
        } catch (IllegalStateException | NullPointerException | ClassCastException | UnsupportedOperationException e) {
            throw new IOException("Unexpected search response from CurseForge: " + e.getMessage(), e);
        }
    }

    /** Category ids for CurseForge category names or slugs the query names (Lumora maps its categories to names). */
    private List<Integer> categoryIds(ProjectType type, List<String> wanted) {
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        try {
            for (Category c : categoriesOf(classId(type))) {
                String name = norm(c.name());
                for (String w : wanted) {
                    String n = norm(w);
                    if (!n.isEmpty() && (name.equals(n) || norm(c.header() == null ? "" : c.header()).equals(n) || name.startsWith(n))) {
                        int id = Integer.parseInt(c.id());
                        if (!out.contains(id)) {
                            out.add(id);
                        }
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            // categories not reachable: search without them
        }
        return out;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static ProjectSummary summary(JsonObject m) {
        String author = null;
        if (m.has("authors") && m.get("authors").isJsonArray() && !m.getAsJsonArray("authors").isEmpty()) {
            author = str(m.getAsJsonArray("authors").get(0).getAsJsonObject(), "name");
        }
        String icon = m.has("logo") && m.get("logo").isJsonObject() ? str(m.getAsJsonObject("logo"), "thumbnailUrl") : null;
        List<String> cats = new ArrayList<>();
        if (m.has("categories") && m.get("categories").isJsonArray()) {
            for (JsonElement c : m.getAsJsonArray("categories")) {
                String n = str(c.getAsJsonObject(), "name");
                if (n != null) {
                    cats.add(n);
                }
            }
        }
        Set<String> loaders = new LinkedHashSet<>();
        Set<String> versions = new LinkedHashSet<>();
        if (m.has("latestFilesIndexes") && m.get("latestFilesIndexes").isJsonArray()) {
            for (JsonElement f : m.getAsJsonArray("latestFilesIndexes")) {
                JsonObject fo = f.getAsJsonObject();
                String l = fo.has("modLoader") && !fo.get("modLoader").isJsonNull() ? loaderName(fo.get("modLoader").getAsInt()) : null;
                if (l != null) {
                    loaders.add(l);
                }
                String v = str(fo, "gameVersion");
                if (v != null) {
                    versions.add(v);
                }
            }
        }
        String featured = null;
        if (m.has("screenshots") && m.get("screenshots").isJsonArray() && !m.getAsJsonArray("screenshots").isEmpty()) {
            featured = str(m.getAsJsonArray("screenshots").get(0).getAsJsonObject(), "url");
        }
        int classId = m.has("classId") && !m.get("classId").isJsonNull() ? m.get("classId").getAsInt() : 6;
        return new ProjectSummary(Platform.CURSEFORGE, String.valueOf(m.get("id").getAsLong()), str(m, "slug"), str(m, "name"), str(m, "summary"),
                author, num(m, "downloadCount"), num(m, "thumbsUpCount"), icon, cats, new ArrayList<>(loaders), new ArrayList<>(versions),
                time(m, "dateModified"), time(m, "dateReleased"), null, typeOf(classId), featured, Side.UNKNOWN, Side.UNKNOWN);
    }

    // ---------------------------------------------------------------- projects

    @Override
    public Project project(String idOrSlug) throws IOException {
        String id = idOrSlug;
        if (!NUMBER.matcher(idOrSlug).matches()) {
            if (!SLUG.matcher(idOrSlug).matches()) {
                throw new IllegalArgumentException("invalid id: " + idOrSlug);
            }
            JsonObject found = get(api + "/v1/mods/search?gameId=" + MINECRAFT + "&slug=" + enc(idOrSlug), Duration.ofMinutes(10));
            JsonArray data = found.getAsJsonArray("data");
            if (data == null || data.isEmpty()) {
                throw new IOException("CurseForge project " + idOrSlug + " not found");
            }
            id = String.valueOf(data.get(0).getAsJsonObject().get("id").getAsLong());
        }
        JsonObject m = get(api + "/v1/mods/" + id, Duration.ofMinutes(10)).getAsJsonObject("data");
        String body = null;
        try {
            JsonObject d = get(api + "/v1/mods/" + id + "/description", Duration.ofMinutes(30));
            body = d.has("data") && d.get("data").isJsonPrimitive() ? d.get("data").getAsString() : null;
        } catch (IOException e) {
            // description is optional
        }
        try {
            ProjectSummary s = summary(m);
            List<GalleryImage> gallery = new ArrayList<>();
            if (m.has("screenshots") && m.get("screenshots").isJsonArray()) {
                for (JsonElement e : m.getAsJsonArray("screenshots")) {
                    JsonObject g = e.getAsJsonObject();
                    gallery.add(new GalleryImage(str(g, "thumbnailUrl") != null ? str(g, "thumbnailUrl") : str(g, "url"), str(g, "url"),
                            str(g, "title"), str(g, "description"), false));
                }
            }
            Map<String, String> links = new LinkedHashMap<>();
            String page = projectPage(s.type(), s.slug());
            if (m.has("links") && m.get("links").isJsonObject()) {
                JsonObject l = m.getAsJsonObject("links");
                put(links, "source", str(l, "sourceUrl"));
                put(links, "issues", str(l, "issuesUrl"));
                put(links, "wiki", str(l, "wikiUrl"));
                String website = str(l, "websiteUrl");
                if (website != null && website.startsWith("https://")) {
                    page = website;
                }
            }
            return new Project(s, body, gallery, links, List.of(), null, null, page);
        } catch (IllegalStateException | NullPointerException | ClassCastException | UnsupportedOperationException e) {
            throw new IOException("Unexpected project response from CurseForge: " + e.getMessage(), e);
        }
    }

    private static void put(Map<String, String> links, String key, String url) {
        if (url != null && url.startsWith("https://")) {
            links.put(key, url);
        }
    }

    @Override
    public List<ProjectSummary> projects(List<String> ids) throws IOException {
        List<Long> numbers = new ArrayList<>();
        for (String id : ids) {
            if (id != null && NUMBER.matcher(id).matches()) {
                numbers.add(Long.parseLong(id));
            }
        }
        if (numbers.isEmpty()) {
            return List.of();
        }
        JsonObject req = new JsonObject();
        req.add("modIds", COMPACT.toJsonTree(numbers));
        JsonObject o = post(api + "/v1/mods", COMPACT.toJson(req));
        List<ProjectSummary> out = new ArrayList<>();
        try {
            for (JsonElement e : o.getAsJsonArray("data")) {
                out.add(summary(e.getAsJsonObject()));
            }
        } catch (IllegalStateException | NullPointerException | ClassCastException e) {
            throw new IOException("Unexpected mods response from CurseForge: " + e.getMessage(), e);
        }
        return out;
    }

    // ---------------------------------------------------------------- files

    @Override
    public List<ProjectVersion> versions(String projectId, List<String> loaders, List<String> gameVersions) throws IOException {
        if (projectId == null || !NUMBER.matcher(projectId).matches()) {
            Project p = project(projectId);
            projectId = p.summary().id();
        }
        String gv = gameVersions != null && gameVersions.size() == 1 && gameVersions.get(0).matches("[0-9A-Za-z._-]{1,32}") ? gameVersions.get(0) : null;
        List<ProjectVersion> out = new ArrayList<>();
        for (int index = 0; index < 500; index += 50) {
            String url = api + "/v1/mods/" + projectId + "/files?pageSize=50&index=" + index + (gv == null ? "" : "&gameVersion=" + enc(gv));
            JsonObject o = get(url, Duration.ofMinutes(5));
            JsonArray data = o.getAsJsonArray("data");
            if (data == null || data.isEmpty()) {
                break;
            }
            for (JsonElement e : data) {
                ProjectVersion v = version(e.getAsJsonObject());
                if (v == null) {
                    continue;
                }
                boolean loaderFits = loaders == null || loaders.isEmpty() || v.loaders().isEmpty() || v.loaders().stream().anyMatch(loaders::contains);
                boolean versionFits = gameVersions == null || gameVersions.isEmpty() || v.gameVersions().stream().anyMatch(gameVersions::contains);
                if (loaderFits && versionFits) {
                    out.add(v);
                }
            }
            JsonObject p = o.getAsJsonObject("pagination");
            if (p == null || index + 50 >= p.get("totalCount").getAsInt()) {
                break;
            }
        }
        out.sort((a, b) -> b.published() == null || a.published() == null ? 0 : b.published().compareTo(a.published()));
        return out;
    }

    /** CurseForge file ids are global: "fileId" alone identifies a version. */
    @Override
    public ProjectVersion version(String versionId) throws IOException {
        if (versionId == null || !NUMBER.matcher(versionId).matches()) {
            throw new IllegalArgumentException("invalid file id: " + versionId);
        }
        List<ProjectVersion> list = files(List.of(Long.parseLong(versionId)));
        if (list.isEmpty()) {
            throw new IOException("CurseForge file " + versionId + " not found");
        }
        return list.get(0);
    }

    /** Several files by id (POST /v1/mods/files). */
    public List<ProjectVersion> files(List<Long> fileIds) throws IOException {
        List<ProjectVersion> out = new ArrayList<>();
        for (int from = 0; from < fileIds.size(); from += 200) {
            JsonObject req = new JsonObject();
            req.add("fileIds", COMPACT.toJsonTree(fileIds.subList(from, Math.min(fileIds.size(), from + 200))));
            JsonObject o = post(api + "/v1/mods/files", COMPACT.toJson(req));
            for (JsonElement e : o.getAsJsonArray("data")) {
                ProjectVersion v = version(e.getAsJsonObject());
                if (v != null) {
                    out.add(v);
                }
            }
        }
        return out;
    }

    /** Loader and side tags CurseForge puts among a file's game versions. */
    private static final Map<String, String> LOADER_TAGS = Map.of("forge", "forge", "fabric", "fabric", "quilt", "quilt", "neoforge", "neoforge");

    static ProjectVersion version(JsonObject f) {
        String fileName = str(f, "fileName");
        if (fileName == null) {
            return null;
        }
        String sha1 = null;
        if (f.has("hashes") && f.get("hashes").isJsonArray()) {
            for (JsonElement h : f.getAsJsonArray("hashes")) {
                JsonObject ho = h.getAsJsonObject();
                if (ho.has("algo") && ho.get("algo").getAsInt() == 1) {
                    sha1 = str(ho, "value");
                }
            }
        }
        if (sha1 == null || !sha1.matches("[0-9a-fA-F]{40}")) {
            return null; // nothing to verify against
        }
        List<String> versions = new ArrayList<>();
        Set<String> loaders = new LinkedHashSet<>();
        if (f.has("gameVersions") && f.get("gameVersions").isJsonArray()) {
            for (JsonElement g : f.getAsJsonArray("gameVersions")) {
                String v = g.getAsString();
                String l = LOADER_TAGS.get(v.toLowerCase(Locale.ROOT));
                if (l != null) {
                    loaders.add(l);
                } else if (v.matches("\\d+(\\.\\d+)+(-.*)?|\\d+w\\d+[a-z]")) {
                    versions.add(v);
                }
            }
        }
        if (loaders.isEmpty() && fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            loaders.add("forge"); // very old files carry no loader tag; they are Forge mods
        }
        String url = str(f, "downloadUrl");
        ProjectFile file = new ProjectFile(url != null && url.startsWith("https://") ? url : null, fileName, num(f, "fileLength"),
                sha1.toLowerCase(Locale.ROOT), null, true);
        List<Dependency> deps = new ArrayList<>();
        if (f.has("dependencies") && f.get("dependencies").isJsonArray()) {
            for (JsonElement d : f.getAsJsonArray("dependencies")) {
                JsonObject dob = d.getAsJsonObject();
                DependencyKind kind = switch (dob.has("relationType") ? dob.get("relationType").getAsInt() : 0) {
                    case 3 -> DependencyKind.REQUIRED;
                    case 2 -> DependencyKind.OPTIONAL;
                    case 5 -> DependencyKind.INCOMPATIBLE;
                    case 1, 6 -> DependencyKind.EMBEDDED;
                    default -> null; // tools (4) are not installed
                };
                if (kind != null && dob.has("modId")) {
                    deps.add(new Dependency(String.valueOf(dob.get("modId").getAsLong()), null, null, kind));
                }
            }
        }
        int release = f.has("releaseType") ? f.get("releaseType").getAsInt() : 1;
        Channel channel = release == 2 ? Channel.BETA : release == 3 ? Channel.ALPHA : Channel.RELEASE;
        return new ProjectVersion(Platform.CURSEFORGE, String.valueOf(f.get("modId").getAsLong()), String.valueOf(f.get("id").getAsLong()),
                str(f, "displayName"), str(f, "displayName") != null ? str(f, "displayName") : fileName, channel, time(f, "fileDate"),
                versions, new ArrayList<>(loaders), List.of(file), deps, num(f, "downloadCount"), null);
    }

    /** Release notes of one file (HTML). */
    public String changelog(String projectId, String fileId) throws IOException {
        if (!NUMBER.matcher(projectId).matches() || !NUMBER.matcher(fileId).matches()) {
            return null;
        }
        JsonObject o = get(api + "/v1/mods/" + projectId + "/files/" + fileId + "/changelog", Duration.ofMinutes(30));
        return o.has("data") && o.get("data").isJsonPrimitive() ? o.get("data").getAsString() : null;
    }

    /** CurseForge has no SHA-1 lookup; files are identified by fingerprint ({@link #identify}). */
    @Override
    public Map<String, ProjectVersion> versionsByHash(List<String> sha1) {
        return Map.of();
    }

    @Override
    public Map<String, ProjectVersion> latestByHash(List<String> sha1, List<String> loaders, List<String> gameVersions) {
        return Map.of();
    }

    @Override
    public Map<Path, ProjectVersion> identify(List<Path> files) throws IOException {
        Map<Long, Path> byPrint = new LinkedHashMap<>();
        for (Path f : files) {
            byPrint.put(CurseForgeFingerprint.of(f), f);
        }
        Map<Path, ProjectVersion> out = new LinkedHashMap<>();
        if (byPrint.isEmpty()) {
            return out;
        }
        JsonObject req = new JsonObject();
        req.add("fingerprints", COMPACT.toJsonTree(new ArrayList<>(byPrint.keySet())));
        JsonObject o = post(api + "/v1/fingerprints/" + MINECRAFT, COMPACT.toJson(req));
        try {
            JsonObject data = o.getAsJsonObject("data");
            for (JsonElement e : data.getAsJsonArray("exactMatches")) {
                JsonObject file = e.getAsJsonObject().getAsJsonObject("file");
                long print = file.get("fileFingerprint").getAsLong();
                ProjectVersion v = version(file);
                Path p = byPrint.get(print);
                if (v != null && p != null) {
                    out.put(p, v);
                }
            }
        } catch (IllegalStateException | NullPointerException | ClassCastException e) {
            throw new IOException("Unexpected fingerprint response from CurseForge: " + e.getMessage(), e);
        }
        return out;
    }

    @Override
    public List<Category> categories() throws IOException {
        List<Category> all = new ArrayList<>();
        for (ProjectType t : supportedTypes()) {
            all.addAll(categoriesOf(classId(t)));
        }
        return all;
    }

    private List<Category> categoriesOf(int classId) throws IOException {
        Map<Integer, List<Category>> map = categories;
        if (map != null && map.containsKey(classId)) {
            return map.get(classId);
        }
        JsonObject o = get(api + "/v1/categories?gameId=" + MINECRAFT + "&classId=" + classId, Duration.ofHours(24));
        List<Category> list = new ArrayList<>();
        Map<Integer, String> names = new LinkedHashMap<>();
        for (JsonElement e : o.getAsJsonArray("data")) {
            JsonObject c = e.getAsJsonObject();
            names.put(c.get("id").getAsInt(), str(c, "name"));
        }
        for (JsonElement e : o.getAsJsonArray("data")) {
            JsonObject c = e.getAsJsonObject();
            Integer parent = c.has("parentCategoryId") && !c.get("parentCategoryId").isJsonNull() ? c.get("parentCategoryId").getAsInt() : null;
            list.add(new Category(String.valueOf(c.get("id").getAsInt()), str(c, "name"), typeOf(classId), parent == null ? null : names.get(parent)));
        }
        Map<Integer, List<Category>> next = new LinkedHashMap<>(map == null ? Map.of() : map);
        next.put(classId, list);
        categories = next;
        return list;
    }

    // ---------------------------------------------------------------- HTTP

    private Map<String, String> headers() throws IOException {
        String k = key.get();
        if (k == null || k.isBlank()) {
            throw new IOException("no CurseForge API key");
        }
        return Map.of("x-api-key", k.strip());
    }

    private JsonObject get(String url, Duration ttl) throws IOException {
        String body = cache.get("GET " + url, ttl, () -> call(url, null));
        return parse(body);
    }

    private JsonObject post(String url, String json) throws IOException {
        return parse(cache.get("POST " + url + " " + json, Duration.ofMinutes(5), () -> call(url, json)));
    }

    private String call(String url, String json) throws IOException {
        try {
            return downloader.api(URI.create(url), json, headers());
        } catch (Downloader.HttpStatusException e) {
            if (e.status() == 401 || e.status() == 403) {
                throw new IOException(dev.lumora.launcher.core.i18n.Lang.t("curseforge.error.key"), e);
            }
            throw e;
        }
    }

    private static JsonObject parse(String body) throws IOException {
        try {
            return COMPACT.fromJson(body, JsonObject.class);
        } catch (JsonParseException e) {
            throw new IOException("Unexpected response from CurseForge: " + e.getMessage(), e);
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
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

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
