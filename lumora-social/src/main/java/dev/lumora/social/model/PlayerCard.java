package dev.lumora.social.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lumora.social.Js;

import java.util.ArrayList;
import java.util.List;

/**
 * What the server tells about one player (the profile card of the player menu): whether the player uses Lumora and
 * how the two of you are connected. Online state only for friends, like everywhere else.
 */
public final class PlayerCard {
    public enum Request { NONE, INCOMING, OUTGOING }

    public final String uuid;
    /** The name the server knows, or null if the player never used Lumora with this server. */
    public final String name;
    public final boolean lumora;
    public final boolean friend;
    public final long friendSince;
    public final Request request;
    /** Only for friends (else null). */
    public final Presence presence;
    /** Names of the groups you share. */
    public final List<String> groups;
    public final boolean sameParty;
    /** The player invited you into their party (invitation id), else null. */
    public final String partyInvite;
    /** You invited the player into your party and they have not answered. */
    public final boolean invited;

    public PlayerCard(String uuid, String name, boolean lumora, boolean friend, long friendSince, Request request, Presence presence,
                      List<String> groups, boolean sameParty, String partyInvite, boolean invited) {
        this.uuid = uuid;
        this.name = name;
        this.lumora = lumora;
        this.friend = friend;
        this.friendSince = friendSince;
        this.request = request;
        this.presence = presence;
        this.groups = List.copyOf(groups);
        this.sameParty = sameParty;
        this.partyInvite = partyInvite;
        this.invited = invited;
    }

    public static PlayerCard read(JsonObject o) {
        if (o == null) {
            return null;
        }
        String uuid = Js.str(o, "uuid", "");
        Request r = switch (Js.str(o, "request", "")) {
            case "in" -> Request.INCOMING;
            case "out" -> Request.OUTGOING;
            default -> Request.NONE;
        };
        JsonObject p = Js.obj(o, "presence");
        List<String> groups = new ArrayList<>();
        if (o.has("groups") && o.get("groups").isJsonArray()) {
            for (var e : o.getAsJsonArray("groups")) {
                if (e.isJsonPrimitive()) {
                    groups.add(e.getAsString());
                }
            }
        }
        return new PlayerCard(uuid, Js.str(o, "name", null), Js.bool(o, "lumora", false), Js.bool(o, "friend", false),
                Js.num(o, "friendSince", 0), r, p == null ? null : Presence.read(p), groups, Js.bool(o, "sameParty", false),
                Js.str(o, "partyInvite", null), Js.bool(o, "invited", false));
    }

    public JsonObject json() {
        Js.Obj o = Js.obj().put("uuid", uuid).put("lumora", lumora).put("friend", friend).put("friendSince", friendSince)
                .put("request", request == Request.INCOMING ? "in" : request == Request.OUTGOING ? "out" : "")
                .put("sameParty", sameParty).put("invited", invited);
        if (name != null) {
            o.put("name", name);
        }
        if (presence != null) {
            o.put("presence", presence.json());
        }
        if (partyInvite != null) {
            o.put("partyInvite", partyInvite);
        }
        JsonArray g = new JsonArray();
        groups.forEach(s -> g.add(Js.prim(s)));
        return o.put("groups", g).json();
    }
}
