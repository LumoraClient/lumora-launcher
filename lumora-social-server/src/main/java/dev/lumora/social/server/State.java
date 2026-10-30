package dev.lumora.social.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;
import dev.lumora.social.model.Role;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Everything the server keeps except message texts (those live in {@code messages/*.jsonl}, see {@link Store}).
 * Players are stored by UUID; names come from the last verified login. Not thread-safe: {@link Hub} holds its lock.
 */
final class State {
    static final class User {
        final String uuid;
        String name;
        long firstSeen;
        long lastSeen;

        User(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    static final class GroupRec {
        final String id;
        String name;
        String description;
        String icon;
        int color;
        String owner;
        long created;
        final LinkedHashMap<String, Role> roles = new LinkedHashMap<>();
        /** uuid -> member */
        final LinkedHashMap<String, MemberRec> members = new LinkedHashMap<>();

        GroupRec(String id) {
            this.id = id;
        }
    }

    static final class MemberRec {
        String role;
        final long joined;

        MemberRec(String role, long joined) {
            this.role = role;
            this.joined = joined;
        }
    }

    final Map<String, User> users = new HashMap<>();
    /** uuid -> (friend uuid -> since); always stored in both directions */
    final Map<String, Map<String, Long>> friends = new HashMap<>();
    /** requester uuid -> (target uuid -> at) */
    final Map<String, Map<String, Long>> requests = new HashMap<>();
    final Map<String, GroupRec> groups = new LinkedHashMap<>();
    /** uuid -> (conv -> last read message id) */
    final Map<String, Map<String, Long>> reads = new HashMap<>();
    /** uuid -> muted conversations */
    final Map<String, Set<String>> muted = new HashMap<>();
    long nextMessageId = 1;
    /** conv -> newest messages (loaded from the message files, at most Rules.HISTORY_KEPT) */
    final Map<String, ArrayDeque<dev.lumora.social.model.ChatMessage>> messages = new HashMap<>();

    JsonObject json() {
        JsonObject o = new JsonObject();
        o.addProperty("format", 1);
        o.addProperty("nextMessageId", nextMessageId);
        JsonArray us = new JsonArray();
        for (User u : users.values()) {
            us.add(Js.obj().put("uuid", u.uuid).put("name", u.name).put("firstSeen", u.firstSeen).put("lastSeen", u.lastSeen).json());
        }
        o.add("users", us);
        o.add("friends", pairs(friends));
        o.add("requests", pairs(requests));
        JsonArray gs = new JsonArray();
        for (GroupRec g : groups.values()) {
            JsonArray roles = new JsonArray();
            for (Role r : g.roles.values()) {
                roles.add(r.json());
            }
            JsonArray members = new JsonArray();
            for (Map.Entry<String, MemberRec> m : g.members.entrySet()) {
                members.add(Js.obj().put("uuid", m.getKey()).put("role", m.getValue().role).put("joined", m.getValue().joined).json());
            }
            gs.add(Js.obj().put("id", g.id).put("name", g.name).put("description", g.description).put("icon", g.icon)
                    .put("color", g.color).put("owner", g.owner).put("created", g.created).put("roles", roles)
                    .put("members", members).json());
        }
        o.add("groups", gs);
        o.add("reads", pairs(reads));
        JsonObject mu = new JsonObject();
        for (Map.Entry<String, Set<String>> e : muted.entrySet()) {
            JsonArray a = new JsonArray();
            for (String c : e.getValue()) {
                a.add(Js.prim(c));
            }
            mu.add(e.getKey(), a);
        }
        o.add("muted", mu);
        return o;
    }

    private static JsonObject pairs(Map<String, Map<String, Long>> m) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Map<String, Long>> e : m.entrySet()) {
            JsonObject inner = new JsonObject();
            for (Map.Entry<String, Long> x : e.getValue().entrySet()) {
                inner.addProperty(x.getKey(), x.getValue());
            }
            o.add(e.getKey(), inner);
        }
        return o;
    }

    private static void readPairs(JsonObject src, Map<String, Map<String, Long>> into) {
        if (src == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : src.entrySet()) {
            if (!e.getValue().isJsonObject()) {
                continue;
            }
            Map<String, Long> inner = new HashMap<>();
            for (Map.Entry<String, JsonElement> x : e.getValue().getAsJsonObject().entrySet()) {
                if (x.getValue().isJsonPrimitive()) {
                    try {
                        inner.put(x.getKey(), x.getValue().getAsLong());
                    } catch (NumberFormatException ignored) {
                        // skip broken entry
                    }
                }
            }
            into.put(e.getKey(), inner);
        }
    }

    static State read(JsonObject o) {
        State s = new State();
        s.nextMessageId = Math.max(1, Js.num(o, "nextMessageId", 1));
        for (JsonObject u : Js.list(o, "users", x -> x)) {
            String uuid = Rules.uuid(Js.str(u, "uuid", null));
            String name = Js.str(u, "name", null);
            if (uuid != null && Rules.validName(name)) {
                User user = new User(uuid, name);
                user.firstSeen = Js.num(u, "firstSeen", 0);
                user.lastSeen = Js.num(u, "lastSeen", 0);
                s.users.put(uuid, user);
            }
        }
        readPairs(Js.obj(o, "friends"), s.friends);
        readPairs(Js.obj(o, "requests"), s.requests);
        readPairs(Js.obj(o, "reads"), s.reads);
        for (JsonObject g : Js.list(o, "groups", x -> x)) {
            String id = Js.str(g, "id", null);
            String owner = Rules.uuid(Js.str(g, "owner", null));
            if (id == null || owner == null) {
                continue;
            }
            GroupRec rec = new GroupRec(id);
            rec.name = Rules.clean(Js.str(g, "name", "?"), Rules.GROUP_NAME_MAX, false);
            rec.description = Rules.clean(Js.str(g, "description", ""), Rules.GROUP_DESCRIPTION_MAX, true);
            rec.icon = Rules.icon(Js.str(g, "icon", "circle"));
            rec.color = Rules.color(Js.num(g, "color", 0));
            rec.owner = owner;
            rec.created = Js.num(g, "created", 0);
            for (Role r : Js.list(g, "roles", Role::read)) {
                rec.roles.put(r.id, r);
            }
            for (JsonObject m : Js.list(g, "members", x -> x)) {
                String uuid = Rules.uuid(Js.str(m, "uuid", null));
                if (uuid != null) {
                    rec.members.put(uuid, new MemberRec(Js.str(m, "role", Role.MEMBER), Js.num(m, "joined", 0)));
                }
            }
            if (!rec.members.isEmpty()) {
                s.groups.put(id, rec);
            }
        }
        JsonObject mu = Js.obj(o, "muted");
        if (mu != null) {
            for (Map.Entry<String, JsonElement> e : mu.entrySet()) {
                Set<String> set = new HashSet<>();
                if (e.getValue().isJsonArray()) {
                    for (JsonElement c : e.getValue().getAsJsonArray()) {
                        if (c.isJsonPrimitive()) {
                            set.add(c.getAsString());
                        }
                    }
                }
                s.muted.put(e.getKey(), set);
            }
        }
        return s;
    }
}
