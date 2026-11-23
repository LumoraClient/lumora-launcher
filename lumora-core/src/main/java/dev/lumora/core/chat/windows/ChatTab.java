package dev.lumora.core.chat.windows;

/** A chat tab: what it collects and what happens with matching messages. */
public final class ChatTab {
    /** The tab every chat starts with: all messages except those other tabs take out of the main chat. */
    public static final String MAIN = "main";
    public static final int DEFAULT_HIGHLIGHT = 0xFFE8B84A;

    public final String id;
    /** Empty for the main tab: its name comes from the language file. */
    public String name;
    public ChatRule rule;
    /** Off: matching messages leave the main chat and only show here. */
    public boolean keepInMain = true;
    public boolean sound;
    public boolean highlight;
    public int highlightColor = DEFAULT_HIGHLIGHT;
    /** Messages that came in while the tab was not shown. */
    public transient int unread;

    public ChatTab(String id, String name, ChatRule rule) {
        this.id = id;
        this.name = name == null ? "" : name;
        this.rule = rule == null ? ChatRule.ALL : rule;
    }

    public boolean main() {
        return MAIN.equals(id);
    }

    ChatTab copy() {
        ChatTab t = new ChatTab(id, name, rule);
        t.keepInMain = keepInMain;
        t.sound = sound;
        t.highlight = highlight;
        t.highlightColor = highlightColor;
        return t;
    }
}
