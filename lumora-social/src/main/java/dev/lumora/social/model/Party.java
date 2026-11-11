package dev.lumora.social.model;

import com.google.gson.JsonObject;
import dev.lumora.social.Js;

import java.util.List;

/** A party: players who play together for a while. Not stored by the server, it ends when its last member leaves. */
public final class Party {
    public final String id;
    /** UUID of the leader, the only one who invites and removes. */
    public final String leader;
    /** Members in the order they joined, the leader included. */
    public final List<Profile> members;
    /** Players invited who have not answered yet. */
    public final List<Profile> invited;

    public Party(String id, String leader, List<Profile> members, List<Profile> invited) {
        this.id = id;
        this.leader = leader;
        this.members = List.copyOf(members);
        this.invited = List.copyOf(invited);
    }

    public static Party read(JsonObject o) {
        if (o == null) {
            return null;
        }
        String id = Js.str(o, "id", "");
        String leader = Js.str(o, "leader", "");
        if (id.isEmpty() || leader.isEmpty()) {
            return null;
        }
        return new Party(id, leader, Js.list(o, "members", Profile::read), Js.list(o, "invited", Profile::read));
    }

    public JsonObject json() {
        return Js.obj().put("id", id).put("leader", leader).put("members", Js.array(members, Profile::json))
                .put("invited", Js.array(invited, Profile::json)).json();
    }

    public boolean member(String uuid) {
        return members.stream().anyMatch(p -> p.uuid.equals(uuid));
    }

    public boolean isInvited(String uuid) {
        return invited.stream().anyMatch(p -> p.uuid.equals(uuid));
    }
}
