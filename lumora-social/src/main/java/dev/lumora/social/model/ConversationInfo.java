package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;

/**
 * Per-user state of a conversation: unread count, own read position, the highest position any other participant has
 * read (for the "read" check marks), the newest message and whether notifications are muted.
 */
public final class ConversationInfo {
    public final String conv;
    public final int unread;
    public final long lastRead;
    public final long othersRead;
    public final ChatMessage last;
    public final boolean muted;

    public ConversationInfo(String conv, int unread, long lastRead, long othersRead, ChatMessage last, boolean muted) {
        this.conv = conv;
        this.unread = unread;
        this.lastRead = lastRead;
        this.othersRead = othersRead;
        this.last = last;
        this.muted = muted;
    }

    public ConversationInfo read(long upTo) {
        return new ConversationInfo(conv, 0, Math.max(lastRead, upTo), othersRead, last, muted);
    }

    public static ConversationInfo read(JsonObject o) {
        String conv = Js.str(o, "conv", null);
        if (conv == null) {
            return null;
        }
        JsonObject last = Js.obj(o, "last");
        return new ConversationInfo(conv, (int) Math.max(0, Js.num(o, "unread", 0)), Js.num(o, "lastRead", 0),
                Js.num(o, "othersRead", 0), last == null ? null : ChatMessage.read(last), Js.bool(o, "muted", false));
    }

    public JsonObject json() {
        return Js.obj().put("conv", conv).put("unread", unread).put("lastRead", lastRead).put("othersRead", othersRead)
                .put("last", last == null ? null : last.json()).put("muted", muted).json();
    }
}
