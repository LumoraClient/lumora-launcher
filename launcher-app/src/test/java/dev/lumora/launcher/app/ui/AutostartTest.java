package dev.lumora.launcher.app.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only the reg.exe command lines (the registry itself is not touched by tests). */
class AutostartTest {
    @Test
    void runKeyOfTheCurrentUserOnlyWithTrayArgument() {
        Path exe = Path.of("C:\\Program Files\\Lumora Client\\LumoraLauncher.exe");
        List<String> add = Autostart.addCommand(exe);
        assertEquals(List.of("reg", "add", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "LumoraLauncher",
                "/t", "REG_SZ", "/d", "\"C:\\Program Files\\Lumora Client\\LumoraLauncher.exe\" --tray", "/f"), add);
        assertTrue(Autostart.deleteCommand().contains("delete"));
        assertTrue(Autostart.queryCommand().get(2).startsWith("HKCU\\"), "never HKLM (no admin rights, other users untouched)");
    }

    @Test
    void versionFamiliesGroupSubVersions() {
        assertEquals("1.21", VersionGrid.family("1.21.11"));
        assertEquals("1.21", VersionGrid.family("1.21"));
        assertEquals("26.1", VersionGrid.family("26.1.2"));
        assertEquals("1.8", VersionGrid.family("1.8.9"));
        assertEquals("26.3", VersionGrid.family("26.3"));
    }
}
