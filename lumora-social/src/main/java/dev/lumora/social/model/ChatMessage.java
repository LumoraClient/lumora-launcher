package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;

/** One chat message. Ids grow monotonically per server, so they order messages and mark read positions. */
public final class ChatMessage {
    public final long id;
    public final String conv;
    public final String sender;
    public final String text;
    public final long at;

    public ChatMessage(long id, String conv, String sender, String text, long at) {
        this.id = id;
        this.conv = conv;
        this.sender = sender;
        this.text = text;
        this.at = at;
    }

    public static ChatMessage read(JsonObject o) {
        long id = Js.num(o, "id", -1);
        String conv = Js.str(o, "conv", null);
        String sender = Rules.uuid(Js.str(o, "sender", null));
        if (id < 0 || conv == null || sender == null) {
            return null;
        }
        return new ChatMessage(id, conv, sender, Rules.clean(Js.str(o, "text", ""), Rules.MESSAGE_MAX, true), Js.num(o, "at", 0));
    }

    public JsonObject json() {
        return Js.obj().put("id", id).put("conv", conv).put("sender", sender).put("text", text).put("at", at).json();
    }
}
