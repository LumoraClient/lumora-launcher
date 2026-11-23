package dev.lumora.core.chat.windows;

import java.util.ArrayList;
import java.util.List;

/** The received messages the chat windows show, oldest first, at most {@code limit} (the chat history setting). */
public final class ChatStore {
    private final List<ChatEntry> entries = new ArrayList<>();
    private long nextId = 1;
    private int version;

    public ChatEntry add(Object component, String clean, String sender, Object head, long time, boolean replaceNewest, int limit) {
        if (replaceNewest && !entries.isEmpty()) {
            entries.remove(entries.size() - 1); // the same message again: one line with "(x2)"
        }
        String prefix = ChatAnalysis.prefix(clean, sender);
        String rank = ChatAnalysis.rank(clean, sender);
        ChatEntry e = new ChatEntry(nextId++, component, clean == null ? "" : clean, sender, prefix, rank, head, time);
        entries.add(e);
        int over = entries.size() - Math.max(1, limit);
        if (over > 0) {
            entries.subList(0, over).clear();
        }
        version++;
        return e;
    }

    public void clear() {
        entries.clear();
        version++;
    }

    public int version() {
        return version;
    }

    public int size() {
        return entries.size();
    }

    /** All messages, oldest first (do not change the list). */
    public List<ChatEntry> all() {
        return entries;
    }

    /** The newest {@code n} messages, oldest first. */
    public List<ChatEntry> recent(int n) {
        return new ArrayList<>(entries.subList(Math.max(0, entries.size() - n), entries.size()));
    }
}
