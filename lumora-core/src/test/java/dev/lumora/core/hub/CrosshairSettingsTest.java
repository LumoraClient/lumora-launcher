package dev.lumora.core.hub;

import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.extras.CrosshairPresets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The crosshair editor above the crosshair module's settings. */
class CrosshairSettingsTest {
    @TempDir
    Path dir;
    LumoraCore core;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    private SettingsGroup group() {
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        core.addonManager().setWanted("lumora-crosshair", true);
        return core.addonManager().record("lumora-crosshair").orElseThrow().configuration().settings();
    }

    private static String value(SettingsGroup g, String key) {
        return g.get(key).orElseThrow().encode();
    }

    private static List<String> rows(SettingsView v) {
        return v.items().stream().filter(o -> o instanceof Setting<?>).map(o -> ((Setting<?>) o).key()).toList();
    }

    /** Clicks the points of the list from {@code fromY} down (every 3 px down, 4 px across) until {@code done}. */
    private static boolean clickUntil(SettingsView v, int fromY, int button, BooleanSupplier done) {
        for (int y = fromY; y < 440; y += 3) {
            for (int x = 20; x < 840; x += 4) {
                v.render(new FakeGraphics(), 854, 480, x, y);
                v.mouseClicked(x, y, button);
                v.mouseReleased(x, y, button);
                if (done.getAsBoolean()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void tabsPresetsAndThePixelEditorChangeTheSettings() {
        SettingsGroup g = group();
        SettingsView v = new SettingsView(core, new SettingsViewTest.Host(), "Fadenkreuz", "", g, List.of(), null, false);
        FakeGraphics first = new FakeGraphics();
        v.render(first, 854, 480, 0, 0);
        assertEquals(0, first.depth);
        assertTrue(first.texts.contains("Shapes") || first.texts.contains("Formen"), first.texts.toString());
        // mode shape: the shape rows, no rows for what the editor sets
        List<String> rows = rows(v);
        assertTrue(rows.containsAll(List.of("size", "gap", "thickness", "color", "outline", "dot")), rows.toString());
        assertFalse(rows.contains("mode") || rows.contains("style") || rows.contains("preset") || rows.contains("pixels")
                || rows.contains("scale"), rows.toString());

        assertTrue(clickUntil(v, 62, 0, () -> value(g, "mode").equals("preset")), "the second tab");
        rows = rows(v);
        assertTrue(rows.contains("scale") && !rows.contains("size"), rows.toString());
        String before = value(g, "preset");
        assertTrue(clickUntil(v, 90, 0, () -> !value(g, "preset").equals(before)), "a preset tile");
        assertEquals("preset", value(g, "mode"));

        assertTrue(clickUntil(v, 62, 0, () -> value(g, "mode").equals("custom")), "the third tab");
        String pixels = value(g, "pixels");
        assertTrue(clickUntil(v, 90, 0, () -> !value(g, "pixels").equals(pixels)), "painting a pixel");
        boolean[] px = CrosshairPresets.decode(value(g, "pixels"));
        int n = CrosshairPresets.GRID;
        for (int i = 0; i < px.length; i++) {
            int x = i % n;
            int y = i / n;
            assertEquals(px[i], px[y * n + (n - 1 - x)], "mirrored left/right");
            assertEquals(px[i], px[(n - 1 - y) * n + x], "mirrored top/bottom");
        }
        // the right button erases (the first painted pixel)
        String painted = value(g, "pixels");
        assertTrue(clickUntil(v, 90, 1, () -> !value(g, "pixels").equals(painted)), "erasing");

        // the view still renders in all modes on a small screen
        for (String m : List.of("shape", "preset", "custom")) {
            g.get("mode").orElseThrow().decode(m);
            FakeGraphics small = new FakeGraphics();
            v.render(small, 320, 240, 100, 100);
            assertEquals(0, small.depth, m);
        }
    }
}
