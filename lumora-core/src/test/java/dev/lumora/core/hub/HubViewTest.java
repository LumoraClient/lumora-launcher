package dev.lumora.core.hub;

import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.input.Keybind;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.extras.Extras;
import dev.lumora.core.theme.LumoraTheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubViewTest {
    @TempDir
    Path dir;

    /** Client settings in a map, like lumora-client.json. */
    static final class FakeHost implements HubHost {
        final Map<String, Object> options = new HashMap<>(Map.of(
                "theme", "lumora", "accentColor", "purple", "customMainMenu", true, "menuBackground", true,
                "customLoadingScreen", true, "lumoraFont", true, "menuShaders", "auto", "zoomEnabled", true,
                "zoomFactor", 4.0, "transitionMillis", 220));
        int editorOpened;

        @Override public String versionLine() { return "Minecraft test · Java 25"; }
        @Override public boolean inWorld() { return false; }
        @Override public boolean has(String option) { return options.containsKey(option); }
        @Override public Object get(String option) { return options.get(option); }
        @Override public void set(String option, Object value) {
            options.put(option, value);
            if (option.equals("accentColor")) {
                LumoraTheme.setAccent(String.valueOf(value));
            }
        }
        @Override public boolean fontSupported() { return true; }
        @Override public String zoomKey() { return "C"; }
        @Override public void openHudEditor() { editorOpened++; }
        @Override public void openSettings(String title, SettingsGroup settings, List<Keybind> keybinds, Runnable onClose) { }
        @Override public boolean classicMenuUntilRestart() { return false; }
        @Override public void showMainMenu(boolean lumora) { }
        @Override public String renderer() { return "OpenGL"; }
        @Override public String gpu() { return "Test GPU"; }
        @Override public String protocol() { return null; }
        @Override public boolean openProtocol() { return false; }
        @Override public void startBenchmark() { }
        @Override public String writeDiagnostics() { return "game.json"; }
        @Override public Path diagnosticsDirectory() { return null; }
        @Override public void openFolder(Path dir) { }
        @Override public void close() { }
    }

    private LumoraCore core() {
        FakePlatform platform = new FakePlatform(dir);
        LumoraCore core = new LumoraCore(platform);
        core.loadAddons(Extras.candidates(platform));
        return core;
    }

    @AfterEach
    void resetAccent() {
        LumoraTheme.setAccent("purple");
        HubView.select(0);
    }

    @Test
    void everyPageRendersOnSmallAndLargeScreens() {
        LumoraCore core = core();
        HubView view = new HubView(core, new FakeHost());
        for (int page = 0; page < 5; page++) {
            HubView.select(page);
            for (int[] size : new int[][]{{320, 240}, {427, 240}, {854, 480}, {1280, 720}}) {
                FakeGraphics g = new FakeGraphics();
                view.render(g, size[0], size[1], size[0] / 2, size[1] / 2);
                assertEquals(0, g.depth, "push/pop balanced");
                assertTrue(g.texts.contains("§lLumora"), "brand on page " + page);
            }
        }
    }

    @Test
    void modulePageShowsBuiltInModulesAsCards() {
        LumoraCore core = core();
        HubView view = new HubView(core, new FakeHost());
        List<HubView.Card> cards = view.cards();
        assertTrue(cards.stream().anyMatch(c -> c.name().equals("Zoom")));
        assertTrue(cards.stream().anyMatch(c -> c.id().equals("addon:lumora-crosshair")));
        // the info widgets addon is only a container: its widgets have cards, the addon itself has none
        assertFalse(cards.stream().anyMatch(c -> c.id().equals("addon:lumora-info")));
        HubView.select(0);
        FakeGraphics g = new FakeGraphics();
        view.render(g, 854, 480, 0, 0);
        assertTrue(g.texts.contains("Zoom"));
        assertTrue(g.texts.stream().anyMatch(t -> t.endsWith(" modules")));
    }

    @Test
    void categoriesStartWithAllAndEndWithAddons() {
        List<HubView.Card> cards = List.of(
                card("a", "addons"), card("b", "world"), card("c", "player"), card("d", "custom"), card("e", "general"));
        assertEquals(List.of("all", "general", "player", "world", "custom", "addons"), HubView.categories(cards));
    }

    @Test
    void searchFiltersByNameAndDescriptionWithinTheCategory() {
        List<HubView.Card> cards = List.of(card("FPS", "general"), card("Kompass", "world"), card("Ping", "server"));
        assertEquals(1, HubView.filter(cards, "all", "fp").size());
        assertEquals(3, HubView.filter(cards, "all", "").size());
        assertEquals(0, HubView.filter(cards, "server", "kompass").size());
        assertEquals(1, HubView.filter(cards, "world", "KOMP").size());
        assertEquals(3, HubView.filter(cards, "all", "description").size());
    }

    @Test
    void vanillaThemeSwitchesLumoraMenusOffAndBack() {
        LumoraCore core = core();
        FakeHost host = new FakeHost();
        HubView view = new HubView(core, host);
        view.applyTheme(LumoraTheme.VANILLA);
        assertEquals("vanilla", host.options.get("theme"));
        assertEquals(false, host.options.get("customMainMenu"));
        assertEquals(false, host.options.get("lumoraFont"));
        assertEquals("off", host.options.get("menuShaders"));
        assertFalse(core.addon("lumora-browser").orElseThrow().wanted());
        view.applyTheme(LumoraTheme.LUMORA);
        assertEquals("lumora", host.options.get("theme"));
        assertEquals(true, host.options.get("customLoadingScreen"));
        assertEquals("auto", host.options.get("menuShaders"));
        assertTrue(core.addon("lumora-browser").orElseThrow().wanted());
    }

    @Test
    void accentColoursAndMixing() {
        assertEquals(0xFF8B5CF6, LumoraTheme.accent());
        assertEquals(0xFFA27BFA, LumoraTheme.accentHover());
        LumoraTheme.setAccent("blue");
        assertEquals(0xFF3B82F6, LumoraTheme.accent());
        assertEquals(0x803B82F6, LumoraTheme.recolor(0x808B5CF6));
        LumoraTheme.setAccent("unknown");
        assertEquals(LumoraTheme.DEFAULT, LumoraTheme.current());
        assertEquals(0xFF808080, LumoraTheme.mix(0xFF000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x40123456, LumoraTheme.alpha(0xFF123456, 0x40));
    }

    @Test
    void newProfileIsCreatedFromTheSidebarButton() {
        LumoraCore core = core();
        HubView view = new HubView(core, new FakeHost());
        HubView.select(0);
        FakeGraphics g = new FakeGraphics();
        view.render(g, 854, 480, 0, 0);
        int before = core.hudEditor().profiles().size();
        // the button sits at the bottom of the sidebar, above "General"
        boolean clicked = false;
        for (int y = 300; y < 470 && !clicked; y += 2) {
            if (view.mouseClicked(80, y, 0) && core.hudEditor().profiles().size() > before) {
                clicked = true;
            }
            view.render(g, 854, 480, 0, 0);
        }
        assertTrue(clicked);
        assertEquals("custom-1", core.hudEditor().activeProfile());
    }

    private static HubView.Card card(String name, String category) {
        return new HubView.Card(name, name, "description", category, "addon", true, null, null, null, null, null);
    }
}
