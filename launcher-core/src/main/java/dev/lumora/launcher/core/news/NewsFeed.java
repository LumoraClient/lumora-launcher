package dev.lumora.launcher.core.news;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * News and changelog for the launcher's "Neuigkeiten" page:
 * <ul>
 *   <li>Lumora: news and changelog shipped with the launcher ({@code /lumora/news.json}),</li>
 *   <li>Minecraft: Java Edition news and patch notes from Mojang's launcher content service
 *       ({@code launchercontent.mojang.com/v2/news.json}, {@code javaPatchNotes.json}), the same public files the
 *       official launcher shows. Only that host is contacted, without redirects, with size limits.</li>
 * </ul>
 * Downloaded files are cached in {@code cacheDir}, so the page also works offline with the last state.
 */
public final class NewsFeed {
    public enum Source { LUMORA, MINECRAFT }

    /** One news card. {@code image} is an https URL on the content host, or {@code bundled:<name>} (resource). */
    public record Item(Source source, String id, String title, String text, LocalDate date, String image, String link,
                       String author) {
    }

    /** One changelog entry: Minecraft patch notes (HTML body loaded on demand) or a Lumora release (bullet list). */
    public record Change(Source source, String id, String title, String version, String type, LocalDate date,
                         String image, String contentUrl, List<String> items) {
        public boolean snapshot() {
            return "snapshot".equals(type);
        }
    }

    static final String HOST = "launchercontent.mojang.com";
    private static final String BASE = "https://" + HOST;
    private static final Set<String> LINK_HOSTS = Set.of("www.minecraft.net", "minecraft.net", "help.minecraft.net",
            "feedback.minecraft.net");
    private static final int MAX_JSON = 4 * 1024 * 1024;
    private static final int MAX_IMAGE = 4 * 1024 * 1024;

    private final Path cacheDir;
    private final HttpClient http;

