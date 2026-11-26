package dev.lumora.launcher.core.mods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInAddonsTest {
    @TempDir
    Path dir;

    @Test
    void theGameGetsACopyNamedAfterItsHashAndOldCopiesGo() throws Exception {
        Path source = dir.resolve("bundled").resolve("lumora-voice.jar");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{1, 2, 3});
        Path builtin = dir.resolve("lumora").resolve("builtin");
        Path first = BuiltInAddons.stage(source, builtin);
        assertTrue(first.getFileName().toString().matches("lumora-voice-[0-9a-f]{16}\\.jar"), first.toString());
        assertEquals(3, Files.size(first));
        assertEquals(first, BuiltInAddons.stage(source, builtin), "same file: nothing copied again");

        // a launcher update ships a new jar: new name, the old copy is removed
        Files.write(source, new byte[]{4, 5, 6, 7});
        Path second = BuiltInAddons.stage(source, builtin);
        assertFalse(second.equals(first));
        assertFalse(Files.exists(first));
        assertEquals(4, Files.size(second));
    }

    @Test
    void withoutTheJarInTheBundleNothingIsPassed() throws Exception {
        Path bundled = dir.resolve("bundled");
        Files.createDirectories(bundled);
        Files.writeString(bundled.resolve(BundledMods.INDEX), "{\"lumora-loader.jar\": \"00\"}");
        List<String> warnings = new ArrayList<>();
        assertNull(BuiltInAddons.sysProp(bundled, dir.resolve("lumora"), warnings::add));
        assertTrue(warnings.isEmpty());
        // listed but missing: a warning, the game still starts
        Files.writeString(bundled.resolve(BundledMods.INDEX), "{\"lumora-voice.jar\": \"00\"}");
        assertNull(BuiltInAddons.sysProp(bundled, dir.resolve("lumora"), warnings::add));
        assertEquals(1, warnings.size());
    }
}
