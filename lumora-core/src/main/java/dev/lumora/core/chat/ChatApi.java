package dev.lumora.core.chat;

import dev.lumora.api.Registration;
import dev.lumora.api.chat.Chat;
import dev.lumora.api.chat.ChatIcons;
import dev.lumora.api.chat.ChatInputButton;
import dev.lumora.api.chat.ChatLine;
import dev.lumora.api.chat.ChatMenuEntry;
import dev.lumora.api.event.chat.ChatMessageEvent;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;
import dev.lumora.core.event.SimpleEventBus;
import dev.lumora.core.extras.GameExtras;
import dev.lumora.api.render.HudGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The chat for addons (Lumora API 1.5): their entries in the menu of chat messages and the {@link ChatMessageEvent}.
 * The version adapters' chat mixins call {@link #received} for every new message; the chat module's menu asks
 * {@link #menuEntries}. One instance per client ({@link #current()}).
 * <p>
 * Since API 1.9: buttons and panels at the chat input ({@link ChatInputBar}, drawn and clicked through
 * {@link #renderScreen}, {@link #screenClick}, {@link #screenScroll}, {@link #screenKey}), inline icons for
 * {@code :name:} ({@link #split}, used by the adapters' chat mixins) and core layers on the open chat (emoji
 * suggestions).
 */
public final class ChatApi {
    /** An addon entry ready for the menu: label and what it runs. */
    public record MenuItem(String label, Runnable action) {
    }

    private record Owned(String owner, ChatMenuEntry entry) {
        String key() {
            return owner + ":" + entry.id();
        }
    }

    private static volatile ChatApi current;

    private final SimpleEventBus events;
    private final CoreLogger log;
    private final CopyOnWriteArrayList<Owned> entries = new CopyOnWriteArrayList<>();
    private final Set<String> broken = ConcurrentHashMap.newKeySet();
    /** Turns plain text into a message with sender and links (the chat module's rules); set by the chat module. */
    private volatile Function<String, ChatLine> parser = text -> new ChatLine(text, null, List.of());
    private volatile Consumer<String> opener = text -> { };

    // --- API 1.9: input buttons, panels, inline icons

    /** A button of an addon at the chat input. */
    record OwnedButton(String owner, ChatInputButton button) {
        String key() {
            return owner + ":" + button.id();
        }
    }

    private record OwnedIcons(String owner, ChatIcons icons) {
    }

    /** One piece of a message: plain text, or an icon in place of its {@code :name:}. */
    public record Segment(String text, ChatIcons.Icon icon) {
        public boolean isIcon() {
            return icon != null;
        }
    }

    /**
     * Something the core draws on the open chat besides the input buttons (the emoji suggestions): drawn after the
     * buttons and asked first for clicks, the mouse wheel and keys.
     */
    public interface ScreenLayer {
        void render(HudGraphics g, int screenWidth, int screenHeight, int mouseX, int mouseY);

        default boolean click(int mouseX, int mouseY, int button) {
            return false;
        }

        default boolean scroll(int mouseX, int mouseY, double amount) {
            return false;
        }

        /** A key in the open chat (GLFW key code, {@link dev.lumora.api.input.Keys}). */
        default boolean key(int key) {
            return false;
        }

        /** The chat closed (no frame for a moment). */
        default void chatClosed() {
        }
    }

    /** Longest name between the colons. */
    static final int MAX_NAME = 40;
    /** Remembered glyphs for {@link #restore}; cleared when it grows past this. */
    private static final int MAX_PLACEHOLDERS = 8192;

    private final CopyOnWriteArrayList<OwnedButton> buttons = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OwnedIcons> icons = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ScreenLayer> layers = new CopyOnWriteArrayList<>();
    /** Glyph text → the {@code :name:} it stands for (copying a message gives the text back). */
    private final Map<String, String> placeholders = new ConcurrentHashMap<>();
    private final ChatInputBar bar = new ChatInputBar(this);
    private volatile Supplier<GameExtras> extras = () -> GameExtras.NONE;
    /** When a frame of the open chat was drawn last (the chat closed when there was none for a moment). */
    private volatile long lastFrame;

    public ChatApi(SimpleEventBus events, CoreLogger log) {
        this.events = events;
        this.log = log;
        current = this;
    }

    public void setExtras(Supplier<GameExtras> extras) {
        this.extras = extras;
    }

    CoreLogger log() {
        return log;
    }

    GameExtras extras() {
        try {
            GameExtras x = extras.get();
            return x == null ? GameExtras.NONE : x;
        } catch (RuntimeException e) {
            return GameExtras.NONE;
        }
    }

    /** The buttons at the chat input, in the order they were added (tests, the input bar). */
    List<OwnedButton> buttons() {
        return buttons;
    }

    ChatInputBar bar() {
        return bar;
    }

    /** Adds a core layer on the open chat; returns the removal. */
    public Runnable addLayer(ScreenLayer layer) {
        layers.add(layer);
        return () -> layers.remove(layer);
    }

    /** Adds inline icons on behalf of a built-in module (owner {@code lumora-…}); returns the removal. */
    public Runnable addIconsInternal(String owner, ChatIcons provider) {
        OwnedIcons o = new OwnedIcons(owner, provider);
        icons.add(o);
        return () -> icons.remove(o);
    }

    /** Adds an input button on behalf of a built-in module; returns the removal. */
    public Runnable addButtonInternal(String owner, ChatInputButton button) {
        OwnedButton o = new OwnedButton(owner, button);
        buttons.add(o);
        return () -> remove(o);
    }

    private void remove(OwnedButton o) {
        buttons.remove(o);
        bar.removed(o);
    }

    // --- inline icons

    /** Whether any addon added icons (the chat mixins skip all work otherwise). */
    public static boolean hasIcons() {
        ChatApi c = current;
        return c != null && !c.icons.isEmpty();
    }

    /** The icon for {@code name} (lower case), or null; the first provider that knows it wins. */
    ChatIcons.Icon icon(String name) {
        for (OwnedIcons o : icons) {
            try {
                ChatIcons.Icon i = o.icons.icon(name);
                if (i != null) {
                    return i;
                }
            } catch (RuntimeException | LinkageError e) {
                icons.remove(o);
                log.warn("Chat icons of " + o.owner + " failed and are switched off", e);
            }
        }
        return null;
    }

    /**
     * {@code text} split at every {@code :name:} that has an icon, or null if there is none (the message stays as it
     * is). Called by the version adapters' chat mixins for every piece of text of a new message.
     */
    public static List<Segment> split(String text) {
        ChatApi c = current;
        if (c == null || c.icons.isEmpty() || text == null || text.indexOf(':') < 0) {
            return null;
        }
        List<Segment> out = c.split(text, c::icon);
        if (out != null) {
            for (Segment s : out) {
                if (s.isIcon()) {
                    if (c.placeholders.size() > MAX_PLACEHOLDERS) {
                        c.placeholders.clear();
                    }
                    c.placeholders.put(s.icon().glyph(), s.text());
                }
            }
        }
        return out;
    }

    /**
     * Splits {@code text} at {@code :name:} codes known to {@code lookup}. A colon that does not start a known code
     * may still end one ("12:30:smile:" finds ":smile:"). Icon segments keep their {@code :name:} as text.
     */
    List<Segment> split(String text, Function<String, ChatIcons.Icon> lookup) {
        List<Segment> out = null;
        int plainFrom = 0;
        int i = text.indexOf(':');
        while (i >= 0 && i < text.length() - 1) {
            int j = text.indexOf(':', i + 1);
            if (j < 0) {
                break;
            }
            String name = text.substring(i + 1, j).toLowerCase(java.util.Locale.ROOT);
            ChatIcons.Icon icon = validName(name) ? lookup.apply(name) : null;
            if (icon == null) {
                i = j; // the second colon may start the next code
                continue;
            }
            if (out == null) {
                out = new ArrayList<>();
            }
            if (i > plainFrom) {
                out.add(new Segment(text.substring(plainFrom, i), null));
            }
            out.add(new Segment(text.substring(i, j + 1), icon));
            plainFrom = j + 1;
            i = text.indexOf(':', plainFrom);
        }
        if (out != null && plainFrom < text.length()) {
            out.add(new Segment(text.substring(plainFrom), null));
        }
        return out;
    }

    static boolean validName(String name) {
        if (name.isEmpty() || name.length() > MAX_NAME) {
            return false;
        }
        for (int k = 0; k < name.length(); k++) {
            char ch = name.charAt(k);
            if (!(ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '_' || ch == '+' || ch == '-')) {
                return false;
            }
        }
        return true;
    }

    /** {@code text} with every icon glyph Lumora put into the chat replaced by its {@code :name:} again (copying). */
    public static String restore(String text) {
        ChatApi c = current;
        if (c == null || text == null || c.placeholders.isEmpty()) {
            return text;
        }
        StringBuilder out = null;
        for (int k = 0; k < text.length(); k++) {
            char ch = text.charAt(k);
            String code = Character.isSurrogate(ch) || ch < 0xE000 || ch > 0xF8FF ? null : c.placeholders.get(String.valueOf(ch));
            if (code != null && out == null) {
                out = new StringBuilder(text.substring(0, k));
            }
            if (out != null) {
                out.append(code != null ? code : String.valueOf(ch));
            }
        }
        return out == null ? text : out.toString();
    }

    // --- the open chat: buttons, panels, layers

    /** Draws the input buttons, the open panel and the core layers on the open chat (after Minecraft). */
    public static void renderScreen(HudGraphics g, int mouseX, int mouseY) {
        ChatApi c = current;
        if (c == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - c.lastFrame > 500) {
            c.chatOpened();
        }
        c.lastFrame = now;
        int[] size = c.extras().guiSize();
        if (size == null || size[0] <= 0 || size[1] <= 0) {
            return;
        }
        c.bar.render(g, size[0], size[1], mouseX, mouseY, now);
        for (ScreenLayer l : c.layers) {
            try {
                l.render(g, size[0], size[1], mouseX, mouseY);
            } catch (RuntimeException e) {
                c.layers.remove(l);
                c.log.warn("Chat layer failed and is removed", e);
            }
        }
    }

    /** A click in the open chat; true if a layer, button or panel used it. */
    public static boolean screenClick(double mouseX, double mouseY, int button) {
        ChatApi c = current;
        if (c == null) {
            return false;
        }
        int x = (int) Math.floor(mouseX);
        int y = (int) Math.floor(mouseY);
        for (ScreenLayer l : c.layers) {
            if (l.click(x, y, button)) {
                return true;
            }
        }
        return c.bar.click(x, y, button);
    }

    /** The mouse wheel in the open chat ({@code amount} positive = up); true if Lumora used it. */
    public static boolean screenScroll(double mouseX, double mouseY, double amount) {
        ChatApi c = current;
        if (amount == 0) {
            return false;
        }
        if (c == null) {
            return dev.lumora.core.extras.ChatModule.screenScroll(mouseX, mouseY, amount);
        }
        int x = (int) Math.floor(mouseX);
        int y = (int) Math.floor(mouseY);
        for (ScreenLayer l : c.layers) {
            if (l.scroll(x, y, amount)) {
                return true;
            }
        }
        return c.bar.scroll(x, y, amount) || dev.lumora.core.extras.ChatModule.screenScroll(mouseX, mouseY, amount);
    }

    /** A key in the open chat (GLFW code); true if Lumora used it (Minecraft must not see it). */
    public static boolean screenKey(int key) {
        ChatApi c = current;
        if (c == null) {
            return dev.lumora.core.extras.ChatModule.screenKey(key);
        }
        for (ScreenLayer l : c.layers) {
            if (l.key(key)) {
                return true;
            }
        }
        return c.bar.key(key) || dev.lumora.core.extras.ChatModule.screenKey(key);
    }

    /** Presses the input button {@code id} of addon {@code owner} like a click (smoke tests); false if there is none. */
    public static boolean press(String owner, String id) {
        ChatApi c = current;
        if (c == null) {
            return false;
        }
        for (OwnedButton o : c.buttons) {
            if (o.owner.equals(owner) && o.button.id().equals(id)) {
                c.lastFrame = System.currentTimeMillis();
                c.bar.press(o);
                return true;
            }
        }
        return false;
    }

    /** Whether a panel is open right now (smoke tests). */
    public static boolean panelOpen() {
        ChatApi c = current;
        return c != null && c.bar.openPanel() != null;
    }

    /** The chat was opened again after a pause: panels from the last time are closed. */
    private void chatOpened() {
        bar.closePanel();
        for (ScreenLayer l : layers) {
            l.chatClosed();
        }
    }

    // --- the input field

    /** What is typed in the open chat, or "" (no chat open). */
    String inputText() {
        GameExtras.ChatInput in = extras().chatInput();
        return in == null || in.text() == null ? "" : in.text();
    }

    /** Inserts {@code text} at the cursor of the open chat input. */
    void insert(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GameExtras x = extras();
        GameExtras.ChatInput in = x.chatInput();
        if (in == null) {
            return;
        }
        String t = in.text() == null ? "" : in.text();
        int cursor = Math.max(0, Math.min(t.length(), in.cursor()));
        x.setChatInput(t.substring(0, cursor) + text + t.substring(cursor), cursor + text.length());
    }

    /**
     * Replaces the {@code count} characters before the cursor with {@code text} (emoji suggestions: ":jo" becomes
     * ":joy: ").
     */
    public void replaceBeforeCursor(int count, String text) {
        GameExtras x = extras();
        GameExtras.ChatInput in = x.chatInput();
        if (in == null) {
            return;
        }
        String t = in.text() == null ? "" : in.text();
        int cursor = Math.max(0, Math.min(t.length(), in.cursor()));
        int from = Math.max(0, cursor - count);
        x.setChatInput(t.substring(0, from) + text + t.substring(cursor), from + text.length());
    }

    /** The open chat input (text and cursor), or null. */
    public GameExtras.ChatInput input() {
        return extras().chatInput();
    }

    /** The client's chat API, or null before the core started. */
    public static ChatApi current() {
        return current;
    }

    /** A new chat message (plain text) from a version adapter's chat mixin. */
    public static void received(String plain) {
        ChatApi c = current;
        if (c == null || plain == null || plain.isBlank() || !c.events.hasListeners(ChatMessageEvent.class)) {
            return;
        }
        try {
            c.events.post(new ChatMessageEvent(c.parser.apply(plain)));
        } catch (RuntimeException e) {
            c.log.warn("Chat message event failed: " + e);
        }
    }

    public void setParser(Function<String, ChatLine> parser) {
        this.parser = parser;
    }

    public void setOpener(Consumer<String> opener) {
        this.opener = opener;
    }

    /** Addon entries for the menu of {@code line}, in the order they were added. */
    public List<MenuItem> menuEntries(ChatLine line) {
        List<MenuItem> l = new ArrayList<>();
        for (Owned o : entries) {
            if (broken.contains(o.key())) {
                continue;
            }
            try {
                if (o.entry.visibleFor(line)) {
                    String label = o.entry.label(line);
                    l.add(new MenuItem(label, () -> {
                        try {
                            o.entry.select(line);
                        } catch (RuntimeException | LinkageError e) {
                            log.warn("Chat menu entry " + o.key() + " failed", e);
                        }
                    }));
                }
            } catch (RuntimeException | LinkageError e) {
                if (broken.add(o.key())) {
                    log.warn("Chat menu entry " + o.key() + " failed and is left out", e);
                }
            }
        }
        return l;
    }

    public Chat scoped(String addon, RegistrationTracker tracker, Runnable guard) {
        return new Chat() {
            @Override
            public Registration addMenuEntry(ChatMenuEntry entry) {
                guard.run();
                if (entry == null) {
                    throw new IllegalArgumentException("entry");
                }
                for (Owned o : entries) {
                    if (o.owner.equals(addon) && o.entry.id().equals(entry.id())) {
                        throw new IllegalArgumentException("chat menu entry " + addon + ":" + entry.id() + " already exists");
                    }
                }
                Owned o = new Owned(addon, entry);
                entries.add(o);
                return tracker.track(RegistrationTracker.of(() -> {
                    entries.remove(o);
                    broken.remove(o.key());
                }));
            }

            @Override
            public void openChat(String text) {
                guard.run();
                opener.accept(text == null ? "" : text);
            }

            @Override
            public Registration addInputButton(ChatInputButton button) {
                guard.run();
                if (button == null) {
                    throw new IllegalArgumentException("button");
                }
                for (OwnedButton o : buttons) {
                    if (o.owner.equals(addon) && o.button.id().equals(button.id())) {
                        throw new IllegalArgumentException("chat input button " + addon + ":" + button.id() + " already exists");
                    }
                }
                OwnedButton o = new OwnedButton(addon, button);
                buttons.add(o);
                return tracker.track(RegistrationTracker.of(() -> remove(o)));
            }

            @Override
            public Registration addIcons(ChatIcons provider) {
                guard.run();
                if (provider == null) {
                    throw new IllegalArgumentException("icons");
                }
                OwnedIcons o = new OwnedIcons(addon, provider);
                icons.add(o);
                return tracker.track(RegistrationTracker.of(() -> icons.remove(o)));
            }

            @Override
            public void insertText(String text) {
                guard.run();
                insert(text);
            }

            @Override
            public String input() {
                guard.run();
                return inputText();
            }

            @Override
            public void closePanel() {
                guard.run();
                bar.closePanel();
            }
        };
    }
}
