package dev.lumora.core.browser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Game modes of known servers from the public repository <a href="https://github.com/LabyMod/server-media">
 * LabyMod/server-media</a> ({@code minecraft_servers/<server>/manifest.json}): nice name, addresses and the game modes
 * with their colour and supported versions. The repository has no licence, so Lumora ships none of it: the player's
 * client reads only these text fields straight from GitHub (the git tree at most every 12 hours, then the changed
 * manifests, each checked against its git blob hash) and caches them in {@code config/lumora/server-gamemodes}. No
 * pictures are loaded.
 */
final class ServerGamemodes {
    static final String REPO = "LabyMod/server-media";
    static final String TREE_URL = "https://api.github.com/repos/" + REPO + "/git/trees/master?recursive=1";
    static final String RAW_URL = "https://raw.githubusercontent.com/" + REPO + "/master/minecraft_servers/";
    static final int TREE_MAX = 8 * 1024 * 1024;
    static final int MANIFEST_MAX = 256 * 1024;
    static final Pattern DIR = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
    static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final ExecutorService POOL = Icons.pool("Lumora server game modes", 2);
    private static volatile ServerGamemodes instance;

    /** One game mode: name, colour (RGB, -1 if none) and the version range ({@code "1.8<1.12.2,1.20<*"}, "" = all). */
    record Mode(String name, int color, String versions) {
    }

    /** A server: folder, nice name, direct address, wildcard domains ({@code %.example.net} → {@code example.net}). */
    record Server(String dir, String name, String address, List<String> domains, List<Mode> modes) {
    }

    private final Path cache;
    private volatile Map<String, Server> servers = Map.of();
    private final Map<String, Server> lookups = new ConcurrentHashMap<>();
    private volatile boolean started;

    ServerGamemodes(Path cache) {
        this.cache = cache;
    }

    static ServerGamemodes get(Path configDir) {
        ServerGamemodes g = instance;
        if (g == null || !g.cache.equals(configDir.resolve("server-gamemodes"))) {
            g = new ServerGamemodes(configDir.resolve("server-gamemodes"));
            instance = g;
        }
        return g;
    }

    /** Loads the cache and refreshes it in the background when it is old; once per game start. */
    void start() {
        if (started) {
            return;
        }
        started = true;
        POOL.execute(() -> {
            long checked = loadIndex();
            if (System.currentTimeMillis() - checked > ServerMappings.REFRESH_MS) {
                refresh();
            }
        });
    }

    // --- lookup

    /** The server for an address, null if unknown. */
    Server find(String address) {
        String host = ServerMappings.host(address);
        if (host.isEmpty()) {
            return null;
        }
        Server cached = lookups.get(host);
        if (cached != null) {
            return cached.dir().isEmpty() ? null : cached;
        }
        Server found = match(servers, host);
        lookups.put(host, found == null ? new Server("", "", "", List.of(), List.of()) : found);
        return found;
    }

    /** The direct address first, then the longest wildcard domain the host belongs to (or equals). */
    static Server match(Map<String, Server> all, String host) {
        Server best = null;
        int length = -1;
        for (Server s : all.values()) {
            if (ServerMappings.host(s.address()).equals(host)) {
                return s;
            }
            for (String d : s.domains()) {
                if ((host.equals(d) || host.endsWith("." + d)) && d.length() > length) {
                    length = d.length();
                    best = s;
                }
            }
        }
        return best;
    }

