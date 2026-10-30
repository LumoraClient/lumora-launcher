package dev.lumora.core;

import dev.lumora.api.Registration;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.hud.WidgetRenderEvent;
import dev.lumora.api.hud.WidgetCategory;
import dev.lumora.api.hud.WidgetInfo;
import dev.lumora.core.hud.WidgetEntry;
import dev.lumora.core.hud.WidgetRegistryImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetRegistryTest {
    @TempDir
    Path dir;
    FakePlatform platform;
    LumoraCore core;
    WidgetRegistryImpl registry;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        core = new LumoraCore(platform);
        registry = core.widgetRegistry();
    }

    @AfterEach
    void tearDown() {
        core.stopping();
    }

    /** Widget with one stored and one bound setting. */
    static final class Configurable extends TestContexts.Box {
        BooleanSetting shadow;
        DecimalSetting opacity;
        float ownerField = 0.85f;

        Configurable() {
            super("cfg");
        }

        @Override
        public String category() {
            return WidgetCategory.VOICE;
        }

        @Override
        public void configure(SettingsGroup s) {
            shadow = s.toggle("shadow", true);
            opacity = s.slider("opacity", 0.85, 0.1, 1, 0.05);
            opacity.bind(() -> (double) ownerField, v -> ownerField = (float) (double) v);
        }
    }

    @Test
    void registerLookupAndDuplicates() {
        TestContexts.Box a = new TestContexts.Box("a");
        Registration reg = registry.register("demo", a);
        assertEquals("demo:a", a.fullId());
        assertEquals("demo", a.addonId());
        assertEquals("demo.widget.a", a.nameKey());
        assertThrows(IllegalArgumentException.class, () -> registry.register("demo", new TestContexts.Box("a")), "duplicate id");
        assertThrows(IllegalArgumentException.class, () -> registry.register("other", a), "same instance twice");
        registry.register("other", new TestContexts.Box("a")); // same local id, other addon: fine
        assertEquals(2, registry.all().size());
        assertEquals(1, a.enabled, "enabled by default -> onEnable");

        reg.unregister();
        assertEquals(1, a.disabled);
        assertEquals(1, a.disposed);
        assertTrue(registry.entry("demo:a").isEmpty());
        assertTrue(registry.layout().active().widgets.containsKey("demo:a"), "layout is kept for later");
    }

    @Test
    void categoryFilterAndToggling() {
        registry.register("demo", new Configurable());
        registry.register("demo", new TestContexts.Box("plain"));
        assertEquals(1, registry.byCategory(WidgetCategory.VOICE).size());
        WidgetInfo info = registry.entry("demo:plain").orElseThrow();
        TestContexts.Box box = (TestContexts.Box) info.widget();
        info.setEnabled(false);
        assertEquals(1, box.disabled);
        registry.tick();
        assertEquals(0, box.ticks, "disabled widgets do not tick");
        info.setEnabled(true);
        registry.tick();
        assertEquals(1, box.ticks);
        info.setShown(false);
        registry.tick();
        assertEquals(2, box.ticks, "hidden widgets keep ticking");
        FakeGraphics g = new FakeGraphics();
        core.renderHud(g, TestContexts.ctx(platform, 320, 240, false));
        assertEquals(0, box.renders, "hidden widgets are not drawn");
    }

    @Test
    void renderPlacesWidgetsAndIsolatesFailures() {
        TestContexts.Box ok = new TestContexts.Box("ok");
        ok.placement = dev.lumora.api.hud.WidgetPlacement.at(1f, 1f).enabledByDefault();
        TestContexts.Box bad = new TestContexts.Box("bad");
        bad.throwInRender = true;
        registry.register("demo", ok);
        registry.register("demo", bad);
        FakeGraphics g = new FakeGraphics();
        core.renderHud(g, TestContexts.ctx(platform, 320, 240, false));
        assertEquals(0, g.depth, "push/pop balanced even when a widget throws");
        WidgetEntry okEntry = registry.entry("demo:ok").orElseThrow();
        assertEquals(280, okEntry.bounds().x, "x = 1.0 * (320 - 40)");
        assertEquals(230, okEntry.bounds().y);
        WidgetEntry badEntry = registry.entry("demo:bad").orElseThrow();
        assertTrue(badEntry.failure().isPresent());
        core.renderHud(new FakeGraphics(), TestContexts.ctx(platform, 320, 240, false));
        assertEquals(1, bad.renders, "failed widget is not drawn again");
        assertEquals(2, ok.renders);
        badEntry.setEnabled(true);
        assertTrue(badEntry.failure().isEmpty(), "re-enabling clears the failure (retry)");
    }

    @Test
    void renderEventCanSkipWidgets() {
        TestContexts.Box box = new TestContexts.Box("box");
        registry.register("demo", box);
        core.events().subscribe(WidgetRenderEvent.class, e -> e.setCancelled(true));
        core.renderHud(new FakeGraphics(), TestContexts.ctx(platform, 320, 240, false));
        assertEquals(0, box.renders);
    }

    @Test
    void settingsAreStoredPerProfileButBoundOnesAreNot() throws Exception {
        Configurable w = new Configurable();
        registry.register("demo", w);
        w.shadow.set(false);
        w.opacity.set(0.5);
        assertEquals(0.5f, w.ownerField);
        var stored = registry.layout().active().widgets.get("demo:cfg").settings;
        assertEquals("false", stored.get("shadow"));
        assertFalse(stored.containsKey("opacity"), "bound setting lives in the owner only");

        registry.activateProfile("pvp");
        assertFalse(w.shadow.get(), "new profile starts as a copy");
        w.shadow.set(true);
        registry.activateProfile("default");
        assertFalse(w.shadow.get(), "each profile keeps its own value");
        registry.activateProfile("pvp");
        assertTrue(w.shadow.get());

        registry.saveNow();
        String json = Files.readString(dir.resolve("lumora/hud-layout.json"));
        assertTrue(json.contains("\"pvp\""));
        assertTrue(json.contains("\"activeProfile\": \"pvp\""));
    }

    @Test
    void positionsSurviveARestart() {
        TestContexts.Box box = new TestContexts.Box("box");
        registry.register("demo", box);
        WidgetEntry e = registry.entry("demo:box").orElseThrow();
        e.state().x = 0.42f;
        e.state().y = 0.7f;
        e.state().scale = 1.5f;
        e.state().layer = 3;
        registry.changed();
        core.stopping();

        core = new LumoraCore(new FakePlatform(dir));
        registry = core.widgetRegistry();
        registry.register("demo", new TestContexts.Box("box"));
        WidgetEntry again = registry.entry("demo:box").orElseThrow();
        assertEquals(0.42f, again.x());
        assertEquals(0.7f, again.y());
        assertEquals(1.5f, again.scale());
        assertEquals(3, again.layer());
    }
}
