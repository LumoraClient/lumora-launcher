package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonState;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.TextureRef;
import dev.lumora.api.ui.WidgetStyle;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.RegistrationTracker;
import dev.lumora.core.hud.Bounds;
import dev.lumora.core.hud.HudRenderer;
import dev.lumora.core.hud.WidgetDesign;
import dev.lumora.core.hud.WidgetState;
import dev.lumora.core.theme.ThemeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetDesignTest {
    static final int LABEL = 0xFF112233;
    static final int VALUE = 0xFF445566;

    @TempDir
    Path dir;
    LumoraCore core;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
        WidgetDesign.use(null);
    }

    /** Remembers text with position, colour and shadow, and every fill; 6 px per character. */
    static final class Rec implements HudGraphics {
        record Text(String text, int x, int color, boolean shadow) {
        }

        final List<Text> texts = new ArrayList<>();
        final List<int[]> fills = new ArrayList<>();

        @Override public int textWidth(String text) { return text.length() * 6; }
        @Override public int lineHeight() { return 9; }
        @Override public void text(String text, int x, int y, int color, boolean shadow) { texts.add(new Text(text, x, color, shadow)); }
        @Override public void fill(int x1, int y1, int x2, int y2, int color) { fills.add(new int[]{x1, y1, x2, y2, color}); }
        @Override public void texture(TextureRef t, int x, int y, int w, int h, float u, float v, int rw, int rh, int tw, int th) { }
        @Override public void push() { }
        @Override public void pop() { }
        @Override public void translate(float x, float y) { }
        @Override public void scale(float x, float y) { }
        @Override public void enableScissor(int x1, int y1, int x2, int y2) { }
        @Override public void disableScissor() { }
        @Override public <T> java.util.Optional<T> unwrap(Class<T> type) { return java.util.Optional.empty(); }
    }

    static WidgetDesign.Look look(String format, boolean uppercase, float scale) {
        return new WidgetDesign.Look(true, 0x80000000, 3, true, 0xFFFFFFFF, true, 0xFF00FF00, 0xFFAA0000, 0xFF0000AA,
                false, format, uppercase, scale);
    }

    @Test
    void whileOffEveryWidgetKeepsItsOwnLook() {
        assertFalse(WidgetDesign.active());
        assertEquals(1f, WidgetDesign.scale());
        assertFalse(WidgetDesign.background(false));
        assertEquals(LABEL, WidgetDesign.labelColor(LABEL));
        Rec g = new Rec();
        assertFalse(WidgetDesign.panel(g, 50, 10), "the widget draws its own panel");
        assertTrue(g.fills.isEmpty());
        WidgetDesign.line(g, 0, 0, "FPS", "60", LABEL, VALUE, true, false);
        assertEquals(List.of(new Rec.Text("FPS", 0, LABEL, true), new Rec.Text("60", 22, VALUE, true)), g.texts);
        assertEquals(34, WidgetDesign.lineWidth(g::textWidth, "FPS", "60"));
        assertEquals(12, WidgetDesign.lineWidth(g::textWidth, "", "60"));
    }

    @Test
    void theFormatDecidesHowLabelAndValueAreShown() {
        WidgetDesign.use(() -> look(WidgetDesign.BRACKETS, true, 1f));
        Rec g = new Rec();
        WidgetDesign.line(g, 0, 0, "fps", "60", LABEL, VALUE, true, false);
        assertEquals(List.of(new Rec.Text("[FPS]", 0, 0xFFAA0000, false), new Rec.Text("60", 34, 0xFF0000AA, false)), g.texts,
                "label and value in the design's colours, without shadow");
        assertEquals(46, WidgetDesign.lineWidth(g::textWidth, "fps", "60"));

        WidgetDesign.use(() -> look(WidgetDesign.VALUE_FIRST, false, 1f));
        g = new Rec();
        WidgetDesign.line(g, 0, 0, "FPS", "60", LABEL, VALUE, true, false);
        assertEquals(List.of(new Rec.Text("60", 0, 0xFF0000AA, false), new Rec.Text("FPS", 16, 0xFFAA0000, false)), g.texts);

        WidgetDesign.use(() -> look(WidgetDesign.VALUE_ONLY, false, 1f));
        g = new Rec();
        WidgetDesign.line(g, 0, 0, "FPS", "60", LABEL, VALUE, true, false);
        assertEquals(List.of(new Rec.Text("60", 0, 0xFF0000AA, false)), g.texts);
        assertEquals(12, WidgetDesign.lineWidth(g::textWidth, "FPS", "60"));

        WidgetDesign.use(() -> look(WidgetDesign.COLON, false, 1f));
        g = new Rec();
        WidgetDesign.line(g, 0, 0, "Ping", "20 ms", LABEL, 0xFF00FF00, true, true);
        assertEquals(new Rec.Text("Ping:", 0, 0xFFAA0000, false), g.texts.get(0));
        assertEquals(0xFF00FF00, g.texts.get(1).color(), "a value whose colour means something keeps it");
    }

    @Test
    void thePanelHasTheDesignsBackgroundBorderAndAccentStripe() {
        WidgetDesign.use(() -> new WidgetDesign.Look(false, 0x80000000, 3, true, 0xFFFFFFFF, false, 0, 0, 0xFFFFFFFF, true,
                WidgetDesign.SPACE, false, 1f));
        Rec g = new Rec();
        assertTrue(WidgetDesign.panel(g, 40, 12));
        assertFalse(g.fills.isEmpty());
        for (int[] f : g.fills) {
            assertEquals(0xFFFFFFFF, f[4], "only the border");
            boolean centre = f[0] <= 20 && f[2] > 20 && f[1] <= 6 && f[3] > 6;
            assertFalse(centre, "the border leaves the inside free");
        }

        WidgetDesign.use(() -> look(WidgetDesign.SPACE, false, 1f));
        g = new Rec();
        WidgetDesign.panel(g, 40, 12);
        assertTrue(g.fills.stream().anyMatch(f -> f[4] == 0x80000000), "background");
        assertTrue(g.fills.stream().anyMatch(f -> f[4] == 0xFF00FF00 && f[0] == 0 && f[2] == 2), "accent stripe on the left");
    }

    @Test
    void theSizeOfAllWidgetsMultipliesEachWidgetsOwnSize() {
        WidgetState s = new WidgetState();
        s.scale = 1.5f;
        Bounds b = new Bounds();
        HudRenderer.place(b, s, 40, 10, 400, 300);
        assertEquals(60, b.width);
        WidgetDesign.use(() -> look(WidgetDesign.SPACE, false, 2f));
        HudRenderer.place(b, s, 40, 10, 400, 300);
        assertEquals(120, b.width);
        assertEquals(30, b.height);
        assertEquals(1.5f, s.scale, "the widget keeps its own size");
    }

    @Test
    void theModuleIsOffByDefaultAndSetsTheLookOfAllWidgetsWhenOn() {
        core = new LumoraCore(new FakePlatform(dir));
        core.loadAddons(List.of());
        assertTrue(core.addons().stream().anyMatch(a -> a.id().equals("lumora-widgetdesign") && a.state() != AddonState.ENABLED));
        assertFalse(WidgetDesign.active(), "nobody's widgets change until the player switches it on");
        SettingsGroup own = core.widgetRegistry().entry("lumora-info:compass").orElseThrow().widget().settings();
        assertTrue(own.get("background").orElseThrow().isVisible());

        assertTrue(core.addonManager().setWanted("lumora-widgetdesign", true).isEmpty());
        assertTrue(WidgetDesign.active());
        assertFalse(own.get("background").orElseThrow().isVisible(), "the widget's own look settings are hidden");
        assertFalse(own.get("label_color").orElseThrow().isVisible());

        SettingsGroup design = core.addonManager().record("lumora-widgetdesign").orElseThrow().configuration().settings();
        ((ChoiceSetting) design.get("format").orElseThrow()).select(WidgetDesign.COLON);
        ((BooleanSetting) design.get("uppercase").orElseThrow()).set(true);
        ((DecimalSetting) design.get("scale").orElseThrow()).set(1.5);
        assertEquals(WidgetDesign.COLON, WidgetDesign.look().format(), "a change shows at once");
        assertEquals("FPS:", WidgetDesign.look().label("fps"));
        assertEquals(1.5f, WidgetDesign.scale());

        // the presets
        ((dev.lumora.api.config.ButtonSetting) design.get("preset_minimal").orElseThrow()).press();
        assertFalse(WidgetDesign.look().background());
        assertFalse(WidgetDesign.look().accentBar());
        ((dev.lumora.api.config.ButtonSetting) design.get("preset_lumora").orElseThrow()).press();
        assertTrue(WidgetDesign.look().background());
        assertEquals(WidgetDesign.SPACE, WidgetDesign.look().format());

        assertTrue(core.addonManager().setWanted("lumora-widgetdesign", false).isEmpty());
        assertFalse(WidgetDesign.active());
        assertEquals(1f, WidgetDesign.scale());
        assertTrue(own.get("background").orElseThrow().isVisible());
    }

    @Test
    void addonsReadTheDesignThroughTheTheme() {
        WidgetStyle style = new ThemeService(true).scoped("demo", new RegistrationTracker(), () -> { }, () -> { }).widgetStyle();
        assertFalse(style.active());
        assertFalse(WidgetStyle.NONE.active());
        assertEquals("fps", style.label("fps"));
        WidgetDesign.use(() -> look(WidgetDesign.BRACKETS, true, 1.25f));
        assertTrue(style.active());
        assertEquals("[FPS]", style.label("fps"));
        assertEquals(0xFF0000AA, style.valueColor());
        assertEquals(3, style.cornerRadius());
        assertEquals(1.25f, style.scale());
        Rec g = new Rec();
        style.line(g, 0, 0, "Session", "00:12");
        assertEquals("[SESSION]", g.texts.get(0).text());
        assertEquals(style.lineWidth(g, "Session", "00:12"), g.texts.get(1).x() + 30);
    }
}
