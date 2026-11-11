package dev.lumora.core.chat;

import dev.lumora.api.Registration;
import dev.lumora.api.chat.Chat;
import dev.lumora.api.chat.ChatLine;
import dev.lumora.api.chat.ChatMenuEntry;
import dev.lumora.api.event.chat.ChatMessageEvent;
import dev.lumora.core.CoreLogger;
import dev.lumora.core.RegistrationTracker;
import dev.lumora.core.event.SimpleEventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The chat for addons (Lumora API 1.5): their entries in the menu of chat messages and the {@link ChatMessageEvent}.
 * The version adapters' chat mixins call {@link #received} for every new message; the chat module's menu asks
 * {@link #menuEntries}. One instance per client ({@link #current()}).
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

    public ChatApi(SimpleEventBus events, CoreLogger log) {
        this.events = events;
        this.log = log;
        current = this;
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
        };
    }
}