    public NewsFeed(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    // ------------------------------------------------------------------ Lumora (bundled)

    private static JsonObject bundled() {
        try (InputStream in = NewsFeed.class.getResourceAsStream("/lumora/news.json")) {
            if (in == null) {
                return new JsonObject();
            }
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            Log.warn("Bundled news.json unreadable: " + e);
            return new JsonObject();
        }
    }

    public static List<Item> lumoraNews() {
        List<Item> out = new ArrayList<>();
        for (JsonElement e : array(bundled(), "news")) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Item(Source.LUMORA, str(o, "id"), str(o, "title"), str(o, "text"), date(str(o, "date")),
                    o.has("image") ? "bundled:" + str(o, "image") : null, null, str(o, "author")));
        }
        return out;
    }

    public static List<Change> lumoraChangelog() {
        List<Change> out = new ArrayList<>();
        for (JsonElement e : array(bundled(), "changelog")) {
            JsonObject o = e.getAsJsonObject();
            List<String> items = new ArrayList<>();
            for (JsonElement i : array(o, "items")) {
                items.add(i.getAsString());
            }
            out.add(new Change(Source.LUMORA, "lumora-" + str(o, "version"), str(o, "title"), str(o, "version"), "lumora",
                    date(str(o, "date")), null, null, items));
        }
        return out;
    }

    // ------------------------------------------------------------------ Minecraft (Mojang launcher content)

    /** Java Edition news, newest first (cached copy when offline; empty if never loaded). */
    public List<Item> minecraftNews() {
        String json = fetchCached("/v2/news.json", "news.json");
        return json == null ? List.of() : parseNews(json);
    }

    /** Java patch notes, newest first. */
    public List<Change> minecraftPatchNotes() {
        String json = fetchCached("/v2/javaPatchNotes.json", "javaPatchNotes.json");
        return json == null ? List.of() : parsePatchNotes(json);
    }

    static List<Item> parseNews(String json) {
        List<Item> out = new ArrayList<>();
        for (JsonElement e : array(JsonParser.parseString(json).getAsJsonObject(), "entries")) {
            JsonObject o = e.getAsJsonObject();
            boolean java = false;
            for (JsonElement t : array(o, "newsType")) {
                java |= "Java".equals(t.getAsString());
            }
            if (!java && !"Minecraft: Java Edition".equals(str(o, "category"))) {
                continue;
            }
            JsonObject img = o.has("newsPageImage") && o.get("newsPageImage").isJsonObject() ? o.getAsJsonObject("newsPageImage")
                    : o.has("playPageImage") && o.get("playPageImage").isJsonObject() ? o.getAsJsonObject("playPageImage") : null;
            String link = str(o, "readMoreLink");
            out.add(new Item(Source.MINECRAFT, str(o, "id").isEmpty() ? str(o, "title") : str(o, "id"), str(o, "title"),
                    str(o, "text"), date(str(o, "date")), img == null ? null : imageUrl(str(img, "url")), safeLink(link),
                    str(o, "category")));
        }
        out.sort(Comparator.comparing(Item::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    static List<Change> parsePatchNotes(String json) {
        List<Change> out = new ArrayList<>();
        for (JsonElement e : array(JsonParser.parseString(json).getAsJsonObject(), "entries")) {
            JsonObject o = e.getAsJsonObject();
            String content = str(o, "contentPath");
            if (!content.matches("javaPatchNotes/[A-Za-z0-9._-]+\\.json")) {
                content = null;
            }
            JsonObject img = o.has("image") && o.get("image").isJsonObject() ? o.getAsJsonObject("image") : null;
            out.add(new Change(Source.MINECRAFT, str(o, "id"), str(o, "title"), str(o, "version"),
                    str(o, "type").toLowerCase(Locale.ROOT), date(str(o, "date")), img == null ? null : imageUrl(str(img, "url")),
                    content == null ? null : BASE + "/v2/" + content, List.of()));
        }
        out.sort(Comparator.comparing(Change::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /** HTML body of a patch note (from the content host only). */
    public String patchNotesHtml(Change c) throws IOException {
        if (c.contentUrl() == null) {
            return null;
        }
        URI u = URI.create(c.contentUrl());
        String name = "pn-" + u.getPath().replaceAll("[^A-Za-z0-9._-]", "_");
        String json = fetchCached(u.getPath(), name);
        if (json == null) {
            throw new IOException("Patch Notes nicht erreichbar");
        }
        return str(JsonParser.parseString(json).getAsJsonObject(), "body");
    }

    /** Image bytes: {@code bundled:<name>} from the launcher, else the content host only (cached on disk); or null. */
    public byte[] image(String url) {
        if (url != null && url.startsWith("bundled:")) {
            String name = url.substring("bundled:".length());
            if (!name.matches("[a-z0-9-]+\\.png")) {
                return null;
            }
            try (InputStream in = NewsFeed.class.getResourceAsStream("/lumora/news/" + name)) {
                return in == null ? null : in.readAllBytes();
            } catch (IOException e) {
                return null;
            }
        }
        if (url == null || !url.startsWith(BASE + "/")) {
            return null;
        }
        try {
            Path f = cacheDir.resolve("img-" + sha1(url));
            if (Files.isRegularFile(f)) {
                return Files.readAllBytes(f);
            }
            byte[] b = fetch(URI.create(url), MAX_IMAGE);
            write(f, b);
            return b;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    static String imageUrl(String path) {
        if (path == null || !path.startsWith("/v2/images/") || path.contains("..")) {
            return null;
        }
        return BASE + path.replace(" ", "%20");
    }

    /** Read-more links only to minecraft.net (https), else null. */
    static String safeLink(String link) {
        try {
            URI u = URI.create(link);
            return "https".equals(u.getScheme()) && LINK_HOSTS.contains(u.getHost()) ? link : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String fetchCached(String path, String cacheName) {
        Path f = cacheDir.resolve(cacheName);
        try {
            byte[] b = fetch(URI.create(BASE + path), MAX_JSON);
            String s = new String(b, StandardCharsets.UTF_8);
            JsonParser.parseString(s); // only valid JSON replaces the cache
            write(f, b);
            return s;
        } catch (IOException | RuntimeException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.info("Launcher content " + path + " not loaded (" + e.getClass().getSimpleName() + "), using cache");
            try {
                return Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8) : null;
            } catch (IOException ex) {
                return null;
            }
        }
    }

    private byte[] fetch(URI uri, int max) throws IOException, InterruptedException {
        if (!HOST.equals(uri.getHost()) || !"https".equals(uri.getScheme())) {
            throw new IOException("host not allowed: " + uri.getHost());
        }
        HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "LumoraLauncher").build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            if (res.statusCode() != 200) {
                throw new IOException("HTTP " + res.statusCode());
            }
            byte[] b = in.readNBytes(max + 1);
            if (b.length > max) {
                throw new IOException("too large");
            }
            return b;
        }
    }

    private void write(Path f, byte[] b) throws IOException {
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.write(tmp, b);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static JsonArray array(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    static LocalDate date(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return s.length() > 10 ? OffsetDateTime.parse(s).toLocalDate() : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
