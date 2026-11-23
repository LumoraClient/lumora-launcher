package dev.lumora.core.chat.windows;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.FakeGraphics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lumora's chat windows: rules, suggestions, the saved layout, drawing, tabs, dragging and the menus. */
class ChatWindowsTest {
    static ChatEntry entry(String clean, String sender) {
        return new ChatEntry(1, clean, clean, sender, ChatAnalysis.prefix(clean, sender), ChatAnalysis.rank(clean, sender), null, 0);
    }

    @Test
    void prefixAndRank() {
        assertEquals("[Baublase]", ChatAnalysis.prefix("[Baublase] Learned word: Laterne", null));
        assertEquals("Baublase", ChatAnalysis.prefix("Baublase | Folge uns auf TikTok", null));
        assertNull(ChatAnalysis.prefix("Steve: hi", "Steve"), "the sender is no prefix");
        assertNull(ChatAnalysis.prefix("<Steve> hi", "Steve"));
        assertEquals("Spieler", ChatAnalysis.rank("[x] Baum Spieler Dragocookie18: Hi", "Dragocookie18"));
        assertEquals("Jr.Mod", ChatAnalysis.rank("[x] Baum Jr.Mod Axolotle_GG: Hallo", "Axolotle_GG"));
        assertEquals("[VIP]", ChatAnalysis.rank("[VIP] » Steve: hi", "Steve"), "separators are skipped");
        assertNull(ChatAnalysis.rank("Steve: hi", "Steve"));
        assertEquals("Baublase", ChatAnalysis.bare("[Baublase]"));
    }

    @Test
    void rules() {
        ChatEntry m = entry("[x] Spieler Steve: hey Alex, look", "Steve");
        assertTrue(new ChatRule(ChatRule.Kind.PLAYER, "steve").matches(m, "Alex"));
        assertTrue(new ChatRule(ChatRule.Kind.PREFIX, "[x]").matches(m, "Alex"));
        assertTrue(new ChatRule(ChatRule.Kind.RANK, "spieler").matches(m, "Alex"));
        assertTrue(new ChatRule(ChatRule.Kind.MENTION, "").matches(m, "Alex"));
        assertFalse(new ChatRule(ChatRule.Kind.MENTION, "").matches(m, "Steve"), "own messages never mention oneself");
        assertTrue(new ChatRule(ChatRule.Kind.MENTION, "Lexi, look").matches(m, "Bob"), "extra words");
        assertTrue(new ChatRule(ChatRule.Kind.CONTAINS, "nothing, LOOK").matches(m, "Alex"));
        assertFalse(new ChatRule(ChatRule.Kind.SERVER, "").matches(m, "Alex"));
        assertTrue(new ChatRule(ChatRule.Kind.SERVER, "").matches(entry("Server restarts in 5 minutes", null), "Alex"));
        assertFalse(new ChatRule(ChatRule.Kind.PLAYER, "").complete());
    }

    @Test
    void suggestionsFromTheRecentChat() {
        List<ChatEntry> recent = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            recent.add(entry("[Baublase] Learned word " + i, null));
            recent.add(entry("Spieler Axolotle_GG: hi " + i, "Axolotle_GG"));
        }
        recent.add(entry("Spieler Me: hi", "Me"));
        List<ChatSuggestions.Suggestion> s = ChatSuggestions.recent(recent, "Me", List.of(), 4);
        assertEquals(3, s.size(), s.toString());
        assertTrue(s.stream().anyMatch(x -> x.rule().equals(new ChatRule(ChatRule.Kind.PREFIX, "[Baublase]")) && x.count() == 3));
        assertTrue(s.stream().anyMatch(x -> x.rule().equals(new ChatRule(ChatRule.Kind.PLAYER, "Axolotle_GG"))));
        assertTrue(s.stream().anyMatch(x -> x.rule().equals(new ChatRule(ChatRule.Kind.RANK, "Spieler")) && x.count() == 4));
        assertTrue(s.stream().noneMatch(x -> x.rule().value().equals("Me")), "no tab for the own name");

        List<ChatSuggestions.Suggestion> without = ChatSuggestions.recent(recent, "Me",
                List.of(new ChatRule(ChatRule.Kind.PREFIX, "[baublase]")), 4);
        assertEquals(2, without.size(), "a rule a tab already has is not suggested again");

