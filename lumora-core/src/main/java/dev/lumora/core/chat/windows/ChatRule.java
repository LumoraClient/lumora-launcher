package dev.lumora.core.chat.windows;

import dev.lumora.core.extras.ChatModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a chat tab collects: every message, the messages of one player, those starting with a prefix, those with a
 * rank in front of the sender, mentions of the own name, messages containing words or messages without a player.
 */
public record ChatRule(Kind kind, String value) {
    public enum Kind {
        ALL("all", false),
        PLAYER("player", true),
        PREFIX("prefix", true),
        RANK("rank", true),
        MENTION("mention", false),
        CONTAINS("contains", true),
        SERVER("server", false);

        public final String id;
        /** The rule needs a text (name, prefix, rank or words). */
        public final boolean needsValue;

        Kind(String id, boolean needsValue) {
            this.id = id;
            this.needsValue = needsValue;
        }

        public static Kind of(String id) {
            for (Kind k : values()) {
                if (k.id.equals(id)) {
                    return k;
                }
            }
            return ALL;
        }
    }

    public static final ChatRule ALL = new ChatRule(Kind.ALL, "");

    public ChatRule {
        kind = kind == null ? Kind.ALL : kind;
        value = value == null ? "" : value.strip();
    }

    /** A rule that needs a text but has none matches nothing (the wizard does not let it through). */
    public boolean complete() {
        return !kind.needsValue || !value.isEmpty();
    }

    public boolean matches(ChatEntry e, String ownName) {
        return switch (kind) {
            case ALL -> true;
            case PLAYER -> e.sender() != null && e.sender().equalsIgnoreCase(value);
            case PREFIX -> !value.isEmpty() && startsWith(e.clean(), value);
            case RANK -> e.rank() != null && e.rank().equalsIgnoreCase(value);
            case MENTION -> mentions(e, ownName, words(value));
            case CONTAINS -> containsAny(e.clean(), words(value));
            case SERVER -> e.sender() == null;
        };
    }

    private static boolean startsWith(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static boolean mentions(ChatEntry e, String own, List<String> extra) {
        if (own != null && !own.isEmpty() && e.sender() != null && e.sender().equalsIgnoreCase(own)) {
            return false; // the own messages never mention oneself
        }
        if (own != null && !own.isEmpty() && ChatModule.indexOfWord(e.clean(), own) >= 0) {
            return true;
        }
        for (String w : extra) {
            if (ChatModule.indexOfWord(e.clean(), w) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> words) {
        String t = text.toLowerCase(Locale.ROOT);
        for (String w : words) {
            if (t.contains(w.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Comma separated words or phrases (blank ones dropped). */
    public static List<String> words(String value) {
        List<String> out = new ArrayList<>();
        for (String w : (value == null ? "" : value).split(",")) {
            String s = w.strip();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
