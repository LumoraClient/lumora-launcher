package dev.lumora.core;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonPermissionException;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.chat.ChatLine;
import dev.lumora.api.chat.ChatMenuEntry;
import dev.lumora.api.event.chat.ChatMessageEvent;
import dev.lumora.api.hud.WidgetPlacement;
import dev.lumora.api.ui.Theme;
import dev.lumora.api.ui.UiKit;
import dev.lumora.core.chat.ChatApi;
import dev.lumora.core.hud.WidgetEntry;
import dev.lumora.core.theme.LumoraTheme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lumora API 1.5: theme and accents, UiKit, anchored widgets, chat menu entries and messages, social info. */
class Api15Test {
    @TempDir
    Path dir;
    private LumoraCore core;
    private FakePlatform platform;
    private AddonContext context;

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
        LumoraTheme.setAccent("purple");
    }

    private void start(String id, String permissions, Consumer<AddonContext> enable) throws Exception {
        if (core == null) {
            platform = new FakePlatform(dir);
            core = new LumoraCore(platform);
        }
        var addon = new AddonLifecycleTest.TestAddon(ctx -> {
            context = ctx;
            enable.accept(ctx);
        });
        var json = AddonLifecycleTest.json(id, "\"permissions\":[" + permissions + "]").replace("\"api\":\"1.0.0\"", "\"api\":\"1.5.0\"");
        var d = dev.lumora.core.addon.AddonDescriptorParser.parse(json, "test.jar");
        core.loadAddons(List.of(dev.lumora.core.addon.AddonCandidate.of(d, () -> addon)));
        assertEquals(AddonState.ENABLED, core.addonManager().record(id).orElseThrow().state(), "addon " + id + " enabled");
    }

    @Test
    void permissionsOfTheNewServices() throws Exception {
        start("plain", "\"hud\"", ctx -> { });
        assertEquals(LumoraTheme.accent(), context.theme().accent(), "reading the theme needs no permission");
        assertThrows(AddonPermissionException.class, () -> context.theme().addAccent("gold", "Gold", 0xFFD4A017));
        assertThrows(AddonPermissionException.class, () -> context.chat());
        assertThrows(AddonPermissionException.class, () -> context.social());
        assertEquals("1.5.0", core.apiVersion());
    }

    @Test
    void anAddonAccentIsOfferedChosenAndComesBackWithTheAddon() throws Exception {
        start("gold", "\"design\"", ctx -> ctx.theme().addAccent("gold", "Gold", 0xD4A017));
        Theme t = context.theme();
        assertTrue(t.accents().stream().anyMatch(a -> a.id().equals("gold:gold") && a.color() == 0xFFD4A017), "in the hub's choice");
        LumoraTheme.setAccent("gold:gold");
        assertEquals(0xFFD4A017, t.accent());
        assertEquals("gold:gold", t.accentId());

        assertTrue(core.addonManager().setWanted("gold", false).isEmpty());
        assertEquals("purple", LumoraTheme.current().id(), "addon off: back to Lila");
        assertFalse(LumoraTheme.ACCENTS.stream().anyMatch(a -> a.id().equals("gold:gold")));
        assertTrue(core.addonManager().setWanted("gold", true).isEmpty());
        assertEquals("gold:gold", LumoraTheme.current().id(), "the player's choice comes back with the addon");
        LumoraTheme.removeAccent("purple");
        assertTrue(LumoraTheme.ACCENTS.stream().anyMatch(a -> a.id().equals("purple")), "Lumora's own accents stay");
    }

    @Test
    void uiKitDrawsInTheThemeColours() throws Exception {
        start("ui", "", ctx -> { });
        Theme t = context.theme();
        FakeGraphics g = new FakeGraphics();
        UiKit.panel(g, t, 0, 0, 300, 200);
        UiKit.title(g, t, 10, 10, "Titel");
        UiKit.button(g, t, 10, 30, 80, 20, "Start", true, true);
        UiKit.toggle(g, t, 100, 34, true, false);
        UiKit.slider(g, t, 10, 60, 100, 0.5, false);
        UiKit.textField(g, t, 10, 80, 100, 16, "", "Name …", false);
        assertTrue(g.texts.stream().anyMatch(s -> s.contains("Start")));
        assertTrue(g.texts.stream().anyMatch(s -> s.contains("Name …")), "placeholder while empty");
        assertTrue(g.fills > 5);
        assertTrue(UiKit.inside(15, 35, 10, 30, 80, 20));
        assertEquals(0.5, UiKit.sliderValue(10 + 4 + 46, 10, 100), 1e-9);
    }

    @Test
    void aWidgetCanHangOnAnAnchorZone() {
        platform = new FakePlatform(dir);
        core = new LumoraCore(platform);
        TestContexts.Box box = new TestContexts.Box("corner");
        box.placement = WidgetPlacement.anchored("bottom_right", 4, 30).enabledByDefault();
        core.widgetRegistry().register("demo", box);
        WidgetEntry e = core.widgetRegistry().entry("demo:corner").orElseThrow();
        assertEquals("bottom_right", e.state().anchor);
        assertEquals(List.of(4, 30), List.of(e.state().offsetX, e.state().offsetY));
        assertThrows(IllegalArgumentException.class, () -> WidgetPlacement.anchored("nowhere", 0, 0));
        WidgetPlacement old = new WidgetPlacement(0.5f, 1f, 1f, true);
        assertFalse(old.anchoredPlacement(), "the 4-argument constructor of 1.0 still works");
    }

    @Test
    void chatMenuEntriesAndMessageEvents() throws Exception {
        List<String> ran = new ArrayList<>();
        List<ChatLine> received = new ArrayList<>();
        start("chatty", "\"chat\"", ctx -> {
            ctx.chat().addMenuEntry(ChatMenuEntry.builder("shout", "Laut").label(l -> "Laut: " + l.sender())
                    .visibleWhen(l -> l.sender() != null).onSelect(l -> ran.add(l.text())).build());
            ctx.events().subscribe(ChatMessageEvent.class, e -> received.add(e.line()));
        });
        ChatApi.received("<Steve> schau mal https://lumora.dev");
        assertEquals(1, received.size());
        assertEquals("Steve", received.get(0).sender());
        assertEquals(List.of("https://lumora.dev"), received.get(0).links());

        List<ChatApi.MenuItem> items = core.chat().menuEntries(received.get(0));
        assertEquals(1, items.size());
        assertEquals("Laut: Steve", items.get(0).label());
        items.get(0).action().run();
        assertEquals(List.of("<Steve> schau mal https://lumora.dev"), ran);
        assertTrue(core.chat().menuEntries(new ChatLine("Server neu gestartet", null, List.of())).isEmpty(), "condition");

        assertTrue(core.addonManager().setWanted("chatty", false).isEmpty());
        assertTrue(core.chat().menuEntries(received.get(0)).isEmpty(), "removed with the addon");
    }

    @Test
    void socialInfoIsEmptyWithoutAConnection() throws Exception {
        start("voice", "\"social\"", ctx -> { });
        var s = context.social();
        assertFalse(s.connected());
        assertTrue(s.friends().isEmpty());
        assertTrue(s.party().isEmpty());
        assertTrue(s.self().isEmpty());
        assertFalse(s.isFriend(java.util.UUID.randomUUID()));
    }
}
