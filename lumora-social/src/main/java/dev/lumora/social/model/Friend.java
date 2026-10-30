package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;

/** An accepted friend with the current presence. */
public final class Friend {
    public final Profile profile;
    public final Presence presence;
    public final long since;

    public Friend(Profile profile, Presence presence, long since) {
        this.profile = profile;
        this.presence = presence;
        this.since = since;
    }

    public Friend withPresence(Presence p) {
        return new Friend(profile, p, since);
    }

    public static Friend read(JsonObject o) {
        Profile p = Profile.read(Js.obj(o, "profile"));
        return p == null ? null : new Friend(p, Presence.read(Js.obj(o, "presence")), Js.num(o, "since", 0));
    }

    public JsonObject json() {
        return Js.obj().put("profile", profile.json()).put("presence", presence.json()).put("since", since).json();
    }
}
