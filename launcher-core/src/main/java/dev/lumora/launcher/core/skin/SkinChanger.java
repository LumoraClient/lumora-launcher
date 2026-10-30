package dev.lumora.launcher.core.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.launcher.core.download.UserAgent;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;
import dev.lumora.launcher.core.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Changes the signed-in account's skin and cape through the Minecraft profile API of api.minecraftservices.com (the
 * endpoints the official launcher uses): read the profile, upload a skin (multipart PNG + model), reset it, choose or
 * hide a cape. The access token only goes to that host in the Authorization header and is never logged.
 * Also keeps a local skin library (PNG files with a name and model) in the data directory.
 */
public final class SkinChanger {
    public static final String API = "https://api.minecraftservices.com/minecraft/profile";
    /** Mojang accepts 64x64 (and old 64x32) PNGs up to 24 KB. */
    public static final int MAX_PNG = 24 * 1024;
    private static final Pattern ID = Pattern.compile("[0-9a-fA-F-]{8,64}");

    public record Texture(String id, String state, String url, String variant, String alias) {
        public boolean active() {
            return "ACTIVE".equalsIgnoreCase(state);
        }
    }

    public record Profile(String name, List<Texture> skins, List<Texture> capes) {
        public Texture activeSkin() {
            return skins.stream().filter(Texture::active).findFirst().orElse(null);
        }

        public Texture activeCape() {
            return capes.stream().filter(Texture::active).findFirst().orElse(null);
        }
    }

    /** A skin of the local library. */
    public record Saved(String id, String name, boolean slim, Path file) {
    }

    private final HttpClient http;
    private final String api;
    private final Path library;

