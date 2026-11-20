package dev.lumora.launcher.core.install;

import dev.lumora.launcher.core.mojang.ResolvedVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Natives: emptied and extracted again; files a running game holds stay when they are the same. */
class NativeExtractorTest {
    @TempDir
    Path dir;

    private List<ResolvedVersion.NativeLibrary> jar(String... entries) throws Exception {
        Path libs = dir.resolve("libraries");
        Path jar = libs.resolve("org/lwjgl/lwjgl-platform-natives-windows.jar");
        Files.createDirectories(jar.getParent());
        try (OutputStream o = Files.newOutputStream(jar); ZipOutputStream z = new ZipOutputStream(o)) {
            for (int i = 0; i < entries.length; i += 2) {
                z.putNextEntry(new ZipEntry(entries[i]));
                z.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return List.of(new ResolvedVersion.NativeLibrary("org.lwjgl:lwjgl-platform", "org/lwjgl/lwjgl-platform-natives-windows.jar",
                null, null, 0, List.of("META-INF/")));
    }

    @Test
    void extractsIntoAnEmptiedDirectory() throws Exception {
        List<ResolvedVersion.NativeLibrary> n = jar("lwjgl.dll", "dll", "META-INF/MANIFEST.MF", "m");
        Path target = dir.resolve("natives");
        Files.createDirectories(target);
        Files.writeString(target.resolve("old.dll"), "old");
        NativeExtractor.extract(n, dir.resolve("libraries"), target);
        assertEquals("dll", Files.readString(target.resolve("lwjgl.dll")));
        assertFalse(Files.exists(target.resolve("old.dll")), "emptied first");
        assertFalse(Files.exists(target.resolve("META-INF")), "excluded");
    }

    @Test
    void keepsIdenticalFilesARunningGameHolds() throws Exception {
        // Windows refuses to delete a file that is open, like a DLL the first game run has loaded
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"));
        List<ResolvedVersion.NativeLibrary> n = jar("lwjgl.dll", "dll", "OpenAL64.dll", "al");
        Path target = dir.resolve("natives");
        NativeExtractor.extract(n, dir.resolve("libraries"), target);
        try (InputStream held = new java.io.FileInputStream(target.resolve("lwjgl.dll").toFile())) { // without FILE_SHARE_DELETE, like a loaded DLL
            NativeExtractor.extract(n, dir.resolve("libraries"), target); // the second run of the profile
            assertTrue(held.read() >= 0);
        }
        assertEquals("dll", Files.readString(target.resolve("lwjgl.dll")));
        assertEquals("al", Files.readString(target.resolve("OpenAL64.dll")));
    }
}
