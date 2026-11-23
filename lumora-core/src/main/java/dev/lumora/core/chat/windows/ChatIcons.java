package dev.lumora.core.chat.windows;

import dev.lumora.api.render.HudGraphics;

/** Small pixel icons of the chat windows, drawn from rectangles (no textures, same look in every version). */
final class ChatIcons {
    private ChatIcons() {
    }

    /** 6x6 pencil, tip at the bottom left. */
    static void pencil(HudGraphics g, int x, int y, int c) {
        for (int i = 0; i < 5; i++) {
            g.fill(x + 1 + i, y + 4 - i, x + 2 + i, y + 5 - i, c);
            g.fill(x + 2 + i, y + 4 - i, x + 3 + i, y + 5 - i, c);
        }
        g.fill(x, y + 5, x + 1, y + 6, c);
    }

    /** 7x7 plus. */
    static void plus(HudGraphics g, int x, int y, int c) {
        g.fill(x + 3, y, x + 4, y + 7, c);
        g.fill(x, y + 3, x + 7, y + 4, c);
    }

    /** 7x5 three lines. */
    static void lines(HudGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 7, y + 1, c);
        g.fill(x, y + 2, x + 7, y + 3, c);
        g.fill(x, y + 4, x + 7, y + 5, c);
    }

    /** 8x2 three dots. */
    static void dots(HudGraphics g, int x, int y, int c) {
        for (int i = 0; i < 3; i++) {
            g.fill(x + i * 3, y, x + 2 + i * 3, y + 2, c);
        }
    }

    /** 7x7 cross. */
    static void cross(HudGraphics g, int x, int y, int c) {
        for (int i = 0; i < 7; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, c);
            g.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, c);
        }
    }

    /** 9x7 "▸ lines": messages starting with a prefix. */
    static void prefix(HudGraphics g, int x, int y, int c) {
        g.fill(x, y + 1, x + 1, y + 6, c);
        g.fill(x + 1, y + 2, x + 2, y + 5, c);
        g.fill(x + 2, y + 3, x + 3, y + 4, c);
        g.fill(x + 4, y + 1, x + 9, y + 2, c);
        g.fill(x + 4, y + 3, x + 9, y + 4, c);
        g.fill(x + 4, y + 5, x + 9, y + 6, c);
    }

    /** 7x7 person: a player. */
    static void person(HudGraphics g, int x, int y, int c) {
        g.fill(x + 2, y, x + 5, y + 3, c);
        g.fill(x, y + 4, x + 7, y + 7, c);
    }

    /** 7x7 badge: a rank. */
    static void badge(HudGraphics g, int x, int y, int c) {
        g.fill(x + 1, y, x + 6, y + 5, c);
        g.fill(x + 2, y + 5, x + 5, y + 6, c);
        g.fill(x + 3, y + 6, x + 4, y + 7, c);
    }

    /** 7x7 "@": mentions. */
    static void at(HudGraphics g, int x, int y, int c) {
        g.fill(x + 1, y, x + 6, y + 1, c);
        g.fill(x, y + 1, x + 1, y + 6, c);
        g.fill(x + 6, y + 1, x + 7, y + 5, c);
        g.fill(x + 1, y + 6, x + 6, y + 7, c);
        g.fill(x + 2, y + 2, x + 5, y + 3, c);
        g.fill(x + 2, y + 4, x + 5, y + 5, c);
        g.fill(x + 2, y + 3, x + 3, y + 4, c);
        g.fill(x + 4, y + 3, x + 5, y + 4, c);
    }

    /** 7x7 speech bubble: everything / words. */
    static void bubble(HudGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 7, y + 5, c);
        g.fill(x + 1, y + 5, x + 3, y + 6, c);
        g.fill(x + 1, y + 6, x + 2, y + 7, c);
    }

    /** 7x7 globe-ish square: server messages. */
    static void server(HudGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 7, y + 3, c);
        g.fill(x, y + 4, x + 7, y + 7, c);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF000000);
        g.fill(x + 1, y + 5, x + 2, y + 6, 0xFF000000);
    }

    static void of(HudGraphics g, ChatRule.Kind kind, int x, int y, int c) {
        switch (kind) {
            case PLAYER -> person(g, x, y, c);
            case PREFIX -> prefix(g, x, y, c);
            case RANK -> badge(g, x, y, c);
            case MENTION -> at(g, x, y, c);
            case SERVER -> server(g, x, y, c);
            default -> bubble(g, x, y, c);
        }
    }
}
