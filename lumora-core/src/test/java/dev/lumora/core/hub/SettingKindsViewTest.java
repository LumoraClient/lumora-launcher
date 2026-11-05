package dev.lumora.core.hub;

import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.Setting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The settings screen with the setting kinds of Lumora API 1.3: sections, conditions, check boxes, rows, drop-downs, buttons, color picker. */
class SettingKindsViewTest {
    @TempDir
    Path dir;

    /** Records texts with their position (the settings view is untransformed once its opening animation is over). */
    static final class Texts implements HudGraphics {
        record Text(String text, int x, int y) {
        }

        final List<Text> texts = new ArrayList<>();

        @Override public int textWidth(String text) { return text.replace("§l", "").length() * 6; }
        @Override public int lineHeight() { return 9; }
        @Override public void text(String text, int x, int y, int color, boolean shadow) { texts.add(new Text(text, x, y)); }
        @Override public void fill(int x1, int y1, int x2, int y2, int color) { }
        @Override public void texture(TextureRef t, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th) { }
        @Override public void push() { }
        @Override public void pop() { }
        @Override public void translate(float x, float y) { }
        @Override public void scale(float x, float y) { }
        @Override public void enableScissor(int x1, int y1, int x2, int y2) { }
        @Override public void disableScissor() { }
        @Override public <T> Optional<T> unwrap(Class<T> type) { return Optional.empty(); }

        Text last(String label) {
            Text found = null;
            for (Text t : texts) {
                if (t.text().equals(label)) {
                    found = t;
                }
            }
            return found;
        }
    }

    private SettingsView open(SettingsGroup g) throws InterruptedException {
        LumoraCore core = new LumoraCore(new FakePlatform(dir));
        SettingsView v = new SettingsView(core, new SettingsViewTest.Host(), "Test", "", g, List.of(), null, false);
        v.render(new Texts(), 854, 480, -1, -1);
        Thread.sleep(230); // opening animation
        return v;
    }

    private static Texts render(SettingsView v) {
        Texts t = new Texts();
        v.render(t, 854, 480, -1, -1);
        return t;
    }

    private static void click(SettingsView v, Texts.Text t) {
        assertNotNull(t);
        v.mouseClicked(t.x() + 2, t.y() + 3, 0);
    }

    private static List<String> keys(SettingsView v) {
        return v.items().stream().filter(o -> o instanceof Setting<?>).map(o -> ((Setting<?>) o).key()).toList();
    }

    @Test
    void sectionsAndConditionsDecideWhatIsShown() throws Exception {
        SettingsGroup g = new SettingsGroup("test.");
        BooleanSetting sounds = g.toggle("sounds", false).section("test.section.sounds");
        g.slider("volume", 50, 0, 100).unit("%").requires(sounds);
        g.toggle("other", true).section("test.section.other");
        SettingsView v = open(g);
        assertEquals(List.of("sounds", "other"), keys(v), "volume hidden while sounds are off");
        Texts t = render(v);
        assertNotNull(t.last("§ltest.section.sounds"), "section heading");
        assertNotNull(t.last("§ltest.section.other"));
        sounds.set(true);
        assertEquals(List.of("sounds", "volume", "other"), keys(v));
        assertNotNull(render(v).last("50%"), "value with its unit");
    }

    @Test
    void checkBoxesRowsAndButtonsReactToClicks() throws Exception {
        SettingsGroup g = new SettingsGroup("test.");
        BooleanSetting box = g.checkbox("box", false);
        ChoiceSetting row = g.selection("row", "a", "a", "b", "c");
        AtomicInteger pressed = new AtomicInteger();
        g.button("reset", pressed::incrementAndGet).label("test.reset.label");
        SettingsView v = open(g);
        Texts t = render(v);
        click(v, t.last("box"));
        assertTrue(box.get(), "a click on the check box label");
        click(v, render(v).last("c"));
        assertEquals("c", row.get(), "a click on an option of the row");
        click(v, render(v).last("reset")); // no translation: the button shows the setting name (drawn after the row label)
        assertEquals(1, pressed.get(), "the button runs its action");
    }

    @Test
    void theDropDownOpensSelectsAndClosesWithEscape() throws Exception {
        SettingsGroup g = new SettingsGroup("test.");
        ChoiceSetting drop = g.dropdown("drop", "beta", "alpha", "beta", "gamma");
        SettingsView v = open(g);
        click(v, render(v).last("beta"));
        Texts open = render(v);
        assertNotNull(open.last("gamma"), "the list is open");
        click(v, open.last("gamma"));
        assertEquals("gamma", drop.get());
        assertNull(render(v).last("alpha"), "closed after picking");
        click(v, render(v).last("gamma"));
        assertNotNull(render(v).last("alpha"));
        assertFalse(v.closeRequested(), "Esc closes the list first");
        assertNull(render(v).last("alpha"));
        assertTrue(v.closeRequested());
    }

    @Test
    void theColorPickerOffersTheRainbowAndAPalette() throws Exception {
        SettingsGroup g = new SettingsGroup("test.");
        ColorSetting c = g.chroma("color", 0xFF336699, true);
        SettingsView v = open(g);
        Texts t = render(v);
        Texts.Text hex = t.last("#ff336699");
        assertNotNull(hex);
        // the swatch sits left of the hex field: field text at hx + 5, swatch from hx - 20 to hx - 6
        v.mouseClicked(hex.x() - 18, hex.y() + 2, 0);
        Texts picker = render(v);
        assertNotNull(picker.last("Rainbow"), "picker open");
        click(v, picker.last("Rainbow"));
        assertTrue(c.isChroma());
        assertEquals(0xFF336699, c.get(), "the base color stays");
        assertFalse(v.closeRequested(), "Esc closes the picker first");
        assertNull(render(v).last("Rainbow"));
    }

    @Test
    void hsvConversionRoundTrips() {
        for (int rgb : new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF336699, 0xFFFFFFFF, 0xFF000000, 0xFF8B5CF6}) {
            float[] hsv = SettingsView.rgbToHsv(rgb);
            int back = SettingsView.hsvToRgb(hsv[0], hsv[1], hsv[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs((rgb >> shift & 0xFF) - (back >> shift & 0xFF)) <= 1, Integer.toHexString(rgb));
            }
        }
    }

    @Test
    void zoomIsASettingsPageBoundToTheClientConfig() throws Exception {
        HubViewTest.FakeHost host = new HubViewTest.FakeHost();
        host.options.put("zoomMode", "hold");
        host.options.put("zoomSmooth", true);
        host.options.put("zoomVignette", false);
        HubView hub = new HubView(new LumoraCore(new FakePlatform(dir)), host);
        SettingsGroup z = hub.zoomSettings();
        ChoiceSetting factor = (ChoiceSetting) z.get("factor").orElseThrow();
        assertEquals("4", factor.get());
        factor.select("8");
        assertEquals(8.0, host.options.get("zoomFactor"));
        ((ChoiceSetting) z.get("mode").orElseThrow()).select("toggle");
        assertEquals("toggle", host.options.get("zoomMode"));
        BooleanSetting vignette = (BooleanSetting) z.get("vignette").orElseThrow();
        vignette.toggle();
        assertEquals(true, host.options.get("zoomVignette"));
        assertTrue(z.get("cinematic").isEmpty(), "options this version does not have are left out");
        host.options.put("zoomEnabled", false);
        assertFalse(factor.isVisible(), "the rest only while zoom is on");
        assertEquals("6", HubView.nearestFactor(5.5));
        assertTrue(z.get("enabled").orElseThrow() instanceof BooleanSetting);
    }
}
