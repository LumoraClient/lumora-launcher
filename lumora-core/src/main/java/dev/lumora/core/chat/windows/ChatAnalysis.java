package dev.lumora.core.chat.windows;

import dev.lumora.core.extras.ChatModule;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the parts of a message the tab filters work with: the prefix at the start and the rank before the sender. */
public final class ChatAnalysis {
    private static final Pattern BRACKET = Pattern.compile("^([\\[(<{][^\\])>}]{1,24}[\\])>}])");
    private static final Pattern WORD = Pattern.compile("^([^\\s|»:>•┃]{1,20})\\s*(?:\\||»|>>|::|•|┃|:)");
    private static final Pattern SYMBOLS = Pattern.compile("^[^\\p{L}\\p{N}]*$");

    private ChatAnalysis() {
    }

    /** The first bracket ("[Baublase]") or the first word in front of a separator ("Baublase |"); never the sender. */
    public static String prefix(String clean, String sender) {
        String t = clean == null ? "" : clean.strip();
        Matcher b = BRACKET.matcher(t);
        if (b.find()) {
            String p = b.group(1);
            return sender != null && p.substring(1, p.length() - 1).strip().equalsIgnoreCase(sender) ? null : p;
        }
        Matcher w = WORD.matcher(t);
        if (w.find()) {
            String p = w.group(1);
            if (sender != null && p.equalsIgnoreCase(sender) || SYMBOLS.matcher(p).matches()) {
                return null;
            }
            return p;
        }
        return null;
    }

    /** The word right in front of the sender's name ("Spieler" in "[▯] Spieler Steve: hi"), null if there is none. */
    public static String rank(String clean, String sender) {
        if (clean == null || sender == null) {
            return null;
        }
        int at = ChatModule.indexOfWord(clean, sender);
        if (at <= 0) {
            return null;
        }
        String[] tokens = clean.substring(0, at).strip().split("\\s+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String tok = tokens[i].strip();
            if (!tok.isEmpty() && !SYMBOLS.matcher(tok).matches()) {
                return tok.length() > 24 ? null : tok;
            }
        }
        return null;
    }

    /** The prefix as a tab name: without its brackets. */
    public static String bare(String prefix) {
        String p = prefix == null ? "" : prefix.strip();
        if (p.length() > 2 && "[(<{".indexOf(p.charAt(0)) >= 0 && "])>}".indexOf(p.charAt(p.length() - 1)) >= 0) {
            p = p.substring(1, p.length() - 1).strip();
        }
        return p;
    }
}
