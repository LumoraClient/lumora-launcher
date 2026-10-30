package dev.lumora.core;

import dev.lumora.core.hud.HudLayout;
import dev.lumora.core.hud.LayoutStore;
import dev.lumora.core.hud.WidgetState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutStoreTest {
    @TempDir
    Path dir;

    @Test
    void roundTrip() {
        FakePlatform p = new FakePlatform(dir);
        HudLayout l = new HudLayout();
        l.grid = true;
        l.gridSize = 10;
        WidgetState s = new WidgetState();
        s.enabled = true;
        s.shown = false;
        s.x = 0.25f;
        s.y = 1f;
        s.scale = 1.75f;
        s.layer = -2;
        s.settings.put("mode", "compact");
        l.active().widgets.put("demo:w", s);
        l.profiles.put("pvp", l.active().copy());
        l.activeProfile = "pvp";
        HudLayout back = LayoutStore.parse(LayoutStore.toJson(l), p.logger());
        assertTrue(back.grid);
        assertEquals(10, back.gridSize);
        assertEquals("pvp", back.activeProfile);
        WidgetState b = back.profiles.get("default").widgets.get("demo:w");
        assertTrue(b.sameAs(s));
    }

    @Test
    void brokenEntriesAndValuesFallBackIndividually() {
        FakePlatform p = new FakePlatform(dir);
        String json = """
                {"schema": 2, "activeProfile": "missing",
                 "profiles": {
                   "default": {"widgets": {
                     "demo:good": {"enabled": true, "x": 0.5, "y": "oops", "scale": 99, "layer": 2},
                     "demo:partial": {},
                     "no-colon": {"enabled": true},
                     "demo:notobject": 5
                   }},
                   "Bad Profile": {"widgets": {}}
                 }}
                """;
        HudLayout l = LayoutStore.parse(json, p.logger());
        assertEquals("default", l.activeProfile, "unknown active profile -> default");
        assertFalse(l.profiles.containsKey("Bad Profile"));
        WidgetState good = l.active().widgets.get("demo:good");
        assertTrue(good.enabled);
        assertEquals(0.5f, good.x);
        assertEquals(0f, good.y, "invalid value -> default");
        assertEquals(4f, good.scale, "clamped");
        WidgetState partial = l.active().widgets.get("demo:partial");
        assertFalse(partial.enabled, "missing optional data -> defaults");
        assertTrue(partial.shown);
        assertEquals(1f, partial.scale);
        assertFalse(l.active().widgets.containsKey("no-colon"));
        assertFalse(l.active().widgets.containsKey("demo:notobject"));
        assertTrue(p.logged("Skipping invalid widget entry"));
    }

    @Test
    void corruptFileIsBackedUpAndDefaultsAreUsed() throws IOException {
        FakePlatform p = new FakePlatform(dir);
        Path file = dir.resolve("hud-layout.json");
        Files.writeString(file, "{not json");
        HudLayout l = LayoutStore.load(file, null, p.logger());
        assertTrue(l.active().widgets.isEmpty());
        assertFalse(Files.exists(file));
        try (Stream<Path> files = Files.list(dir)) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().startsWith("hud-layout.json.corrupt-")));
        }
        assertNull(LayoutStore.parse("[1,2]", p.logger()));
    }

    @Test
    void legacyFileIsMigrated() throws IOException {
        FakePlatform p = new FakePlatform(dir);
        Path legacy = dir.resolve("lumora-hud.json");
        Files.writeString(legacy, """
                {"modules": {"fps": {"enabled": true, "x": 0.005, "y": 0.01, "scale": 1.2},
                             "keystrokes": {"enabled": false, "x": 0.995, "y": 0.86, "scale": 1.0}},
                 "background": false, "accent": true}
                """);
        HudLayout l = LayoutStore.load(dir.resolve("lumora/hud-layout.json"), legacy, p.logger());
        WidgetState fps = l.active().widgets.get("lumora:fps");
        assertTrue(fps.enabled);
        assertEquals(1.2f, fps.scale);
        assertEquals("false", fps.settings.get("background"));
        assertFalse(l.active().widgets.get("lumora:keystrokes").enabled);
        assertTrue(Files.exists(legacy), "legacy file is left untouched");
    }
}
