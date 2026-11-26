package dev.lumora.voice.render;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Player heads of the speaker list without network access. */
class SkinFacesTest {
    private static String profile(String skinUrl) {
        String textures = "{\"textures\":{\"SKIN\":{\"url\":\"" + skinUrl + "\"}}}";
        String value = Base64.getEncoder().encodeToString(textures.getBytes(StandardCharsets.UTF_8));
        return "{\"id\":\"x\",\"name\":\"Steve\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + value + "\"}]}";
    }

    @Test void cropTakesTheFaceWithTheHatLayerOnTop() {
        var skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) skin.setRGB(x, y, 0xFF112233);
        skin.setRGB(40, 8, 0xFFAA0000); // hat pixel over the top left of the face
        skin.setRGB(41, 8, 0x40FFFFFF); // almost transparent hat pixel: the face shows
        int[] face = SkinFaces.crop(skin);
        assertEquals(64, face.length);
        assertEquals(0xFFAA0000, face[0]);
        assertEquals(0xFF112233, face[1]);
        assertEquals(0xFF112233, face[63]);
    }

    @Test void skinsComeOnlyFromMojangsTextureServer() {
        assertEquals("https://textures.minecraft.net/texture/abc",
                SkinFaces.skinUri(profile("http://textures.minecraft.net/texture/abc")).toString(), "http becomes https");
        assertNull(SkinFaces.skinUri(profile("https://evil.example/texture/abc")));
        assertNull(SkinFaces.skinUri("{broken"));
        assertNull(SkinFaces.skinUri("{\"properties\":[]}"));
    }

    @Test void offlineUuidsNeverAskMojang() {
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8)); // version 3
        assertEquals(3, offline.version());
        assertNull(SkinFaces.face(offline));
        assertNull(SkinFaces.face(null));
    }
}
