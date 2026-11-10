package dev.lumora.core.extras;

import dev.lumora.api.game.PlayerSnapshot;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.FakeGraphics;
import dev.lumora.core.FakePlatform;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.extras.ChatOverlay.Edge;
import dev.lumora.core.extras.GameExtras.ChatMessage;
import dev.lumora.core.extras.GameExtras.ChatView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModuleTest {
    @TempDir
    Path dir;
    private LumoraCore core;

    /** A chat as a version adapter reports it: 30 one-line messages, 320 x 180 px, bottom at y 230. */
    static final class Chat implements GameExtras {
        ChatView view = new ChatView(0, 332, 230, 9.0, 20, 0, 30, 320, 180, 1.0, 480, 270);
        final List<ChatMessage> messages = new ArrayList<>();
        boolean leftDown;
        int refreshed;
        String input = "";

        @Override public ChatView chatView() { return view; }
        @Override public ChatMessage chatMessage(int line) { return line < messages.size() ? messages.get(line) : null; }
        @Override public void refreshChat() { refreshed++; }
        @Override public void setChatInput(String text) { input = text; }
        @Override public boolean mouseLeftDown() { return leftDown; }
        @Override public List<Effect> effects() { return List.of(); }
        @Override public int count(String itemId) { return 0; }
        @Override public String heldItemId() { return ""; }
        @Override public String heldItemName() { return ""; }
        @Override public long dayTime() { return 0; }
        @Override public List<String> resourcePacks() { return List.of(); }
        @Override public int targetEntityId() { return -1; }
        @Override public int hurtTime(int entityId) { return -1; }
        @Override public int ownHurtTime() { return 0; }
        @Override public double gamma() { return 0.5; }
        @Override public void setGamma(double value) { }
        @Override public int sprintKey() { return -1; }
        @Override public int sneakKey() { return -1; }
        @Override public void holdSprint(boolean down) { }
        @Override public void holdSneak(boolean down) { }
    }

    private static ChatMessage message(String text, int line) {
        return new ChatMessage(text, List.of(), List.of(), List.of(), line, line);
    }

    private ChatModule load(FakePlatform p) {
        core = new LumoraCore(p);
        core.loadAddons(List.of());
        ChatModule m = ChatModule.active();
        assertNotNull(m, "the chat module is on by default");
        return m;
    }

    private FakePlatform platform(Chat chat) {
        FakePlatform p = new FakePlatform(dir);
        p.extras = chat;
        p.inWorld = true;
        p.player = new PlayerSnapshot(UUID.randomUUID(), "Alex", 0, 64, 0, 0, 0, 20, 20, 20, 0, 0, true, false, false, 0);
        return p;
    }

    @AfterEach
    void stop() {
        if (core != null) {
            core.stopping();
        }
    }

    @Test
    void theSenderIsFoundLikeServersWriteIt() {
        List<String> none = List.of();
        assertEquals("Steve", ChatModule.sender("<Steve> hi", none, none, none), "Minecraft's own chat");
        assertEquals("Steve", ChatModule.sender("[14:05] <Steve> hi (x2)", none, none, none), "with Lumora's timestamp");
        assertEquals("Alex", ChatModule.sender("[VIP] Alex: hello Steve", none, none, List.of("Steve", "Alex")),
                "the player in front of the colon, not the one mentioned");
        assertEquals("Notch", ChatModule.sender("[Owner] Notch » hey", List.of("Notch"), none, none), "shift-click insertion");
        assertEquals("Jeb_", ChatModule.sender("✦ Jeb_ ✦ hey", none, List.of("/msg Jeb_ "), none), "/msg suggestion");
        assertEquals("Dinnerbone", ChatModule.sender("[Admin] Dinnerbone: test", none, none, none), "name before a separator");
        assertEquals("Steve", ChatModule.sender("Steve joined the game", none, none, List.of("Steve")));
        assertNull(ChatModule.sender("Server restarting in 5 minutes", none, none, List.of("Alex")));
        assertNull(ChatModule.sender("Hallo", List.of("not a name!"), none, none));
    }

    @Test
    void linksComeFromClickEventsAndTheText() {
        assertEquals(List.of("https://lumora.dev/x"), ChatModule.links("look: https://lumora.dev/x.", List.of()));
        assertEquals(List.of("https://a.de/page"), ChatModule.links("a.de https://a.de/page", List.of("https://a.de/page")));
        assertEquals(List.of("www.example.com"), ChatModule.links("see www.example.com!", List.of()));
        assertEquals(3, ChatModule.links("http://a.de http://b.de http://c.de http://d.de", List.of()).size(), "at most three");
        assertEquals("example.com", ChatOverlay.host("https://www.example.com/a?b=c"));
    }

    @Test
    void theOwnNameIsAMentionUnlessItIsTheOwnMessage() {
        assertTrue(ChatModule.mentions("hey alex, come here", "Alex", List.of()));
        assertFalse(ChatModule.mentions("<Alex> hello everyone", "Alex", List.of()), "own message");
        assertFalse(ChatModule.mentions("Alexander is here", "Alex", List.of()), "only the whole name");
        assertFalse(ChatModule.mentions("hey", "", List.of()));
        assertEquals("[09:05] hi", "[09:05] " + ChatModule.clean("[14:05:09] hi (x3)"));
    }

    @Test
    void theMixinsReadSizeLookAndHistory() {
        ChatModule m = load(platform(new Chat()));
        assertEquals(-1, ChatModule.width(), "Minecraft's size until the player changes it");
        assertEquals(-1, ChatModule.height(true));
        m.customSize.set(true);
        assertEquals(320, ChatModule.width());
        assertEquals(180, ChatModule.height(true));
        assertEquals(90, ChatModule.height(false));
        m.heightOpen.set(60);
        assertEquals(60, ChatModule.height(false), "the closed chat is never higher than the open one");
        assertEquals(0.5, ChatModule.backgroundOpacity(0.3));
        m.background.set(false);
        assertEquals(0.0, ChatModule.backgroundOpacity(0.3));
        assertEquals(200.0, ChatModule.fadeTicks(200.0), "10 s like Minecraft");
        m.fadeSeconds.set(30);
        assertEquals(600, ChatModule.fadeTicks(200));
        m.fade.set(false);
        assertTrue(ChatModule.fadeTicks(200.0) > 1.0e8, "stays forever");
        assertEquals(500, ChatModule.history(100));
        assertTrue(ChatModule.textShadow(true));
        m.textShadow.set(false);
        assertTrue(ChatModule.textShadow(true), "other text keeps its shadow");
        ChatModule.renderingChat(true);
        assertFalse(ChatModule.textShadow(true), "chat text has none");
        ChatModule.renderingChat(false);
    }

    @Test
    void edgesAndLinesUnderTheMouse() {
        ChatView v = new Chat().view;
        assertEquals(50, v.top());
        assertEquals(Edge.WIDTH, ChatOverlay.edgeAt(v, 333, 100));
        assertEquals(Edge.HEIGHT, ChatOverlay.edgeAt(v, 100, 51));
        assertEquals(Edge.BOTH, ChatOverlay.edgeAt(v, 331, 49));
        assertEquals(Edge.NONE, ChatOverlay.edgeAt(v, 100, 100));
        assertEquals(0, ChatOverlay.lineAt(v, 10, 225));
        assertEquals(1, ChatOverlay.lineAt(v, 10, 215));
        assertEquals(0, ChatOverlay.lineAt(v, 340, 225), "the ⋯ button belongs to the line");
        assertEquals(-1, ChatOverlay.lineAt(v, 10, 235), "below the chat");
        ChatView scrolled = new ChatView(0, 332, 230, 9.0, 20, 5, 30, 320, 180, 1.0, 480, 270);
        assertEquals(6, ChatOverlay.lineAt(scrolled, 10, 215));
        ChatView few = new ChatView(0, 332, 230, 9.0, 20, 0, 2, 320, 180, 1.0, 480, 270);
        assertEquals(-1, ChatOverlay.lineAt(few, 10, 200), "no third message");

        assertEquals(List.of(200, 180), toList(ChatOverlay.dragged(v, Edge.WIDTH, 212, 0, 320, 180, 1000, 800)));
        assertEquals(List.of(320, 100), toList(ChatOverlay.dragged(v, Edge.HEIGHT, 0, 130, 320, 180, 1000, 800)));
        assertEquals(List.of(40, 20), toList(ChatOverlay.dragged(v, Edge.BOTH, -50, 400, 320, 180, 1000, 800)), "minimum");
        assertEquals(466, ChatOverlay.dragged(v, Edge.WIDTH, 2000, 0, 320, 180, 1000, 800)[0], "not wider than the screen");
        assertEquals(228, ChatOverlay.dragged(v, Edge.HEIGHT, 0, -500, 320, 180, 1000, 800)[1], "not higher than the screen");
    }

    private static List<Integer> toList(int[] a) {
        return List.of(a[0], a[1]);
    }

    @Test
    void draggingTheEdgeResizesTheChat() {
        Chat chat = new Chat();
        FakePlatform p = platform(chat);
        ChatModule m = load(p);
        HudGraphics g = new FakeGraphics();
        long now = 1000;
        m.overlay.render(g, chat, 100, 100, now);
        assertTrue(ChatModule.chatClick(332, 100, 0), "grabbing the right edge");
        chat.leftDown = true;
        m.overlay.render(g, chat, 212, 100, now += 16);
        assertTrue(m.customSize.get());
        assertEquals(200, m.width.get());
        assertEquals(180, m.heightOpen.get(), "the height stays");
        assertTrue(chat.refreshed > 0, "the lines are wrapped again");
        chat.leftDown = false;
        m.overlay.render(g, chat, 212, 100, now += 16);
        assertEquals(Edge.NONE, m.overlay.drag);
        assertFalse(ChatModule.chatClick(100, 100, 0), "a click in the chat is Minecraft's");
        m.resize.set(false);
        assertFalse(ChatModule.chatClick(332, 100, 0), "resizing can be switched off");
    }

    @Test
    void theMenuCopiesTheMessageTheNameOrTheLinkAndReplies() {
        Chat chat = new Chat();
        FakePlatform p = platform(chat);
        chat.messages.add(message("[14:05] <Steve> look at https://example.com/a", 0));
        chat.messages.add(message("Server restarting", 1));
        ChatModule m = load(p);
        FakeGraphics g = new FakeGraphics();
        m.overlay.render(g, chat, 100, 225, 1000);
        assertTrue(ChatModule.chatClick(100, 225, 1), "right-click on a message opens the menu");
        List<ChatOverlay.Item> items = m.overlay.menu.items;
        assertEquals(4, items.size(), "copy, name, reply, link");
        m.overlay.render(g, chat, 100, 225, 1016);
        ChatOverlay.Menu menu = m.overlay.menu;
        assertTrue(ChatModule.chatClick(menu.x + 5, menu.y + 3, 0));
        assertEquals("<Steve> look at https://example.com/a", p.clipboard, "without Lumora's timestamp");
        assertNull(m.overlay.menu);

        items.get(1).action().run();
        assertEquals("Steve", p.clipboard);
        items.get(2).action().run();
        assertEquals("/msg Steve ", chat.input);
        items.get(3).action().run();
        assertEquals("https://example.com/a", p.clipboard);

        assertEquals(1, m.overlay.items(chat, chat.messages.get(1)).size(), "no sender, no link: only copy");
        assertEquals(2, m.overlay.items(chat, message("<Alex> my own message", 0)).size(), "no reply to oneself");

        m.overlay.render(g, chat, 100, 225, 1032);
        assertTrue(ChatModule.chatClick(100, 225, 1));
        assertTrue(ChatModule.chatClick(5, 5, 0), "a click next to the menu closes it");
        assertNull(m.overlay.menu);
        m.menu.set(false);
        assertFalse(ChatModule.chatClick(100, 225, 1), "menu switched off");
    }
}
