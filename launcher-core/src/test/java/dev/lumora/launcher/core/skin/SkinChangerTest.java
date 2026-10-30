package dev.lumora.launcher.core.skin;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinChangerTest {
    @TempDir
    Path tmp;
    HttpServer server;
    String base;
    final List<String> calls = new ArrayList<>();
    volatile String lastBody = "";
    volatile String lastAuth;

    static final String PROFILE = """
            {"id":"abc","name":"CuzImMax",
             "skins":[{"id":"s1","state":"ACTIVE","url":"http://textures.minecraft.net/texture/aa","variant":"SLIM"}],
             "capes":[{"id":"2340c0e0-3d11-4c1b-a5b1-0b7d6c5f9e21","state":"INACTIVE","url":"https://textures.minecraft.net/texture/bb","alias":"Migrator"},
                      {"id":"c-2","state":"ACTIVE","url":"https://evil.example/x.png","alias":"Pan"}]}""";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort() + "/minecraft/profile";
        server.createContext("/", ex -> {
            calls.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
            lastAuth = ex.getRequestHeaders().getFirst("Authorization");
            lastBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            byte[] b = PROFILE.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    SkinChanger changer() {
        return new SkinChanger(HttpClient.newHttpClient(), base, tmp.resolve("lib"));
    }

    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void profileUploadResetAndCapes() throws IOException {
        SkinChanger s = changer();
        SkinChanger.Profile p = s.profile("tok");
        assertEquals("Bearer tok", lastAuth);
        assertEquals("CuzImMax", p.name());
        assertEquals("https://textures.minecraft.net/texture/aa", p.activeSkin().url(), "http upgraded to https");
        assertNull(p.activeCape().url(), "only textures.minecraft.net images are shown");
        s.upload("tok", png(64, 64), true);
        assertEquals("POST /minecraft/profile/skins", calls.getLast());
        assertTrue(lastBody.contains("name=\"variant\"") && lastBody.contains("slim"));
        assertTrue(lastBody.contains("filename=\"skin.png\"") && lastBody.contains("PNG"));
        s.resetSkin("tok");
        assertEquals("DELETE /minecraft/profile/skins/active", calls.getLast());
        s.setCape("tok", "2340c0e0-3d11-4c1b-a5b1-0b7d6c5f9e21");
        assertEquals("PUT /minecraft/profile/capes/active", calls.getLast());
        assertTrue(lastBody.contains("\"capeId\":\"2340c0e0-3d11-4c1b-a5b1-0b7d6c5f9e21\""));
        s.setCape("tok", null);
        assertEquals("DELETE /minecraft/profile/capes/active", calls.getLast());
        assertThrows(IllegalArgumentException.class, () -> s.setCape("tok", "\"}{evil"));
    }

    @Test
    void onlyRealSkinFiles() throws IOException {
        SkinChanger.checkSkin(png(64, 64));
        SkinChanger.checkSkin(png(64, 32));
        assertThrows(IllegalArgumentException.class, () -> SkinChanger.checkSkin(png(128, 128)));
        assertThrows(IllegalArgumentException.class, () -> SkinChanger.checkSkin("not a png at all, just text...".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> changer().upload("tok", png(10, 10), false));
        assertTrue(calls.isEmpty(), "invalid files never reach Mojang");
    }

    @Test
    void library() throws IOException {
        SkinChanger s = changer();
        SkinChanger.Saved a = s.save("PvP", png(64, 64), false);
        s.save("Alex-Variante", png(64, 64), true);
        assertEquals(List.of("Alex-Variante", "PvP"), s.library().stream().map(SkinChanger.Saved::name).toList());
        s.delete(a.id());
        assertEquals(1, s.library().size());
        s.delete("../../etc");
        assertEquals(1, s.library().size());
    }
}
