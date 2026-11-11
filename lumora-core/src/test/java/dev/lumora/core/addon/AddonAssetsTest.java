package dev.lumora.core.addon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonAssetsTest {
    @TempDir
    Path tmp;

    private static void jar(Path file, Map<String, String> entries) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream o = Files.newOutputStream(file); ZipOutputStream z = new ZipOutputStream(o)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
    }

    private static final String META_2611 = "{\n  \"pack\": {\n    \"description\": \"Lumora Client\",\n    \"min_format\": 84,\n    \"max_format\": 84\n  }\n}";

    @Test
    void copiesTheAssetsOfAddonJarsWithThePackFormatOfTheClient() throws IOException {
        Path addons = tmp.resolve("addons");
        jar(addons.resolve("voice.jar"), Map.of(
                "lumora-addon.json", "{}",
                "assets/lumoravoice/textures/gui/bg.png", "png",
                "assets/lumoravoice/font/nametags.json", "{}",
                "dev/lumora/voice/Main.class", "x"));
        Path target = tmp.resolve(".lumora").resolve("addon-assets");
        List<String> warnings = new ArrayList<>();
        Path out = AddonAssets.prepare(addons, target, META_2611.getBytes(StandardCharsets.UTF_8), warnings::add);
        assertEquals(target, out);
        assertEquals("png", Files.readString(target.resolve("assets/lumoravoice/textures/gui/bg.png")));
        assertTrue(Files.isRegularFile(target.resolve("assets/lumoravoice/font/nametags.json")));
        assertFalse(Files.exists(target.resolve("dev")));
        String meta = Files.readString(target.resolve("pack.mcmeta"));
        assertTrue(meta.contains("\"description\": \"Lumora-Addons\""), meta);
        assertTrue(meta.contains("\"min_format\": 84"), meta);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void neverReplacesMinecraftOrLumoraAndSkipsJarsThatAreNoAddons() throws IOException {
        Path addons = tmp.resolve("addons");
        jar(addons.resolve("a.jar"), Map.of(
                "lumora-addon.json", "{}",
                "assets/minecraft/textures/block/stone.png", "evil",
                "assets/lumora/textures/gui/logo.png", "evil",
                "assets/realms/x.png", "evil",
                "assets/Bad Namespace/x.png", "evil",
                "assets/good/ok.png", "ok"));
        jar(addons.resolve("b-fabric-mod.jar"), Map.of("fabric.mod.json", "{}", "assets/other/x.png", "x"));
        Path target = tmp.resolve("out");
        AddonAssets.prepare(addons, target, null, w -> { });
        assertTrue(Files.isRegularFile(target.resolve("assets/good/ok.png")));
        assertFalse(Files.exists(target.resolve("assets/minecraft")));
        assertFalse(Files.exists(target.resolve("assets/lumora")));
        assertFalse(Files.exists(target.resolve("assets/realms")));
        assertFalse(Files.exists(target.resolve("assets/other")));
        assertTrue(Files.readString(target.resolve("pack.mcmeta")).contains("Lumora-Addons"));
    }

    @Test
    void rejectsOddPaths() {
        assertTrue(AddonAssets.allowed("assets/voice/textures/a.png"));
        assertFalse(AddonAssets.allowed("assets/voice/../minecraft/a.png"));
        assertFalse(AddonAssets.allowed("assets/voice/./a.png"));
        assertFalse(AddonAssets.allowed("assets/voice//a.png"));
        assertFalse(AddonAssets.allowed("assets/voice\\a.png"));
        assertFalse(AddonAssets.allowed("assets/voice"));
        assertFalse(AddonAssets.allowed("data/voice/a.json"));
        assertFalse(AddonAssets.allowed("assets/voice/a b.png"));
    }

    @Test
    void firstJarWinsAndNothingIsCopiedAgainWhileTheJarsStayTheSame() throws IOException {
        Path addons = tmp.resolve("addons");
        jar(addons.resolve("a.jar"), Map.of("lumora-addon.json", "{}", "assets/shared/x.txt", "from a"));
        jar(addons.resolve("b.jar"), Map.of("lumora-addon.json", "{}", "assets/shared/x.txt", "from b"));
        Path target = tmp.resolve("out");
        AddonAssets.prepare(addons, target, null, w -> { });
        assertEquals("from a", Files.readString(target.resolve("assets/shared/x.txt")));
        // unchanged jars: a file edited by hand stays (no second copy)
        Files.writeString(target.resolve("assets/shared/x.txt"), "edited");
        AddonAssets.prepare(addons, target, null, w -> { });
        assertEquals("edited", Files.readString(target.resolve("assets/shared/x.txt")));
        // a jar was removed: copied again from scratch
        Files.delete(addons.resolve("a.jar"));
        AddonAssets.prepare(addons, target, null, w -> { });
        assertEquals("from b", Files.readString(target.resolve("assets/shared/x.txt")));
    }

    @Test
    void anUpdateFromTheLibraryBringsTheNewLanguageFilesAtOnce() throws IOException {
        // the hub replaced LumoraVoice-0.8.3.jar while it was loaded: the old jar waits in .lumora-remove
        Path addons = tmp.resolve("addons");
        jar(addons.resolve("LumoraVoice-0.8.3.jar"), Map.of("lumora-addon.json", "{}", "assets/lumoravoice/lang/en_us.json", "{\"old\":\"x\"}"));
        jar(addons.resolve("LumoraVoice-0.8.4.jar"), Map.of("lumora-addon.json", "{}", "assets/lumoravoice/lang/en_us.json", "{\"new\":\"y\"}"));
        Files.writeString(addons.resolve(AddonLibrary.REMOVE_FILE), "LumoraVoice-0.8.3.jar\n");
        Path target = tmp.resolve("out");
        AddonAssets.prepare(addons, target, null, w -> { });
        assertEquals("{\"new\":\"y\"}", Files.readString(target.resolve("assets/lumoravoice/lang/en_us.json")));
        assertFalse(Files.exists(addons.resolve("LumoraVoice-0.8.3.jar")), "old copy deleted before the assets are taken");
        assertFalse(Files.exists(addons.resolve(AddonLibrary.REMOVE_FILE)));
    }

    @Test
    void noAddonsOrNoAssetsMeansNoPack() throws IOException {
        assertNull(AddonAssets.prepare(tmp.resolve("missing"), tmp.resolve("out1"), null, w -> { }));
        Path addons = tmp.resolve("addons");
        jar(addons.resolve("timer.jar"), Map.of("lumora-addon.json", "{}", "dev/x/Timer.class", "x"));
        assertNull(AddonAssets.prepare(addons, tmp.resolve("out2"), null, w -> { }));
        // an addon with assets added later: the next call prepares the pack
        jar(addons.resolve("voice.jar"), Map.of("lumora-addon.json", "{}", "assets/v/a.png", "p"));
        Files.setLastModifiedTime(addons.resolve("voice.jar"), FileTime.fromMillis(1_000_000));
        assertEquals(tmp.resolve("out2"), AddonAssets.prepare(addons, tmp.resolve("out2"), null, w -> { }));
    }

    @Test
    void aBrokenJarIsSkippedWithAWarning() throws IOException {
        Path addons = tmp.resolve("addons");
        Files.createDirectories(addons);
        Files.writeString(addons.resolve("broken.jar"), "no zip");
        jar(addons.resolve("ok.jar"), Map.of("lumora-addon.json", "{}", "assets/ok/a.png", "p"));
        List<String> warnings = new ArrayList<>();
        assertEquals(tmp.resolve("out"), AddonAssets.prepare(addons, tmp.resolve("out"), null, warnings::add));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("broken.jar"));
    }
}
