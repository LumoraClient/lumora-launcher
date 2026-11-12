package dev.lumora.core;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonPermissionException;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.net.PluginChannels;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.render.NameTags;
import dev.lumora.api.screen.AddonView;
import dev.lumora.api.session.SessionService;
import dev.lumora.core.social.GameSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Lumora API 1.1: plugin channels, name tags, addon screens, session, world access, permissions. */
class Api11Test {
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
    }

    private static final String ALL = "\"api\":\"1.1.0\",\"permissions\":[\"screens\",\"players\",\"session\",\"network\",\"nametags\"]";

    private void start(String id, String permissions, Consumer<AddonContext> enable) throws Exception {
        if (core == null) {
            platform = new FakePlatform(dir);
            core = new LumoraCore(platform);
        }
        var addon = new AddonLifecycleTest.TestAddon(ctx -> {
            context = ctx;
            enable.accept(ctx);
        });
        var json = AddonLifecycleTest.json(id, permissions);
        if (permissions.contains("\"api\"")) {
            json = json.replace("\"api\":\"1.0.0\",", "");
        }
        var d = dev.lumora.core.addon.AddonDescriptorParser.parse(json, "test.jar");
        core.loadAddons(List.of(dev.lumora.core.addon.AddonCandidate.of(d, () -> addon)));
        assertEquals(AddonState.ENABLED, core.addonManager().record(id).orElseThrow().state(), "addon " + id + " enabled");
    }

    private void disable(String id) {
        assertTrue(core.addonManager().setWanted(id, false).isEmpty());
    }

    @Test
    void newPermissionsAreParsedAndEnforced() throws Exception {
        start("plain", "\"permissions\":[\"hud\"]", ctx -> { });
        assertThrows(AddonPermissionException.class, () -> context.world());
        assertThrows(AddonPermissionException.class, () -> context.session());
        assertThrows(AddonPermissionException.class, () -> context.channels());
        assertThrows(AddonPermissionException.class, () -> context.screens());
        assertThrows(AddonPermissionException.class, () -> context.nameTags());
        assertEquals(platform.configDir(), context.configDirectory(), "config folder needs no permission");
    }

    @Test
    void addonsForApi11LoadAndOldAddonsStillLoad() throws Exception {
        start("old", "", ctx -> { });
        start("new", ALL, ctx -> { });
        assertEquals("1.6.0", core.apiVersion());
    }

    @Test
    void pluginChannelsDeliverToTheOwnerAndStopAfterDisable() throws Exception {
        List<String> received = new ArrayList<>();
        PluginChannels.PluginChannel[] channel = new PluginChannels.PluginChannel[1];
        start("voice", ALL, ctx -> channel[0] = ctx.channels().register("voicechat:secret",
                bytes -> received.add(new String(bytes, StandardCharsets.UTF_8))));
        assertTrue(platform.mcChannels.contains("voicechat:secret"), "registered with Minecraft");
        assertFalse(channel[0].serverSupports(), "server did not announce it");
        channel[0].send("x".getBytes(StandardCharsets.UTF_8));
        assertTrue(platform.sent.isEmpty());
        platform.serverChannels.add("voicechat:secret");
        assertTrue(channel[0].serverSupports());
        channel[0].send("hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("voicechat:secret:hello"), platform.sent);

        core.channelRegistry().deliver("voicechat:secret", "a".getBytes(StandardCharsets.UTF_8));
        core.channelRegistry().deliver("other:channel", "b".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("a"), received);

        disable("voice");
        core.channelRegistry().deliver("voicechat:secret", "c".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("a"), received, "nothing delivered after disable");
        assertFalse(channel[0].serverSupports());
    }

    @Test
    void pluginChannelsRejectBadIdsAndDoubleOwners() throws Exception {
        start("first", ALL, ctx -> {
            assertThrows(IllegalArgumentException.class, () -> ctx.channels().register("NoNamespace", b -> { }));
            assertThrows(IllegalArgumentException.class, () -> ctx.channels().register("a:B", b -> { }));
            ctx.channels().register("shared:chan", b -> { });
        });
        start("second", ALL, ctx -> assertThrows(IllegalStateException.class, () -> ctx.channels().register("shared:chan", b -> { })));
    }

    @Test
    void channelsThatTheVersionCannotRegisterAreNeverSupported() throws Exception {
        PluginChannels.PluginChannel[] channel = new PluginChannels.PluginChannel[1];
        platform = new FakePlatform(dir);
        platform.maxChannelLength = 20; // Minecraft 1.8
        core = new LumoraCore(platform);
        start("voice", ALL, ctx -> channel[0] = ctx.channels().register("voicechat:request_secret", b -> { }));
        platform.serverChannels.add("voicechat:request_secret");
        assertFalse(channel[0].serverSupports());
    }

    @Test
    void failingReceiverDoesNotBreakDelivery() throws Exception {
        start("voice", ALL, ctx -> ctx.channels().register("x:y", b -> { throw new IllegalStateException("boom"); }));
        core.channelRegistry().deliver("x:y", new byte[0]);
        assertTrue(platform.logged("Plugin channel x:y of voice failed"));
    }

    @Test
    void nameTagsFirstIconWinsBrokenDecoratorIsSkipped() throws Exception {
        UUID steve = UUID.randomUUID();
        var speaker = new NameTags.Icon("voice:nametags", "", 0xFFFFFF, "♪", "Kanal");
        int[] brokenCalls = {0};
        start("broken", ALL, ctx -> ctx.nameTags().register((id, place) -> { brokenCalls[0]++; throw new IllegalStateException("x"); }));
        start("voice", ALL, ctx -> ctx.nameTags().register((id, place) ->
                id.equals(steve) && place == NameTags.Place.WORLD ? Optional.of(speaker) : Optional.empty()));
        assertTrue(core.nameTags().active());
        assertEquals(speaker, core.nameTags().icon(steve, NameTags.Place.WORLD).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new NameTags.Icon("Bad Font", "x", 0, "", ""));
        assertEquals("Kanal", core.nameTags().icon(steve, NameTags.Place.WORLD).orElseThrow().subtitle());
        assertTrue(core.nameTags().icon(steve, NameTags.Place.TAB_LIST).isEmpty());
        assertTrue(core.nameTags().icon(UUID.randomUUID(), NameTags.Place.WORLD).isEmpty());
        assertEquals(1, brokenCalls[0], "a failing decorator is asked once and then skipped");
        disable("voice");
        assertTrue(core.nameTags().icon(steve, NameTags.Place.WORLD).isEmpty());
        disable("broken");
        assertFalse(core.nameTags().active());
    }

    static final class View implements AddonView {
        final List<String> calls = new ArrayList<>();
        boolean throwOnRender;

        @Override
        public void render(HudGraphics g, int width, int height, int mouseX, int mouseY) {
            calls.add("render");
            if (throwOnRender) {
                throw new IllegalStateException("broken menu");
            }
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            calls.add("click");
            return true;
        }

        @Override
        public void removed() {
            calls.add("removed");
        }
    }

    @Test
    void screensStackCloseAndCleanUp() throws Exception {
        View settings = new View(), dialog = new View();
        start("voice", ALL, ctx -> { });
        context.screens().open(settings);
        context.screens().open(dialog);
        assertEquals(2, platform.screenStack.size());
        assertTrue(platform.screenStack.peek().mouseClicked(1, 1, 0), "input reaches the view");
        assertEquals(List.of("click"), dialog.calls);
        context.screens().back();
        assertEquals(List.of("click", "removed"), dialog.calls);
        assertEquals(1, platform.screenStack.size());

        platform.clipboard = "copied";
        assertEquals("copied", context.screens().clipboard());
        context.screens().openAddonSettings();
        assertEquals(List.of("voice"), platform.openedSettings);

        disable("voice");
        assertTrue(platform.screenStack.isEmpty(), "disabling closes the addon's menus");
        assertTrue(settings.calls.contains("removed"));
    }

    @Test
    void registeredMenuOpensFromTheHubAndIsRemovedOnDisable() throws Exception {
        List<View> created = new ArrayList<>();
        start("voice", ALL, ctx -> ctx.screens().registerMenu(() -> {
            View v = new View();
            created.add(v);
            return v;
        }));
        assertTrue(core.views().hasMenu("voice"));
        assertFalse(core.views().hasMenu("other"));
        assertTrue(core.views().openMenu("voice"));
        assertEquals(1, created.size(), "a fresh view per open");
        assertEquals(1, platform.screenStack.size());
        disable("voice");
        assertTrue(platform.screenStack.isEmpty(), "menu opened from the hub is closed with the addon");
        assertFalse(core.views().hasMenu("voice"));
        assertFalse(core.views().openMenu("voice"));
    }

    @Test
    void brokenViewIsLoggedOnceAndStaysClosable() throws Exception {
        View view = new View();
        view.throwOnRender = true;
        start("voice", ALL, ctx -> ctx.screens().open(view));
        AddonView shown = platform.screenStack.peek();
        shown.render(new FakeGraphics(), 100, 100, 0, 0);
        shown.render(new FakeGraphics(), 100, 100, 0, 0);
        assertEquals(1, platform.logs.stream().filter(l -> l.contains("Menu of voice failed in render")).count());
        assertTrue(shown.closeRequested());
    }

    @Test
    void sessionHidesTheTokenAndJoinsWithUndashedUuid() throws Exception {
        UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        List<String> joins = new ArrayList<>();
        var service = new dev.lumora.core.session.SessionServiceImpl(
                () -> Optional.of(new GameSession("Notch", id.toString().replace("-", ""), () -> "token-123")),
                (token, uuid, hash) -> joins.add(token + "/" + uuid + "/" + hash));
        assertEquals(new SessionService.Profile(id, "Notch"), service.profile().orElseThrow());
        service.joinServer("-1a2b");
        assertEquals(List.of("token-123/069a79f444e94726a5befca90e38aaf5/-1a2b"), joins);
        assertThrows(IllegalArgumentException.class, () -> service.joinServer(""));

        var dashed = new dev.lumora.core.session.SessionServiceImpl(
                () -> Optional.of(new GameSession("Notch", id.toString(), () -> "t")), (t, u, h) -> { });
        assertEquals(id, dashed.profile().orElseThrow().id(), "1.20+ reports UUIDs with dashes");

        var none = new dev.lumora.core.session.SessionServiceImpl(Optional::empty, (t, u, h) -> fail("no join without session"));
        assertTrue(none.profile().isEmpty());
        assertThrows(IOException.class, () -> none.joinServer("abc"));
    }

    @Test
    void contextHandsOutPlatformWorldAndModCheck() throws Exception {
        start("voice", ALL, ctx -> { });
        platform.mods.add("voicechat");
        assertTrue(context.modLoaded("voicechat"));
        assertFalse(context.modLoaded("other"));
        assertSame(dev.lumora.api.game.WorldAccess.NONE, context.world());
        assertTrue(context.world().players(10).isEmpty());
        assertTrue(context.session().profile().isEmpty());
    }
}
