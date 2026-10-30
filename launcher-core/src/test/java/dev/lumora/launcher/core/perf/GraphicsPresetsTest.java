package dev.lumora.launcher.core.perf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphicsPresetsTest {
    @TempDir
    Path dir;

    @Test
    void onlyExistingKeysAndBackup() throws Exception {
        // 1.8.9 style: fancyGraphics, unquoted clouds, no graphicsMode / simulationDistance
        String original = "version:184\nrenderDistance:12\nfancyGraphics:true\nrenderClouds:true\nmaxFps:120\nlang:de_de\n";
        Files.writeString(dir.resolve("options.txt"), original);
        List<String> changed = GraphicsPresets.apply(dir, GraphicsPresets.Preset.MAX_FPS);
        assertEquals(List.of("renderDistance", "fancyGraphics", "renderClouds", "maxFps"), changed);
        String now = Files.readString(dir.resolve("options.txt"));
        assertTrue(now.contains("renderDistance:8\n"));
        assertTrue(now.contains("renderClouds:false\n"), "unquoted like the old version wrote it");
        assertTrue(now.contains("lang:de_de\n"), "other options stay");
        assertFalse(now.contains("graphicsMode"), "no key the version does not know");
        assertFalse(now.contains("simulationDistance"));
        assertEquals(GraphicsPresets.Preset.MAX_FPS, GraphicsPresets.current(dir));
        assertEquals(original, Files.readString(dir.resolve(GraphicsPresets.BACKUP)));

        // a second preset keeps the first backup (the user's original)
        GraphicsPresets.apply(dir, GraphicsPresets.Preset.QUALITY);
        assertEquals(original, Files.readString(dir.resolve(GraphicsPresets.BACKUP)));
        assertTrue(GraphicsPresets.restore(dir));
        assertEquals(original, Files.readString(dir.resolve("options.txt")));
        assertFalse(Files.exists(dir.resolve(GraphicsPresets.BACKUP)));
    }

    @Test
    void modernQuotedClouds() throws Exception {
        Files.writeString(dir.resolve("options.txt"), "graphicsMode:2\nrenderClouds:\"true\"\nsimulationDistance:12\n");
        GraphicsPresets.apply(dir, GraphicsPresets.Preset.BALANCED);
        String now = Files.readString(dir.resolve("options.txt"));
        assertTrue(now.contains("graphicsMode:1\n"));
        assertTrue(now.contains("renderClouds:\"fast\"\n"));
        assertTrue(now.contains("simulationDistance:8\n"));
    }

    @Test
    void nothingWithoutOptionsFile() throws Exception {
        assertTrue(GraphicsPresets.apply(dir, GraphicsPresets.Preset.QUALITY).isEmpty());
        assertNull(GraphicsPresets.current(dir));
        assertFalse(GraphicsPresets.restore(dir));
    }
}