        List<ChatSuggestions.Suggestion> one = ChatSuggestions.of(recent.get(1), recent, "Me");
        assertEquals(2, one.size(), "player and rank of the message");
    }

    @Test
    void layoutIsSavedAndLoaded(@TempDir Path dir) {
        Path file = dir.resolve("lumora/chat-tabs.json");
        ChatLayout l = new ChatLayout(file);
        ChatTab mentions = new ChatTab(l.newTabId(), "Mentions", new ChatRule(ChatRule.Kind.MENTION, ""));
        mentions.keepInMain = false;
        mentions.sound = true;
        l.add(mentions, l.main(), 0, 0, 0, 0);
        ChatTab bau = new ChatTab(l.newTabId(), "Baublase", new ChatRule(ChatRule.Kind.PREFIX, "[Baublase]"));
        bau.highlight = true;
        ChatWindow w = l.add(bau, null, 300, 60, 180, 70);
        l.save();

        ChatLayout back = new ChatLayout(file);
        back.load();
        assertEquals(2, back.windows.size());
        assertEquals(2, back.main().tabs.size());
        assertTrue(back.main().tabs.get(0).main());
        ChatTab m = back.main().tabs.get(1);
        assertEquals("Mentions", m.name);
        assertFalse(m.keepInMain);
        assertTrue(m.sound);
        ChatWindow w2 = back.windows.get(1);
        assertEquals(300, w2.x);
        assertEquals(60, w2.bottom);
        assertEquals(180, w2.w);
        assertEquals(new ChatRule(ChatRule.Kind.PREFIX, "[Baublase]"), w2.tabs.get(0).rule);
        assertTrue(w2.tabs.get(0).highlight);
        assertNotNull(w);
        // new ids do not clash with loaded ones
        assertFalse(back.newTabId().equals(m.id));

        // a mention leaves the main chat, other messages stay
        assertFalse(back.inMain(entry("hi Alex", "Steve"), "Alex"));
        assertTrue(back.inMain(entry("hi Bob", "Steve"), "Alex"));

        back.remove(w2.tabs.get(0));
        assertEquals(1, back.windows.size(), "a window without tabs goes");
        back.remove(back.mainTab());
        assertEquals(2, back.main().tabs.size(), "the main tab stays");
    }

    // --- windows with a fake Minecraft

    /** Messages are strings, 6 px per character; lines are the wrapped pieces. */
    static final class FakeText implements ChatText {
        final List<String> drawn = new ArrayList<>();
        final List<String> heads = new ArrayList<>();
        final List<Object> clicked = new ArrayList<>();
        final List<String> online = new ArrayList<>();

        @Override
        public List<Object> wrap(Object message, int width) {
            String s = (String) message;
            int per = Math.max(1, width / 6);
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < s.length(); i += per) {
                out.add(s.substring(i, Math.min(s.length(), i + per)));
            }
            return out;
        }

        @Override
        public void draw(HudGraphics g, Object line, int x, int y, int color, boolean shadow) {
            drawn.add((String) line);
        }

        @Override
        public Object styleAt(Object line, int x) {
            return x >= 0 && x < ((String) line).length() * 6 ? "style:" + line : null;
        }

        @Override
        public void hover(HudGraphics g, Object style, int mouseX, int mouseY) {
        }

        @Override
        public boolean click(Object style, boolean shift) {
            clicked.add(style);
            return true;
        }

        @Override
        public Object head(String name) {
            return online.contains(name) ? "head:" + name : null;
        }

        @Override
        public void drawHead(HudGraphics g, Object head, int x, int y, int size) {
            heads.add((String) head);
        }

        @Override
        public Options options() {
            return new Options(1, 0, 1, 0.5, 120, 90, 45, false);
        }

        @Override
        public Parts parts(Object message) {
            return new Parts((String) message, List.of(), List.of(), List.of());
        }
    }

    static final class FakeHost implements ChatWindows.Host {
        final FakeText text = new FakeText();
        final List<AddonView> views = new ArrayList<>();
        boolean mouseDown;
        int sounds;
        int width = 120;
        int height = 90;
        boolean custom;

        @Override public ChatText text() { return text; }
        @Override public String own() { return "Me"; }
        @Override public List<String> online() { return text.online; }
        @Override public boolean customSize() { return custom; }
        @Override public int width() { return width; }
        @Override public int heightOpen() { return height; }
        @Override public int heightClosed() { return 45; }
        @Override public void setMainSize(int w, int h) { custom = true; width = w; height = h; }
        @Override public int widthMax() { return 1000; }
        @Override public int heightMax() { return 800; }
        @Override public boolean fade() { return true; }
        @Override public int fadeSeconds() { return 10; }
        @Override public double backgroundOpacity(double vanilla) { return vanilla; }
        @Override public boolean textShadow() { return true; }
        @Override public int history() { return 500; }
        @Override public boolean heads() { return true; }
        @Override public boolean resize() { return true; }
        @Override public boolean menu() { return true; }
        @Override public String tr(String key, Object... args) { return key; }
        @Override public void copy(String text) { }
        @Override public void clickSound() { }
        @Override public void tabSound() { sounds++; }
        @Override public void setChatInput(String text) { }
        @Override public boolean mouseLeftDown() { return mouseDown; }
        @Override public boolean shiftDown() { return false; }
        @Override public void show(AddonView view) { views.add(view); }
        @Override public void closeView(AddonView view) { views.remove(view); }
        @Override public String clipboard() { return ""; }
        @Override public List<ChatWindows.MenuEntry> addonEntries(String t, String s, List<String> l) { return List.of(); }
    }

    static void frame(ChatWindows w, FakeGraphics g, boolean open, int mx, int my) {
        long now = System.currentTimeMillis();
        w.render(g, 400, 300, open, false, now);
        if (open) {
            w.renderScreen(g, mx, my, now);
        }
    }

    @Test
    void drawsTheActiveTabWithHeadsAndFades() {
        FakeHost host = new FakeHost();
        host.text.online.add("Steve");
        ChatLayout layout = new ChatLayout(null);
        ChatWindows w = new ChatWindows(host, layout);
        w.received("Steve: hello", false);
        w.received("Restart soon", false);
        FakeGraphics g = new FakeGraphics();
        frame(w, g, false, 0, 0);
        assertEquals(List.of("Restart soon", "Steve: hello"), host.text.drawn, "newest at the bottom, drawn first");
        assertEquals(List.of("head:Steve"), host.text.heads, "a head only for the player of the tab list");

        // a tab that takes server messages out of the main chat
        ChatTab server = new ChatTab(layout.newTabId(), "Server", new ChatRule(ChatRule.Kind.SERVER, ""));
        server.keepInMain = false;
        server.sound = true;
        layout.add(server, null, 250, 40, 140, 60);
        layout.main().select(layout.mainTab());
        w.received("Welcome back", false);
        assertEquals(1, host.sounds, "the tab's sound");
        host.text.drawn.clear();
        frame(w, g, false, 0, 0);
        assertTrue(host.text.drawn.contains("Steve: hello"));
        assertEquals(2, host.text.drawn.stream().filter(s -> s.contains("Restart") || s.contains("Welcome")).count(),
                "server messages only in the server window: " + host.text.drawn);

        // stacked message replaces the newest
        w.received("Welcome back (x2)", true);
        assertEquals(3, w.store().size());
    }

    @Test
    void tabBarButtonsAndDragging() {
        FakeHost host = new FakeHost();
        ChatLayout layout = new ChatLayout(null);
        ChatWindows w = new ChatWindows(host, layout);
        for (int i = 0; i < 30; i++) {
            w.received("message number " + i, false);
        }
        FakeGraphics g = new FakeGraphics();
        frame(w, g, true, 0, 0);
        ChatWindows.Box b = w.box(layout.main(), host.text.options(), true, 400, 300);
        assertEquals(260, b.bottom(), "where Minecraft's chat is: 40 above the bottom");
        assertEquals(b.top() - ChatWindows.TAB_H, b.barTop());

        // "+" opens the wizard
        int plusX = b.right() - ChatWindows.BUTTON_W - ChatWindows.BUTTON_W - 2 + 4;
        assertTrue(w.click(plusX, b.barTop() + 6, 0));
        assertInstanceOf(TabWizard.class, host.views.get(0));

        // "≡" opens the list
        assertTrue(w.click(b.right() - 6, b.barTop() + 6, 0));
        assertNotNull(w.menu);
        assertTrue(w.click(5, 5, 0), "a click next to the menu closes it");
        assertNull(w.menu);

        // dragging the bar moves the window
        frame(w, g, true, 30, b.barTop() + 6);
        assertTrue(w.click(30, b.barTop() + 6, 0));
        host.mouseDown = true;
        frame(w, g, true, 80, b.barTop() - 14);
        host.mouseDown = false;
        frame(w, g, true, 80, b.barTop() - 14);
        assertEquals(b.x() + 50, layout.main().x);
        assertEquals(60, layout.main().bottom);

        // the right edge resizes the main window (the chat module's size)
        b = w.box(layout.main(), host.text.options(), true, 400, 300);
        frame(w, g, true, b.right(), b.top() + 20);
        assertTrue(w.click(b.right(), b.top() + 20, 0));
        host.mouseDown = true;
        frame(w, g, true, b.right() + 40, b.top() + 20);
        host.mouseDown = false;
        frame(w, g, true, b.right() + 40, b.top() + 20);
        assertTrue(host.custom);
        assertEquals(160, host.width);

        // scrolling up and clicking a message runs its click event
        assertTrue(w.scroll(b.x() + 20, b.bottom() - 4, 1));
        assertEquals(7, layout.main().scroll);
        frame(w, g, true, 0, 0);
        b = w.box(layout.main(), host.text.options(), true, 400, 300);
        int tx = b.x() + ChatWindows.PAD + 3;
        assertTrue(w.click(tx, b.bottom() - 4, 0));
        assertEquals(0, host.text.clicked.size(), "the click event comes when the mouse goes up");
        host.mouseDown = false;
        frame(w, g, true, tx, b.bottom() - 4);
        assertEquals(1, host.text.clicked.size());
        assertTrue(host.text.clicked.get(0).toString().startsWith("style:"));

        // holding the left button and dragging scrolls the messages; the window stays where it is
        int xBefore = layout.main().x;
        int bottomBefore = layout.main().bottom;
        int scrollBefore = layout.main().scroll;
        assertTrue(w.click(tx, b.bottom() - 30, 0));
        host.mouseDown = true;
        frame(w, g, true, tx, b.bottom() - 30 + 27); // three lines down
        assertEquals(scrollBefore + 3, layout.main().scroll, "dragging down shows older messages");
        frame(w, g, true, tx, b.bottom() - 30 - 9); // one line up from the start
        assertEquals(scrollBefore - 1, layout.main().scroll);
        host.mouseDown = false;
        frame(w, g, true, tx, b.bottom() - 30 - 9);
        assertEquals(1, host.text.clicked.size(), "a drag is no click");
        assertEquals(xBefore, layout.main().x);
        assertEquals(bottomBefore, layout.main().bottom);

        // the left edge at the screen edge is easy to grab
        assertEquals(1, ChatWindows.edgesAt(new ChatWindows.Box(0, 100, 200, 200, 88), 2, 150) & 1);

        // right-click: message menu with "Filter"
        assertTrue(w.click(b.x() + 20, b.bottom() - 4, 1));
        assertTrue(w.menu.items.stream().anyMatch(i -> i.label().equals("tabs.filter")));
    }

    @Test
    void peekShowsFadedMessagesWithoutOpeningTheChat() {
        FakeHost host = new FakeHost();
        ChatWindows w = new ChatWindows(host, new ChatLayout(null));
        w.received("an old message", false);
        long later = System.currentTimeMillis() + 60_000; // long after it faded (10 s)
        FakeGraphics g = new FakeGraphics();
        w.render(g, 400, 300, false, false, later);
        assertTrue(host.text.drawn.isEmpty(), "closed chat: faded out");
        w.render(g, 400, 300, false, true, later);
        assertEquals(List.of("an old message"), host.text.drawn, "peek key held: shown again");
        assertFalse(w.click(10, 255, 0), "nothing to click while peeking");
    }

    @Test
    void newWindowsGoBesideOrAboveTheMainChat() {
        FakeHost host = new FakeHost();
        ChatWindows w = new ChatWindows(host, new ChatLayout(null));
        frame(w, new FakeGraphics(), true, 0, 0);
        int[] beside = w.newWindowPlace();
        assertEquals(128 + 6, beside[0], "right of the main chat (120 + 8 wide)");
        assertEquals(40, beside[1]);

        host.custom = true;
        host.width = 300; // no room at the side any more
        frame(w, new FakeGraphics(), true, 0, 0);
        int[] above = w.newWindowPlace();
        assertEquals(0, above[0]);
        assertEquals(300 - (260 - 90 - ChatWindows.TAB_H - 4), above[1], "its bottom just above the main chat's tab bar");
    }

    @Test
    void unreadCountsAndSelecting() {
        FakeHost host = new FakeHost();
        ChatLayout layout = new ChatLayout(null);
        ChatWindows w = new ChatWindows(host, layout);
        ChatTab steve = new ChatTab(layout.newTabId(), "Steve", new ChatRule(ChatRule.Kind.PLAYER, "Steve"));
        layout.add(steve, layout.main(), 0, 0, 0, 0);
        layout.main().select(layout.mainTab());
        host.text.online.add("Steve");
        w.received("Steve: one", false);
        w.received("Steve: two", false);
        assertEquals(2, steve.unread);
        layout.main().select(steve);
        assertEquals(0, steve.unread);
        assertEquals(2, w.view(steve).size());
        assertEquals(2, w.view(layout.mainTab()).size(), "kept in the main chat too");
    }
}
