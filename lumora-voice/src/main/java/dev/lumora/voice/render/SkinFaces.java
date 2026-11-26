package dev.lumora.voice.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lumora.api.render.HudGraphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;

/**
 * Player heads for the speaker list: the 8x8 face of the skin with the hat layer, loaded once per player from Mojang's
 * official profile service (sessionserver.mojang.com, then the texture from textures.minecraft.net; only the public
 * profile UUID is sent). The 64 pixels are drawn as small rectangles, so no texture API of any Minecraft version is
 * needed. Until a face is loaded, and for offline-mode UUIDs (not version 4, Mojang has no skin for them), a colored
 * square with the first letter of the name is drawn.
 */
public final class SkinFaces {
    private static final int[] NONE = new int[0];
    private static final Map<UUID, int[]> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "lumoravoice-skin-faces");
        t.setDaemon(true);
        return t;
    });
    private static volatile HttpClient http;

    private SkinFaces() { }

    /** 64 ARGB pixels (row by row), or null while loading and when the player has no Mojang skin. */
    public static int[] face(UUID id) {
        if (id == null || id.version() != 4) return null;
        int[] face = CACHE.get(id);
        if (face == null) {
            if (CACHE.putIfAbsent(id, NONE) == null) {
                LOADER.execute(() -> {
                    int[] loaded = load(id);
                    CACHE.put(id, loaded != null ? loaded : NONE);
                });
            }
            return null;
        }
        return face.length == 64 ? face : null;
    }

    /** Draws the head at ({@code x}, {@code y}), {@code size} pixels wide and high. */
    public static void draw(HudGraphics g, UUID id, String name, int x, int y, int size) {
        int[] px = face(id);
        if (px == null) {
            int hash = id == null ? 0 : id.hashCode();
            g.fill(x, y, x + size, y + size, 0xFF000000 | (hash & 0x7F7F7F) | 0x303030);
            String letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            g.text(letter, x + (size - g.textWidth(letter)) / 2 + 1, y + (size - 8) / 2, 0xFFFFFFFF, false);
            return;
        }
        for (int row = 0; row < 8; row++) {
            int y0 = y + row * size / 8, y1 = y + (row + 1) * size / 8;
            int col = 0;
            while (col < 8) {
                int color = px[row * 8 + col], end = col + 1;
                while (end < 8 && px[row * 8 + end] == color) end++;
                g.fill(x + col * size / 8, y0, x + end * size / 8, y1, color);
                col = end;
            }
        }
    }

    private static HttpClient http() {
        var client = http;
        if (client == null) {
            client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();
            http = client;
        }
        return client;
    }

    static int[] load(UUID id) {
        try {
            String plain = id.toString().replace("-", "");
            HttpResponse<String> profile = http().send(HttpRequest.newBuilder(
                    URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + plain))
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
            if (profile.statusCode() != 200) return null;
            URI skin = skinUri(profile.body());
            if (skin == null) return null;
            HttpResponse<byte[]> png = http().send(HttpRequest.newBuilder(skin).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (png.statusCode() != 200) return null;
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png.body()));
            return image == null || image.getWidth() < 64 || image.getHeight() < 32 ? null : crop(image);
        } catch (Exception | LinkageError failed) {
            return null;
        }
    }

    /** The skin URL of a session server profile, only from textures.minecraft.net; null otherwise. */
    static URI skinUri(String profileJson) {
        try {
            JsonObject p = new JsonParser().parse(profileJson).getAsJsonObject();
            String value = p.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            JsonObject textures = new JsonParser().parse(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)).getAsJsonObject();
            String url = textures.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
            URI uri = URI.create(url);
            return "textures.minecraft.net".equals(uri.getHost()) ? uri : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /** Face (8,8) with the hat layer (40,8) on top where it is not transparent. */
    static int[] crop(BufferedImage skin) {
        int[] out = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = skin.getRGB(8 + x, 8 + y) | 0xFF000000;
                int hat = skin.getRGB(40 + x, 8 + y);
                out[y * 8 + x] = (hat >>> 24) > 0x80 ? hat | 0xFF000000 : base;
            }
        }
        return out;
    }
}
