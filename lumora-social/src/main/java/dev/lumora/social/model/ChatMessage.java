package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;

/**
 * One chat message. Ids grow monotonically per server, so they order messages and mark read positions. An edited
 * message keeps its id and time and carries {@link #editedAt}; a deleted one keeps its place with an empty text.
 */
public final class ChatMessage {
    public final long id;
    public final String conv;
    public final String sender;
    public final String text;
    public final long at;
    /** When the text was last changed (epoch ms), 0 = never. */
    public final long editedAt;
    public final boolean deleted;

    public ChatMessage(long id, String conv, String sender, String text, long at) {
        this(id, conv, sender, text, at, 0, false);
    }

    public ChatMessage(long id, String conv, String sender, String text, long at, long editedAt, boolean deleted) {
        this.id = id;
        this.conv = conv;
        this.sender = sender;
        this.text = deleted ? "" : text;
        this.at = at;
        this.editedAt = editedAt;
        this.deleted = deleted;
    }

    /** The same message with a new text. */
    public ChatMessage edited(String newText, long when) {
        return new ChatMessage(id, conv, sender, newText, at, when, false);
    }

    /** The same message, deleted (the text is gone, the place in the conversation stays). */
    public ChatMessage removed(long when) {
        return new ChatMessage(id, conv, sender, "", at, when, true);
    }

    public static ChatMessage read(JsonObject o) {
        long id = Js.num(o, "id", -1);
        String conv = Js.str(o, "conv", null);
        String sender = Rules.uuid(Js.str(o, "sender", null));
        if (id < 0 || conv == null || sender == null) {
            return null;
        }
        return new ChatMessage(id, conv, sender, Rules.clean(Js.str(o, "text", ""), Rules.MESSAGE_MAX, true), Js.num(o, "at", 0),
                Math.max(0, Js.num(o, "edited", 0)), Js.bool(o, "deleted", false));
    }

    public JsonObject json() {
        Js.Obj o = Js.obj().put("id", id).put("conv", conv).put("sender", sender).put("text", text).put("at", at);
        if (editedAt > 0) {
            o.put("edited", editedAt);
        }
        if (deleted) {
            o.put("deleted", true);
        }
        return o.json();
    }
}
