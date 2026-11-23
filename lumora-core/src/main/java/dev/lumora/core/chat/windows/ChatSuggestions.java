package dev.lumora.core.chat.windows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab suggestions found in the chat: prefixes, ranks and players that come up often in the recent messages, or the
 * ones of a single message ("Filter" in the message menu).
 */
public final class ChatSuggestions {
    /** How many recent messages the suggestions look at (the wizard says "of the last 50 messages"). */
    public static final int RECENT = 50;

    /**
     * @param rule    what the tab would collect
     * @param count   matching messages among the recent ones
     * @param example the newest matching message
     */
    public record Suggestion(ChatRule rule, int count, ChatEntry example) {
    }

    private ChatSuggestions() {
    }

    /** The most frequent prefixes, ranks and players of the recent messages (at least twice), most frequent first. */
    public static List<Suggestion> recent(List<ChatEntry> recent, String own, Collection<ChatRule> existing, int max) {
        Map<ChatRule, Suggestion> found = new LinkedHashMap<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatEntry e = recent.get(i);
            for (ChatRule r : rules(e, own)) {
                Suggestion s = found.get(r);
                found.put(r, s == null ? new Suggestion(r, 1, e) : new Suggestion(r, s.count() + 1, s.example()));
            }
        }
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : found.values()) {
            if (s.count() >= 2 && !contains(existing, s.rule())) {
                out.add(s);
            }
        }
        out.sort(Comparator.comparingInt(Suggestion::count).reversed());
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** What a tab could collect from one message: its sender, prefix and rank, each with its count in {@code recent}. */
    public static List<Suggestion> of(ChatEntry e, List<ChatEntry> recent, String own) {
        List<Suggestion> out = new ArrayList<>();
        for (ChatRule r : rules(e, own)) {
            int n = 0;
            for (ChatEntry x : recent) {
                if (r.matches(x, own)) {
                    n++;
                }
            }
            out.add(new Suggestion(r, Math.max(1, n), e));
        }
        return out;
    }

    /** The rules a message suggests: player, prefix, rank (case-insensitive duplicates once). */
    static List<ChatRule> rules(ChatEntry e, String own) {
        List<ChatRule> out = new ArrayList<>();
        if (e.sender() != null && (own == null || !e.sender().equalsIgnoreCase(own))) {
            out.add(new ChatRule(ChatRule.Kind.PLAYER, e.sender()));
        }
        if (e.prefix() != null) {
            out.add(new ChatRule(ChatRule.Kind.PREFIX, e.prefix()));
        }
        if (e.rank() != null && (e.prefix() == null || !e.rank().equalsIgnoreCase(e.prefix()))) {
            out.add(new ChatRule(ChatRule.Kind.RANK, e.rank()));
        }
        return out;
    }

    private static boolean contains(Collection<ChatRule> rules, ChatRule r) {
        for (ChatRule x : rules) {
            if (x.kind() == r.kind() && x.value().equalsIgnoreCase(r.value())) {
                return true;
            }
        }
        return false;
    }
}
