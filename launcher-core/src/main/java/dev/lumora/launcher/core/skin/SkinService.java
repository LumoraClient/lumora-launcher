package dev.lumora.launcher.core.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.lumora.launcher.core.download.Downloader;
import dev.lumora.launcher.core.util.FileUtil;
import dev.lumora.launcher.core.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Skins from Mojang's public profile services (the same data every client sees in game): the textures property
 * of sessionserver.mojang.com/session/minecraft/profile/&lt;uuid&gt;, the PNG from textures.minecraft.net. A player
 * name is resolved via api.mojang.com. Skins are cached for a day. No token is sent anywhere.
 */
public final class SkinService {
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Duration CACHE = Duration.ofHours(24);
    private static final int MAX_SKIN = 256 * 1024;

    /** @param slim Alex-style arms (3 px) */
    public record Skin(String uuid, String name, byte[] png, boolean slim, boolean fallback) {
    }

    private final Downloader downloader;
    private final Path cacheDir;

    public SkinService(Downloader downloader, Path cacheDir) {
        this.downloader = downloader;
        this.cacheDir = cacheDir;
    }

    public Skin byName(String name) throws IOException {
        if (name == null || !NAME.matcher(name.strip()).matches()) {
            throw new IOException("Ungültiger Spielername");
        }
        String body = downloader.fetchString(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name.strip()));
        JsonObject o = Json.GSON.fromJson(body, JsonObject.class);
        if (o == null || !o.has("id")) {
            throw new IOException("Spieler „" + name + "“ nicht gefunden");
        }
        return byUuid(o.get("id").getAsString());
    }

    public Skin byUuid(String uuid) throws IOException {
        String id = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        if (!UUID.matcher(id).matches()) {
            throw new IOException("Ungültige UUID");
        }
        Path png = FileUtil.resolveInside(cacheDir, id + ".png");
        Path meta = FileUtil.resolveInside(cacheDir, id + ".json");
        if (Files.isRegularFile(png) && Files.isRegularFile(meta)
                && Files.getLastModifiedTime(png).toInstant().isAfter(Instant.now().minus(CACHE))) {
            Cached c = Json.read(meta, Cached.class);
            return new Skin(id, c.name, Files.readAllBytes(png), c.slim, false);
        }
        String body = downloader.fetchString(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id));
        Textures t = parseProfile(body);
        if (t.url == null) {
            return new Skin(id, t.name, null, t.slim, true);
        }
        byte[] data = downloader.fetchBytes(URI.create(t.url), MAX_SKIN);
        Files.createDirectories(cacheDir);
        FileUtil.writeAtomically(png, data);
        Cached c = new Cached();
        c.name = t.name;
        c.slim = t.slim;
        Json.write(meta, c);
        return new Skin(id, t.name, data, t.slim, false);
    }

    static final class Cached {
        String name;
        boolean slim;
    }

    record Textures(String name, String url, boolean slim) {
    }

    /** Reads name, skin URL (forced to https) and model from a session-server profile. */
    static Textures parseProfile(String body) throws IOException {
        try {
            JsonObject o = Json.GSON.fromJson(body, JsonObject.class);
            String name = o.get("name").getAsString();
            JsonArray props = o.getAsJsonArray("properties");
            for (JsonElement p : props) {
                JsonObject po = p.getAsJsonObject();
                if (!"textures".equals(po.get("name").getAsString())) {
                    continue;
                }
                String json = new String(Base64.getDecoder().decode(po.get("value").getAsString()), StandardCharsets.UTF_8);
                JsonObject tex = Json.GSON.fromJson(json, JsonObject.class).getAsJsonObject("textures");
                if (tex == null || !tex.has("SKIN")) {
                    return new Textures(name, null, false);
                }
                JsonObject skin = tex.getAsJsonObject("SKIN");
                String url = skin.get("url").getAsString().replaceFirst("^http://", "https://");
                boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                        && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                if (!url.startsWith("https://textures.minecraft.net/")) {
                    throw new IOException("unexpected skin host");
                }
                return new Textures(name, url, slim);
            }
            return new Textures(name, null, false);
        } catch (JsonParseException | IllegalStateException | NullPointerException | IllegalArgumentException | UnsupportedOperationException e) {
            throw new IOException("Unerwartete Profil-Antwort: " + e.getMessage(), e);
        }
    }

    /** Default skin (Steve / Alex) from an installed Minecraft client jar, if available. */
    public static byte[] defaultSkin(Path clientJar, boolean slim) {
        if (clientJar == null || !Files.isRegularFile(clientJar)) {
            return null;
        }
        try (ZipFile z = new ZipFile(clientJar.toFile())) {
            for (String path : slim
                    ? new String[]{"assets/minecraft/textures/entity/player/slim/alex.png", "assets/minecraft/textures/entity/alex.png"}
                    : new String[]{"assets/minecraft/textures/entity/player/wide/steve.png", "assets/minecraft/textures/entity/steve.png"}) {
                ZipEntry e = z.getEntry(path);
                if (e != null) {
                    try (InputStream in = z.getInputStream(e)) {
                        return in.readNBytes(MAX_SKIN);
                    }
                }
            }
        } catch (IOException ignored) {
            // no default skin then
        }
        return null;
    }
}
