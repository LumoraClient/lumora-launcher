package dev.lumora.core.chat.windows;

import dev.lumora.api.input.Keys;
import dev.lumora.api.render.HudGraphics;
import dev.lumora.api.screen.AddonView;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lumora's chat: windows with tabs, drawn by Lumora instead of Minecraft's chat. Every window shows the messages of
 * its active tab; the main window sits where Minecraft's chat was, other windows anywhere. In the open chat a tab
 * bar sits on top of every window ("+" for a new tab, "≡" for the list of tabs and windows), the bar moves the
 * window and its edges resize it. Messages keep Minecraft's own text, clicks and hover texts ({@link ChatText}); a
 * player's head sits in front of their messages.
 */
public final class ChatWindows {
    /** Height of the tab bar above a window in the open chat. */
    static final int TAB_H = 12;
    static final int BUTTON_W = 12;
    /** Distance in pixels from an edge that still grabs it. */
    static final int GRAB = 3;
    /** Text inset left and right of the window (unscaled). */
    static final int PAD = 4;
    /** Head size and the room it takes in front of the text (unscaled). */
    static final int HEAD = 8;
    static final int HEAD_SPACE = HEAD + 2;
    private static final int MOVE_THRESHOLD = 3;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFB4BED0;
    private static final int BAR = 0xD80E1218;
    private static final int TAB_ACTIVE = 0x40FFFFFF;
    private static final int TAB_HOVER = 0x22FFFFFF;

    /** What the chat module gives the windows. */
    public interface Host {
        ChatText text();

        String own();

        List<String> online();

        boolean customSize();

        int width();

        int heightOpen();

        int heightClosed();

        /** The main window was resized by dragging (switches the chat module to its own size). */
        void setMainSize(int width, int heightOpen);

        int widthMax();

        int heightMax();

        boolean fade();

        int fadeSeconds();

        double backgroundOpacity(double vanilla);

        boolean textShadow();

        int history();

        boolean heads();

        boolean resize();

        boolean menu();

        String tr(String key, Object... args);

        void copy(String text);

        void clickSound();

        /** The sound of a tab that plays one for its messages. */
        void tabSound();

        void setChatInput(String text);

        boolean mouseLeftDown();

        boolean shiftDown();

        void show(AddonView view);

        void closeView(AddonView view);

        String clipboard();

        /** Entries of addons for the message menu (Lumora API 1.5). */
        List<MenuEntry> addonEntries(String text, String sender, List<String> links);
    }

    /** An entry an addon puts into the message menu. */
    public record MenuEntry(String label, Runnable action) {
    }

    enum Part { TAB, EDIT, PLUS, LIST, BAR }

