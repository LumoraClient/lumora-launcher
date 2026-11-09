package dev.lumora.core.addon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddonLibraryTest {
    @TempDir
    Path dir;

    public static Path jar(Path folder, String file, String id, String version, String minecraft) throws IOException {
        Files.createDirectories(folder);
        Path jar = folder.resolve(file);
        try (OutputStream out = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(JarAddonLoader.DESCRIPTOR));
            zip.write(("{\"schema\":1,\"id\":\"" + id + "\",\"name\":\"" + id + " name\",\"version\":\"" + version + "\","
                    + "\"description\":\"About " + id + "\",\"authors\":[\"Lumora\"],\"api\":\"1.3.0\",\"minecraft\":[\"" + minecraft
                    + "\"],\"entrypoint\":\"com.example.Addon\",\"depends\":{\"lumora\":\">=1.0.0\"}}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return jar;
    }

    @Test
    void theLibraryFolderSitsNextToTheInstances() {
        Path addons = dir.resolve("LumoraClient").resolve("instances").resolve("26.3").resolve("addons");
        assertEquals(dir.resolve("LumoraClient").resolve("addons"), AddonLibrary.locate(addons));
        assertNull(AddonLibrary.locate(dir.resolve("somewhere").resolve("game").resolve("addons")), "not a Lumora instance");
    }

    @Test
    void scanShowsWhatIsInstalledAndInstallCopiesTheJar() throws IOException {
        Path library = dir.resolve("addons");
        Path addons = dir.resolve("instances").resolve("26.3").resolve("addons");
        jar(library, "voice-1.0.jar", "voice", "1.0.0", "*");
        jar(library, "timer-2.0.jar", "timer", "2.0.0", "*");
        jar(library, "old-only.jar", "old", "1.0.0", "1.8.9");
        Files.writeString(library.resolve("broken.jar"), "no zip");
        jar(addons, "timer-1.0.jar", "timer", "1.0.0", "*");
        AddonLibrary lib = new AddonLibrary(library, addons, "26.3");

        List<AddonLibrary.Entry> entries = lib.scan();
        assertEquals(4, entries.size());
        assertEquals(AddonLibrary.Status.BROKEN, status(entries, "broken.jar"));
        assertEquals(AddonLibrary.Status.INCOMPATIBLE, status(entries, "old"));
        assertEquals(AddonLibrary.Status.UPDATE, status(entries, "timer"));
        assertEquals(AddonLibrary.Status.AVAILABLE, status(entries, "voice"));

        AddonLibrary.Entry voice = entries.stream().filter(e -> e.id().equals("voice")).findFirst().orElseThrow();
        assertNull(lib.install(voice));
        assertTrue(Files.isRegularFile(addons.resolve("voice-1.0.jar")), "copied into the game's addon folder");
        assertEquals(AddonLibrary.Status.RESTART, status(lib.scan(), "voice"), "loads at the next start");
        assertEquals(AddonLibrary.Status.INSTALLED, status(new AddonLibrary(library, addons, "26.3").scan(), "voice"),
                "after the restart it is installed");

        // the update replaces the older copy of the same addon
        AddonLibrary.Entry timer = lib.scan().stream().filter(e -> e.id().equals("timer")).findFirst().orElseThrow();
        assertNull(lib.install(timer));
        assertTrue(Files.isRegularFile(addons.resolve("timer-2.0.jar")));
        assertFalse(Files.exists(addons.resolve("timer-1.0.jar")), "the old version is gone");
        assertFalse(Files.exists(addons.resolve("timer-2.0.jar.part")));
    }

    @Test
    void jarsMarkedForRemovalGoAtTheNextStart() throws IOException {
        Path addons = dir.resolve("addons");
        jar(addons, "old-1.0.jar", "old", "1.0.0", "*");
        jar(addons, "keep.jar", "keep", "1.0.0", "*");
        Files.writeString(addons.resolve(AddonLibrary.REMOVE_FILE), "old-1.0.jar\n../escape.jar\n");
        AddonLibrary.removePending(addons);
        assertFalse(Files.exists(addons.resolve("old-1.0.jar")));
        assertTrue(Files.exists(addons.resolve("keep.jar")));
        assertFalse(Files.exists(addons.resolve(AddonLibrary.REMOVE_FILE)));
    }

    private static AddonLibrary.Status status(List<AddonLibrary.Entry> entries, String id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow().status();
    }
}
