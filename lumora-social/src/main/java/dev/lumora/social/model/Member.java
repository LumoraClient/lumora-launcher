package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;

/** A group member with role id and the time of joining. */
public final class Member {
    public final Profile profile;
    public final String role;
    public final long joined;

    public Member(Profile profile, String role, long joined) {
        this.profile = profile;
        this.role = role;
        this.joined = joined;
    }

    public Member withRole(String r) {
        return new Member(profile, r, joined);
    }

    public Member withProfile(Profile p) {
        return new Member(p, role, joined);
    }

    public static Member read(JsonObject o) {
        Profile p = Profile.read(Js.obj(o, "profile"));
        return p == null ? null : new Member(p, Js.str(o, "role", Role.MEMBER), Js.num(o, "joined", 0));
    }

    public JsonObject json() {
        return Js.obj().put("profile", profile.json()).put("role", role).put("joined", joined).json();
    }
}
