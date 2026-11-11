package dev.lumora.core.extras;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.extras.GameExtras.ChatMessage;
import dev.lumora.core.extras.GameExtras.ChatView;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * What the chat module draws on top of the open chat: the frame for dragging the right and the top edge, the
 * highlight of the message under the mouse with its "⋯" button, and the message menu (also on right-click).
 */
final class ChatOverlay {
    enum Edge { NONE, WIDTH, HEIGHT, BOTH }

    /** Distance in pixels from an edge that still grabs it. */
    static final int GRAB = 3;
    static final int BUTTON_W = 12;
    static final int ITEM_H = 12;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MENU_BG = 0xF0141220;

    private final ChatModule module;
    Edge drag = Edge.NONE;
    Menu menu;
    private long lastFrame;
    private ChatMessage cached;
    private int cachedLine = -1;
    private int cachedLines = -1;
    private int cachedMessages = -1;
    /** The "⋯" button drawn in the last frame (line -1: none). */
    private int buttonLine = -1;
    private int buttonX;
    private int buttonY;
    private int buttonH;
    private String flash;
    private long flashUntil;
    private int flashX;
    private int flashY;

    record Item(String label, Runnable action) {
    }

    static final class Menu {
        final List<Item> items;
        int x;
        int y;
        int w;
        int h;

        Menu(List<Item> items, int x, int y) {
            this.items = items;
            this.x = x;
            this.y = y;
        }

        int itemAt(double mx, double my) {
            if (w == 0 || mx < x || mx >= x + w || my < y + 2 || my >= y + 2 + items.size() * ITEM_H) {
                return -1;
            }
            return (int) ((my - y - 2) / ITEM_H);
        }
    }

    ChatOverlay(ChatModule module) {
        this.module = module;
    }

    void reset() {
        drag = Edge.NONE;
        menu = null;
        cached = null;
        cachedLine = -1;
        buttonLine = -1;
        flash = null;
    }

    // --- geometry (GUI pixels)

    static Edge edgeAt(ChatView v, double mx, double my) {
        int top = v.top();
        boolean right = Math.abs(mx - v.right()) <= GRAB && my >= top - GRAB && my <= v.bottom();
        boolean upper = Math.abs(my - top) <= GRAB && mx >= v.left() && mx <= v.right() + GRAB;
        if (right && upper) {
            return Edge.BOTH;
        }
        return right ? Edge.WIDTH : upper ? Edge.HEIGHT : Edge.NONE;
    }

    /** Wrapped line under the mouse (0 = newest), -1 if none. The "⋯" button right of the chat counts as its line. */
    static int lineAt(ChatView v, double mx, double my) {
        if (mx < v.left() || mx > v.right() + 4 + BUTTON_W || v.rowHeight() <= 0) {
            return -1;
        }
        double r = (v.bottom() - my) / v.rowHeight();
        if (r < 0) {
            return -1;
        }
        int row = (int) r;
        if (row >= v.rows()) {
            return -1;
        }
        int line = v.scroll() + row;
        return line < v.lines() ? line : -1;
    }

    static int rowTop(ChatView v, int row) {
        return (int) Math.round(v.bottom() - (row + 1) * v.rowHeight());
    }

    static int rowBottom(ChatView v, int row) {
        return (int) Math.round(v.bottom() - row * v.rowHeight());
    }

