package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;

/** An invitation into the party {@code id} of {@code from}; {@code size} members at the time it was sent. */
public final class PartyInvite {
    public final String id;
    public final Profile from;
    public final int size;
    public final long at;

    public PartyInvite(String id, Profile from, int size, long at) {
        this.id = id;
        this.from = from;
        this.size = size;
        this.at = at;
    }

    public static PartyInvite read(JsonObject o) {
        Profile p = Profile.read(Js.obj(o, "from"));
        String id = Js.str(o, "id", "");
        return p == null || id.isEmpty() ? null : new PartyInvite(id, p, (int) Js.num(o, "size", 1), Js.num(o, "at", 0));
    }

    public JsonObject json() {
        return Js.obj().put("id", id).put("from", from.json()).put("size", size).put("at", at).json();
    }
}
