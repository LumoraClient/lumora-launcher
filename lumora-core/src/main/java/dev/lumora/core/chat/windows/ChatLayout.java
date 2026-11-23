package dev.lumora.core.chat.windows;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.core.io.AtomicFiles;
import dev.lumora.core.io.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The chat windows and their tabs, saved in {@code config/lumora/chat-tabs.json}. There is always a main window with
 * the main tab first; other tabs sit in the main window or in windows of their own.
 */
public final class ChatLayout {
    static final int DEFAULT_BOTTOM = 40;

    private final Path file;
    public final List<ChatWindow> windows = new ArrayList<>();
    /** Changes with every change of tabs or rules (views are cached against it). */
    private int version;
    private int nextId = 1;

    public ChatLayout(Path file) {
        this.file = file;
        reset();
    }

    public void reset() {
        windows.clear();
        ChatWindow main = new ChatWindow(ChatWindow.MAIN, 0, ChatWindow.MINECRAFT_PLACE, 0, 0, 0);
        main.tabs.add(new ChatTab(ChatTab.MAIN, "", ChatRule.ALL));
        windows.add(main);
        changed();
    }

    public int version() {
        return version;
    }

    public void changed() {
        version++;
    }

    public ChatWindow main() {
        return windows.get(0);
    }

    public ChatTab mainTab() {
        return main().tabs.get(0);
    }

    public List<ChatTab> tabs() {
        List<ChatTab> out = new ArrayList<>();
        for (ChatWindow w : windows) {
            out.addAll(w.tabs);
        }
        return out;
    }

    public ChatWindow windowOf(ChatTab tab) {
        for (ChatWindow w : windows) {
            if (w.tabs.contains(tab)) {
                return w;
            }
        }
        return null;
    }

