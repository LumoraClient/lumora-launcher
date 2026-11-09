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

import java.nio.file.Files;
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

        {
            options.put("font", "inter");
        }
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
        String openedTitle;
        List<SettingsView.Section> openedSections = List.of();
        boolean openedWithEditor;

        @Override public void openSettings(String title, String description, SettingsGroup settings, List<Keybind> keybinds,
                                           Runnable onClose, boolean hudEditor) {
            openedTitle = title;
            openedWithEditor = hudEditor;
        }
        @Override public void openSettings(String title, String description, SettingsGroup settings, List<Keybind> keybinds,
                                           List<SettingsView.Section> sections, Runnable onClose, boolean hudEditor) {
            openedSections = sections;
            openSettings(title, description, settings, keybinds, onClose, hudEditor);
        }
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
        for (int page = 0; page < 6; page++) {
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
    void aModuleWithItsOwnHudWidgetIsOneCardWithTheWidgetAsSection() {
        LumoraCore core = core();
        FakeHost host = new FakeHost();
        HubView view = new HubView(core, host);
        List<HubView.Card> cards = view.cards();
        // Block-Overlay registers the "Block info" HUD widget: one card, no separate widget card
        assertTrue(core.widgets().stream().anyMatch(w -> w.addonId().equals("lumora-blockoverlay")));
        assertFalse(cards.stream().anyMatch(c -> c.id().startsWith("widget:lumora-blockoverlay")), cards.toString());
        HubView.Card card = cards.stream().filter(c -> c.id().equals("addon:lumora-blockoverlay")).findFirst().orElseThrow();
        assertTrue(card.settings() != null);
        card.settings().run();
        assertEquals("Block-Overlay", host.openedTitle);
        assertTrue(host.openedWithEditor);
        assertEquals(1, host.openedSections.size());
        SettingsView.Section s = host.openedSections.get(0);
        assertTrue(s.title().startsWith("HUD · "), s.title());
        // the section switch shows and hides the widget
        boolean before = s.on().getAsBoolean();
        s.flip().run();
        assertEquals(!before, s.on().getAsBoolean());
        s.flip().run();
        // the search still finds the card by the widget's name
        String widgetName = s.title().substring("HUD · ".length());
        assertTrue(HubView.filter(cards, HubView.ALL, widgetName).contains(card), widgetName);
        // widgets of Lumora's collections stay cards of their own
        assertTrue(cards.stream().anyMatch(c -> c.id().startsWith("widget:lumora-info") || c.id().startsWith("widget:lumora:")));
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
    void buttonDesignAndListsAreOnTheThemesPageNotModuleCards() {
        LumoraCore core = core();
        FakeHost host = new FakeHost();
        HubView view = new HubView(core, host);
        List<HubView.Card> cards = view.cards();
        assertFalse(cards.stream().anyMatch(c -> c.id().equals("addon:lumora-buttons")), "Button-Design only under Themes");
        assertFalse(cards.stream().anyMatch(c -> c.id().equals("addon:lumora-browser")), "server and world list only under Themes");
        assertTrue(cards.stream().anyMatch(c -> c.id().equals("addon:lumora-connection")), "Verbindung stays a module");
        assertTrue(cards.stream().anyMatch(c -> c.id().equals("addon:lumora-attackindicator")));
        // the Themes page switches the server list of the module
        HubView.select(1);
        FakeGraphics g = new FakeGraphics();
        view.render(g, 854, 480, 0, 0);
        String label = g.texts.contains("Lumora-Serverliste") ? "Lumora-Serverliste" : "Lumora server list";
        assertTrue(g.texts.contains(label), g.texts.toString());
        dev.lumora.api.config.BooleanSetting multiplayer = (dev.lumora.api.config.BooleanSetting) core.addonManager()
                .record("lumora-browser").orElseThrow().configuration().settings().get("multiplayer").orElseThrow();
        boolean before = multiplayer.enabled() && core.addon("lumora-browser").orElseThrow().wanted();
        for (int i = 0; i < 30; i++) { // the rows sit at the bottom of the page: scroll down
            view.mouseScrolled(427, 300, -5);
            view.render(new FakeGraphics(), 854, 480, 427, 300);
        }
        boolean clicked = false;
        for (int y = 40; y < 480 && !clicked; y += 3) {
            HubView.select(1);
            view.render(new FakeGraphics(), 854, 480, 600, y);
            view.mouseClicked(600, y, 0);
            clicked = (multiplayer.enabled() && core.addon("lumora-browser").orElseThrow().wanted()) != before;
        }
        assertTrue(clicked, "a click on the row switches the Lumora server list");
        core.stopping(); // waits for the settings to be written before the temp folder is removed
    }

    @Test
    void theHeartMarksAFavoriteThatComesFirst() {
        LumoraCore core = core();
        HubView view = new HubView(core, new FakeHost());
        assertTrue(view.favorites().isEmpty());
        view.favorites().toggle("addon:lumora-crosshair");
        List<HubView.Card> cards = view.cards();
        assertEquals("addon:lumora-crosshair", cards.get(0).id(), "favorites first");
        assertEquals(1, HubView.filter(cards, HubView.FAVORITES, "", view.favorites().all()).size());
        HubView.select(0);
        FakeGraphics g = new FakeGraphics();
        view.render(g, 854, 480, 0, 0);
        assertTrue(g.texts.contains("Favorites") || g.texts.contains("Favoriten"), "own sidebar entry");
        // a click on the heart of the first card (top left) takes the favorite away again
        boolean clicked = false;
        for (int y = 40; y < 200 && !clicked; y++) {
            for (int x = 150; x < 600 && !clicked; x++) {
                view.render(new FakeGraphics(), 854, 480, x, y);
                view.mouseClicked(x, y, 0);
                clicked = view.favorites().isEmpty();
            }
        }
        assertTrue(clicked, "the heart toggles the favorite");
        core.stopping();
    }

    @Test
    void theAddonsPageListsTheLibraryAndInstalls() throws Exception {
        FakePlatform platform = new FakePlatform(dir);
        platform.addons = dir.resolve("LumoraClient").resolve("instances").resolve("26.3").resolve("addons");
        Path library = dir.resolve("LumoraClient").resolve("addons");
        dev.lumora.core.addon.AddonLibraryTest.jar(library, "voice-1.0.jar", "voice", "1.0.0", "*");
        LumoraCore core = new LumoraCore(platform);
        core.loadAddons(Extras.candidates(platform));
        HubView view = new HubView(core, new FakeHost());
        HubView.select(5);
        FakeGraphics g = new FakeGraphics();
        view.render(g, 854, 480, 0, 0);
        assertTrue(g.texts.contains("§lAddons"), "page title");
        assertTrue(g.texts.stream().anyMatch(t -> t.contains("voice name")), g.texts.toString());
        String install = g.texts.contains("Installieren") ? "Installieren" : "Install";
        assertTrue(g.texts.contains(install));
        boolean installed = false;
        for (int y = 60; y < 300 && !installed; y += 2) {
            for (int x = 200; x < 850 && !installed; x += 4) {
                HubView.select(5);
                view.render(new FakeGraphics(), 854, 480, x, y);
                view.mouseClicked(x, y, 0);
                installed = Files.isRegularFile(platform.addons.resolve("voice-1.0.jar"));
            }
        }
        assertTrue(installed, "the install button copies the jar into the game's addon folder");
        core.stopping();
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

    @Test
    void fontChipsSwitchTheFont() {
        LumoraCore core = core();
        FakeHost host = new FakeHost();
        HubView view = new HubView(core, host);
        HubView.select(1);
        boolean outfit = false;
        for (int y = 60; y < 470 && !outfit; y += 3) {
            for (int x = 200; x < 840 && !outfit; x += 5) {
                HubView.select(1); // a click may have opened another page
                view.render(new FakeGraphics(), 854, 480, x, y);
                view.mouseClicked(x, y, 0);
                outfit = "outfit".equals(host.options.get("font")) && Boolean.TRUE.equals(host.options.get("lumoraFont"));
                if (LumoraTheme.VANILLA.equals(host.options.get("theme"))) {
                    view.applyTheme(LumoraTheme.LUMORA); // clicked the Vanilla card: back to Lumora
                }
                LumoraTheme.setAccent("purple");
            }
        }
        assertTrue(outfit, "the Outfit chip selects Outfit");
    }

    private static HubView.Card card(String name, String category) {
        return new HubView.Card(name, name, "description", category, "addon", true, null, null, null, null, null);
    }
}
