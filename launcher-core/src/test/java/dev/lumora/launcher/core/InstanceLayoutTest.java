package dev.lumora.launcher.core;

import dev.lumora.launcher.core.config.LauncherConfig;
import dev.lumora.launcher.core.profile.Flavor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceLayoutTest {
    @TempDir
    Path tmp;
    LauncherPaths paths;
    LauncherConfig config;
    Path minecraft;

    @BeforeEach
    void setUp() {
        paths = new LauncherPaths(tmp.resolve("LumoraClient"));
        minecraft = tmp.resolve(".minecraft");
        config = new LauncherConfig();
        config.standardGameDir = minecraft.toString();
    }

    @Test
    void withoutChoiceAllVersionsShareTheStandardFolderAndTheLumoraSettings() {
        InstanceLayout a = InstanceLayout.of(paths, config, Flavor.LUMORA, "26.3");
        InstanceLayout b = InstanceLayout.of(paths, config, Flavor.LUMORA, "1.8.9");
        assertFalse(a.own());
        assertEquals(minecraft, a.gameDir());
        assertEquals(minecraft, b.gameDir());
        assertEquals(paths.root().resolve("data/shared/config"), a.configDir());
        assertEquals(a.configDir(), b.configDir(), "one HUD and the same module settings in every version");
        // what belongs to one version only stays apart (client jars, natives)
        assertEquals(paths.root().resolve("data/26.3/.lumora/client"), a.clientDir());
        assertEquals(paths.root().resolve("data/1.8.9/.natives"), b.nativesDir());
        // Vanilla plays in the standard folder too, like Minecraft itself
        assertEquals(minecraft, InstanceLayout.of(paths, config, Flavor.VANILLA, "26.3").gameDir());
    }

    @Test
    void ownInstanceKeepsEverythingInItsFolder() {
        config.instances.put("26.3", new LauncherConfig.InstanceChoice(true, false));
        InstanceLayout l = InstanceLayout.of(paths, config, "26.3");
        Path inst = paths.root().resolve("instances/26.3");
        assertEquals(inst, l.gameDir());
        assertEquals(inst.resolve("config"), l.configDir());
        assertEquals(inst.resolve("addons"), l.addonsDir());
        assertEquals(inst.resolve(".lumora/client"), l.clientDir());
        assertEquals(inst.resolve(".natives"), l.nativesDir());
    }

    @Test
    void settingsSharedOrOwnIndependentOfTheGameFolder() {
        config.instances.put("26.3", new LauncherConfig.InstanceChoice(true, true));
        config.instances.put("1.8.9", new LauncherConfig.InstanceChoice(false, false));
        InstanceLayout own = InstanceLayout.of(paths, config, "26.3");
        assertEquals(paths.root().resolve("instances/26.3"), own.gameDir());
        assertEquals(paths.root().resolve("data/shared/config"), own.configDir());
        InstanceLayout std = InstanceLayout.of(paths, config, "1.8.9");
        assertEquals(minecraft, std.gameDir());
        assertEquals(paths.root().resolve("data/1.8.9/config"), std.configDir());
    }

    @Test
    void existingInstancesStayOwnInstancesOnce() throws Exception {
        Files.createDirectories(paths.instance("26.3").resolve("saves"));
        Files.createDirectories(paths.instance("1.8.9"));
        assertTrue(InstanceLayout.adoptExisting(paths, config));
        assertEquals(paths.instance("26.3"), InstanceLayout.of(paths, config, "26.3").gameDir());
        assertEquals(paths.instance("26.3").resolve("config"), InstanceLayout.of(paths, config, "26.3").configDir(),
                "nothing changes for an existing instance, also not its settings");
        assertEquals(minecraft, InstanceLayout.of(paths, config, "1.21.11").gameDir(), "versions without a folder use the standard folder");
        // only once: a folder created later does not switch a version to an own instance
        Files.createDirectories(paths.instance("1.21.11"));
        assertFalse(InstanceLayout.adoptExisting(paths, config));
        assertEquals(minecraft, InstanceLayout.of(paths, config, "1.21.11").gameDir());
    }

    @Test
    void freshInstallationHasNothingToAdopt() {
        assertTrue(InstanceLayout.adoptExisting(paths, config));
        assertTrue(config.instances.isEmpty());
        assertEquals(1, config.instanceLayout);
    }

    @Test
    void settingsComeAlongIntoAnEmptyFolderButNeverOverwrite() throws Exception {
        config.instances.put("26.3", new LauncherConfig.InstanceChoice(true, false));
        InstanceLayout before = InstanceLayout.of(paths, config, "26.3");
        Files.createDirectories(before.configDir().resolve("lumora"));
        Files.writeString(before.configDir().resolve("lumora-hud.json"), "{\"hud\":1}");
        Files.writeString(before.configDir().resolve("lumora/addons.json"), "{}");
        Files.createDirectories(before.addonsDir());
        Files.writeString(before.addonsDir().resolve("spotify.jar"), "jar");

        config.instances.put("26.3", new LauncherConfig.InstanceChoice(false, true));
        InstanceLayout after = InstanceLayout.of(paths, config, "26.3");
        assertEquals(List.of("config", "addons"), InstanceLayout.carrySettings(before, after));
        assertEquals("{\"hud\":1}", Files.readString(after.configDir().resolve("lumora-hud.json")));
        assertTrue(Files.isRegularFile(after.configDir().resolve("lumora/addons.json")));
        assertTrue(Files.isRegularFile(after.addonsDir().resolve("spotify.jar")));
        assertTrue(Files.isRegularFile(before.configDir().resolve("lumora-hud.json")), "the old settings stay");

        // another version with its own settings switching to shared: the shared ones are kept
        config.instances.put("1.8.9", new LauncherConfig.InstanceChoice(false, false));
        InstanceLayout other = InstanceLayout.of(paths, config, "1.8.9");
        Files.createDirectories(other.configDir());
        Files.writeString(other.configDir().resolve("lumora-hud.json"), "{\"hud\":2}");
        config.instances.put("1.8.9", new LauncherConfig.InstanceChoice(false, true));
        assertEquals(List.of(), InstanceLayout.carrySettings(other, InstanceLayout.of(paths, config, "1.8.9")));
        assertEquals("{\"hud\":1}", Files.readString(after.configDir().resolve("lumora-hud.json")));
    }

    @Test
    void invalidInstanceIdsAreDroppedFromTheSettings() {
        config.instances.put("../evil", new LauncherConfig.InstanceChoice(true, false));
        config.instances.put("26.3", new LauncherConfig.InstanceChoice(true, false));
        config.instances.put("1.8.9", null);
        config.validateAndRepair(List.of("26.3", "1.8.9"), List.of(), "26.3");
        assertEquals(List.of("26.3"), List.copyOf(config.instances.keySet()));
    }

    @Test
    void resetKeepsTheLumoraSettingsAsBackup() throws Exception {
        Files.createDirectories(paths.versionData("shared").resolve("config/lumora"));
        Files.writeString(paths.versionData("shared").resolve("config/lumora-client.json"), "{}");
        Files.createDirectories(paths.instance("26.3").resolve("config"));
        Files.createDirectories(paths.instance("26.3").resolve("saves/Welt"));
        List<Path> kept = InstanceLayout.resetLumoraSettings(paths, java.time.Instant.parse("2026-11-14T12:00:00Z"));
        assertEquals(2, kept.size());
        assertFalse(Files.exists(paths.versionData("shared").resolve("config")));
        assertTrue(kept.stream().allMatch(p -> p.getFileName().toString().startsWith("config.backup-")));
        assertTrue(Files.isRegularFile(kept.stream().filter(p -> p.startsWith(paths.versionData("shared"))).findFirst().orElseThrow()
                .resolve("lumora-client.json")));
        assertTrue(Files.isDirectory(paths.instance("26.3").resolve("saves/Welt")), "worlds stay");
    }

    @Test
    void restoreAddsOnlyUnknownInstanceFolders() throws Exception {
        Files.createDirectories(paths.instance("26.3"));
        Files.createDirectories(paths.instance("1.8.9"));
        config.instances.put("26.3", new LauncherConfig.InstanceChoice(false, true));
        assertEquals(List.of("1.8.9"), InstanceLayout.restoreMissing(paths, config));
        assertTrue(config.instances.get("1.8.9").own);
        assertFalse(config.instances.get("26.3").own, "existing choices stay");
        assertEquals(List.of(), InstanceLayout.restoreMissing(paths, config));
    }
}
