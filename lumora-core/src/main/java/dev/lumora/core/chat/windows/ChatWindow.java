package dev.lumora.core.chat.windows;

import java.util.ArrayList;
import java.util.List;

/**
 * A chat window: its tabs and where it is. Positions are GUI pixels: {@code x} is the left edge, {@code bottom} the
 * distance of the bottom edge from the bottom of the screen (a chat grows upwards). The main window takes its size from
 * the chat module's settings, so only position, tabs and the active tab are kept here for it.
 */
public final class ChatWindow {
    public static final String MAIN = "main";
    public static final int MIN_W = 60;
    public static final int MIN_H = 20;
    /** {@code bottom} of a main window that was never moved: where Minecraft puts its chat in this version. */
    public static final int MINECRAFT_PLACE = -1;

    public final String id;
    public final List<ChatTab> tabs = new ArrayList<>();
    public int active;
    public int x;
    public int bottom;
    public int w;
    public int h;
    public int hClosed;
    /** Wrapped lines scrolled up (0 = newest at the bottom). */
    public transient int scroll;

    public ChatWindow(String id, int x, int bottom, int w, int h, int hClosed) {
        this.id = id;
        this.x = x;
        this.bottom = bottom;
        this.w = w;
        this.h = h;
        this.hClosed = hClosed;
    }

    public boolean main() {
        return MAIN.equals(id);
    }

    public ChatTab activeTab() {
        if (tabs.isEmpty()) {
            return null;
        }
        active = Math.max(0, Math.min(active, tabs.size() - 1));
        return tabs.get(active);
    }

    public void select(ChatTab tab) {
        int i = tabs.indexOf(tab);
        if (i >= 0) {
            active = i;
            tab.unread = 0;
            scroll = 0;
        }
    }
}
