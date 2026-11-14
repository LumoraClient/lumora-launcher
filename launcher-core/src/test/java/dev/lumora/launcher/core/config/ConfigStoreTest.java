package dev.lumora.launcher.core.config;

import dev.lumora.launcher.core.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
    @TempDir
    Path tmp;

    private ConfigStore store() {
        return new ConfigStore(tmp.resolve("config/launcher.json"), List.of("26.1.2", "1.21.11", "1.21.8"), List.of("modmenu", "lithium"), "26.1.2");
    }

    @Test
    void createsDefaultsWhenMissing() {
        ConfigStore.LoadResult r = store().load();
        assertFalse(r.recoveredFromCorruption());
        assertEquals("26.1.2", r.config().selectedProfile);
        assertEquals(4096, r.config().maxMemoryMb);
        assertTrue(Files.exists(tmp.resolve("config/launcher.json")), "defaults are persisted");
    }

    @Test
    void corruptFileIsBackedUpAndDefaultsRestored() throws Exception {
        Path file = tmp.resolve("config/launcher.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{ this is not json");
        ConfigStore.LoadResult r = store().load();
        assertTrue(r.recoveredFromCorruption());
        assertNotNull(r.backup());
        assertTrue(Files.exists(r.backup()));
        assertEquals("{ this is not json", Files.readString(r.backup()));
        assertEquals("26.1.2", r.config().selectedProfile);
        // the new file is valid again
        assertEquals("26.1.2", Json.read(file, LauncherConfig.class).selectedProfile);
    }

    @Test
    void invalidValuesAreRepaired() throws Exception {
        Path file = tmp.resolve("config/launcher.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                {"selectedProfile":"does-not-exist","maxMemoryMb":100,"minMemoryMb":99999,
                 "extraJvmArgs":["-Xmx64G","-XX:+UseZGC","","-cp foo"],
                 "enabledExtensions":["modmenu","modmenu","evil"],
                 "microsoftClientId":"not-a-guid","discordRichPresence":false,"windowWidth":5}
                """);
        ConfigStore.LoadResult r = store().load();
        LauncherConfig c = r.config();
        assertEquals("26.1.2", c.selectedProfile);
        assertEquals(LauncherConfig.MIN_MEMORY_MB, c.maxMemoryMb);
        assertTrue(c.minMemoryMb <= c.maxMemoryMb);
        assertEquals(List.of("-XX:+UseZGC"), c.extraJvmArgs);
        assertEquals(List.of("modmenu"), c.enabledExtensions);
        assertNull(c.windowWidth);
        assertFalse(r.repairs().isEmpty());
    }

    @Test
    void legacyRendererProfilesMapToTheNewVersionProfile() {
        for (String old : List.of("vulkan", "opengl")) {
            LauncherConfig c = new LauncherConfig();
            c.selectedProfile = old;
            List<String> notes = c.validateAndRepair(List.of("26.1.2", "1.21.11"), List.of(), "1.21.11");
            assertEquals("26.1.2", c.selectedProfile, old);
            assertTrue(notes.getFirst().contains("is now"), notes.toString());
        }
    }

    @Test
    void socialServerIsNormalisedAndPlainWebSocketOnlyOnTheLocalNetwork() {
        LauncherConfig c = new LauncherConfig();
        c.socialServer = " chat.example.com ";
        c.validateAndRepair(List.of("26.1.2"), List.of(), "26.1.2");
        assertEquals("wss://chat.example.com:443/", c.socialServer);
        c.socialServer = "192.168.0.10";
        c.validateAndRepair(List.of("26.1.2"), List.of(), "26.1.2");
        assertEquals("ws://192.168.0.10:25600/", c.socialServer);
        c.socialServer = "ws://chat.example.com";
        List<String> notes = c.validateAndRepair(List.of("26.1.2"), List.of(), "26.1.2");
        assertNull(c.socialServer);
        assertTrue(notes.stream().anyMatch(n -> n.startsWith("socialServer invalid")), notes.toString());
        assertTrue(new LauncherConfig().socialShareServer);
    }

    @Test
    void saveAndReloadRoundTrip() throws Exception {
        ConfigStore s = store();
        LauncherConfig c = s.load().config();
        c.selectedProfile = "1.21.11";
        c.maxMemoryMb = 6144;
        c.enabledExtensions.add("lithium");
        s.save(c);
        LauncherConfig again = s.load().config();
        assertEquals("1.21.11", again.selectedProfile);
        assertEquals(6144, again.maxMemoryMb);
        assertEquals(List.of("lithium"), again.enabledExtensions);
    }
}
