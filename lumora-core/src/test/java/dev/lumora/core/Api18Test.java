package dev.lumora.core;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.render.DynamicTextures;
import dev.lumora.api.render.TextureRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lumora API 1.8: textures an addon makes at runtime. */
class Api18Test {
    @TempDir
    Path dir;
    private LumoraCore core;
    private FakePlatform platform;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    private AddonContext start(String id) throws Exception {
        platform = new FakePlatform(dir);
        core = new LumoraCore(platform);
        AddonContext[] ctx = new AddonContext[1];
        var addon = new AddonLifecycleTest.TestAddon(c -> ctx[0] = c);
        var json = AddonLifecycleTest.json(id, "\"permissions\":[]").replace("\"api\":\"1.0.0\"", "\"api\":\"1.8.0\"");
        var d = dev.lumora.core.addon.AddonDescriptorParser.parse(json, "test.jar");
        core.loadAddons(List.of(dev.lumora.core.addon.AddonCandidate.of(d, () -> addon)));
        assertEquals(AddonState.ENABLED, core.addonManager().record(id).orElseThrow().state());
        return ctx[0];
    }

    @Test
    void uploadsUnderTheAddonsOwnNameAndFreesThemWhenSwitchedOff() throws Exception {
        AddonContext ctx = start("spotify");
        int[] px = {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF};
        TextureRef ref = ctx.textures().upload("cover", 2, 2, px);
        assertNotNull(ref);
        assertEquals("lumora", ref.namespace());
        assertEquals("addon/spotify/cover", ref.path());
        assertArrayEquals(px, platform.textures.get("addon/spotify/cover"));

        // replacing keeps one texture
        ctx.textures().upload("cover", 2, 2, new int[]{1, 2, 3, 4});
        assertEquals(1, platform.textures.size());
        ctx.textures().upload("cover-old", 1, 1, new int[]{5});
        ctx.textures().release("cover-old");
        assertEquals(1, platform.textures.size());

        core.addonManager().setWanted("spotify", false);
        assertTrue(platform.textures.isEmpty(), "freed with the addon");
    }

    @Test
    void checksNamesSizesAndTheLimit() throws Exception {
        AddonContext ctx = start("pics");
        DynamicTextures t = ctx.textures();
        assertThrows(IllegalArgumentException.class, () -> t.upload("Cover!", 1, 1, new int[1]));
        assertThrows(IllegalArgumentException.class, () -> t.upload("big", DynamicTextures.MAX_SIZE + 1, 1, new int[DynamicTextures.MAX_SIZE + 1]));
        assertThrows(IllegalArgumentException.class, () -> t.upload("few", 2, 2, new int[3]));
        for (int i = 0; i < DynamicTextures.MAX_TEXTURES; i++) {
            assertNotNull(t.upload("t" + i, 1, 1, new int[1]));
        }
        assertNull(t.upload("one-more", 1, 1, new int[1]), "limit per addon");
        assertNotNull(t.upload("t0", 1, 1, new int[1]), "replacing an own texture still works");
    }

    @Test
    void nullWhereTheVersionCannotMakeTextures() throws Exception {
        AddonContext ctx = start("pics");
        platform.textureSupport = false;
        assertNull(ctx.textures().upload("cover", 1, 1, new int[1]));
        assertEquals("1.8.0", core.apiVersion());
    }
}