    /** The game modes that support this Minecraft version (all if the version is unknown). */
    static List<Mode> modesFor(Server s, String minecraftVersion) {
        List<Mode> out = new ArrayList<>();
        for (Mode m : s.modes()) {
            if (supports(m.versions(), minecraftVersion)) {
                out.add(m);
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

    // --- parsing

    /** Folder → blob hash of its manifest.json from a tree response. */
    static Map<String, String> parseTree(String json) {
        Map<String, String> out = new LinkedHashMap<>();
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
            String[] parts = path.split("/");
            if ("blob".equals(str(o, "type")) && parts.length == 3 && parts[0].equals("minecraft_servers")
                    && parts[2].equals("manifest.json") && DIR.matcher(parts[1]).matches()
                    && ServerMappings.SHA.matcher(sha).matches()) {
                out.put(parts[1], sha);
            }
        }
        return out;
    }

    /** A manifest; null without nice name and address. Only names, addresses and game modes are read. */
    static Server parseManifest(String dir, String json) {
        JsonElement root = Json.parse(json);
        if (!root.isJsonObject()) {
            return null;
        }
        JsonObject o = root.getAsJsonObject();
        String name = str(o, "nice_name").strip();
        String address = str(o, "direct_ip").strip();
        if (name.isEmpty() || address.isEmpty()) {
            return null;
        }
        List<String> domains = new ArrayList<>();
        if (o.has("server_wildcards") && o.get("server_wildcards").isJsonArray()) {
            for (JsonElement w : o.getAsJsonArray("server_wildcards")) {
                if (w.isJsonPrimitive() && domains.size() < 16) {
                    String d = w.getAsString().strip().toLowerCase(Locale.ROOT);
                    if (d.startsWith("%.")) {
                        d = d.substring(2);
                    }
                    if (!d.isEmpty() && !d.contains("%") && d.contains(".") && d.length() <= 255) {
                        domains.add(d);
                    }
                }
            }
        }
        List<Mode> modes = new ArrayList<>();
        if (o.has("gamemodes") && o.get("gamemodes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("gamemodes").entrySet()) {
                if (!e.getValue().isJsonObject() || modes.size() >= 64) {
                    continue;
                }
                JsonObject g = e.getValue().getAsJsonObject();
                String mode = str(g, "name").strip();
                if (mode.isEmpty()) {
                    continue;
                }
                String c = str(g, "color");
                int color = COLOR.matcher(c).matches() ? Integer.parseInt(c.substring(1), 16) : -1;
                modes.add(new Mode(mode.length() > 40 ? mode.substring(0, 40) : mode, color, str(g, "versions")));
            }
        }
        return new Server(dir, name.length() > 48 ? name.substring(0, 48) : name, address, List.copyOf(domains),
                List.copyOf(modes));
    }

    // --- refresh and cache

    private void refresh() {
        try {
            byte[] tree = ServerMappings.download(TREE_URL, TREE_MAX);
            Map<String, String> folders = tree == null ? Map.of() : parseTree(new String(tree, StandardCharsets.UTF_8));
            if (folders.isEmpty()) {
                retryLater();
                return;
            }
            Map<String, String> oldShas = readShas();
            Map<String, Server> next = new LinkedHashMap<>();
            Map<String, String> nextShas = new HashMap<>();
            for (Map.Entry<String, String> f : folders.entrySet()) {
                Server old = servers.get(f.getKey());
                if (old != null && f.getValue().equals(oldShas.get(f.getKey()))) {
                    next.put(f.getKey(), old);
                    nextShas.put(f.getKey(), f.getValue());
                    continue;
                }
                byte[] data = ServerMappings.download(RAW_URL + f.getKey() + "/manifest.json", MANIFEST_MAX);
                if (data == null || !f.getValue().equals(ServerMappings.blobSha(data))) {
                    if (old != null) {
                        next.put(f.getKey(), old);
                        nextShas.put(f.getKey(), oldShas.get(f.getKey()));
                    }
                    continue;
                }
                try {
                    Server s = parseManifest(f.getKey(), new String(data, StandardCharsets.UTF_8));
                    if (s != null) {
                        next.put(f.getKey(), s);
                        nextShas.put(f.getKey(), f.getValue());
                    }
                } catch (RuntimeException e) {
                    // a broken manifest is left out
                }
            }
            servers = Map.copyOf(next);
            lookups.clear();
            saveIndex(System.currentTimeMillis(), next, nextShas);
        } catch (IOException | RuntimeException e) {
            retryLater();
        }
    }

    private void retryLater() {
        try {
            saveIndex(System.currentTimeMillis() - ServerMappings.REFRESH_MS + ServerMappings.RETRY_MS, servers, readShas());
        } catch (IOException ignored) {
            // next start
        }
    }

    private Path index() {
        return cache.resolve("index.json");
    }

    long loadIndex() {
        try {
            if (!Files.isRegularFile(index())) {
                return 0;
            }
            JsonObject root = Json.parse(Files.readString(index(), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Server> out = new LinkedHashMap<>();
            JsonObject all = root.has("servers") ? root.getAsJsonObject("servers") : new JsonObject();
            for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                if (!DIR.matcher(e.getKey()).matches() || !e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                List<String> domains = new ArrayList<>();
                if (o.has("domains") && o.get("domains").isJsonArray()) {
                    for (JsonElement d : o.getAsJsonArray("domains")) {
                        domains.add(d.getAsString());
                    }
                }
                List<Mode> modes = new ArrayList<>();
                if (o.has("modes") && o.get("modes").isJsonArray()) {
                    for (JsonElement m : o.getAsJsonArray("modes")) {
                        JsonObject mo = m.getAsJsonObject();
                        modes.add(new Mode(str(mo, "name"), mo.has("color") ? mo.get("color").getAsInt() : -1, str(mo, "versions")));
                    }
                }
                out.put(e.getKey(), new Server(e.getKey(), str(o, "name"), str(o, "address"), List.copyOf(domains), List.copyOf(modes)));
            }
            servers = Map.copyOf(out);
            lookups.clear();
            return root.has("checked") ? root.get("checked").getAsLong() : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private Map<String, String> readShas() {
        Map<String, String> out = new HashMap<>();
        try {
            if (Files.isRegularFile(index())) {
                JsonObject root = Json.parse(Files.readString(index(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("servers")) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("servers").entrySet()) {
                        if (e.getValue().isJsonObject()) {
                            out.put(e.getKey(), str(e.getValue().getAsJsonObject(), "manifestSha"));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // no index yet
        }
        return out;
    }

    void saveIndex(long checked, Map<String, Server> all, Map<String, String> shas) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("source", "https://github.com/" + REPO + " (no licence: only read on this computer)");
        root.addProperty("checked", checked);
        JsonObject out = new JsonObject();
        for (Server s : all.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s.name());
            o.addProperty("address", s.address());
            JsonArray d = new JsonArray();
            for (String x : s.domains()) {
                d.add(new JsonPrimitive(x));
            }
            o.add("domains", d);
            JsonArray m = new JsonArray();
            for (Mode x : s.modes()) {
                JsonObject mo = new JsonObject();
                mo.addProperty("name", x.name());
                mo.addProperty("color", x.color());
                mo.addProperty("versions", x.versions());
                m.add(mo);
            }
            o.add("modes", m);
            o.addProperty("manifestSha", shas.get(s.dir()));
            out.add(s.dir(), o);
        }
        root.add("servers", out);
        Files.createDirectories(cache);
        AtomicFiles.write(index(), root.toString());
    }

    void set(Map<String, Server> all) {
        servers = Map.copyOf(all);
        lookups.clear();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }
}
