package dev.lumora.launcher.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetupHandoffTest {
    private static final List<String> KNOWN = List.of("26.3", "26.1.2", "1.21.11", "1.8.9");

    @TempDir
    Path tmp;

    private Path write(String json) throws Exception {
        Path config = tmp.resolve("config/launcher.json");
        Files.createDirectories(config.getParent());
        Files.writeString(SetupHandoff.file(config), json, StandardCharsets.UTF_8);
        return config;
    }

    @Test
    void takesOverChoicesAndDeletesTheFile() throws Exception {
        Path config = write("{\"dataDir\":\"D:\\\\Spiele\\\\Lumora\",\"profiles\":[\"1.8.9\",\"26.1.2\",\"nope\"],"
                + "\"defaultProfile\":\"26.1.2\",\"theme\":\"vanilla\",\"seed\":\"20261113-1\"}");
        LauncherConfig c = new LauncherConfig();
        assertTrue(SetupHandoff.apply(config, c, KNOWN));
        assertEquals("D:\\Spiele\\Lumora", c.installDir);
        assertEquals(List.of("1.8.9", "26.1.2"), c.visibleProfiles);
        assertEquals("26.1.2", c.selectedProfile);
        assertEquals("vanilla", c.gameTheme);
        assertEquals("20261113-1", c.gameThemeSeed);
        assertFalse(Files.exists(SetupHandoff.file(config)));
        assertFalse(SetupHandoff.apply(config, c, KNOWN));
    }

    @Test
    void allVersionsMeansNoFilterAndDefaultDirMeansNull() throws Exception {
        Path config = write("{\"dataDir\":\"\",\"profiles\":[\"26.3\",\"26.1.2\",\"1.21.11\",\"1.8.9\"],\"theme\":\"lumora\"}");
        LauncherConfig c = new LauncherConfig();
        c.installDir = "X:\\old";
        c.selectedProfile = "1.21.11";
        assertTrue(SetupHandoff.apply(config, c, KNOWN));
        assertNull(c.installDir);
        assertTrue(c.visibleProfiles.isEmpty());
        assertEquals("1.21.11", c.selectedProfile);
        assertEquals("lumora", c.gameTheme);
        assertTrue(c.gameThemeSeed.matches("[0-9]+"));
    }

    @Test
    void hiddenSelectionMovesToFirstChosenVersion() throws Exception {
        Path config = write("{\"profiles\":[\"1.21.11\",\"1.8.9\"],\"defaultProfile\":\"26.3\"}");
        LauncherConfig c = new LauncherConfig();
        c.selectedProfile = "26.1.2";
        SetupHandoff.apply(config, c, KNOWN);
        assertEquals("1.21.11", c.selectedProfile);
        assertNull(c.gameTheme);
    }

    @Test
    void invalidFileIsDroppedWithoutChanges() throws Exception {
        Path config = write("{kaputt");
        LauncherConfig c = new LauncherConfig();
        assertFalse(SetupHandoff.apply(config, c, KNOWN));
        assertFalse(Files.exists(SetupHandoff.file(config)));
        assertTrue(c.visibleProfiles.isEmpty());
    }

    @Test
    void validationKeepsTheSelectedVersionVisible() {
        LauncherConfig c = new LauncherConfig();
        c.selectedProfile = "26.1.2";
        c.visibleProfiles = new java.util.ArrayList<>(List.of("1.8.9", "gibt-es-nicht"));
        c.validateAndRepair(KNOWN, List.of(), "26.1.2");
        assertEquals(List.of("1.8.9"), c.visibleProfiles);
        assertEquals("1.8.9", c.selectedProfile);
    }
}
