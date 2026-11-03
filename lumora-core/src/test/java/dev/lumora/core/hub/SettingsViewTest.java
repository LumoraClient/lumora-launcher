package dev.lumora.core.hub;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.input.Keys;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsViewTest {
    @TempDir
    Path dir;

    static final class Host implements SettingsView.Host {
        int closed;
        int editor;

        @Override public void close(SettingsView view) { closed++; }
        @Override public boolean inWorld() { return true; }
        @Override public void openHudEditor() { editor++; }
    }

    private static SettingsGroup group() {
        SettingsGroup g = new SettingsGroup("test");
        g.slider("scale", 10, 0, 100);
        g.toggle("clicks", true);
        g.toggle("shadow", false);
        g.toggle("border", false);
        g.choice("style", "a", "a", "b", "c");
        g.integer("count", 3, 1, 9);
        g.text("label", "hi", 16);
        g.color("color", 0xFFFFFFFF, true);
        g.keybind("key", Keys.R);
        return g;
    }

    private static void render(SettingsView v, int mx, int my) {
        v.render(new FakeGraphics(), 854, 480, mx, my);
    }

    /** Clicks every 2 px of a horizontal line until {@code done} reports a change. */
    private static boolean clickAlong(SettingsView v, int y, java.util.function.BooleanSupplier done) {
        for (int x = 20; x < 840; x += 2) {
            render(v, x, y);
            v.mouseClicked(x, y, 0);
            v.mouseReleased(x, y, 0);
            if (done.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    @Test
    void everySettingTypeRendersOnSmallAndLargeScreens() {
        LumoraCore core = new LumoraCore(new FakePlatform(dir));
        SettingsView view = new SettingsView(core, new Host(), "Key Strokes", "Shows pressed keys", group(), List.of(), null, true);
        for (int[] s : new int[][]{{320, 240}, {427, 240}, {854, 480}}) {
            FakeGraphics g = new FakeGraphics();
            view.render(g, s[0], s[1], 0, 0);
            assertEquals(0, g.depth);
            assertTrue(g.texts.stream().anyMatch(t -> t.contains("Shows pressed keys")));
        }
        assertEquals(9, view.items().size());
    }

    @Test
    void sliderDragsAndSwitchToggles() {
        LumoraCore core = new LumoraCore(new FakePlatform(dir));
        SettingsGroup g = group();
        SettingsView view = new SettingsView(core, new Host(), "Test", "", g, List.of(), null, false);
        IntegerSetting scale = (IntegerSetting) g.get("scale").orElseThrow();
        render(view, 0, 0);
        // the slider is the first row below the header (title, line): find it by dragging along its height
        boolean moved = false;
        for (int y = 40; y < 120 && !moved; y++) {
            render(view, 0, y);
            if (view.mouseClicked(745, y, 0)) {
                view.mouseDragged(900, y, 0);
                view.mouseReleased(900, y, 0);
                moved = scale.get() == 100;
            }
        }
        assertTrue(moved, "slider dragged to its end");
        BooleanSetting shadow = (BooleanSetting) g.get("shadow").orElseThrow();
        boolean toggled = false;
        for (int y = 40; y < 200 && !toggled; y += 2) {
            toggled = clickAlong(view, y, shadow::enabled);
        }
        assertTrue(toggled, "a click on the switch turns it on");
    }

    @Test
    void escapeLeavesFieldsFirstAndOnCloseRunsOnce() {
        LumoraCore core = new LumoraCore(new FakePlatform(dir));
        AtomicInteger saved = new AtomicInteger();
        SettingsView view = new SettingsView(core, new Host(), "Test", "", group(), List.of(), saved::incrementAndGet, false);
        render(view, 0, 0);
        assertTrue(view.keyPressed(Keys.F, true, false)); // Ctrl+F focuses the search
        assertFalse(view.closeRequested());
        assertTrue(view.closeRequested());
        view.removed();
        view.removed();
        assertEquals(1, saved.get());
    }

    @Test
    void colorHexIsEditable() throws Exception {
        LumoraCore core = new LumoraCore(new FakePlatform(dir));
        SettingsGroup g = group();
        ColorSetting color = (ColorSetting) g.get("color").orElseThrow();
        SettingsView view = new SettingsView(core, new Host(), "Test", "", g, List.of(), null, false);
        render(view, 0, 0);
        Thread.sleep(220); // typed text right after opening belongs to the key that opened the view
        // focus the hex field: click along all rows until typing changes the colour
        boolean edited = false;
        for (int y = 40; y < 470 && !edited; y += 3) {
            for (int x = 500; x < 840 && !edited; x += 6) {
                render(view, x, y);
                view.mouseClicked(x, y, 0);
                view.keyPressed(Keys.BACKSPACE, true, false);
                for (char c : "#ff00ff00".toCharArray()) {
                    view.charTyped(c);
                }
                edited = color.get() == 0xFF00FF00;
                view.keyPressed(Keys.ENTER, false, false);
                view.closeRequested(); // typing outside a field went to the search: clear it
                view.closeRequested();
            }
        }
        assertTrue(edited);
    }
}