    /** New width and open height for the mouse at (mx, my) while dragging {@code edge}: {width, height}. */
    static int[] dragged(ChatView v, Edge edge, double mx, double my, int width, int height, int maxWidth, int maxHeight) {
        int w = width;
        int h = height;
        if (edge == Edge.WIDTH || edge == Edge.BOTH) {
            int pad = (v.right() - v.left()) - v.width();
            int max = Math.max(40, Math.min(maxWidth, v.guiWidth() - v.left() - pad - 2));
            w = clamp((int) Math.round(mx - v.left() - pad), 40, max);
        }
        if (edge == Edge.HEIGHT || edge == Edge.BOTH) {
            int max = Math.max(20, Math.min(maxHeight, (int) ((v.bottom() - 2) / v.scale())));
            h = clamp((int) Math.round((v.bottom() - my) / v.scale()), 20, max);
        }
        return new int[] {w, h};
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // --- drawing

    void render(HudGraphics g, GameExtras x, int mx, int my, long now) {
        if (now - lastFrame > 250) {
            reset(); // the chat was closed in between
        }
        lastFrame = now;
        ChatView v = x.chatView();
        if (v == null) {
            return;
        }
        if (drag != Edge.NONE) {
            if (x.mouseLeftDown()) {
                applyDrag(x, v, mx, my);
                v = x.chatView();
                if (v == null) {
                    return;
                }
            } else {
                drag = Edge.NONE;
            }
        }
        boolean resize = module.resize.get();
        Edge edge = drag != Edge.NONE ? drag : resize && menu == null ? edgeAt(v, mx, my) : Edge.NONE;
        if (resize) {
            drawFrame(g, v, edge);
        }
        buttonLine = -1;
        if (drag == Edge.NONE && edge == Edge.NONE && menu == null && module.menu.get()) {
            drawHover(g, x, v, mx, my);
        }
        if (menu != null) {
            drawMenu(g, v, mx, my);
        }
        if (flash != null && now < flashUntil) {
            int w = g.textWidth(flash) + 8;
            int fx = clamp(flashX, 2, Math.max(2, v.guiWidth() - w - 2));
            g.card(fx, flashY, w, 12, 3, MENU_BG, LumoraTheme.alpha(0xFF7CE38B, 0xC0));
            g.text(flash, fx + 4, flashY + 2, 0xFF7CE38B, false);
        }
    }

    private void applyDrag(GameExtras x, ChatView v, int mx, int my) {
        if (!module.customSize.get()) {
            module.width.set(v.width());
            module.heightOpen.set(v.height());
            module.customSize.set(true);
        }
        int[] wh = dragged(v, drag, mx, my, module.width.get(), module.heightOpen.get(), module.width.max(),
                module.heightOpen.max());
        if (wh[1] != module.heightOpen.get()) {
            module.heightOpen.set(wh[1]);
        }
        if (wh[0] != module.width.get()) {
            module.width.set(wh[0]); // re-wraps the lines (onChange)
        }
    }

    private void drawFrame(HudGraphics g, ChatView v, Edge edge) {
        int top = v.top();
        int accent = LumoraTheme.accent();
        if (edge == Edge.NONE) {
            // a small corner as a hint that the chat can be resized
            int c = 0x90FFFFFF;
            g.fill(v.right() - 5, top, v.right(), top + 1, c);
            g.fill(v.right() - 1, top, v.right(), top + 5, c);
            return;
        }
        g.outline(v.left(), top, v.right() - v.left(), v.bottom() - top, LumoraTheme.alpha(accent, 0x90));
        if (edge == Edge.WIDTH || edge == Edge.BOTH) {
            g.fill(v.right() - 1, top, v.right() + 1, v.bottom(), accent);
        }
        if (edge == Edge.HEIGHT || edge == Edge.BOTH) {
            g.fill(v.left(), top - 1, v.right(), top + 1, accent);
        }
        g.fill(v.right() - 3, top - 2, v.right() + 3, top + 4, accent);
        if (drag != Edge.NONE) {
            String size = module.width.get() + " × " + module.heightOpen.get();
            int w = g.textWidth(size) + 8;
            int lx = clamp(v.right() - w, 2, Math.max(2, v.guiWidth() - w - 2));
            int ly = Math.max(2, top - 15);
            g.card(lx, ly, w, 12, 3, MENU_BG, LumoraTheme.alpha(accent, 0xA0));
            g.text(size, lx + 4, ly + 2, TEXT, false);
        }
    }

    private void drawHover(HudGraphics g, GameExtras x, ChatView v, int mx, int my) {
        int line = lineAt(v, mx, my);
        if (line < 0) {
            return;
        }
        ChatMessage msg = message(x, v, line);
        if (msg == null) {
            return;
        }
        int lo = Math.max(msg.firstLine(), v.scroll());
        int hi = Math.min(msg.lastLine(), v.scroll() + v.rows() - 1);
        if (lo <= hi) {
            g.fill(v.left(), rowTop(v, hi - v.scroll()), v.right(), rowBottom(v, lo - v.scroll()), 0x26FFFFFF);
        }
        int row = line - v.scroll();
        int by = rowTop(v, row);
        int bh = Math.max(9, rowBottom(v, row) - by);
        int bx = v.right() + 4;
        if (bx + BUTTON_W > v.guiWidth() - 1) {
            bx = v.right() - BUTTON_W - 1; // no room right of the chat
        }
        boolean over = mx >= bx && mx < bx + BUTTON_W && my >= by && my < by + bh;
        g.roundedRect(bx, by, BUTTON_W, bh, 2, over ? LumoraTheme.alpha(LumoraTheme.accent(), 0xE0) : 0xC0101018);
        int cy = by + bh / 2 - 1;
        for (int i = 0; i < 3; i++) {
            g.fill(bx + 2 + i * 3, cy, bx + 4 + i * 3, cy + 2, TEXT);
        }
        buttonLine = line;
        buttonX = bx;
        buttonY = by;
        buttonH = bh;
    }

    private void drawMenu(HudGraphics g, ChatView v, int mx, int my) {
        Menu m = menu;
        if (m.w == 0) {
            int w = 0;
            for (Item i : m.items) {
                w = Math.max(w, g.textWidth(i.label()));
            }
            m.w = w + 12;
            m.h = m.items.size() * ITEM_H + 4;
            m.x = clamp(m.x, 2, Math.max(2, v.guiWidth() - m.w - 2));
            if (m.y + m.h > v.guiHeight() - 16) {
                m.y = Math.max(2, m.y - m.h);
            }
        }
        int accent = LumoraTheme.accent();
        g.card(m.x, m.y, m.w, m.h, 4, MENU_BG, LumoraTheme.alpha(accent, 0x90));
        int hover = m.itemAt(mx, my);
        for (int i = 0; i < m.items.size(); i++) {
            int iy = m.y + 2 + i * ITEM_H;
            if (i == hover) {
                g.roundedRect(m.x + 2, iy, m.w - 4, ITEM_H, 3, LumoraTheme.alpha(accent, 0x70));
            }
            g.text(m.items.get(i).label(), m.x + 6, iy + (ITEM_H - g.lineHeight()) / 2 + 1, TEXT, false);
        }
    }

    // --- input

    boolean click(GameExtras x, double mx, double my, int button) {
        ChatView v = x.chatView();
        if (v == null) {
            return false;
        }
        if (menu != null) {
            Menu m = menu;
            menu = null;
            int i = m.itemAt(mx, my);
            if (i >= 0 && button == 0) {
                module.click();
                m.items.get(i).action().run();
            }
            return true; // a click next to the menu only closes it
        }
        if (button == 0 && module.resize.get()) {
            Edge e = edgeAt(v, mx, my);
            if (e != Edge.NONE) {
                drag = e;
                return true;
            }
        }
        if (!module.menu.get()) {
            return false;
        }
        if (button == 0 && buttonLine >= 0 && mx >= buttonX && mx < buttonX + BUTTON_W && my >= buttonY && my < buttonY + buttonH) {
            return open(x, v, buttonLine, buttonX + BUTTON_W + 2, buttonY);
        }
        if (button == 1) {
            int line = lineAt(v, mx, my);
            return line >= 0 && open(x, v, line, (int) mx + 2, (int) my);
        }
        return false;
    }

    private boolean open(GameExtras x, ChatView v, int line, int ax, int ay) {
        ChatMessage msg = message(x, v, line);
        if (msg == null) {
            return false;
        }
        menu = new Menu(items(x, msg), ax, ay);
        flashX = ax;
        flashY = Math.max(2, ay - 14);
        return true;
    }

    List<Item> items(GameExtras x, ChatMessage msg) {
        String text = ChatModule.clean(msg.text());
        String sender = ChatModule.sender(msg.text(), msg.insertions(), msg.commands(), module.tabNames());
        List<String> links = ChatModule.links(text, msg.links());
        List<Item> items = new ArrayList<>();
        items.add(new Item(module.tr("menu.copy"), () -> copied(text)));
        if (sender != null) {
            items.add(new Item(module.tr("menu.copy_name", sender), () -> copied(sender)));
            if (!sender.equalsIgnoreCase(module.ownName())) {
                items.add(new Item(module.tr("menu.reply", sender), () -> x.setChatInput("/msg " + sender + " ")));
            }
        }
        for (String l : links) {
            String label = links.size() == 1 ? module.tr("menu.copy_link") : module.tr("menu.copy_link_to", host(l));
            items.add(new Item(label, () -> copied(l)));
        }
        dev.lumora.core.chat.ChatApi api = dev.lumora.core.chat.ChatApi.current();
        if (api != null) {
            // entries of addons (Lumora API 1.5) after Lumora's own
            for (dev.lumora.core.chat.ChatApi.MenuItem m : api.menuEntries(new dev.lumora.api.chat.ChatLine(text, sender, links))) {
                items.add(new Item(m.label(), m.action()));
            }
        }
        return items;
    }

    private void copied(String text) {
        module.copy(text);
        flash = module.tr("menu.copied");
        flashUntil = System.currentTimeMillis() + 1200;
    }

    static String host(String url) {
        String h = url.replaceFirst("(?i)^[a-z]+://", "").replaceFirst("^www\\.", "").replaceFirst("[/?#].*$", "");
        return h.length() > 28 ? h.substring(0, 27) + "…" : h;
    }

    private ChatMessage message(GameExtras x, ChatView v, int line) {
        int count = module.messages;
        if (line != cachedLine || v.lines() != cachedLines || count != cachedMessages) {
            try {
                cached = x.chatMessage(line);
            } catch (RuntimeException e) {
                cached = null;
            }
            cachedLine = line;
            cachedLines = v.lines();
            cachedMessages = count;
        }
        return cached;
    }
}