    record Chrome(Part part, ChatWindow window, ChatTab tab, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    record Hit(ChatWindow window, ChatEntry entry, Object line, int y0, int y1, int x0, int x1, int textX, double scale) {
    }

    /** A window's place on screen this frame (GUI pixels); {@code barTop} is the top of the tab bar in the open chat. */
    record Box(int x, int top, int right, int bottom, int barTop) {
        int w() {
            return right - x;
        }

        int h() {
            return bottom - top;
        }
    }

    private static final class Wrapped {
        int width = -1;
        final Map<Long, List<Object>> lines = new HashMap<>();
    }

    enum Drag { NONE, PENDING_MOVE, MOVE, RESIZE, PENDING_SCROLL, SCROLL }

    private final Host host;
    private final ChatLayout layout;
    private final ChatStore store = new ChatStore();
    private final Map<ChatWindow, Wrapped> wrapped = new IdentityHashMap<>();
    private final Map<ChatTab, List<ChatEntry>> views = new IdentityHashMap<>();
    private int viewsStore = -1;
    private int viewsLayout = -1;
    private String viewsOwn = "";

    private final List<Chrome> chrome = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<ChatWindow, Box> boxes = new IdentityHashMap<>();
    private long lastOpenFrame;
    private boolean open;
    private int screenW = 320;
    private int screenH = 240;
    private int mouseX;
    private int mouseY;

    private Drag drag = Drag.NONE;
    private ChatWindow dragWindow;
    private boolean dragL;
    private boolean dragR;
    private boolean dragT;
    private boolean dragB;
    private int dragMx;
    private int dragMy;
    private int startX;
    private int startBottom;
    private int startW;
    private int startH;
    private int startScroll;
    /** The message line pressed: a click on it when the mouse comes up without dragging. */
    private Hit pressed;
    private int pressedX;

    PopupMenu menu;
    private Hit dotsHit;
    private int dotsX;
    private int dotsY;
    private int dotsH;
    private String flash;
    private long flashUntil;
    private int flashX;
    private int flashY;

    public ChatWindows(Host host, ChatLayout layout) {
        this.host = host;
        this.layout = layout;
    }

    public ChatLayout layout() {
        return layout;
    }

    public ChatStore store() {
        return store;
    }

    Host host() {
        return host;
    }

    // --- messages

    /**
     * A message Minecraft added to its chat.
     *
     * @param component      the message as Minecraft draws it (with Lumora's timestamp and "(x2)")
     * @param replacePrevious the same message came again: it replaces the newest one
     */
    public void received(Object component, boolean replacePrevious) {
        ChatText text = host.text();
        if (text == null || component == null) {
            return;
        }
        ChatText.Parts parts = text.parts(component);
        String plain = dev.lumora.core.chat.ChatApi.restore(parts.text());
        String clean = dev.lumora.core.extras.ChatModule.clean(plain);
        String sender = dev.lumora.core.extras.ChatModule.sender(plain, parts.insertions(), parts.commands(), host.online());
        Object head = sender == null ? null : text.head(sender);
        ChatEntry e = store.add(component, clean, sender, head, System.currentTimeMillis(), replacePrevious, host.history());
        if (replacePrevious) {
            return; // counted and announced the first time
        }
        String own = host.own();
        boolean sound = false;
        for (ChatWindow w : layout.windows) {
            ChatTab shown = w.activeTab();
            for (ChatTab t : w.tabs) {
                if (!layout.shows(t, e, own)) {
                    continue;
                }
                if (t != shown) {
                    t.unread++;
                }
                sound |= t.sound && !t.main();
            }
        }
        if (sound) {
            host.tabSound();
        }
    }

    public void clear() {
        store.clear();
        for (ChatTab t : layout.tabs()) {
            t.unread = 0;
        }
        for (ChatWindow w : layout.windows) {
            w.scroll = 0;
        }
    }

    /** The messages of a tab, oldest first. */
    List<ChatEntry> view(ChatTab tab) {
        String own = host.own();
        if (viewsStore != store.version() || viewsLayout != layout.version() || !viewsOwn.equals(own)) {
            views.clear();
            viewsStore = store.version();
            viewsLayout = layout.version();
            viewsOwn = own;
        }
        return views.computeIfAbsent(tab, t -> {
            List<ChatEntry> out = new ArrayList<>();
            for (ChatEntry e : store.all()) {
                if (layout.shows(t, e, own)) {
                    out.add(e);
                }
            }
            return out;
        });
    }

    /** The tabs that highlight a message, first one wins. */
    private int highlight(ChatEntry e) {
        String own = host.own();
        for (ChatWindow w : layout.windows) {
            for (ChatTab t : w.tabs) {
                if (t.highlight && !t.main() && t.rule.complete() && t.rule.matches(e, own)) {
                    return t.highlightColor;
                }
            }
        }
        return 0;
    }

    // --- geometry

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    int windowWidth(ChatWindow w, ChatText.Options o) {
        if (w.main()) {
            return (host.customSize() ? host.width() : o.width()) + 2 * PAD;
        }
        return w.w;
    }

    int windowHeight(ChatWindow w, ChatText.Options o, boolean open) {
        int h;
        int hc;
        if (w.main()) {
            h = host.customSize() ? host.heightOpen() : o.height();
            hc = host.customSize() ? Math.min(host.heightClosed(), h) : o.heightClosed();
        } else {
            h = w.h;
            hc = Math.min(w.hClosed, w.h);
        }
        return open ? h : hc;
    }

    Box box(ChatWindow w, ChatText.Options o, boolean open, int gw, int gh) {
        int width = clamp(windowWidth(w, o), ChatWindow.MIN_W, Math.max(ChatWindow.MIN_W, gw));
        int height = clamp(windowHeight(w, o, open), ChatWindow.MIN_H, Math.max(ChatWindow.MIN_H, gh - TAB_H - 2));
        int x = clamp(w.x, 0, Math.max(0, gw - width));
        int fullH = windowHeight(w, o, true);
        int maxBottom = Math.max(0, gh - Math.min(fullH, gh - TAB_H - 2) - TAB_H);
        int wantBottom = w.bottom < 0 ? o.bottom() : w.bottom;
        int bottom = gh - clamp(wantBottom, 0, maxBottom);
        return new Box(x, bottom - height, x + width, bottom, bottom - height - TAB_H);
    }

    // --- drawing (called instead of Minecraft's chat)

    /**
     * Draws all windows. {@code focused}: the chat screen is open (tab bars, full height, scrolling); {@code peek}: the
     * peek key is held (full height and every message, but nothing to click).
     */
    public void render(HudGraphics g, int gw, int gh, boolean focused, boolean peek, long now) {
        ChatText text = host.text();
        if (text == null) {
            return;
        }
        ChatText.Options o = text.options();
        if (o.hidden()) {
            return;
        }
        open = focused;
        if (focused) {
            lastOpenFrame = now;
            hits.clear();
            chrome.clear();
        }
        screenW = gw;
        screenH = gh;
        boxes.clear();
        boolean first = true;
        for (ChatWindow w : layout.windows) {
            Box b = box(w, o, focused || peek, gw, gh);
            boxes.put(w, b);
            if (!first) {
                text.layer(g); // a window covers the windows below it, their text too
            }
            first = false;
            drawWindow(g, text, o, w, b, focused, focused || peek, now);
        }
    }

    private void drawWindow(HudGraphics g, ChatText text, ChatText.Options o, ChatWindow w, Box b, boolean focused, boolean full, long now) {
        ChatTab tab = w.activeTab();
        if (tab == null) {
            return;
        }
        double s = Math.max(0.1, o.scale());
        int lh = (int) (9.0 * (o.lineSpacing() + 1.0));
        double rowH = lh * s;
        int textOffset = (int) Math.round(8.0 * (o.lineSpacing() + 1.0) - 4.0 * o.lineSpacing());
        int rows = Math.max(1, (int) Math.floor(b.h() / rowH));
        double bg = host.backgroundOpacity(o.backgroundOpacity());
        float textOpacity = (float) (o.textOpacity() * 0.9 + 0.1);
        int localW = (int) Math.floor(b.w() / s);
        boolean heads = host.heads();
        int wrapW = Math.max(20, localW - 2 * PAD - 3);
        Wrapped cache = wrapped.computeIfAbsent(w, k -> new Wrapped());
        int key = wrapW * 2 + (heads ? 1 : 0);
        if (cache.width != key || cache.lines.size() > store.size() * 2 + 50) {
            cache.lines.clear();
            cache.width = key;
        }
        List<ChatEntry> entries = view(tab);
        long fadeMs = host.fade() ? host.fadeSeconds() * 1000L : Long.MAX_VALUE;

        // rows bottom-up: (entry, line, first line of the entry)
        int total = 0;
        if (focused) {
            for (ChatEntry e : entries) {
                total += lines(text, cache, e, wrapW, heads).size();
            }
            w.scroll = clamp(w.scroll, 0, Math.max(0, total - rows));
        }
        int skip = focused ? w.scroll : 0;
        g.push();
        g.translate(b.x(), b.bottom());
        g.scale((float) s);
        int row = 0;
        int seen = 0;
        outer:
        for (int i = entries.size() - 1; i >= 0; i--) {
            ChatEntry e = entries.get(i);
            float alpha = 1f;
            if (!full) {
                long age = now - e.time();
                double t = fadeMs == Long.MAX_VALUE ? 0 : (double) age / fadeMs;
                double a = Math.max(0, Math.min(1, (1.0 - t) * 10.0));
                alpha = (float) (a * a);
                if (alpha <= 1.0e-3f) {
                    break; // older messages faded out too
                }
            }
            List<Object> ls = lines(text, cache, e, wrapW, heads);
            boolean head = heads && e.head() != null;
            int indent = PAD + (head ? HEAD_SPACE : 0);
            int light = highlight(e);
            for (int li = ls.size() - 1; li >= 0; li--) {
                if (seen++ < skip) {
                    continue;
                }
                if (row >= rows) {
                    break outer;
                }
                int rowBottom = -row * lh;
                int rowTop = rowBottom - lh;
                g.fill(0, rowTop, localW, rowBottom, alphaBlack(bg * (full ? 0.9 : alpha)));
                if (light != 0) {
                    g.fill(0, rowTop, localW, rowBottom, LumoraTheme.alpha(light, (int) (0x58 * alpha)));
                    g.fill(0, rowTop, 2, rowBottom, LumoraTheme.alpha(light, (int) (0xFF * alpha)));
                }
                int textY = rowBottom - textOffset;
                int a = (int) (255 * alpha * textOpacity);
                if (a > 3) {
                    if (head && li == 0) {
                        text.drawHead(g, e.head(), PAD, textY, HEAD);
                    }
                    text.draw(g, ls.get(li), indent, textY, (a << 24) | 0xFFFFFF, host.textShadow());
                }
                if (focused) {
                    int y0 = (int) Math.round(b.bottom() - (row + 1) * rowH);
                    int y1 = (int) Math.round(b.bottom() - row * rowH);
                    hits.add(new Hit(w, e, ls.get(li), y0, y1, b.x(), b.right(), (int) Math.round(b.x() + indent * s), s));
                }
                row++;
            }
        }
        if (full) {
            // the open window is dark all over, also above the oldest line
            int top = (int) -Math.ceil(b.h() / s);
            if (-row * lh > top) {
                g.fill(0, top, localW, -row * lh, alphaBlack(bg * 0.9));
            }
        }
        g.pop();
        if (focused) {
            drawScrollbar(g, b, rows, total, w.scroll);
            drawBar(g, w, b);
            drawEdges(g, w, b);
        }
    }

    private List<Object> lines(ChatText text, Wrapped cache, ChatEntry e, int wrapW, boolean heads) {
        List<Object> l = cache.lines.get(e.id());
        if (l == null) {
            int width = wrapW - (heads && e.head() != null ? HEAD_SPACE : 0);
            try {
                l = text.wrap(e.component(), Math.max(10, width));
            } catch (RuntimeException ex) {
                l = List.of(); // a message Minecraft cannot lay out: left out like Minecraft does
            }
            cache.lines.put(e.id(), l);
        }
        return l;
    }

    private static int alphaBlack(double opacity) {
        int a = (int) Math.round(Math.max(0, Math.min(1, opacity)) * 255);
        return a << 24;
    }

    private void drawScrollbar(HudGraphics g, Box b, int rows, int total, int scroll) {
        if (total <= rows) {
            return;
        }
        int h = b.h();
        int thumb = Math.max(6, h * rows / total);
        int y = b.bottom() - thumb - (h - thumb) * scroll / Math.max(1, total - rows);
        int x = b.right() - 3;
        g.fill(x, b.top(), x + 2, b.bottom(), 0x30FFFFFF);
        g.fill(x, y, x + 2, y + thumb, scroll > 0 ? LumoraTheme.accent() : 0xA0FFFFFF);
    }

    private String tabName(ChatTab t) {
        return t.main() && t.name.isEmpty() ? host.tr("tabs.main") : t.name;
    }

    private void drawBar(HudGraphics g, ChatWindow w, Box b) {
        int y = b.barTop();
        int accent = LumoraTheme.accent();
        g.fill(b.x(), y, b.right(), b.top(), BAR);
        int listX = b.right() - BUTTON_W;
        int plusRoom = BUTTON_W + 2;
        boolean labels = !(w.main() && w.tabs.size() == 1);
        int x = b.x();
        if (labels) {
            int room = listX - plusRoom - b.x();
            int[] widths = new int[w.tabs.size()];
            int sum = 0;
            for (int i = 0; i < w.tabs.size(); i++) {
                ChatTab t = w.tabs.get(i);
                widths[i] = g.textWidth(tabName(t)) + 10 + (i == w.active ? 10 : 0) + (t.unread > 0 && i != w.active ? badgeWidth(g, t.unread) + 3 : 0);
                sum += widths[i];
            }
            int each = sum > room ? Math.max(24, room / Math.max(1, w.tabs.size())) : Integer.MAX_VALUE;
            for (int i = 0; i < w.tabs.size(); i++) {
                ChatTab t = w.tabs.get(i);
                int tw = Math.min(widths[i], each);
                if (x + tw > listX - plusRoom) {
                    break;
                }
                boolean active = i == w.active;
                boolean hover = !dragging() && mouseX >= x && mouseX < x + tw && mouseY >= y && mouseY < b.top();
                if (active) {
                    g.fill(x, y, x + tw, b.top(), TAB_ACTIVE);
                    g.fill(x, y, x + tw, y + 1, accent);
                } else if (hover) {
                    g.fill(x, y, x + tw, b.top(), TAB_HOVER);
                }
                int labelRoom = tw - 10 - (active ? 10 : 0);
                String label = fit(g, tabName(t), labelRoom - (t.unread > 0 && !active ? badgeWidth(g, t.unread) + 3 : 0));
                g.text(label, x + 5, y + 2, active ? TEXT : MUTED, false);
                int after = x + 5 + g.textWidth(label);
                if (active) {
                    ChatIcons.pencil(g, after + 4, y + 3, hoverRect(after + 2, y, 10, TAB_H) ? accent : MUTED);
                    chrome.add(new Chrome(Part.EDIT, w, t, after + 2, y, 10, TAB_H));
                } else if (t.unread > 0) {
                    String n = t.unread > 99 ? "99+" : String.valueOf(t.unread);
                    int bw = badgeWidth(g, t.unread);
                    g.roundedRect(after + 3, y + 2, bw, TAB_H - 4, 3, accent);
                    g.text(n, after + 5, y + 2, TEXT, false);
                }
                chrome.add(new Chrome(Part.TAB, w, t, x, y, tw, TAB_H));
                g.fill(x + tw, y + 2, x + tw + 1, b.top() - 2, 0x30FFFFFF);
                x += tw + 1;
            }
            x += 1;
        } else {
            x = listX - plusRoom;
        }
        // "+" right after the tabs, "≡" at the right end
        drawButton(g, x, y, Part.PLUS, w);
        drawButton(g, listX, y, Part.LIST, w);
        // the empty part of the bar moves the window (added first: the tabs and buttons lie on top of it)
        chrome.add(chromeStart(w), new Chrome(Part.BAR, w, null, b.x(), y, b.w(), TAB_H));
    }

    /** Index of the first chrome element of {@code w} this frame. */
    private int chromeStart(ChatWindow w) {
        for (int i = 0; i < chrome.size(); i++) {
            if (chrome.get(i).window() == w) {
                return i;
            }
        }
        return chrome.size();
    }

    private static int badgeWidth(HudGraphics g, int n) {
        return g.textWidth(n > 99 ? "99+" : String.valueOf(n)) + 4;
    }

    private void drawButton(HudGraphics g, int x, int y, Part part, ChatWindow w) {
        boolean hover = !dragging() && hoverRect(x, y, BUTTON_W, TAB_H);
        g.roundedRect(x + 1, y + 1, BUTTON_W - 2, TAB_H - 2, 2, hover ? LumoraTheme.alpha(LumoraTheme.accent(), 0xE0) : 0xC0202733);
        if (part == Part.PLUS) {
            ChatIcons.plus(g, x + 3, y + 3, TEXT);
        } else {
            ChatIcons.lines(g, x + 3, y + 4, TEXT);
        }
        chrome.add(new Chrome(part, w, null, x, y, BUTTON_W, TAB_H));
    }

    private boolean hoverRect(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    static String fit(HudGraphics g, String s, int max) {
        if (g.textWidth(s) <= max) {
            return s;
        }
        String e = "…";
        int n = s.length();
        while (n > 0 && g.textWidth(s.substring(0, n) + e) > max) {
            n--;
        }
        return n <= 0 ? e : s.substring(0, n) + e;
    }

    private boolean dragging() {
        return drag == Drag.MOVE || drag == Drag.RESIZE || drag == Drag.SCROLL;
    }

    /** Which edges of a window are under the mouse: bits 1 left, 2 right, 4 top, 8 bottom. */
    static int edgesAt(Box b, double mx, double my) {
        int top = b.barTop();
        if (mx < b.x() - GRAB || mx > b.right() + GRAB || my < top - GRAB || my > b.bottom() + GRAB) {
            return 0;
        }
        // GRAB pixels outside the window, 2 inside (the tabs and buttons stay clickable); at the screen edge, where
        // there is nothing outside, GRAB inside
        int e = 0;
        if (mx < b.x() + (b.x() <= 0 ? GRAB : 2)) {
            e |= 1;
        }
        if (mx > b.right() - 2) {
            e |= 2;
        }
        if (my < top + 2) {
            e |= 4;
        }
        if (my > b.bottom() - 2) {
            e |= 8;
        }
        return e;
    }

    private void drawEdges(HudGraphics g, ChatWindow w, Box b) {
        if (!host.resize()) {
            return;
        }
        int accent = LumoraTheme.accent();
        int e = drag == Drag.RESIZE && dragWindow == w ? edgeBits() : menu == null && !dragging() && topmostAt(mouseX, mouseY, w) ? edgesAt(b, mouseX, mouseY) : 0;
        boolean moving = drag == Drag.MOVE && dragWindow == w;
        boolean over = mouseX >= b.x() && mouseX < b.right() && mouseY >= b.barTop() && mouseY < b.bottom();
        int top = b.barTop();
        if (e != 0 || moving) {
            g.outline(b.x(), top, b.w(), b.bottom() - top, LumoraTheme.alpha(accent, moving ? 0xD0 : 0x90));
        }
        if ((e & 1) != 0) {
            g.fill(b.x() - 1, top, b.x() + 1, b.bottom(), accent);
        }
        if ((e & 2) != 0) {
            g.fill(b.right() - 1, top, b.right() + 1, b.bottom(), accent);
        }
        if ((e & 4) != 0) {
            g.fill(b.x(), top - 1, b.right(), top + 1, accent);
        }
        if ((e & 8) != 0) {
            g.fill(b.x(), b.bottom() - 1, b.right(), b.bottom() + 1, accent);
        }
        if (e == 0 && !moving && over) {
            // corners as a hint that every edge can be dragged
            int c = 0x90FFFFFF;
            corner(g, b.x(), top, 1, 1, c);
            corner(g, b.right(), top, -1, 1, c);
            corner(g, b.x(), b.bottom(), 1, -1, c);
            corner(g, b.right(), b.bottom(), -1, -1, c);
        }
        if (drag == Drag.RESIZE && dragWindow == w) {
            String size = b.w() + " × " + b.h();
            int sw = g.textWidth(size) + 8;
            int lx = clamp(b.right() - sw, 2, Math.max(2, screenW - sw - 2));
            int ly = Math.max(2, top - 15);
            g.card(lx, ly, sw, 12, 3, PopupMenu.BG, LumoraTheme.alpha(accent, 0xA0));
            g.text(size, lx + 4, ly + 2, TEXT, false);
        }
    }

    private static void corner(HudGraphics g, int x, int y, int dx, int dy, int c) {
        int x0 = dx > 0 ? x : x - 5;
        int y0 = dy > 0 ? y : y - 1;
        g.fill(x0, y0, x0 + 5, y0 + 1, c);
        int x1 = dx > 0 ? x : x - 1;
        int y1 = dy > 0 ? y : y - 5;
        g.fill(x1, y1, x1 + 1, y1 + 5, c);
    }

    private int edgeBits() {
        return (dragL ? 1 : 0) | (dragR ? 2 : 0) | (dragT ? 4 : 0) | (dragB ? 8 : 0);
    }

    /** Whether {@code w} is the topmost window at the point (windows drawn later lie on top). */
    private boolean topmostAt(double mx, double my, ChatWindow w) {
        for (int i = layout.windows.size() - 1; i >= 0; i--) {
            ChatWindow o = layout.windows.get(i);
            Box b = boxes.get(o);
            if (b == null) {
                continue;
            }
            boolean inside = mx >= b.x() && mx < b.right() && my >= b.barTop() && my < b.bottom();
            if (inside || edgesAt(b, mx, my) != 0) {
                return o == w;
            }
        }
        return false;
    }

    // --- open chat screen: drags, hover, menus (drawn on top of everything)

    /** Called every frame while the chat screen is open, after the screen drew itself. */
    public void renderScreen(HudGraphics g, int mx, int my, long now) {
        mouseX = mx;
        mouseY = my;
        if (now - lastOpenFrame > 250) {
            return; // the windows were not drawn (hidden chat)
        }
        if (drag != Drag.NONE) {
            if (host.mouseLeftDown()) {
                applyDrag(mx, my);
            } else {
                endDrag();
            }
        }
        dotsHit = null;
        if (menu == null && !dragging() && host.menu()) {
            drawHover(g, mx, my);
        }
        if (menu != null) {
            menu.render(g, screenW, screenH, mx, my);
        } else if (!dragging()) {
            Hit h = hitAt(mx, my);
            ChatText text = host.text();
            if (h != null && text != null) {
                Object style = text.styleAt(h.line(), (int) ((mx - h.textX()) / h.scale()));
                if (style != null) {
                    text.hover(g, style, mx, my);
                }
            }
        }
        if (flash != null && now < flashUntil) {
            int fw = g.textWidth(flash) + 8;
            int fx = clamp(flashX, 2, Math.max(2, screenW - fw - 2));
            g.card(fx, flashY, fw, 12, 3, PopupMenu.BG, LumoraTheme.alpha(0xFF7CE38B, 0xC0));
            g.text(flash, fx + 4, flashY + 2, 0xFF7CE38B, false);
        }
    }

    private Hit hitAt(double mx, double my) {
        for (int i = layout.windows.size() - 1; i >= 0; i--) {
            ChatWindow w = layout.windows.get(i);
            Box b = boxes.get(w);
            if (b == null || mx < b.x() || mx >= b.right() + 4 + BUTTON_W || my < b.top() || my >= b.bottom()) {
                continue;
            }
            for (Hit h : hits) {
                if (h.window() == w && my >= h.y0() && my < h.y1()) {
                    return mx < b.right() || mx >= b.right() + 4 ? h : null;
                }
            }
            if (mx < b.right()) {
                return null;
            }
        }
        return null;
    }

    private void drawHover(HudGraphics g, int mx, int my) {
        Hit h = hitAt(mx, my);
        if (h == null) {
            return;
        }
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (Hit o : hits) {
            if (o.entry() == h.entry() && o.window() == h.window()) {
                lo = Math.min(lo, o.y0());
                hi = Math.max(hi, o.y1());
            }
        }
        g.fill(h.x0(), lo, h.x1(), hi, 0x26FFFFFF);
        int by = h.y0();
        int bh = Math.max(9, h.y1() - h.y0());
        int bx = h.x1() + 4;
        if (bx + BUTTON_W > screenW - 1) {
            bx = h.x1() - BUTTON_W - 4; // no room right of the window
        }
        boolean over = mx >= bx && mx < bx + BUTTON_W && my >= by && my < by + bh;
        g.roundedRect(bx, by, BUTTON_W, bh, 2, over ? LumoraTheme.alpha(LumoraTheme.accent(), 0xE0) : 0xC0101018);
        ChatIcons.dots(g, bx + 2, by + bh / 2 - 1, TEXT);
        dotsHit = h;
        dotsX = bx;
        dotsY = by;
        dotsH = bh;
    }

    // --- input

    /** A click in the open chat; true if the windows used it. */
    public boolean click(double mx, double my, int button) {
        if (System.currentTimeMillis() - lastOpenFrame > 250) {
            return false;
        }
        if (menu != null) {
            PopupMenu m = menu;
            menu = null;
            int i = m.itemAt(mx, my);
            if (i >= 0 && button == 0) {
                host.clickSound();
                PopupMenu.Item it = m.items.get(i);
                Runnable r = m.onEdit(i, mx) ? it.edit() : it.action();
                if (r != null) {
                    r.run();
                }
            }
            return true; // a click next to the menu only closes it
        }
        if (dotsHit != null && button == 0 && mx >= dotsX && mx < dotsX + BUTTON_W && my >= dotsY && my < dotsY + dotsH) {
            openMessageMenu(dotsHit, dotsX + BUTTON_W + 2, dotsY);
            return true;
        }
        if (button == 0 && host.resize()) {
            for (int i = layout.windows.size() - 1; i >= 0; i--) {
                ChatWindow w = layout.windows.get(i);
                Box b = boxes.get(w);
                int e = b == null || !topmostAt(mx, my, w) ? 0 : edgesAt(b, mx, my);
                if (e != 0) {
                    startResize(w, b, e, mx, my);
                    return true;
                }
            }
        }
        for (int i = chrome.size() - 1; i >= 0; i--) {
            Chrome c = chrome.get(i);
            if (!c.contains(mx, my)) {
                continue;
            }
            if (button == 0) {
                switch (c.part()) {
                    case PLUS -> {
                        host.clickSound();
                        openWizard(null, c.window(), null);
                    }
                    case LIST -> {
                        host.clickSound();
                        openList(c.x() + c.w(), c.y() + c.h() + 1);
                    }
                    case EDIT -> {
                        host.clickSound();
                        openWizard(c.tab(), c.window(), null);
                    }
                    case TAB -> {
                        if (c.window().activeTab() != c.tab()) {
                            host.clickSound();
                            c.window().select(c.tab());
                            layout.save();
                        }
                        startMove(c.window(), mx, my, true);
                    }
                    case BAR -> startMove(c.window(), mx, my, false);
                }
                return true;
            }
            if (button == 1 && (c.part() == Part.TAB || c.part() == Part.EDIT)) {
                openTabMenu(c.tab(), (int) mx + 2, (int) my);
                return true;
            }
            return true;
        }
        Hit h = hitAt(mx, my);
        if (h == null) {
            return false;
        }
        if (button == 1 && host.menu()) {
            openMessageMenu(h, (int) mx + 2, (int) my);
            return true;
        }
        if (button == 0 && mx < h.x1()) {
            // dragging scrolls the messages, a click without dragging runs the click event of the text (on release)
            drag = Drag.PENDING_SCROLL;
            dragWindow = h.window();
            dragMx = (int) mx;
            dragMy = (int) my;
            startScroll = h.window().scroll;
            pressed = h;
            pressedX = (int) mx;
            return true;
        }
        return false;
    }

    /** Mouse wheel in the open chat over a window (or the main window): true if the windows used it. */
    public boolean scroll(double mx, double my, double amount) {
        if (System.currentTimeMillis() - lastOpenFrame > 250) {
            return false;
        }
        ChatWindow w = windowAt(mx, my);
        int lines = (int) Math.signum(amount) * (host.shiftDown() ? 1 : 7);
        w.scroll = Math.max(0, w.scroll + lines);
        return true;
    }

    /** Page up / page down in the open chat. */
    public boolean key(int key) {
        if (System.currentTimeMillis() - lastOpenFrame > 250 || key != Keys.PAGE_UP && key != Keys.PAGE_DOWN) {
            return false;
        }
        ChatWindow w = windowAt(mouseX, mouseY);
        Box b = boxes.get(w);
        int rows = b == null ? 10 : Math.max(1, (int) (b.h() / rowHeight()));
        w.scroll = Math.max(0, w.scroll + (key == Keys.PAGE_UP ? rows - 1 : -(rows - 1)));
        return true;
    }

    /** Height of one message line on screen (GUI pixels). */
    private double rowHeight() {
        ChatText text = host.text();
        if (text == null) {
            return 9;
        }
        ChatText.Options o = text.options();
        return Math.max(1, (int) (9.0 * (o.lineSpacing() + 1.0)) * Math.max(0.1, o.scale()));
    }

    /** The chat was closed: back to the newest messages. */
    public void closed() {
        for (ChatWindow w : layout.windows) {
            w.scroll = 0;
        }
        menu = null;
        if (drag == Drag.PENDING_SCROLL) {
            drag = Drag.NONE; // closed while pressed: no click
        }
        if (drag != Drag.NONE) {
            endDrag();
        }
        open = false;
    }

    private ChatWindow windowAt(double mx, double my) {
        for (int i = layout.windows.size() - 1; i >= 0; i--) {
            ChatWindow w = layout.windows.get(i);
            Box b = boxes.get(w);
            if (b != null && mx >= b.x() && mx < b.right() && my >= b.barTop() && my < b.bottom()) {
                return w;
            }
        }
        return layout.main();
    }

    // --- dragging

    private void startMove(ChatWindow w, double mx, double my, boolean pending) {
        Box b = boxes.get(w);
        if (b == null) {
            return;
        }
        drag = pending ? Drag.PENDING_MOVE : Drag.MOVE;
        dragWindow = w;
        dragMx = (int) mx;
        dragMy = (int) my;
        startX = b.x();
        startBottom = screenH - b.bottom();
        bringToFront(w);
    }

    private void startResize(ChatWindow w, Box b, int edges, double mx, double my) {
        drag = Drag.RESIZE;
        dragWindow = w;
        dragL = (edges & 1) != 0;
        dragR = (edges & 2) != 0;
        dragT = (edges & 4) != 0;
        dragB = (edges & 8) != 0;
        dragMx = (int) mx;
        dragMy = (int) my;
        startX = b.x();
        startBottom = screenH - b.bottom();
        startW = b.w();
        startH = b.h();
        bringToFront(w);
    }

    private void bringToFront(ChatWindow w) {
        if (!w.main() && layout.windows.indexOf(w) != layout.windows.size() - 1) {
            layout.windows.remove(w);
            layout.windows.add(w);
        }
    }

    private void applyDrag(int mx, int my) {
        ChatWindow w = dragWindow;
        if (w == null) {
            drag = Drag.NONE;
            return;
        }
        int dx = mx - dragMx;
        int dy = my - dragMy;
        if (drag == Drag.PENDING_SCROLL) {
            if (Math.abs(dy) < MOVE_THRESHOLD) {
                return;
            }
            drag = Drag.SCROLL;
        }
        if (drag == Drag.SCROLL) {
            // the messages follow the mouse: down shows older ones (clamped while drawing)
            w.scroll = Math.max(0, startScroll + (int) Math.round(dy / rowHeight()));
            return;
        }
        if (drag == Drag.PENDING_MOVE) {
            if (Math.abs(dx) < MOVE_THRESHOLD && Math.abs(dy) < MOVE_THRESHOLD) {
                return;
            }
            drag = Drag.MOVE;
        }
        if (drag == Drag.MOVE) {
            w.x = Math.max(0, startX + dx);
            w.bottom = Math.max(0, startBottom - dy);
            return;
        }
        int x = startX;
        int width = startW;
        int bottom = startBottom;
        int height = startH;
        int maxW = w.main() ? host.widthMax() + 2 * PAD : screenW;
        int maxH = w.main() ? host.heightMax() : screenH - TAB_H - 2;
        if (dragR) {
            width = clamp(startW + dx, ChatWindow.MIN_W, Math.min(maxW, screenW - startX));
        }
        if (dragL) {
            int right = startX + startW;
            width = clamp(startW - dx, ChatWindow.MIN_W, Math.min(maxW, right));
            x = right - width;
        }
        if (dragT) {
            height = clamp(startH - dy, ChatWindow.MIN_H, Math.min(maxH, screenH - startBottom - TAB_H - 2));
        }
        if (dragB) {
            int top = screenH - startBottom - startH;
            int newBottomY = clamp(screenH - startBottom + dy, top + ChatWindow.MIN_H, Math.min(screenH, top + maxH));
            height = newBottomY - top;
            bottom = screenH - newBottomY;
        }
        w.x = x;
        w.bottom = bottom;
        if (w.main()) {
            host.setMainSize(width - 2 * PAD, height);
        } else {
            w.w = width;
            if (height != w.h) {
                w.hClosed = Math.min(w.hClosed == w.h ? height : w.hClosed, height);
                w.h = height;
            }
        }
    }

    private void endDrag() {
        if (drag == Drag.PENDING_SCROLL && pressed != null) {
            Hit h = pressed;
            ChatText text = host.text();
            Object style = text == null ? null : text.styleAt(h.line(), (int) ((pressedX - h.textX()) / h.scale()));
            pressed = null;
            drag = Drag.NONE;
            dragWindow = null;
            if (style != null) {
                text.click(style, host.shiftDown());
            }
            return;
        }
        pressed = null;
        boolean changed = drag == Drag.MOVE || drag == Drag.RESIZE;
        drag = Drag.NONE;
        dragWindow = null;
        if (changed) {
            layout.save();
        }
    }

    // --- menus

    private void openList(int right, int y) {
        PopupMenu m = new PopupMenu(right, y);
        for (int wi = 0; wi < layout.windows.size(); wi++) {
            ChatWindow w = layout.windows.get(wi);
            m.add(PopupMenu.Item.header(w.main() ? host.tr("tabs.window.main") : host.tr("tabs.window.other", wi)));
            for (ChatTab t : w.tabs) {
                m.add(new PopupMenu.Item(PopupMenu.Kind.ITEM, tabName(t), () -> {
                    w.select(t);
                    bringToFront(w);
                    layout.save();
                }, () -> openWizard(t, w, null), w.activeTab() == t, t.unread));
            }
        }
        m.add(PopupMenu.Item.separator());
        m.add(PopupMenu.Item.of(host.tr("tabs.new"), () -> openWizard(null, layout.main(), null)));
        m.add(PopupMenu.Item.of(host.tr("tabs.reset_places"), this::resetPlaces));
        m.x = right - 120;
        menu = m;
    }

    private void openTabMenu(ChatTab t, int x, int y) {
        ChatWindow w = layout.windowOf(t);
        if (w == null) {
            return;
        }
        PopupMenu m = new PopupMenu(x, y);
        m.add(PopupMenu.Item.of(host.tr("tabs.edit"), () -> openWizard(t, w, null)));
        if (!t.main()) {
            if (w.main()) {
                m.add(PopupMenu.Item.of(host.tr("tabs.to_window"), () -> {
                    int[] p = newWindowPlace();
                    layout.move(t, null, p[0], p[1], p[2], p[3]);
                    layout.save();
                }));
            } else {
                m.add(PopupMenu.Item.of(host.tr("tabs.to_main"), () -> {
                    layout.move(t, layout.main(), 0, 0, 0, 0);
                    layout.save();
                }));
            }
            m.add(PopupMenu.Item.of(host.tr("tabs.delete"), () -> {
                layout.remove(t);
                layout.save();
            }));
        }
        menu = m;
    }

    private void openMessageMenu(Hit h, int x, int y) {
        ChatText text = host.text();
        if (text == null) {
            return;
        }
        ChatEntry e = h.entry();
        ChatText.Parts parts = text.parts(e.component());
        String clean = e.clean();
        String sender = e.sender();
        List<String> links = dev.lumora.core.extras.ChatModule.links(clean, parts.links());
        PopupMenu m = new PopupMenu(x, y);
        m.add(PopupMenu.Item.of(host.tr("menu.copy"), () -> copied(clean)));
        if (sender != null) {
            m.add(PopupMenu.Item.of(host.tr("menu.copy_name", sender), () -> copied(sender)));
            if (!sender.equalsIgnoreCase(host.own())) {
                m.add(PopupMenu.Item.of(host.tr("menu.reply", sender), () -> host.setChatInput("/msg " + sender + " ")));
            }
        }
        for (String l : links) {
            String label = links.size() == 1 ? host.tr("menu.copy_link") : host.tr("menu.copy_link_to", host(l));
            m.add(PopupMenu.Item.of(label, () -> copied(l)));
        }
        m.add(PopupMenu.Item.of(host.tr("tabs.filter"), () -> openWizard(null, h.window(), e)));
        for (MenuEntry it : host.addonEntries(clean, sender, links)) {
            m.add(PopupMenu.Item.of(it.label(), it.action()));
        }
        menu = m;
        flashX = x;
        flashY = Math.max(2, y - 14);
    }

    static String host(String url) {
        String h = url.replaceFirst("(?i)^[a-z]+://", "").replaceFirst("^www\\.", "").replaceFirst("[/?#].*$", "");
        return h.length() > 28 ? h.substring(0, 27) + "…" : h;
    }

    private void copied(String text) {
        host.copy(text);
        flash = host.tr("menu.copied");
        flashUntil = System.currentTimeMillis() + 1200;
    }

    private void resetPlaces() {
        int[] p = newWindowPlace();
        layout.main().x = 0;
        layout.main().bottom = ChatWindow.MINECRAFT_PLACE;
        int n = 0;
        for (ChatWindow w : layout.windows) {
            if (!w.main()) {
                w.x = p[0];
                w.bottom = p[1] + n * (TAB_H + 20);
                n++;
            }
        }
        layout.save();
    }

    /** x, bottom, width, height of a new window: right of the main window if there is room, else at the right edge. */
    int[] newWindowPlace() {
        int w = 200;
        int h = 90;
        Box main = boxes.get(layout.main());
        if (main == null) {
            return new int[] {Math.max(0, screenW - w - 4), ChatLayout.DEFAULT_BOTTOM, w, h};
        }
        if (main.right() + 6 + w <= screenW - 2) {
            return new int[] {main.right() + 6, screenH - main.bottom(), w, h}; // right of the main chat
        }
        int above = main.barTop() - 4 - TAB_H - 2;
        if (above >= 40) {
            // no room at the side: above the main chat instead of on top of it
            return new int[] {main.x(), screenH - (main.barTop() - 4), Math.min(w, main.w()), Math.min(h, above)};
        }
        return new int[] {Math.max(0, screenW - w - 4), screenH - main.bottom(), w, h};
    }

    // --- tabs from the wizard

    /** Opens the window for a new tab in the main chat, with the suggestions of {@code from} first (may be null). */
    public void newTab(ChatEntry from) {
        openWizard(null, layout.main(), from);
    }

    void openWizard(ChatTab edit, ChatWindow window, ChatEntry from) {
        menu = null;
        host.show(new TabWizard(this, edit, window, from));
    }

    /** A tab the wizard created: in {@code window}, or in a new window when it is null. */
    void create(ChatTab tab, ChatWindow window) {
        if (window == null) {
            int[] p = newWindowPlace();
            layout.add(tab, null, p[0], p[1], p[2], p[3]);
        } else {
            layout.add(tab, window, 0, 0, 0, 0);
        }
        layout.save();
    }

    void edited(ChatTab tab, ChatTab changed, boolean newWindow) {
        tab.name = changed.name;
        tab.rule = changed.rule;
        tab.keepInMain = changed.keepInMain;
        tab.sound = changed.sound;
        tab.highlight = changed.highlight;
        tab.highlightColor = changed.highlightColor;
        layout.changed();
        ChatWindow w = layout.windowOf(tab);
        if (!tab.main() && w != null && newWindow == w.main()) {
            if (newWindow) {
                int[] p = newWindowPlace();
                layout.move(tab, null, p[0], p[1], p[2], p[3]);
            } else {
                layout.move(tab, layout.main(), 0, 0, 0, 0);
            }
        }
        layout.save();
    }

    void delete(ChatTab tab) {
        layout.remove(tab);
        layout.save();
    }
}
