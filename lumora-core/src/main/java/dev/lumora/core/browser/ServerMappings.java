package dev.lumora.core.browser;

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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Server mappings of the Lumora repository <a href="https://github.com/LumoraClient/Multiplayer-Mapping">
 * LumoraClient/Multiplayer-Mapping</a> (GPL-3.0): pretty name, addresses, categories, icon, background banner and
 * game modes of known servers, built there from NoRiskClient/nrc-server-mappings and the facts of
 * LabyMod/server-media. The list ({@code servers.json}) is loaded at most every 12 hours, pictures only for servers
 * in the own list, on first use. Everything is cached in {@code config/lumora/multiplayer-mapping}.
 * <p>
 * Only files of that repository on {@code raw.githubusercontent.com} are fetched, without any data of the player.
 * Every download is size-limited; pictures must be PNG and match the git blob hash the list names for them.
 */
final class ServerMappings {
    static final String REPO = "LumoraClient/Multiplayer-Mapping";
    static final String BASE_URL = "https://raw.githubusercontent.com/" + REPO + "/main/";
    static final String INDEX_URL = BASE_URL + "servers.json";
    static final long REFRESH_MS = 12L * 60 * 60 * 1000;
    static final long RETRY_MS = 30L * 60 * 1000;
    static final int INDEX_MAX = 4 * 1024 * 1024;
    static final int IMAGE_MAX = 2 * 1024 * 1024;
    static final Pattern DIR = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    static final Pattern SHA = Pattern.compile("[0-9a-f]{40}");
    static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    /** The old caches of 0.3.75/0.3.76, which read the two source repositories directly. */
    static final List<String> OLD_CACHES = List.of("server-mappings", "server-gamemodes");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final ExecutorService POOL = Icons.pool("Lumora server mappings", 2);
    private static volatile ServerMappings instance;

    /** One game mode: name, colour (RGB, -1 if none) and the version range ({@code "1.8<1.12.2,1.20<*"}, "" = all). */
    record Mode(String name, int color, String versions) {
    }

    /**
     * One mapped server. {@code icon}/{@code background}: path in the repository ({@code servers/<id>/icon.png}) and
     * git blob hash, null if none.
     */
    record Mapping(String dir, String name, List<String> addresses, List<String> categories,
                   String icon, String iconSha, String background, String backgroundSha, List<Mode> modes) {
    }

    private static final Mapping NONE = new Mapping("", "", List.of(), List.of(), null, null, null, null, List.of());

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
        if (m == null || !m.cache.equals(configDir.resolve("multiplayer-mapping"))) {
            m = new ServerMappings(configDir.resolve("multiplayer-mapping"));
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
            removeOldCaches();
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
        lookups.put(host, found == null ? NONE : found);
        return found;
    }

    /** Exact address first, then the longest mapped domain the host belongs to ({@code mc.example.net} → {@code example.net}). */
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

    // --- game modes

    /** The game modes of a server that support this Minecraft version (all if the version is unknown). */
    static List<Mode> modesFor(Mapping m, String minecraftVersion) {
        List<Mode> out = new ArrayList<>();
        for (Mode mode : m.modes()) {
            if (supports(mode.versions(), minecraftVersion)) {
                out.add(mode);
            }
        }
        return out;
    }

    /** {@code "1.8<1.12.2,1.20<*"} against {@code "1.21.11"}; empty or broken ranges allow everything. */
    static boolean supports(String ranges, String version) {
        if (ranges == null || ranges.isBlank() || version == null || version.isBlank()) {
            return true;
        }
        boolean any = false;
        for (String range : ranges.split(",")) {
            String[] p = range.strip().split("<", -1);
            if (p.length != 2) {
                continue;
            }
            any = true;
            boolean fromOk = p[0].strip().equals("*") || compare(version, p[0].strip()) >= 0;
            boolean toOk = p[1].strip().equals("*") || compare(version, p[1].strip()) <= 0;
            if (fromOk && toOk) {
                return true;
            }
        }
        return !any;
    }