    /** Whether a message shows in the main tab: no tab that takes its messages out of the main chat wants it. */
    public boolean inMain(ChatEntry e, String own) {
        for (ChatWindow w : windows) {
            for (ChatTab t : w.tabs) {
                if (!t.main() && !t.keepInMain && t.rule.complete() && t.rule.matches(e, own)) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean shows(ChatTab tab, ChatEntry e, String own) {
        if (tab.main()) {
            return inMain(e, own);
        }
        return tab.rule.complete() && tab.rule.matches(e, own);
    }

    public String newTabId() {
        String id;
        do {
            id = "t" + nextId++;
        } while (find(id) != null);
        return id;
    }

    private String newWindowId() {
        String id;
        int n = windows.size();
        do {
            id = "w" + n++;
        } while (window(id) != null);
        return id;
    }

    public ChatTab find(String tabId) {
        for (ChatTab t : tabs()) {
            if (t.id.equals(tabId)) {
                return t;
            }
        }
        return null;
    }

    public ChatWindow window(String id) {
        for (ChatWindow w : windows) {
            if (w.id.equals(id)) {
                return w;
            }
        }
        return null;
    }

    /** Adds a tab to {@code window}, or to a new window at the given place when {@code window} is null. */
    public ChatWindow add(ChatTab tab, ChatWindow window, int x, int bottom, int w, int h) {
        ChatWindow target = window;
        if (target == null) {
            target = new ChatWindow(newWindowId(), x, bottom, w, h, Math.min(h, 90));
            windows.add(target);
        }
        target.tabs.add(tab);
        target.select(tab);
        changed();
        return target;
    }

    /** Removes a tab (never the main tab); a window without tabs goes too. */
    public void remove(ChatTab tab) {
        if (tab.main()) {
            return;
        }
        ChatWindow w = windowOf(tab);
        if (w == null) {
            return;
        }
        int i = w.tabs.indexOf(tab);
        w.tabs.remove(tab);
        if (w.active >= i && w.active > 0) {
            w.active--;
        }
        if (w.tabs.isEmpty() && !w.main()) {
            windows.remove(w);
        }
        changed();
    }

    /** Moves a tab into another window, or into a new window at the given place when {@code target} is null. */
    public void move(ChatTab tab, ChatWindow target, int x, int bottom, int w, int h) {
        if (tab.main()) {
            return;
        }
        ChatWindow from = windowOf(tab);
        if (from == null || from == target) {
            return;
        }
        remove(tab);
        add(tab, target, x, bottom, w, h);
    }

    // --- file

    public void load() {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject o = Json.parse(Files.readString(file)).getAsJsonObject();
            List<ChatWindow> loaded = new ArrayList<>();
            for (JsonElement we : o.getAsJsonArray("windows")) {
                JsonObject wo = we.getAsJsonObject();
                ChatWindow w = new ChatWindow(str(wo, "id", "w" + loaded.size()), num(wo, "x", 0), num(wo, "bottom", DEFAULT_BOTTOM),
                        num(wo, "w", 200), num(wo, "h", 90), num(wo, "hc", 90));
                for (JsonElement te : wo.getAsJsonArray("tabs")) {
                    JsonObject to = te.getAsJsonObject();
                    ChatTab t = new ChatTab(str(to, "id", newTabId()), str(to, "name", ""),
                            new ChatRule(ChatRule.Kind.of(str(to, "kind", "all")), str(to, "value", "")));
                    t.keepInMain = bool(to, "keep", true);
                    t.sound = bool(to, "sound", false);
                    t.highlight = bool(to, "highlight", false);
                    t.highlightColor = num(to, "color", ChatTab.DEFAULT_HIGHLIGHT) | 0xFF000000;
                    if (t.main() && !loaded.isEmpty()) {
                        continue; // the main tab only in the main window
                    }
                    w.tabs.add(t);
                }
                w.active = num(wo, "active", 0);
                if (!w.tabs.isEmpty()) {
                    loaded.add(w);
                }
            }
            if (loaded.isEmpty() || !loaded.get(0).main() || loaded.get(0).tabs.isEmpty() || !loaded.get(0).tabs.get(0).main()) {
                return; // not a layout of this version: keep the default
            }
            windows.clear();
            windows.addAll(loaded);
            for (ChatTab t : tabs()) {
                if (t.id.startsWith("t")) {
                    try {
                        nextId = Math.max(nextId, Integer.parseInt(t.id.substring(1)) + 1);
                    } catch (NumberFormatException ignored) {
                        // own id
                    }
                }
            }
            changed();
        } catch (IOException | RuntimeException e) {
            AtomicFiles.backup(file, "unreadable");
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        JsonArray ws = new JsonArray();
        for (ChatWindow w : windows) {
            JsonObject wo = new JsonObject();
            wo.addProperty("id", w.id);
            wo.addProperty("x", w.x);
            wo.addProperty("bottom", w.bottom);
            wo.addProperty("w", w.w);
            wo.addProperty("h", w.h);
            wo.addProperty("hc", w.hClosed);
            wo.addProperty("active", w.active);
            JsonArray ts = new JsonArray();
            for (ChatTab t : w.tabs) {
                JsonObject to = new JsonObject();
                to.addProperty("id", t.id);
                to.addProperty("name", t.name);
                to.addProperty("kind", t.rule.kind().id);
                to.addProperty("value", t.rule.value());
                to.addProperty("keep", t.keepInMain);
                to.addProperty("sound", t.sound);
                to.addProperty("highlight", t.highlight);
                to.addProperty("color", t.highlightColor);
                ts.add(to);
            }
            wo.add("tabs", ts);
            ws.add(wo);
        }
        o.add("windows", ws);
        try {
            Files.createDirectories(file.getParent());
            AtomicFiles.write(file, o.toString());
        } catch (IOException ignored) {
            // read-only config: the layout lives until the game closes
        }
    }

    private static String str(JsonObject o, String k, String def) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? def : e.getAsString();
    }

    private static int num(JsonObject o, String k, int def) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? def : e.getAsInt();
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? def : e.getAsBoolean();
    }
}
