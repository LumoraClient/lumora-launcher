package dev.lumora.core;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.AddonState;
import dev.lumora.api.chat.ChatIcons;
import dev.lumora.api.chat.ChatInputButton;
import dev.lumora.api.chat.ChatPanel;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.chat.ChatApi;
import dev.lumora.core.extras.GameExtras;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lumora API 1.9: buttons and panels at the chat input, inline icons, text into the chat input. */
class Api19Test {
    @TempDir
    Path dir;
    private LumoraCore core;
    private FakePlatform platform;
    /** The fake chat input: text and cursor; null = chat closed. */
    final AtomicReference<GameExtras.ChatInput> input = new AtomicReference<>();
    /** The width Lumora gave the chat text field (-1: never set). */
    final java.util.concurrent.atomic.AtomicInteger inputWidth = new java.util.concurrent.atomic.AtomicInteger(-1);

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    private AddonContext start(String id, String permissions) throws Exception {
        platform = new FakePlatform(dir);
        platform.extras = (GameExtras) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{GameExtras.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "chatInput" -> input.get();
                    case "setChatInput" -> {
                        input.set(new GameExtras.ChatInput((String) args[0], args.length > 1 ? (Integer) args[1] : ((String) args[0]).length()));
                        yield null;
                    }
                    case "guiSize" -> new int[]{427, 240};
                    case "setChatInputWidth" -> {
                        inputWidth.set((Integer) args[0]);
                        yield null;
                    }
                    default -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null;
                });
        core = new LumoraCore(platform);
        AddonContext[] ctx = new AddonContext[1];
        var addon = new AddonLifecycleTest.TestAddon(c -> ctx[0] = c);
        var json = AddonLifecycleTest.json(id, "\"permissions\":[" + permissions + "]").replace("\"api\":\"1.0.0\"", "\"api\":\"1.9.0\"");
        var d = dev.lumora.core.addon.AddonDescriptorParser.parse(json, "test.jar");
        core.loadAddons(List.of(dev.lumora.core.addon.AddonCandidate.of(d, () -> addon)));
        assertEquals(AddonState.ENABLED, core.addonManager().record(id).orElseThrow().state());
        return ctx[0];
    }

    /** A panel that remembers what happened to it. */
    static final class Panel implements ChatPanel {
        final List<String> events = new ArrayList<>();

        @Override
        public int width() {
            return 100;
        }

        @Override
        public int height() {
            return 60;
        }

        @Override
        public void render(HudGraphics g, int mouseX, int mouseY) {
            events.add("render");
            g.text("panel", 2, 2, 0xFFFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(int mouseX, int mouseY, int button) {
            events.add("click " + mouseX + "," + mouseY);
            return true;
        }

        @Override
        public void opened() {
            events.add("opened");
        }

        @Override
        public void closed() {
            events.add("closed");
        }
    }

    @Test
    void aButtonOpensAndClosesItsPanelAndGoesWithTheAddon() throws Exception {
        AddonContext ctx = start("tools", "\"chat\"");
        Panel panel = new Panel();
        ctx.chat().addInputButton(ChatInputButton.builder("tool", "Tool").label("T").panel(() -> panel).build());
        assertThrows(IllegalArgumentException.class,
                () -> ctx.chat().addInputButton(ChatInputButton.builder("tool", "Again").label("T").onPress(() -> { }).build()));

        FakeGraphics g = new FakeGraphics();
        ChatApi.renderScreen(g, 0, 0);
        assertTrue(g.texts.contains("T"), "button drawn: " + g.texts);
        // the newest button is the rightmost one, inside the input line; the text field ends before it
        int bx = 427 - 2 - 12;
        int by = 240 - 14;
        assertEquals(1, (int) g.texts.stream().filter(t -> t.equals("T")).count());
        assertTrue(inputWidth.get() > 0 && 4 + inputWidth.get() <= bx - 3, "text field ends before the buttons: " + inputWidth.get());
        int withButton = inputWidth.get();
        assertTrue(ChatApi.screenClick(bx + 5, by + 5, 0));
        assertEquals("opened", panel.events.get(0));

        g = new FakeGraphics();
        ChatApi.renderScreen(g, 0, 0);
        assertTrue(g.texts.contains("panel"), "panel drawn above the buttons");
        assertTrue(panel.events.contains("render"));

        // a click inside the panel arrives in panel coordinates and never reaches the chat
        int px = 427 - 2 - 2 - 100;
        int py = by - 2 - 2 - 60;
        assertTrue(ChatApi.screenClick(px + 10, py + 7, 0));
        assertTrue(panel.events.contains("click 10,7"), panel.events.toString());
        assertTrue(ChatApi.screenScroll(px + 10, py + 7, 1), "the wheel over the panel belongs to it");
        assertFalse(ChatApi.screenClick(10, 10, 0), "elsewhere the chat gets the click");

        core.addonManager().setWanted("tools", false);
        assertTrue(panel.events.contains("closed"), "closed with the addon");
        g = new FakeGraphics();
        ChatApi.renderScreen(g, 0, 0);
        assertFalse(g.texts.contains("T"), "button gone with the addon");
        assertEquals(withButton + 13, inputWidth.get(), "text field wider again by one button");
    }

    @Test
    void textGoesToTheCursorAndTheInputIsReadable() throws Exception {
        AddonContext ctx = start("typer", "\"chat\"");
        assertEquals("", ctx.chat().input(), "no open chat");
        ctx.chat().insertText("ignored");
        assertNull(input.get());
        input.set(new GameExtras.ChatInput("hello world", 5));
        ctx.chat().insertText(" :wave:");
        assertEquals("hello :wave: world", input.get().text());
        assertEquals(12, input.get().cursor());
        assertEquals("hello :wave: world", ctx.chat().input());
    }

    @Test
    void iconsReplaceTheirNamesAndCopyingGivesTheNameBack() throws Exception {
        AddonContext ctx = start("icons", "\"chat\"");
        ctx.chat().addIcons(name -> name.equals("lumora_logo") ? new ChatIcons.Icon("icons:logo", "") : null);
        List<ChatApi.Segment> parts = ChatApi.split("Hi :Lumora_Logo: :nope:");
        assertEquals(3, parts.size(), parts.toString());
        assertEquals("Hi ", parts.get(0).text());
        assertEquals("icons:logo", parts.get(1).icon().font());
        assertEquals(":Lumora_Logo:", parts.get(1).text());
        assertEquals(" :nope:", parts.get(2).text());
        assertEquals("Hi :Lumora_Logo:", ChatApi.restore("Hi "));
        assertNull(ChatApi.split("nothing to see"));

        core.addonManager().setWanted("icons", false);
        assertNull(ChatApi.split("Hi :lumora_logo:"), "icons go with the addon");
    }

    @Test
    void theChatPermissionIsNeeded() throws Exception {
        AddonContext ctx = start("noperm", "");
        assertThrows(dev.lumora.api.addon.AddonPermissionException.class, () -> ctx.chat().addIcons(n -> null), "chat() needs the permission chat");
        assertEquals(dev.lumora.api.LumoraAPI.API_VERSION, core.apiVersion());
    }
}
