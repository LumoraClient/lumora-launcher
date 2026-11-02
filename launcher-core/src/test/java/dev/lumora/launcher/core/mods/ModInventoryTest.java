package dev.lumora.launcher.core.mods;

import dev.lumora.launcher.core.profile.ProfileCatalog;
import dev.lumora.launcher.core.stats.PlayStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModInventoryTest {
    @TempDir
    Path tmp;

    static final byte[] ICON = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    static Path jar(Path file, String json, boolean withIcon) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (withIcon) {
                zip.putNextEntry(new ZipEntry("assets/x/icon.png"));
                zip.write(ICON);
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test
    void readsDisplayMetadataAndIcon() throws IOException {
        Path j = jar(tmp.resolve("x.jar"), """
                {"schemaVersion":1,"id":"x","version":"1.2","name":"X Mod","description":"Does X",
                 "authors":["Alice",{"name":"Bob"}],"license":["MIT","Apache-2.0"],
                 "contact":{"homepage":"https://example.org","sources":"https://example.org/src"},
                 "icon":{"16":"assets/x/small.png","128":"assets/x/icon.png"},"jars":[{"file":"a.jar"},{"file":"b.jar"}]}""", true);
        ModInfo i = ModInfo.read(j);
        assertEquals("X Mod", i.name());
        assertEquals(List.of("Alice", "Bob"), i.authors());
        assertEquals("MIT, Apache-2.0", i.license());
        assertEquals(2, i.bundledModules());
        assertArrayEquals(ICON, i.icon());
        assertEquals("https://example.org/src", i.sources());
    }

    @Test
    void iconPathTraversalIsIgnored() throws IOException {
        Path j = jar(tmp.resolve("y.jar"), "{\"id\":\"y\",\"version\":\"1\",\"icon\":\"../../etc/passwd\"}", false);
        assertNull(ModInfo.read(j).icon());
        assertEquals("y", ModInfo.read(j).name(), "name falls back to the id");
    }

    @Test
    void inventoryListsCoreExtensionsAndUserModsAndTogglesUserMods() throws IOException {
        ProfileCatalog c = ProfileCatalog.loadBundled();
        // a Fabric profile with Sodium and Lithium (skipped once every version runs on the Lumora Loader)
        var fabric = c.profiles().stream().filter(x -> !x.lumoraLoader() && c.profileArtifact(x, "sodium").isPresent()
                && c.profileArtifact(x, "lithium").isPresent()).findFirst();
        org.junit.jupiter.api.Assumptions.assumeTrue(fabric.isPresent(), "no Fabric profile left");
        ProfileCatalog.Profile p = fabric.get();
        Path mods = tmp.resolve("instances/" + p.id + "/mods");
        // sodium installed, the rest not yet; one user mod and one disabled user mod
        jar(mods.resolve(c.profileArtifact(p, "sodium").orElseThrow().fileName), "{\"id\":\"sodium\",\"version\":\"0.7.3\",\"name\":\"Sodium\"}", false);
        jar(mods.resolve("journeymap.jar"), "{\"id\":\"journeymap\",\"version\":\"6\",\"name\":\"JourneyMap\"}", false);
        jar(mods.resolve("zoom.jar.disabled"), "{\"id\":\"zoom\",\"version\":\"1\",\"name\":\"Zoom\"}", false);
        Files.writeString(mods.resolve("readme.txt"), "not a mod");

        List<ModInventory.Entry> all = ModInventory.scan(c, p, List.of("lithium"), mods, a -> null);
        assertEquals(p.mods.size(), all.stream().filter(e -> e.kind() == ModInventory.Kind.CORE).count());
        assertTrue(all.stream().anyMatch(e -> e.kind() == ModInventory.Kind.EXTENSION));
        assertEquals(c.resolvePacks(p, List.of()).size(), all.stream().filter(e -> e.kind() == ModInventory.Kind.RESOURCE_PACK).count());
        assertTrue(all.stream().filter(e -> e.kind() == ModInventory.Kind.DEPENDENCY).allMatch(ModInventory.Entry::enabled),
                "helper mods are loaded because their packs are on by default");
        List<ModInventory.Entry> user = all.stream().filter(e -> e.kind() == ModInventory.Kind.USER).toList();
        assertEquals(List.of("JourneyMap", "Zoom"), user.stream().map(ModInventory.Entry::displayName).toList());
        assertTrue(user.get(0).enabled());
        assertFalse(user.get(1).enabled());

        ModInventory.Entry sodium = all.stream().filter(e -> e.artifact() != null && e.artifact().key().equals("sodium")).findFirst().orElseThrow();
        assertTrue(sodium.installed());
        assertEquals("0.7.3", sodium.version());
        ModInventory.Entry lithium = all.stream().filter(e -> e.artifact() != null && e.artifact().key().equals("lithium")).findFirst().orElseThrow();
        assertTrue(lithium.enabled());
        assertFalse(lithium.installed(), "downloaded on the next start");

        Path off = ModInventory.setUserModEnabled(user.get(0).file(), false);
        assertEquals("journeymap.jar.disabled", off.getFileName().toString());
        Path on = ModInventory.setUserModEnabled(off, true);
        assertEquals("journeymap.jar", on.getFileName().toString());
        jar(mods.resolve("zoom.jar"), "{\"id\":\"zoom\",\"version\":\"2\"}", false);
        assertThrows(IOException.class, () -> ModInventory.setUserModEnabled(mods.resolve("zoom.jar.disabled"), true));
    }

    @Test
    void playStatsAccumulateAndFormat() {
        PlayStats s = new PlayStats(tmp.resolve("config/playstats.json"));
        Instant t0 = Instant.parse("2026-10-18T10:00:00Z");
        s.record("1.21.11", t0, t0.plus(Duration.ofMinutes(75)));
        s.record("1.21.11", t0.plus(Duration.ofHours(3)), t0.plus(Duration.ofHours(3)).minusSeconds(5));
        PlayStats reloaded = new PlayStats(tmp.resolve("config/playstats.json"));
        PlayStats.Entry e = reloaded.get("1.21.11");
        assertEquals(75 * 60, e.seconds, "negative session counts as 0");
        assertEquals(2, e.launches);
        assertEquals("1 h 15 min", PlayStats.format(e.playTime()));
        assertEquals(0, reloaded.get("26.1.2").launches);
        assertEquals("gestern", PlayStats.ago(t0, t0.plus(Duration.ofHours(30))));
        assertEquals("nie", PlayStats.ago(null, t0));
    }
}