    /** Compares dotted versions numerically ({@code 1.8.9 < 1.12 < 26.1}). */
    static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int u = i < x.length ? number(x[i]) : 0;
            int v = i < y.length ? number(y[i]) : 0;
            if (u != v) {
                return Integer.compare(u, v);
            }
        }
        return 0;
    }

    private static int number(String s) {
        int n = 0;
        for (int i = 0; i < s.length() && Character.isDigit(s.charAt(i)) && i < 6; i++) {
            n = n * 10 + (s.charAt(i) - '0');
        }
        return n;
    }

    // --- images

    /**
     * The PNG of a mapping's icon or background from the cache; starts the download (checked against its blob hash)
     * on first use and returns null until it is there.
     */
    byte[] image(Mapping m, boolean background) {
        String file = background ? m.background() : m.icon();
        String sha = background ? m.backgroundSha() : m.iconSha();
        if (file == null || sha == null || !SHA.matcher(sha).matches() || !file(m.dir(), file)) {
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
                    byte[] png = download(BASE_URL + file, IMAGE_MAX);
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

    /** Only {@code servers/<own id>/icon.png} or {@code background.png}: no other paths of the repository. */
    static boolean file(String dir, String path) {
        return path != null && (path.equals("servers/" + dir + "/icon.png") || path.equals("servers/" + dir + "/background.png"));
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

    // --- parsing

    /** The servers of a {@code servers.json}; entries with a bad id, no name or no address are left out. */
    static Map<String, Mapping> parseIndex(String json) {
        Map<String, Mapping> out = new LinkedHashMap<>();
        JsonElement root = Json.parse(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("servers")
                || !root.getAsJsonObject().get("servers").isJsonObject()) {
            return out;
        }
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().getAsJsonObject("servers").entrySet()) {
            if (!DIR.matcher(e.getKey()).matches() || !e.getValue().isJsonObject() || out.size() >= 5000) {
                continue;
            }
            try {
                Mapping m = parseServer(e.getKey(), e.getValue().getAsJsonObject());
                if (m != null) {
                    out.put(e.getKey(), m);
                }
            } catch (RuntimeException ignored) {
                // a broken entry is left out
            }
        }
        return out;
    }

    static Mapping parseServer(String dir, JsonObject o) {
        String name = str(o, "name").strip();
        List<String> addresses = strings(o.get("addresses"), 32);
        if (name.isEmpty() || addresses.isEmpty()) {
            return null;
        }
        if (name.length() > 48) {
            name = name.substring(0, 48);
        }
        String[] icon = picture(dir, o.get("icon"));
        String[] background = picture(dir, o.get("background"));
        List<Mode> modes = new ArrayList<>();
        if (o.has("gamemodes") && o.get("gamemodes").isJsonArray()) {
            for (JsonElement g : o.getAsJsonArray("gamemodes")) {
                if (!g.isJsonObject() || modes.size() >= 64) {
                    continue;
                }
                JsonObject go = g.getAsJsonObject();
                String mode = str(go, "name").strip();
                if (mode.isEmpty()) {
                    continue;
                }
                String c = str(go, "color");
                int color = COLOR.matcher(c).matches() ? Integer.parseInt(c.substring(1), 16) : -1;
                modes.add(new Mode(mode.length() > 40 ? mode.substring(0, 40) : mode, color, str(go, "versions")));
            }
        }
        return new Mapping(dir, name, addresses, strings(o.get("categories"), 16), icon[0], icon[1], background[0],
                background[1], List.copyOf(modes));
    }

    /** {@code {"file": "servers/<dir>/icon.png", "sha": "…"}} → path and hash, both null if missing or invalid. */
    private static String[] picture(String dir, JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return new String[2];
        }
        String file = str(e.getAsJsonObject(), "file");
        String sha = str(e.getAsJsonObject(), "sha");
        return file(dir, file) && SHA.matcher(sha).matches() ? new String[]{file, sha} : new String[2];
    }

    // --- refresh

    private void refresh() {
        try {
            byte[] data = download(INDEX_URL, INDEX_MAX);
            if (data == null) {
                retryLater();
                return;
            }
            String json = new String(data, StandardCharsets.UTF_8);
            Map<String, Mapping> next = parseIndex(json);
            if (next.isEmpty()) {
                retryLater();
                return;
            }
            Files.createDirectories(cache);
            AtomicFiles.write(cache.resolve("servers.json"), json);
            writeChecked(System.currentTimeMillis());
            mappings = Map.copyOf(next);
            lookups.clear();
        } catch (IOException | RuntimeException e) {
            retryLater();
        }
    }

    private void retryLater() {
        try {
            writeChecked(System.currentTimeMillis() - REFRESH_MS + RETRY_MS);
        } catch (IOException ignored) {
            // try again next start
        }
    }

    // --- cache files

    /** Loads the cached list; returns when it was last checked (0 if never). */
    long loadIndex() {
        try {
            Path list = cache.resolve("servers.json");
            if (Files.isRegularFile(list)) {
                mappings = Map.copyOf(parseIndex(Files.readString(list, StandardCharsets.UTF_8)));
                lookups.clear();
            }
            Path state = cache.resolve("state.json");
            if (!Files.isRegularFile(state)) {
                return 0;
            }
            JsonObject o = Json.parse(Files.readString(state, StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("checked") ? o.get("checked").getAsLong() : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    void writeChecked(long checked) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("source", "https://github.com/" + REPO + " (GPL-3.0)");
        o.addProperty("checked", checked);
        Files.createDirectories(cache);
        AtomicFiles.write(cache.resolve("state.json"), o.toString());
    }

    /** Deletes the caches of the two source repositories that 0.3.75/0.3.76 read directly. */
    void removeOldCaches() {
        Path config = cache.getParent();
        if (config == null) {
            return;
        }
        for (String name : OLD_CACHES) {
            Path old = config.resolve(name);
            if (!Files.isDirectory(old)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(old)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            } catch (IOException | RuntimeException ignored) {
                // tried again next start
            }
        }
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

    /** Whether a URL points into the mapping repository on raw.githubusercontent.com. */
    static boolean allowed(String url) {
        URI uri = URI.create(url);
        return "https".equals(uri.getScheme()) && "raw.githubusercontent.com".equals(uri.getHost())
                && uri.getRawPath() != null && uri.getRawPath().startsWith("/" + REPO + "/main/")
                && !uri.getRawPath().contains("..") && uri.getRawQuery() == null;
    }

    /** GET of a file of the mapping repository, at most {@code max} bytes; null on any error. */
    static byte[] download(String url, int max) throws IOException {
        if (!allowed(url)) {
            return null;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Lumora-Client").GET().build();
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

    private static List<String> strings(JsonElement e, int max) {
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) {
                if (x.isJsonPrimitive() && out.size() < max) {
                    String s = x.getAsString().strip();
                    if (!s.isEmpty() && s.length() <= 255) {
                        out.add(s);
                    }
                }
            }
        }
        return List.copyOf(out);
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
