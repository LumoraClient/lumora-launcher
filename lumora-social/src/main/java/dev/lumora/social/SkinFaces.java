package dev.lumora.social;

import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 8x8 skin faces (face plus hat layer, ARGB) of players, loaded from Mojang's official profile service
 * (sessionserver.mojang.com, then the texture from textures.minecraft.net). Only the public profile UUID is sent.
 * {@link #face} returns null until the face is loaded (callers draw a placeholder and redraw next frame). The game draws
 * the 64 pixels as small rectangles, so no texture API of any Minecraft version is needed.
 */
public final class SkinFaces {
    private static final int[] NONE = new int[0];
    private static final Map<String, int[]> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "lumora-skin-faces");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    private SkinFaces() {
    }

    /** 64 ARGB pixels (row by row) or null while loading / when the player has no skin. */
    public static int[] face(String uuid) {
        if (uuid == null) {
            return null;
        }
        int[] f = CACHE.get(uuid);
        if (f == null) {
            if (CACHE.putIfAbsent(uuid, NONE) == null) {
                LOADER.execute(() -> {
                    int[] loaded = load(uuid);
                    CACHE.put(uuid, loaded != null ? loaded : NONE);
                });
            }
            return null;
        }
        return f.length == 64 ? f : null;
    }

    static int[] load(String uuid) {
        try {
            HttpResponse<String> profile = HTTP.send(HttpRequest.newBuilder(
                            URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
                    .timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
            if (profile.statusCode() != 200) {
                return null;
            }
            JsonObject p = Js.object(profile.body());
            String value = p.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            JsonObject tex = Js.object(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8));
            String url = tex.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
            URI skinUri = URI.create(url);
            if (!"textures.minecraft.net".equals(skinUri.getHost())) {
                return null;
            }
            HttpResponse<byte[]> skin = HTTP.send(HttpRequest.newBuilder(skinUri).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            BufferedImage s = ImageIO.read(new ByteArrayInputStream(skin.body()));
            return s == null || s.getWidth() < 64 ? null : crop(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** Face (8,8) with the hat layer (40,8) on top where it is not transparent. */
    static int[] crop(BufferedImage s) {
        int[] out = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int base = s.getRGB(8 + x, 8 + y) | 0xFF000000;
                int hat = s.getRGB(40 + x, 8 + y);
                out[y * 8 + x] = (hat >>> 24) > 0x80 ? hat | 0xFF000000 : base;
            }
        }
        return out;
    }
}
