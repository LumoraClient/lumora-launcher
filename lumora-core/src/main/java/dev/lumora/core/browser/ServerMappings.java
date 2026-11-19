package dev.lumora.core.browser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * Server mappings from the public repository <a href="https://github.com/NoRiskClient/nrc-server-mappings">
 * NoRiskClient/nrc-server-mappings</a> (GPL-3.0, like Lumora): pretty name, categories, icon and background banner
 * of known servers. The list is refreshed at most every 12 hours with one request to GitHub's tree API; only changed
 * manifests are fetched, and images only for servers in the own list, on first use. Everything is cached in
 * {@code config/lumora/server-mappings}.
 * <p>
 * Only {@code api.github.com} and {@code raw.githubusercontent.com} of that repository are contacted, without any
 * data of the player. Every download is size-limited and checked against the git blob hash from the tree (so a file is
 * exactly the one in the repository); folder and file names are validated, images must be PNG.
 */
final class ServerMappings {
    static final String REPO = "NoRiskClient/nrc-server-mappings";
    static final String TREE_URL = "https://api.github.com/repos/" + REPO + "/git/trees/main?recursive=1";
    static final String RAW_URL = "https://raw.githubusercontent.com/" + REPO + "/main/servers/";
    static final long REFRESH_MS = 12L * 60 * 60 * 1000;
    static final long RETRY_MS = 30L * 60 * 1000;
    static final int TREE_MAX = 4 * 1024 * 1024;
    static final int MANIFEST_MAX = 64 * 1024;
    static final int IMAGE_MAX = 6 * 1024 * 1024;
    static final Pattern DIR = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    static final Pattern FILE = Pattern.compile("[A-Za-z0-9_-]{1,64}\\.png");
    static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final ExecutorService POOL = Icons.pool("Lumora server mappings", 2);
    private static volatile ServerMappings instance;

    /** One mapped server. {@code icon}/{@code background}: file name in its folder and git blob hash, null if none. */
    record Mapping(String dir, String name, List<String> addresses, List<String> categories,
                   String icon, String iconSha, String background, String backgroundSha) {
    }

    private final Path cache;
    private volatile Map<String, Mapping> mappings = Map.of();
    private final Map<String, Mapping> lookups = new ConcurrentHashMap<>();
    private final Map<String, Boolean> fetching = new ConcurrentHashMap<>();
    private volatile boolean started;
    private static HttpClient http;

    ServerMappings(Path cache) {
        this.cache = cache;
    }

    /** The shared instance for this config folder (created on first use). */
    static ServerMappings get(Path configDir) {
        ServerMappings m = instance;
        if (m == null || !m.cache.equals(configDir.resolve("server-mappings"))) {
            m = new ServerMappings(configDir.resolve("server-mappings"));
            instance = m;
        }
        return m;
    }

    /** Loads the cached list and refreshes it in the background if it is old; does nothing after the first call. */
    void start() {
        if (started) {
            return;
        }
        started = true;
        POOL.execute(() -> {
            long checked = loadIndex();
            if (System.currentTimeMillis() - checked > REFRESH_MS) {
                refresh();
            }
        });
    }

    // --- lookup

    /** The mapping for a server address ({@code play.example.net:25565}), null if the server is not mapped. */
    Mapping find(String address) {
        String host = host(address);
        if (host.isEmpty()) {
            return null;
        }
        Mapping cached = lookups.get(host);
        if (cached != null) {
            return cached.dir().isEmpty() ? null : cached;
        }
        Mapping found = match(mappings, host);
        lookups.put(host, found == null ? new Mapping("", "", List.of(), List.of(), null, null, null, null) : found);
        return found;
    }

    /** Exact address first, then a mapped domain the host belongs to ({@code mc.example.net} → {@code example.net}). */
    static Mapping match(Map<String, Mapping> all, String host) {
        Mapping suffix = null;
        int best = -1;
        for (Mapping m : all.values()) {
            for (String a : m.addresses()) {
                String h = host(a);
                if (h.isEmpty()) {
                    continue;
                }
                if (h.equals(host)) {
                    return m;
                }
                if (host.endsWith("." + h) && h.length() > best) {
                    best = h.length();
                    suffix = m;
                }
            }
        }
        return suffix;
    }

