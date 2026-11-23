package dev.lumora.core.chat.windows;

import dev.lumora.api.render.HudGraphics;
import dev.lumora.core.theme.LumoraTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * A small menu in the open chat (message menu, tab menu, the list behind "≡"): entries, headers, separators and an
 * optional pencil at the right end of an entry.
 */
final class PopupMenu {
    static final int ITEM_H = 12;
    static final int HEADER_H = 11;
    static final int SEP_H = 5;
    static final int EDIT_W = 12;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8D9BB8;
    static final int BG = 0xF0121A20;

    enum Kind { ITEM, HEADER, SEPARATOR }

    /**
     * @param edit   action of the pencil at the right end, null without one
     * @param marked a dot in front (the active tab)
     */
    record Item(Kind kind, String label, Runnable action, Runnable edit, boolean marked, int badge) {
        static Item of(String label, Runnable action) {
            return new Item(Kind.ITEM, label, action, null, false, 0);
        }

        static Item header(String label) {
            return new Item(Kind.HEADER, label, null, null, false, 0);
        }

        static Item separator() {
            return new Item(Kind.SEPARATOR, "", null, null, false, 0);
        }

        int height() {
            return kind == Kind.ITEM ? ITEM_H : kind == Kind.HEADER ? HEADER_H : SEP_H;
        }
    }

    final List<Item> items = new ArrayList<>();
    int x;
    int y;
    int w;
    int h;
    private boolean laidOut;

    PopupMenu(int x, int y) {
        this.x = x;
        this.y = y;
    }

    PopupMenu add(Item item) {
        items.add(item);
        laidOut = false;
        return this;
    }

    boolean isEmpty() {
        return items.isEmpty();
    }

    private void layout(HudGraphics g, int screenW, int screenH) {
        if (laidOut) {
            return;
        }
        int width = 40;
        int height = 4;
        boolean marks = items.stream().anyMatch(Item::marked);
        for (Item i : items) {
            int iw = g.textWidth(i.label()) + 12 + (marks ? 8 : 0) + (i.edit() != null ? EDIT_W + 2 : 0)
                    + (i.badge() > 0 ? g.textWidth(String.valueOf(i.badge())) + 8 : 0);
            width = Math.max(width, iw);
            height += i.height();
        }
        w = width;
        h = height;
        x = Math.max(2, Math.min(x, screenW - w - 2));
        if (y + h > screenH - 16) {
            y = Math.max(2, y - h);
        }
        laidOut = true;
    }

    /** Index of the entry at the mouse, -1 if none (headers and separators never count). */
    int itemAt(double mx, double my) {
        if (!laidOut || mx < x || mx >= x + w) {
            return -1;
        }
        int iy = y + 2;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (my >= iy && my < iy + it.height()) {
                return it.kind() == Kind.ITEM ? i : -1;
            }
            iy += it.height();
        }
        return -1;
    }

    boolean onEdit(int index, double mx) {
        return index >= 0 && items.get(index).edit() != null && mx >= x + w - EDIT_W - 3;
    }

    boolean contains(double mx, double my) {
        return laidOut && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    void render(HudGraphics g, int screenW, int screenH, int mx, int my) {
        layout(g, screenW, screenH);
        int accent = LumoraTheme.accent();
        g.card(x, y, w, h, 4, BG, LumoraTheme.alpha(accent, 0x90));
        int hover = itemAt(mx, my);
        boolean marks = items.stream().anyMatch(Item::marked);
        int iy = y + 2;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            switch (it.kind()) {
                case HEADER -> g.text(it.label(), x + 6, iy + 2, MUTED, false);
                case SEPARATOR -> g.fill(x + 4, iy + 2, x + w - 4, iy + 3, 0x40FFFFFF);
                case ITEM -> {
                    boolean onEdit = i == hover && onEdit(i, mx);
                    if (i == hover && !onEdit) {
                        g.roundedRect(x + 2, iy, w - 4, ITEM_H, 3, LumoraTheme.alpha(accent, 0x70));
                    }
                    int tx = x + 6;
                    if (marks) {
                        if (it.marked()) {
                            g.roundedRect(tx, iy + 4, 4, 4, 2, accent);
                        }
                        tx += 8;
                    }
                    g.text(it.label(), tx, iy + (ITEM_H - g.lineHeight()) / 2 + 1, TEXT, false);
                    int right = x + w - 4;
                    if (it.edit() != null) {
                        int ex = x + w - EDIT_W - 3;
                        if (onEdit) {
                            g.roundedRect(ex, iy, EDIT_W, ITEM_H, 3, LumoraTheme.alpha(accent, 0x70));
                        }
                        ChatIcons.pencil(g, ex + 3, iy + 3, TEXT);
                        right = ex - 2;
                    }
                    if (it.badge() > 0) {
                        String b = String.valueOf(it.badge());
                        int bw = g.textWidth(b) + 6;
                        g.roundedRect(right - bw, iy + 1, bw, ITEM_H - 2, 4, accent);
                        g.text(b, right - bw + 3, iy + 3, TEXT, false);
                    }
                }
            }
            iy += it.height();
        }
    }
}
