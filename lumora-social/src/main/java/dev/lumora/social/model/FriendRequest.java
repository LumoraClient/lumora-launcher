package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;

/** A pending friend request; {@code incoming} = the other player asked. */
public final class FriendRequest {
    public final Profile profile;
    public final boolean incoming;
    public final long at;

    public FriendRequest(Profile profile, boolean incoming, long at) {
        this.profile = profile;
        this.incoming = incoming;
        this.at = at;
    }

    public static FriendRequest read(JsonObject o) {
        Profile p = Profile.read(Js.obj(o, "profile"));
        return p == null ? null : new FriendRequest(p, Js.bool(o, "incoming", false), Js.num(o, "at", 0));
    }

    public JsonObject json() {
        return Js.obj().put("profile", profile.json()).put("incoming", incoming).put("at", at).json();
    }
}