    /** {@code " Play.Example.NET:25565 "} → {@code play.example.net}; IPv6 in brackets keeps its brackets. */
    static String host(String address) {
        String s = address == null ? "" : address.strip().toLowerCase(Locale.ROOT);
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            return end < 0 ? s : s.substring(0, end + 1);
        }
        int colon = s.indexOf(':');
        if (colon >= 0 && s.indexOf(':', colon + 1) < 0) {
            s = s.substring(0, colon);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // --- images

    /**
     * The PNG of a mapping's icon or background from the cache; starts the download (checked against its blob hash)
     * on first use and returns null until it is there.
     */
    byte[] image(Mapping m, boolean background) {
        String file = background ? m.background() : m.icon();
        String sha = background ? m.backgroundSha() : m.iconSha();
        if (file == null || sha == null || !SHA.matcher(sha).matches()) {
            return null;
        }
        Path path = cache.resolve("images").resolve(sha + ".png");
        if (Files.isRegularFile(path)) {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                return null;
            }
        }
        if (fetching.putIfAbsent(sha, Boolean.TRUE) == null) {
            POOL.execute(() -> {
                try {
                    byte[] png = download(RAW_URL + m.dir() + "/" + file, IMAGE_MAX);
                    if (png != null && isPng(png) && sha.equals(blobSha(png))) {
                        Files.createDirectories(path.getParent());
                        Path tmp = path.resolveSibling(sha + ".tmp");
                        Files.write(tmp, png);
                        Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException | RuntimeException e) {
                    // stays without a picture; tried again the next time the list opens
                } finally {
                    fetching.remove(sha);
                }
            });
        }
        return null;
    }

    static boolean isPng(byte[] data) {
        if (data == null || data.length < PNG.length) {
            return false;
        }
        for (int i = 0; i < PNG.length; i++) {
            if (data[i] != PNG[i]) {
                return false;
            }
        }
        return true;
    }

    /** Git's blob hash: SHA-1 over {@code "blob <length>\0"} and the content. */
    static String blobSha(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(("blob " + data.length + "\0").getBytes(StandardCharsets.US_ASCII));
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- refresh

    /** One file of a server folder from the tree: name and blob hash. */
    record Blob(String file, String sha) {
    }

    /** The server folders of a tree response: folder → file name → blob. */
    static Map<String, Map<String, Blob>> parseTree(String json) {
        Map<String, Map<String, Blob>> out = new LinkedHashMap<>();
        JsonElement root = Json.parse(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("tree")) {
            return out;
        }
        for (JsonElement e : root.getAsJsonObject().getAsJsonArray("tree")) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            String path = str(o, "path");
            String sha = str(o, "sha");
            if (!"blob".equals(str(o, "type")) || !path.startsWith("servers/") || !SHA.matcher(sha).matches()) {
                continue;
            }
            String[] parts = path.split("/");
            if (parts.length != 3 || !DIR.matcher(parts[1]).matches()) {
                continue;
            }
            if (!parts[2].equals("manifest.json") && !FILE.matcher(parts[2]).matches()) {
                continue;
            }
            out.computeIfAbsent(parts[1], k -> new LinkedHashMap<>()).put(parts[2], new Blob(parts[2], sha));
        }
        return out;
    }

    /** A manifest with the image hashes from its folder; null if a required field is missing. */
    static Mapping parseManifest(String dir, String json, Map<String, Blob> files) {
        JsonElement root = Json.parse(json);
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject o = root.getAsJsonObject();
        String name = str(o, "pretty-name").strip();
        List<String> addresses = strings(o.get("server-address"));
        List<String> categories = strings(o.get("categorys"));
        if (name.isEmpty() || addresses.isEmpty()) {
            return null;
        }
        if (name.length() > 48) {
            name = name.substring(0, 48);
        }
        String icon = null;
        String background = null;
        if (o.has("assets") && o.get("assets").isJsonObject()) {
            JsonObject assets = o.getAsJsonObject("assets");
            icon = asset(str(assets, "icon"), files);
            background = asset(str(assets, "background"), files);
        }
        Blob ib = icon == null ? null : files.get(icon);
        Blob bb = background == null ? null : files.get(background);
        return new Mapping(dir, name, addresses, categories, icon, ib == null ? null : ib.sha(), background,
                bb == null ? null : bb.sha());
    }

    /** {@code "./icon.png"} → {@code icon.png} if that file is in the folder, else null. */
    private static String asset(String ref, Map<String, Blob> files) {
        String f = ref.startsWith("./") ? ref.substring(2) : ref;
        return FILE.matcher(f).matches() && files.containsKey(f) ? f : null;
    }

    private void refresh() {
        try {
            byte[] tree = download(TREE_URL, TREE_MAX);
            if (tree == null) {
                retryLater();
                return;
            }
            Map<String, Map<String, Blob>> folders = parseTree(new String(tree, StandardCharsets.UTF_8));
            if (folders.isEmpty()) {
                retryLater();
                return;
            }
            Map<String, String> manifestShas = readManifestShas();
            Map<String, Mapping> next = new LinkedHashMap<>();
            Map<String, String> nextShas = new HashMap<>();
            for (Map.Entry<String, Map<String, Blob>> f : folders.entrySet()) {
                Blob manifest = f.getValue().get("manifest.json");
                if (manifest == null) {
                    continue;
                }
                Mapping old = mappings.get(f.getKey());
                String json = null;
                if (old != null && manifest.sha().equals(manifestShas.get(f.getKey()))) {
                    // unchanged manifest: only the image hashes may have changed
                    json = readCachedManifest(f.getKey());
                }
                if (json == null) {
                    byte[] data = download(RAW_URL + f.getKey() + "/manifest.json", MANIFEST_MAX);
                    if (data == null || !manifest.sha().equals(blobSha(data))) {
                        if (old != null) {
                            next.put(f.getKey(), old);
                            nextShas.put(f.getKey(), manifestShas.get(f.getKey()));
                        }
                        continue;
                    }
                    json = new String(data, StandardCharsets.UTF_8);
                    writeCachedManifest(f.getKey(), json);
                }
                try {
                    Mapping m = parseManifest(f.getKey(), json, f.getValue());
                    if (m != null) {
                        next.put(f.getKey(), m);
                        nextShas.put(f.getKey(), manifest.sha());
                    }
                } catch (RuntimeException e) {
                    // a broken manifest is left out
                }
            }
            mappings = Map.copyOf(next);
            lookups.clear();
            saveIndex(System.currentTimeMillis(), next, nextShas);
        } catch (IOException | RuntimeException e) {
            retryLater();
        }
    }

    private void retryLater() {
        try {
            saveIndex(System.currentTimeMillis() - REFRESH_MS + RETRY_MS, mappings, readManifestShas());
        } catch (IOException ignored) {
            // try again next start
        }
    }

    // --- cache files

    private Path index() {
        return cache.resolve("index.json");
    }

    /** Loads the cached mappings; returns when they were last checked (0 if never). */
    long loadIndex() {
        try {
            if (!Files.isRegularFile(index())) {
                return 0;
            }
            JsonObject root = Json.parse(Files.readString(index(), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Mapping> out = new LinkedHashMap<>();
            JsonObject servers = root.has("servers") ? root.getAsJsonObject("servers") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : servers.entrySet()) {
                if (!DIR.matcher(e.getKey()).matches() || !e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                String icon = nullable(o, "icon");
                String background = nullable(o, "background");
                out.put(e.getKey(), new Mapping(e.getKey(), str(o, "name"), strings(o.get("addresses")),
                        strings(o.get("categories")), icon != null && FILE.matcher(icon).matches() ? icon : null,
                        nullable(o, "iconSha"), background != null && FILE.matcher(background).matches() ? background : null,
                        nullable(o, "backgroundSha")));
            }
            mappings = Map.copyOf(out);
            lookups.clear();
            return root.has("checked") ? root.get("checked").getAsLong() : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private Map<String, String> readManifestShas() {
        Map<String, String> out = new HashMap<>();
        try {
            if (Files.isRegularFile(index())) {
                JsonObject root = Json.parse(Files.readString(index(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("servers")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("servers").entrySet()) {
                        if (e.getValue().isJsonObject()) {
                            out.put(e.getKey(), nullable(e.getValue().getAsJsonObject(), "manifestSha"));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // no index yet
        }
        return out;
    }

    void saveIndex(long checked, Map<String, Mapping> all, Map<String, String> manifestShas) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("source", "https://github.com/" + REPO + " (GPL-3.0)");
        root.addProperty("checked", checked);
        JsonObject servers = new JsonObject();
        for (Mapping m : all.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", m.name());
            o.add("addresses", array(m.addresses()));
            o.add("categories", array(m.categories()));
            o.addProperty("icon", m.icon());
            o.addProperty("iconSha", m.iconSha());
            o.addProperty("background", m.background());
            o.addProperty("backgroundSha", m.backgroundSha());
            o.addProperty("manifestSha", manifestShas.get(m.dir()));
            servers.add(m.dir(), o);
        }
        root.add("servers", servers);
        Files.createDirectories(cache);
        AtomicFiles.write(index(), root.toString());
    }

    private String readCachedManifest(String dir) {
        try {
            Path p = cache.resolve("manifests").resolve(dir + ".json");
            return Files.isRegularFile(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void writeCachedManifest(String dir, String json) throws IOException {
        Path p = cache.resolve("manifests").resolve(dir + ".json");
        Files.createDirectories(p.getParent());
        AtomicFiles.write(p, json);
    }

    /** The current mappings (tests). */
    Map<String, Mapping> mappings() {
        return mappings;
    }

    void set(Map<String, Mapping> all) {
        mappings = Map.copyOf(all);
        lookups.clear();
    }

    // --- network

    private static synchronized HttpClient http() {
        if (http == null) {
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        }
        return http;
    }

    /** GET of one of the two allowed GitHub hosts, at most {@code max} bytes; null on any error. */
    static byte[] download(String url, int max) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme())
                || !("api.github.com".equals(uri.getHost()) || "raw.githubusercontent.com".equals(uri.getHost()))) {
            return null;
        }
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Lumora-Client").header("Accept", "application/vnd.github+json").GET().build();
        try {
            HttpResponse<InputStream> res = http().send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                if (res.statusCode() != 200) {
                    return null;
                }
                byte[] data = in.readNBytes(max + 1);
                return data.length > max ? null : data;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // --- json helpers

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static String nullable(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonPrimitive() && out.size() < 16) {
                    String s = x.getAsString().strip();
                    if (!s.isEmpty() && s.length() <= 255) {
                        out.add(s);
                    }
                }
            }
        } else if (e != null && e.isJsonPrimitive()) {
            out.add(e.getAsString().strip());
        }
        return List.copyOf(out);
    }

    private static JsonArray array(List<String> values) {
        JsonArray a = new JsonArray();
        for (String v : values) {
            a.add(new com.google.gson.JsonPrimitive(v));
        }
        return a;
    }

    /** Display name of a category: {@code KITPVP} → {@code KitPvP}, {@code SURVIVAL} → {@code Survival}. */
    static String category(String c) {
        String u = c.toUpperCase(Locale.ROOT);
        return switch (u) {
            case "PVP" -> "PvP";
            case "KITPVP" -> "KitPvP";
            case "HG", "UHC", "RPG" -> u;
            case "SKYBLOCK" -> "SkyBlock";
            case "SKYWARS" -> "SkyWars";
            case "BEDWARS" -> "BedWars";
            default -> u.isEmpty() ? u : u.charAt(0) + u.substring(1).toLowerCase(Locale.ROOT);
        };
    }
}
