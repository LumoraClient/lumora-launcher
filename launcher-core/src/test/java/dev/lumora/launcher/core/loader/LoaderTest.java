package dev.lumora.launcher.core.loader;

import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.install.ProgressListener;
import dev.lumora.launcher.core.mojang.VersionJson;
import dev.lumora.launcher.core.profile.Flavor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderTest {
    @Test
    void mavenMetadataIsReadWithoutXmlParserAndSortedNumerically() {
        String xml = "<metadata><versioning><versions><version>47.4.9</version><version>47.4.10</version>"
                + "<version>47.5.0-beta</version><version>../evil</version><version>47.4.10-rc1</version></versions></versioning></metadata>";
        List<String> v = new ArrayList<>(MavenMetadata.versions(xml));
        assertEquals(List.of("47.4.9", "47.4.10", "47.5.0-beta", "47.4.10-rc1"), v, "values with / are skipped");
        MavenMetadata.sortNewestFirst(v);
        assertEquals(List.of("47.5.0-beta", "47.4.10", "47.4.10-rc1", "47.4.9"), v);
        assertTrue(MavenMetadata.isPreRelease("26.1.0.0-alpha.1+snapshot-1"));
        assertFalse(MavenMetadata.isPreRelease("21.4.161"));
    }

    @Test
    void forgeVersionsOfOneMinecraftVersionWithRecommendation() {
        List<String> maven = List.of("1.20.1-47.4.28", "1.20.1-47.4.10", "1.20.1-47.4.9", "1.20.10-99.0.0",
                "1.8.9-11.15.1.2318-1.8.9", "1.20.1-47.1.0-weird_branch");
        List<LoaderVersion> list = ForgeModLoader.list(maven, "1.20.1", "47.4.10");
        assertEquals(List.of("47.4.28", "47.4.10", "47.4.9"), list.stream().map(LoaderVersion::label).toList(),
                "other Minecraft versions with the same prefix and odd branch names are left out");
        assertEquals("1.20.1-47.4.10", list.stream().filter(LoaderVersion::recommended).findFirst().orElseThrow().version());
        assertEquals("11.15.1.2318", ForgeModLoader.label("1.8.9-11.15.1.2318-1.8.9", "1.8.9"));
        // no promotion: the newest is recommended
        assertTrue(ForgeModLoader.list(maven, "1.20.1", null).getFirst().recommended());
    }

    @Test
    void neoForgeMapsMinecraftVersionsToItsScheme() {
        assertEquals("21.4.", NeoForgeModLoader.prefix("1.21.4"));
        assertEquals("21.0.", NeoForgeModLoader.prefix("1.21"));
        assertEquals("20.2.", NeoForgeModLoader.prefix("1.20.2"));
        assertNull(NeoForgeModLoader.prefix("1.20.1"), "1.20.1 uses the old artifact");
        assertNull(NeoForgeModLoader.prefix("1.12.2"));
        assertEquals("26.3.0.", NeoForgeModLoader.prefix("26.3"));
        assertEquals("26.1.2.", NeoForgeModLoader.prefix("26.1.2"));
        List<LoaderVersion> l = NeoForgeModLoader.list(List.of("21.4.150-beta", "21.4.161", "21.4.9", "21.40.1", "21.4.170-beta"), "21.4.");
        assertEquals(List.of("21.4.170-beta", "21.4.161", "21.4.150-beta", "21.4.9"), l.stream().map(LoaderVersion::version).toList());
        assertEquals("21.4.161", l.stream().filter(LoaderVersion::recommended).findFirst().orElseThrow().version(),
                "the newest stable build is recommended, not a beta");
        NeoForgeModLoader neo = new NeoForgeModLoader(null, Path.of("lib"), Path.of("ver"));
        assertTrue(neo.supports("1.20.1"));
        assertFalse(neo.supports("1.16.5"));
    }

    @Test
    void fabricMetaAndLegacyVersions() throws IOException {
        String body = "[{\"loader\":{\"version\":\"0.17.3\",\"stable\":true}},{\"loader\":{\"version\":\"0.17.4+beta\",\"stable\":false}},"
                + "{\"loader\":{\"version\":\"0.17.2\",\"stable\":true}}]";
        List<LoaderVersion> l = FabricModLoader.parse(body, "loader");
        assertEquals(3, l.size());
        assertTrue(l.getFirst().recommended());
        assertFalse(l.get(1).stable());
        assertFalse(l.get(2).recommended(), "only one recommendation");
        assertThrows(IOException.class, () -> FabricModLoader.parse("{\"not\":\"a list\"}", "loader"));
        assertTrue(FabricModLoader.legacy("1.8.9"));
        assertTrue(FabricModLoader.legacy("1.12.2"));
        assertFalse(FabricModLoader.legacy("1.14.4"));
        assertFalse(FabricModLoader.legacy("26.3"));
        assertFalse(new QuiltModLoader(null, Path.of("v")).supports("1.8.9"));
    }

    @Test
    void installerPlaceholdersAndCoordinatesResolveInsideTheLibraries(@TempDir Path dir) throws IOException {
        ForgeInstaller fi = new ForgeInstaller(null, dir.resolve("libraries"), dir.resolve("versions"));
        Map<String, String> data = Map.of("SIDE", "client", "MC_SLIM", dir.resolve("slim.jar").toString(), "PATCHED_SHA", "'abc'");
        assertEquals("client", fi.resolve("{SIDE}", data));
        assertEquals(dir.resolve("slim.jar") + "/x", fi.resolve("{MC_SLIM}/x", data));
        assertEquals("abc", fi.resolve("{PATCHED_SHA}", data), "quotes of literals are removed");
        assertEquals(dir.resolve("libraries/net/minecraftforge/forge/1.20.1-47.4.28/forge-1.20.1-47.4.28-client.jar").toAbsolutePath().toString(),
                fi.resolve("[net.minecraftforge:forge:1.20.1-47.4.28:client]", data));
        assertThrows(IOException.class, () -> fi.resolve("{UNKNOWN}", data));
        assertThrows(IllegalArgumentException.class, () -> fi.resolve("[../../x:y:z]", data));
    }

    /** A loader that "installs" by writing a profile, for the manager tests. */
    private static final class FakeLoader implements ModLoader {
        @Override public String id() { return "fake"; }
        @Override public String displayName() { return "Fake"; }
        @Override public String description() { return ""; }
        @Override public List<LoaderVersion> versions(String mc) {
            return List.of(new LoaderVersion("2.0", "2.0", true, true), new LoaderVersion("1.0", "1.0", true, false));
        }
        @Override public String profileId(String mc, String v) { return "fake-" + v + "-" + mc; }
        @Override public VersionJson install(Install r, ProgressListener p) { return new VersionJson(); }
    }

    @Test
    void managerPinsRecommendsRecordsUpdatesAndRemoves(@TempDir Path dir) throws IOException {
        LoaderManager m = new LoaderManager(new ModLoaders(List.of(new FakeLoader())), dir);
        LauncherConfig c = new LauncherConfig();
        assertEquals("2.0", m.selected(c, "fake", "1.21.4", null), "follows the recommendation without a pin");
        assertEquals("0.9", m.selected(c, "fake", "1.21.4", "0.9"), "a tested version wins over the recommendation");
        c.loaderVersions.put(LoaderManager.key("fake", "1.21.4"), "1.0");
        assertEquals("1.0", m.selected(c, "fake", "1.21.4", "0.9"), "a pin wins over everything");

        m.recordInstalled("fake", "1.21.4", "1.0");
        assertTrue(m.isInstalled("fake", "1.21.4", "1.0"));
        assertFalse(m.isInstalled("fake", "26.3", "1.0"));
        assertTrue(m.update(c, "fake", "1.21.4").isEmpty(), "pinned: no update offered");
        c.loaderVersions.clear();
        assertEquals("2.0", m.update(c, "fake", "1.21.4").orElseThrow().version());

        m.uninstall("fake", "1.21.4", "1.0");
        assertFalse(m.isInstalled("fake", "1.21.4", "1.0"));
        assertFalse(Files.exists(dir.resolve("fake-1.0-1.21.4")));
        assertThrows(IOException.class, () -> m.uninstall("fake", "1.21.4", "1.0"));
        assertThrows(IllegalArgumentException.class, () -> new ModLoaders(List.of(new FakeLoader(), new FakeLoader())));
    }

    @Test
    void flavorsKnowTheirLoaderAndOwnFolder() {
        assertEquals("forge", Flavor.FORGE.loaderId());
        assertNull(Flavor.LUMORA.loaderId());
        assertEquals(Flavor.NEOFORGE, Flavor.ofLoader("neoforge"));
        assertEquals("1.21.4-quilt", Flavor.QUILT.instanceId("1.21.4"));
        assertEquals(Flavor.LUMORA, Flavor.of("bogus"));
    }

    @Test
    void configMigratesTheOldVioletAndCleansLoaderSettings() {
        LauncherConfig c = new LauncherConfig();
        c.schemaVersion = 1;
        c.accentColor = "#8B5CF6";
        c.selectedFlavor = "neoforge";
        c.loaderVersions.put("forge@1.20.1", "1.20.1-47.4.28");
        c.loaderVersions.put("forge@1.20.1;rm -rf", "x");
        c.loaderVersions.put("quilt@1.21.4", "bad version with spaces");
        c.loaderJvmArgs.put("forge", "-Dfoo=1");
        c.loaderJvmArgs.put("neoforge", "-Da=1\n-Db=2");
        c.validateAndRepair(List.of("1.20.1"), List.of(), "1.20.1");
        assertEquals(LauncherConfig.DEFAULT_ACCENT, c.accentColor);
        assertEquals("neoforge", c.selectedFlavor);
        assertEquals(Map.of("forge@1.20.1", "1.20.1-47.4.28"), c.loaderVersions);
        assertEquals(Map.of("forge", "-Dfoo=1"), c.loaderJvmArgs);
        // a violet chosen after the redesign stays
        c.accentColor = "#8B5CF6";
        c.validateAndRepair(List.of("1.20.1"), List.of(), "1.20.1");
        assertEquals("#8B5CF6", c.accentColor);
    }
}