    public SkinChanger(Path dataRoot) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build(),
                API, dataRoot.resolve("skins").resolve("library"));
    }

    SkinChanger(HttpClient http, String api, Path library) {
        this.http = http;
        this.api = api;
        this.library = library;
    }

    public Profile profile(String token) throws IOException {
        return parseProfile(send(token, HttpRequest.newBuilder(URI.create(api)).GET()));
    }

    /** Uploads a skin PNG; returns the updated profile. */
    public Profile upload(String token, byte[] png, boolean slim) throws IOException {
        checkSkin(png);
        String boundary = "lumora-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        part(body, boundary, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic"));
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.writeBytes(png);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return parseProfile(send(token, HttpRequest.newBuilder(URI.create(api + "/skins"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))));
    }

    /** Back to the default skin (Steve/Alex as Mojang assigns it). */
    public Profile resetSkin(String token) throws IOException {
        return parseProfile(send(token, HttpRequest.newBuilder(URI.create(api + "/skins/active")).DELETE()));
    }

    /** Shows one of the owned capes, or hides the cape with {@code capeId == null}. */
    public Profile setCape(String token, String capeId) throws IOException {
        if (capeId == null) {
            return parseProfile(send(token, HttpRequest.newBuilder(URI.create(api + "/capes/active")).DELETE()));
        }
        if (!ID.matcher(capeId).matches()) {
            throw new IllegalArgumentException("invalid cape id");
        }
        JsonObject o = new JsonObject();
        o.addProperty("capeId", capeId);
        return parseProfile(send(token, HttpRequest.newBuilder(URI.create(api + "/capes/active"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(o.toString()))));
    }

    private String send(String token, HttpRequest.Builder b) throws IOException {
        if (token == null || token.isBlank()) {
            throw new IOException("Nicht angemeldet");
        }
        HttpRequest req = b.timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                .header("User-Agent", UserAgent.VALUE).header("Accept", "application/json").build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 200) {
                return resp.body();
            }
            Log.warn("Minecraft profile API " + req.method() + " " + req.uri().getPath() + ": HTTP " + code);
            throw new IOException(switch (code) {
                case 400 -> "Mojang hat die Datei abgelehnt (kein gültiger Skin).";
                case 401, 403 -> "Die Anmeldung ist abgelaufen. Bitte einmal neu anmelden.";
                case 404 -> "Für dieses Konto gibt es kein Minecraft-Profil.";
                case 429 -> "Zu viele Änderungen in kurzer Zeit. Bitte in ein paar Minuten noch einmal.";
                default -> "Mojang antwortet mit HTTP " + code + ".";
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("abgebrochen");
        }
    }

    private static void part(ByteArrayOutputStream out, String boundary, String content) {
        out.writeBytes(("--" + boundary + "\r\n" + content + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    static Profile parseProfile(String json) throws IOException {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return new Profile(o.has("name") ? o.get("name").getAsString() : "", textures(o, "skins"), textures(o, "capes"));
        } catch (RuntimeException e) {
            throw new IOException("Unerwartete Antwort von Mojang: " + e.getMessage());
        }
    }

    private static List<Texture> textures(JsonObject o, String key) {
        List<Texture> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (JsonElement e : a) {
                JsonObject t = e.getAsJsonObject();
                String url = s(t, "url");
                if (url != null && !url.startsWith("https://textures.minecraft.net/")) {
                    url = url.replaceFirst("^http://textures\\.minecraft\\.net/", "https://textures.minecraft.net/");
                    if (!url.startsWith("https://textures.minecraft.net/")) {
                        url = null;
                    }
                }
                out.add(new Texture(s(t, "id"), s(t, "state"), url, s(t, "variant"), s(t, "alias")));
            }
        }
        return out;
    }

    private static String s(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    /** A Minecraft skin: PNG signature and 64x64 or 64x32 pixels (read from the IHDR chunk), at most {@link #MAX_PNG}. */
    public static void checkSkin(byte[] png) {
        if (png == null || png.length < 33 || png.length > MAX_PNG) {
            throw new IllegalArgumentException("Ein Skin ist eine PNG-Datei bis 24 KB.");
        }
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        for (int i = 0; i < sig.length; i++) {
            if (png[i] != sig[i]) {
                throw new IllegalArgumentException("Die Datei ist kein PNG-Bild.");
            }
        }
        int w = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
        int h = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
        if (w != 64 || (h != 64 && h != 32)) {
            throw new IllegalArgumentException("Ein Skin muss 64×64 (oder 64×32) Pixel groß sein, diese Datei hat " + w + "×" + h + ".");
        }
    }

    // ------------------------------------------------------------------ local library

    static final class Index {
        List<Entry> skins = new ArrayList<>();
    }

    static final class Entry {
        String id;
        String name;
        boolean slim;
    }

    public List<Saved> library() {
        Index idx = readIndex();
        List<Saved> out = new ArrayList<>();
        for (Entry e : idx.skins) {
            Path f = library.resolve(e.id + ".png");
            if (Files.isRegularFile(f)) {
                out.add(new Saved(e.id, e.name, e.slim, f));
            }
        }
        return out;
    }

    public Saved save(String name, byte[] png, boolean slim) throws IOException {
        checkSkin(png);
        String clean = name == null || name.isBlank() ? "Skin" : name.strip().replaceAll("[\\p{Cntrl}]", "");
        if (clean.length() > 40) {
            clean = clean.substring(0, 40);
        }
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
        Files.createDirectories(library);
        FileUtil.writeAtomically(library.resolve(id + ".png"), png);
        Index idx = readIndex();
        Entry e = new Entry();
        e.id = id;
        e.name = clean;
        e.slim = slim;
        idx.skins.add(0, e);
        Json.write(library.resolve("library.json"), idx);
        return new Saved(id, clean, slim, library.resolve(id + ".png"));
    }

    public void delete(String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{12}")) {
            return;
        }
        Index idx = readIndex();
        idx.skins.removeIf(e -> id.equals(e.id));
        Json.write(library.resolve("library.json"), idx);
        Files.deleteIfExists(library.resolve(id + ".png"));
    }

    private Index readIndex() {
        Path f = library.resolve("library.json");
        if (!Files.isRegularFile(f)) {
            return new Index();
        }
        try {
            Index i = Json.read(f, Index.class);
            if (i == null || i.skins == null) {
                return new Index();
            }
            i.skins.removeIf(e -> e == null || e.id == null || !e.id.matches("[0-9a-f]{12}"));
            return i;
        } catch (IOException | RuntimeException e) {
            return new Index();
        }
    }
}
