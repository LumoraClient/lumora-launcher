package dev.lumora.social.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.lumora.social.Conv;
import dev.lumora.social.Js;
import dev.lumora.social.Rules;
import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.ConversationInfo;
import dev.lumora.social.model.Friend;
import dev.lumora.social.model.FriendRequest;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Member;
import dev.lumora.social.model.Party;
import dev.lumora.social.model.PartyInvite;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Profile;
import dev.lumora.social.model.Role;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The server logic. One lock ({@code this}) guards all state; sending only queues frames, so holding the lock while
 * broadcasting never blocks on a slow client. Login: {@code hello} (claimed profile) → {@code challenge} (random server
 * id) → client joins at Mojang → {@code login} → {@link Verifier} asks Mojang → {@code welcome}. Every request is checked
 * against the verified profile; replies carry the request id ({@code rid}) as {@code ok} or {@code error}.
 */
final class Hub implements WsServer.Handler {
    interface Verifier {
        /** The verified profile, or null when the player did not join this server id. */
        Profile verify(String name, String serverId) throws IOException;
    }

    /** A request that is refused with a message for the user (German, like the clients). */
    static final class Refusal extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Refusal(String message) {
            super(message, null, false, false);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SESSIONS_PER_USER = 6;
    /** Messages per user in 10 seconds (raised by tests that page through history). */
    static int messageBurst = 12;

    private final Store store;
    private final State state;
    private final Verifier verifier;
    private final boolean dev;
    private final Map<String, List<Client>> online = new HashMap<>();
    /** uuid -> recent nonces of sent messages -> message id (repeated sends after a reconnect are answered, not stored again) */
    private final Map<String, LinkedHashMap<String, Long>> nonces = new HashMap<>();
    private final Map<String, ArrayDeque<Long>> rate = new HashMap<>();
    private final ExecutorService verifyPool = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "social-timer");
        t.setDaemon(true);
        return t;
    });
    private boolean dirty;

    Hub(Store store, State state, Verifier verifier, boolean dev) {
        this.store = store;
        this.state = state;
        this.verifier = verifier;
        this.dev = dev;
        timer.scheduleWithFixedDelay(this::saveIfDirty, 1, 1, TimeUnit.SECONDS);
        timer.scheduleWithFixedDelay(this::pruneRates, 10, 10, TimeUnit.MINUTES);
        timer.scheduleWithFixedDelay(this::pruneInvites, 30, 30, TimeUnit.SECONDS);
    }

    /** Rate-limit windows are at most 10 minutes long: older entries (and empty keys) are dropped. */
    synchronized void pruneRates() {
        long now = System.currentTimeMillis();
        rate.values().removeIf(q -> {
            while (!q.isEmpty() && now - q.peekFirst() > 600_000) {
                q.removeFirst();
            }
            return q.isEmpty();
        });
    }

    synchronized void saveIfDirty() {
        if (!dirty) {
            return;
        }
        try {
            store.save(state);
            dirty = false;
        } catch (IOException e) {
            Log.warn("Saving state failed: " + e);
        }
    }

    void shutdown() {
        timer.shutdownNow();
        verifyPool.shutdownNow();
        saveIfDirty();
    }

    synchronized int onlineUsers() {
        return online.size();
    }

    @Override
    public WsServer.Session open(WsServer.Conn conn) {
        Client c = new Client(conn);
        timer.schedule(() -> {
            if (c.profile == null) {
                c.conn.close(1008, "login timeout");
            }
        }, 30, TimeUnit.SECONDS);
        return c;
    }

    /** One connection. */
    final class Client implements WsServer.Session {
        final WsServer.Conn conn;
        Profile claimed;
        String serverId;
        volatile Profile profile;
        String kind = "game";
        Presence presence = Presence.OFFLINE;
        boolean verifying;

        Client(WsServer.Conn conn) {
            this.conn = conn;
        }

        @Override
        public void onText(String text) {
            JsonObject o;
            try {
                o = Js.object(text);
            } catch (RuntimeException e) {
                conn.close(1007, "invalid json");
                return;
            }
            String op = Js.str(o, "op", "");
            long rid = Js.num(o, "rid", -1);
            if (profile == null) {
                handshake(op, o);
                return;
            }
            try {
                synchronized (Hub.this) {
                    handle(this, op, o, rid);
                }
            } catch (Refusal r) {
                conn.send(Js.op("error").put("rid", rid).put("message", r.getMessage()).toString());
            } catch (RuntimeException e) {
                Log.warn("Request " + op + " of " + profile.name + " failed: " + e);
                conn.send(Js.op("error").put("rid", rid).put("message", "Interner Serverfehler").toString());
            }
        }

        private void handshake(String op, JsonObject o) {
            if (op.equals("hello") && claimed == null) {
                if (Js.num(o, "v", -1) != Rules.PROTOCOL) {
                    reject("Lumora und der Social-Server passen nicht zusammen (Protokoll " + Js.num(o, "v", -1) + " statt "
                            + Rules.PROTOCOL + "). Bitte aktualisieren.");
                    return;
                }
                claimed = Profile.read(Js.obj(o, "profile"));
                if (claimed == null) {
                    reject("Ungültiges Profil");
                    return;
                }
                kind = "launcher".equals(Js.str(o, "client", "")) ? "launcher" : "game";
                byte[] b = new byte[20];
                RANDOM.nextBytes(b);
                serverId = hex(b);
                conn.send(Js.op("challenge").put("serverId", serverId).put("dev", dev).toString());
            } else if (op.equals("login") && serverId != null && !verifying) {
                verifying = true;
                verifyPool.execute(this::verify);
            } else {
                conn.close(1008, "login first");
            }
        }

        private void verify() {
            Profile p;
            try {
                p = dev ? claimed : verifier.verify(claimed.name, serverId);
            } catch (IOException e) {
                Log.warn("Mojang check for " + claimed.name + " failed: " + e.getMessage());
                reject("Mojang-Sitzungsdienst nicht erreichbar, später erneut versuchen.");
                return;
            }
            if (p == null || !p.uuid.equals(claimed.uuid)) {
                reject("Anmeldung nicht bestätigt: Mojang kennt keine aktive Sitzung für " + claimed.name + ".");
                return;
            }
            synchronized (Hub.this) {
                List<Client> sessions = online.computeIfAbsent(p.uuid, k -> new ArrayList<>());
                if (sessions.size() >= SESSIONS_PER_USER) {
                    reject("Zu viele gleichzeitige Verbindungen dieses Kontos.");
                    return;
                }
                login(this, p);
            }
        }

        void reject(String reason) {
            conn.send(Js.op("rejected").put("reason", reason).toString());
            conn.close(1008, "rejected");
        }

        @Override
        public void onClose() {
            if (profile != null) {
                synchronized (Hub.this) {
                    logout(this);
                }
            }
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) {
            s.append(String.format(Locale.ROOT, "%02x", x));
        }
        return s.toString();
    }

    private static String randomId(int len) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < len; i++) {
            s.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return s.toString();
    }

    // ------------------------------------------------------------------ sessions and presence

    private void login(Client c, Profile p) {
        long now = System.currentTimeMillis();
        State.User u = state.users.get(p.uuid);
        boolean renamed = false;
        if (u == null) {
            u = new State.User(p.uuid, p.name);
            u.firstSeen = now;
            state.users.put(p.uuid, u);
        } else if (!u.name.equals(p.name)) {
            u.name = p.name;
            renamed = true;
        }
        u.lastSeen = now;
        dirty = true;
        Presence before = presence(p.uuid);
        c.presence = new Presence(c.kind.equals("launcher") ? Presence.State.LAUNCHER : Presence.State.MENU, "", "");
        c.profile = p;
        online.computeIfAbsent(p.uuid, k -> new ArrayList<>()).add(c);
        Log.info("Login " + p.name + " (" + c.kind + ", " + c.conn.address() + ")");
        c.conn.send(welcome(p.uuid).toString());
        if (renamed) {
            for (String f : friendsOf(p.uuid)) {
                sendFriends(f);
            }
            for (State.GroupRec g : groupsOf(p.uuid)) {
                sendGroup(g);
            }
            for (String other : requestPeers(p.uuid)) {
                sendRequests(other);
            }
        }
        presenceChanged(p.uuid, before);
    }

    private void logout(Client c) {
        String uuid = c.profile.uuid;
        Presence before = presence(uuid);
        List<Client> list = online.get(uuid);
        if (list != null) {
            list.remove(c);
            if (list.isEmpty()) {
                online.remove(uuid);
                leaveParty(uuid);
                dropInvites(uuid);
            }
        }
        State.User u = state.users.get(uuid);
        if (u != null) {
            u.lastSeen = System.currentTimeMillis();
            dirty = true;
        }
        presenceChanged(uuid, before);
    }

    /** Highest presence of all sessions of a player. */
    private Presence presence(String uuid) {
        Presence best = Presence.OFFLINE;
        for (Client c : online.getOrDefault(uuid, List.of())) {
            if (c.presence.state.rank() > best.state.rank()) {
                best = c.presence;
            }
        }
        return best;
    }

    private void presenceChanged(String uuid, Presence before) {
        Presence now = presence(uuid);
        if (now.equals(before)) {
            return;
        }
        String msg = Js.op("presence").put("uuid", uuid).put("presence", now.json()).toString();
        for (String f : friendsOf(uuid)) {
            sendTo(f, msg);
        }
    }

    private void sendTo(String uuid, String msg) {
        for (Client c : online.getOrDefault(uuid, List.of())) {
            c.conn.send(msg);
        }
    }

    // ------------------------------------------------------------------ views

    private Profile profile(String uuid) {
        State.User u = state.users.get(uuid);
        return new Profile(uuid, u == null ? "?" : u.name);
    }

    private Set<String> friendsOf(String uuid) {
        return state.friends.getOrDefault(uuid, Map.of()).keySet();
    }

    private boolean friends(String a, String b) {
        return state.friends.getOrDefault(a, Map.of()).containsKey(b);
    }

    private Set<String> requestPeers(String uuid) {
        Set<String> s = new HashSet<>(state.requests.getOrDefault(uuid, Map.of()).keySet());
        for (Map.Entry<String, Map<String, Long>> e : state.requests.entrySet()) {
            if (e.getValue().containsKey(uuid)) {
                s.add(e.getKey());
            }
        }
        return s;
    }

    private List<State.GroupRec> groupsOf(String uuid) {
        List<State.GroupRec> l = new ArrayList<>();
        for (State.GroupRec g : state.groups.values()) {
            if (g.members.containsKey(uuid)) {
                l.add(g);
            }
        }
        return l;
    }

    private JsonArray friendsJson(String uuid) {
        List<Friend> l = new ArrayList<>();
        for (Map.Entry<String, Long> e : state.friends.getOrDefault(uuid, Map.of()).entrySet()) {
            l.add(new Friend(profile(e.getKey()), presence(e.getKey()), e.getValue()));
        }
        return Js.array(l, Friend::json);
    }

    private JsonArray requestsJson(String uuid) {
        List<FriendRequest> l = new ArrayList<>();
        for (Map.Entry<String, Long> e : state.requests.getOrDefault(uuid, Map.of()).entrySet()) {
            l.add(new FriendRequest(profile(e.getKey()), false, e.getValue()));
        }
        for (Map.Entry<String, Map<String, Long>> e : state.requests.entrySet()) {
            Long at = e.getValue().get(uuid);
            if (at != null) {
                l.add(new FriendRequest(profile(e.getKey()), true, at));
            }
        }
        l.sort(Comparator.comparingLong(r -> -r.at));
        return Js.array(l, FriendRequest::json);
    }

    private Group group(State.GroupRec g) {
        List<Member> members = new ArrayList<>();
        for (Map.Entry<String, State.MemberRec> m : g.members.entrySet()) {
            members.add(new Member(profile(m.getKey()), m.getValue().role, m.getValue().joined));
        }
        return new Group(g.id, g.name, g.description, g.icon, g.color, g.owner, new ArrayList<>(g.roles.values()), members, g.created);
    }

    private List<String> participants(String conv) {
        if (Conv.isDm(conv)) {
            return List.of(conv.substring(3, 35), conv.substring(36));
        }
        State.GroupRec g = state.groups.get(Conv.groupId(conv));
        return g == null ? List.of() : new ArrayList<>(g.members.keySet());
    }

    private boolean canRead(String uuid, String conv) {
        if (Conv.isDm(conv)) {
            return Conv.involves(conv, uuid);
        }
        State.GroupRec g = state.groups.get(Conv.groupId(conv));
        return g != null && g.members.containsKey(uuid);
    }

    private ConversationInfo info(String uuid, String conv) {
        ArrayDeque<ChatMessage> q = state.messages.get(conv);
        long lastRead = state.reads.getOrDefault(uuid, Map.of()).getOrDefault(conv, 0L);
        int unread = 0;
        ChatMessage last = null;
        if (q != null) {
            for (ChatMessage m : q) {
                if (m.id > lastRead && !m.sender.equals(uuid)) {
                    unread++;
                }
            }
            last = q.peekLast();
        }
        long others = 0;
        for (String p : participants(conv)) {
            if (!p.equals(uuid)) {
                others = Math.max(others, state.reads.getOrDefault(p, Map.of()).getOrDefault(conv, 0L));
            }
        }
        boolean muted = state.muted.getOrDefault(uuid, Set.of()).contains(conv);
        return new ConversationInfo(conv, unread, lastRead, others, last, muted);
    }

    private JsonObject welcome(String uuid) {
        JsonArray groups = new JsonArray();
        List<ConversationInfo> convs = new ArrayList<>();
        for (State.GroupRec g : groupsOf(uuid)) {
            groups.add(group(g).json());
            convs.add(info(uuid, Conv.group(g.id)));
        }
        for (String conv : state.messages.keySet()) {
            if (Conv.isDm(conv) && Conv.involves(conv, uuid)) {
                convs.add(info(uuid, conv));
            }
        }
        for (String f : friendsOf(uuid)) {
            String conv = Conv.dm(uuid, f);
            if (!state.messages.containsKey(conv)) {
                convs.add(info(uuid, conv));
            }
        }
        return Js.op("welcome").put("self", profile(uuid).json()).put("friends", friendsJson(uuid))
                .put("requests", requestsJson(uuid)).put("groups", groups)
                .put("conversations", Js.array(convs, ConversationInfo::json))
                .put("party", partyJson(uuid)).put("partyInvites", invitesJson(uuid)).json();
    }

    private void sendFriends(String uuid) {
        sendTo(uuid, Js.op("friends").put("friends", friendsJson(uuid)).toString());
    }

    private void sendRequests(String uuid) {
        sendTo(uuid, Js.op("requests").put("requests", requestsJson(uuid)).toString());
    }

    private void sendGroup(State.GroupRec g) {
        String msg = Js.op("group").put("group", group(g).json()).toString();
        for (String m : g.members.keySet()) {
            sendTo(m, msg);
        }
    }

    private void sendConv(String uuid, String conv) {
        sendTo(uuid, Js.op("conv").put("info", info(uuid, conv).json()).toString());
    }

    private void notice(String uuid, String text) {
        sendTo(uuid, Js.op("notice").put("text", text).toString());
    }

    // ------------------------------------------------------------------ requests

    private void limit(String key, int max, long windowMs, String message) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> q = rate.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > windowMs) {
            q.removeFirst();
        }
        if (q.size() >= max) {
            throw new Refusal(message);
        }
        q.addLast(now);
    }

    private static String uuidArg(JsonObject o) {
        String u = Rules.uuid(Js.str(o, "uuid", null));
        if (u == null) {
            throw new Refusal("Ungültiger Spieler");
        }
        return u;
    }

    private State.GroupRec groupArg(JsonObject o, String actor) {
        State.GroupRec g = state.groups.get(Js.str(o, "id", ""));
        if (g == null || !g.members.containsKey(actor)) {
            throw new Refusal("Gruppe nicht gefunden");
        }
        return g;
    }

    private boolean manages(State.GroupRec g, String uuid) {
        State.MemberRec m = g.members.get(uuid);
        if (m == null) {
            return false;
        }
        Role r = g.roles.get(m.role);
        return uuid.equals(g.owner) || (r != null && r.manage);
    }

    private void handle(Client c, String op, JsonObject o, long rid) {
        String me = c.profile.uuid;
        JsonObject reply = null;
        switch (op) {
            case "presence" -> {
                Presence before = presence(me);
                Presence p = Presence.read(Js.obj(o, "presence"));
                if (p.state == Presence.State.OFFLINE) {
                    p = new Presence(c.kind.equals("launcher") ? Presence.State.LAUNCHER : Presence.State.MENU, "", "");
                }
                c.presence = p;
                presenceChanged(me, before);
                return;
            }
            case "friend.request" -> {
                limit(me + ":friend", 10, 60_000, "Zu viele Freundschaftsanfragen, bitte kurz warten.");
                String name = Js.str(o, "name", "");
                State.User target = null;
                for (State.User u : state.users.values()) {
                    if (u.name.equalsIgnoreCase(name) && (target == null || u.lastSeen > target.lastSeen)) {
                        target = u;
                    }
                }
                if (target == null) {
                    throw new Refusal("„" + name + "“ war noch nie mit diesem Lumora-Server verbunden.");
                }
                if (target.uuid.equals(me)) {
                    throw new Refusal("Du kannst dich nicht selbst hinzufügen.");
                }
                if (friends(me, target.uuid)) {
                    throw new Refusal(target.name + " ist schon dein Freund.");
                }
                if (state.requests.getOrDefault(target.uuid, Map.of()).containsKey(me)) {
                    befriend(me, target.uuid); // they asked first: accept
                } else {
                    if (friendsOf(me).size() >= Rules.FRIENDS_MAX) {
                        throw new Refusal("Freundesliste voll (" + Rules.FRIENDS_MAX + ").");
                    }
                    Map<String, Long> out = state.requests.computeIfAbsent(me, k -> new HashMap<>());
                    if (out.size() >= 50 && !out.containsKey(target.uuid)) {
                        throw new Refusal("Zu viele offene Anfragen.");
                    }
                    out.put(target.uuid, System.currentTimeMillis());
                    dirty = true;
                    sendRequests(me);
                    sendRequests(target.uuid);
                }
            }
            case "friend.accept" -> {
                String other = uuidArg(o);
                if (!state.requests.getOrDefault(other, Map.of()).containsKey(me)) {
                    throw new Refusal("Keine Anfrage von diesem Spieler.");
                }
                befriend(me, other);
            }
            case "friend.decline" -> {
                String other = uuidArg(o);
                boolean a = state.requests.getOrDefault(other, new HashMap<>()).remove(me) != null;
                boolean b = state.requests.getOrDefault(me, new HashMap<>()).remove(other) != null;
                if (a || b) {
                    dirty = true;
                    sendRequests(me);
                    sendRequests(other);
                }
            }
            case "party.invite" -> {
                limit(me + ":party", 20, 60_000, "Zu viele Party-Einladungen, bitte kurz warten.");
                String other = uuidArg(o);
                if (other.equals(me)) {
                    throw new Refusal("Du kannst dich nicht selbst einladen.");
                }
                State.User u = state.users.get(other);
                if (u == null) {
                    throw new Refusal("Dieser Spieler nutzt Lumora nicht.");
                }
                if (!online.containsKey(other)) {
                    throw new Refusal(u.name + " ist gerade nicht mit Lumora online.");
                }
                PartyRec p = parties.get(partyOf.getOrDefault(me, ""));
                if (p == null) {
                    p = new PartyRec(randomId(12), me);
                    parties.put(p.id, p);
                    partyOf.put(me, p.id);
                } else if (!p.leader.equals(me)) {
                    throw new Refusal("Nur der Party-Anführer kann einladen.");
                }
                if (p.members.contains(other)) {
                    throw new Refusal(u.name + " ist schon in deiner Party.");
                }
                if (!p.invited.containsKey(other) && p.members.size() + p.invited.size() >= Rules.PARTY_MAX) {
                    throw new Refusal("Die Party ist voll (" + Rules.PARTY_MAX + ").");
                }
                p.invited.put(other, System.currentTimeMillis());
                sendParty(p);
                sendInvites(other);
            }
            case "party.accept" -> {
                PartyRec p = parties.get(Js.str(o, "id", ""));
                if (p == null || !p.invited.containsKey(me)) {
                    throw new Refusal("Die Einladung gilt nicht mehr.");
                }
                if (p.members.size() >= Rules.PARTY_MAX) {
                    throw new Refusal("Die Party ist voll (" + Rules.PARTY_MAX + ").");
                }
                leaveParty(me);
                p.invited.remove(me);
                p.members.add(me);
                partyOf.put(me, p.id);
                sendParty(p);
                sendInvites(me);
            }
            case "party.decline" -> {
                PartyRec p = parties.get(Js.str(o, "id", ""));
                if (p != null && p.invited.remove(me) != null) {
                    sendInvites(me);
                    if (!dissolveIfAlone(p)) {
                        sendParty(p);
                    }
                }
            }
            case "party.leave" -> {
                if (!leaveParty(me)) {
                    throw new Refusal("Du bist in keiner Party.");
                }
            }
            case "party.kick" -> {
                String other = uuidArg(o);
                PartyRec p = parties.get(partyOf.getOrDefault(me, ""));
                if (p == null || !p.leader.equals(me)) {
                    throw new Refusal("Nur der Party-Anführer kann Spieler entfernen.");
                }
                if (other.equals(me)) {
                    throw new Refusal("Verlasse die Party stattdessen.");
                }
                if (p.invited.remove(other) != null) {
                    sendInvites(other);
                    if (!dissolveIfAlone(p)) {
                        sendParty(p);
                    }
                } else if (p.members.contains(other)) {
                    leaveParty(other);
                } else {
                    throw new Refusal("Dieser Spieler ist nicht in deiner Party.");
                }
            }
            case "player.card" -> {
                limit(me + ":card", 60, 60_000, "Zu viele Abfragen, bitte kurz warten.");
                String other = uuidArg(o);
                State.User u = state.users.get(other);
                Js.Obj card = Js.obj().put("uuid", other).put("lumora", u != null);
                if (u != null) {
                    card.put("name", u.name);
                }
                Long since = state.friends.getOrDefault(me, Map.of()).get(other);
                card.put("friend", since != null).put("friendSince", since == null ? 0 : since);
                card.put("request", state.requests.getOrDefault(other, Map.of()).containsKey(me) ? "in"
                        : state.requests.getOrDefault(me, Map.of()).containsKey(other) ? "out" : "");
                if (since != null) {
                    card.put("presence", presence(other).json()); // online state only for friends
                }
                JsonArray shared = new JsonArray();
                for (State.GroupRec g : groupsOf(me)) {
                    if (g.members.containsKey(other)) {
                        shared.add(g.name);
                    }
                }
                card.put("groups", shared);
                PartyRec mine = parties.get(partyOf.getOrDefault(me, ""));
                PartyRec theirs = parties.get(partyOf.getOrDefault(other, ""));
                card.put("sameParty", mine != null && mine == theirs);
                card.put("invited", mine != null && mine.invited.containsKey(other));
                if (theirs != null && theirs.invited.containsKey(me)) {
                    card.put("partyInvite", theirs.id);
                }
                reply = Js.op("ok").put("rid", rid).put("card", card.json()).json();
            }
            case "friend.remove" -> {
                String other = uuidArg(o);
                Map<String, Long> mine = state.friends.get(me);
                if (mine == null || mine.remove(other) == null) {
                    throw new Refusal("Nicht in deiner Freundesliste.");
                }
                Map<String, Long> theirs = state.friends.get(other);
                if (theirs != null) {
                    theirs.remove(me);
                }
                dirty = true;
                sendFriends(me);
                sendFriends(other);
            }
            case "group.create" -> {
                limit(me + ":group", 5, 600_000, "Zu viele neue Gruppen, bitte später erneut.");
                if (groupsOf(me).size() >= Rules.GROUPS_PER_USER_MAX) {
                    throw new Refusal("Du bist schon in " + Rules.GROUPS_PER_USER_MAX + " Gruppen.");
                }
                String name = Rules.clean(Js.str(o, "name", ""), Rules.GROUP_NAME_MAX, false);
                if (name.isEmpty()) {
                    throw new Refusal("Die Gruppe braucht einen Namen.");
                }
                State.GroupRec g = new State.GroupRec(randomId(12));
                g.name = name;
                g.description = Rules.clean(Js.str(o, "description", ""), Rules.GROUP_DESCRIPTION_MAX, true);
                g.icon = Rules.icon(Js.str(o, "icon", "circle"));
                g.color = Rules.color(Js.num(o, "color", 0));
                g.owner = me;
                g.created = System.currentTimeMillis();
                g.roles.put(Role.OWNER, new Role(Role.OWNER, "Owner", "crown", 0xF5B342, true));
                g.roles.put(Role.MEMBER, new Role(Role.MEMBER, "Mitglied", "circle", 0x98A1AD, false));
                g.members.put(me, new State.MemberRec(Role.OWNER, g.created));
                state.groups.put(g.id, g);
                markJoined(me, g);
                dirty = true;
                sendGroup(g);
                sendConv(me, Conv.group(g.id));
                reply = Js.op("ok").put("rid", rid).put("id", g.id).json();
            }
            case "group.update" -> {
                State.GroupRec g = groupArg(o, me);
                if (!manages(g, me)) {
                    throw new Refusal("Nur Owner und verwaltende Rollen können die Gruppe ändern.");
                }
                String name = Rules.clean(Js.str(o, "name", g.name), Rules.GROUP_NAME_MAX, false);
                if (!name.isEmpty()) {
                    g.name = name;
                }
                g.description = Rules.clean(Js.str(o, "description", g.description), Rules.GROUP_DESCRIPTION_MAX, true);
                g.icon = Rules.icon(Js.str(o, "icon", g.icon));
                g.color = Rules.color(Js.num(o, "color", g.color));
                dirty = true;
                sendGroup(g);
            }
            case "group.delete" -> {
                State.GroupRec g = groupArg(o, me);
                if (!g.owner.equals(me)) {
                    throw new Refusal("Nur der Owner kann die Gruppe löschen.");
                }
                deleteGroup(g);
            }
            case "group.add" -> {
                State.GroupRec g = groupArg(o, me);
                String target = uuidArg(o);
                if (!manages(g, me)) {
                    throw new Refusal("Nur Owner und verwaltende Rollen können Mitglieder hinzufügen.");
                }
                if (!friends(me, target)) {
                    throw new Refusal("Du kannst nur Freunde hinzufügen.");
                }
                if (g.members.containsKey(target)) {
                    throw new Refusal(profile(target).name + " ist schon in der Gruppe.");
                }
                if (g.members.size() >= Rules.GROUP_MEMBERS_MAX) {
                    throw new Refusal("Die Gruppe ist voll (" + Rules.GROUP_MEMBERS_MAX + ").");
                }
                if (groupsOf(target).size() >= Rules.GROUPS_PER_USER_MAX) {
                    throw new Refusal(profile(target).name + " ist schon in zu vielen Gruppen.");
                }
                g.members.put(target, new State.MemberRec(Role.MEMBER, System.currentTimeMillis()));
                markJoined(target, g);
                dirty = true;
                sendGroup(g);
                sendConv(target, Conv.group(g.id));
                notice(target, c.profile.name + " hat dich zur Gruppe „" + g.name + "“ hinzugefügt.");
            }
            case "group.kick" -> {
                State.GroupRec g = groupArg(o, me);
                String target = uuidArg(o);
                if (!manages(g, me)) {
                    throw new Refusal("Nur Owner und verwaltende Rollen können Mitglieder entfernen.");
                }
                if (!g.members.containsKey(target) || target.equals(me)) {
                    throw new Refusal("Nicht möglich.");
                }
                if (target.equals(g.owner) || (manages(g, target) && !me.equals(g.owner))) {
                    throw new Refusal("Verwaltende Mitglieder kann nur der Owner entfernen.");
                }
                removeMember(g, target);
                notice(target, "Du wurdest aus der Gruppe „" + g.name + "“ entfernt.");
            }
            case "group.leave" -> {
                State.GroupRec g = groupArg(o, me);
                removeMember(g, me);
            }
            case "group.role.save" -> {
                State.GroupRec g = groupArg(o, me);
                if (!g.owner.equals(me)) {
                    throw new Refusal("Nur der Owner kann Rollen bearbeiten.");
                }
                JsonObject r = Js.obj(o, "role");
                String name = Rules.clean(Js.str(r, "name", ""), Rules.ROLE_NAME_MAX, false);
                if (name.isEmpty()) {
                    throw new Refusal("Die Rolle braucht einen Namen.");
                }
                String id = Js.str(r, "id", null);
                if (id == null) {
                    if (g.roles.size() >= Rules.ROLES_MAX) {
                        throw new Refusal("Höchstens " + Rules.ROLES_MAX + " Rollen.");
                    }
                    id = randomId(8);
                } else if (!g.roles.containsKey(id)) {
                    throw new Refusal("Rolle nicht gefunden");
                }
                boolean manage = id.equals(Role.OWNER) || Js.bool(r, "manage", false);
                g.roles.put(id, new Role(id, name, Rules.icon(Js.str(r, "icon", "circle")), Rules.color(Js.num(r, "color", 0)), manage));
                dirty = true;
                sendGroup(g);
                reply = Js.op("ok").put("rid", rid).put("id", id).json();
            }
            case "group.role.delete" -> {
                State.GroupRec g = groupArg(o, me);
                String id = Js.str(o, "roleId", "");
                if (!g.owner.equals(me)) {
                    throw new Refusal("Nur der Owner kann Rollen löschen.");
                }
                if (id.equals(Role.OWNER) || id.equals(Role.MEMBER) || g.roles.remove(id) == null) {
                    throw new Refusal("Diese Rolle kann nicht gelöscht werden.");
                }
                for (State.MemberRec m : g.members.values()) {
                    if (m.role.equals(id)) {
                        m.role = Role.MEMBER;
                    }
                }
                dirty = true;
                sendGroup(g);
            }
            case "group.member.role" -> {
                State.GroupRec g = groupArg(o, me);
                String target = uuidArg(o);
                String roleId = Js.str(o, "roleId", "");
                State.MemberRec m = g.members.get(target);
                Role role = g.roles.get(roleId);
                if (m == null || role == null) {
                    throw new Refusal("Mitglied oder Rolle nicht gefunden");
                }
                if (roleId.equals(Role.OWNER)) {
                    if (!g.owner.equals(me) || target.equals(me)) {
                        throw new Refusal("Nur der Owner kann die Gruppe übergeben.");
                    }
                    g.members.get(me).role = Role.MEMBER;
                    m.role = Role.OWNER;
                    g.owner = target;
                } else {
                    if (target.equals(g.owner)) {
                        throw new Refusal("Der Owner behält seine Rolle (Gruppe zuerst übergeben).");
                    }
                    boolean owner = g.owner.equals(me);
                    if (!owner && (!manages(g, me) || role.manage || manages(g, target))) {
                        throw new Refusal("Diese Rolle kann nur der Owner vergeben.");
                    }
                    m.role = roleId;
                }
                dirty = true;
                sendGroup(g);
            }
            case "conv.mute" -> {
                String conv = Js.str(o, "conv", "");
                if (!canRead(me, conv)) {
                    throw new Refusal("Unterhaltung nicht gefunden");
                }
                Set<String> set = state.muted.computeIfAbsent(me, k -> new HashSet<>());
                if (Js.bool(o, "muted", false)) {
                    set.add(conv);
                } else {
                    set.remove(conv);
                }
                dirty = true;
                sendConv(me, conv);
            }
            case "msg.send" -> {
                reply = sendMessage(c, o, rid);
            }
            case "msg.history" -> {
                String conv = Js.str(o, "conv", "");
                if (!canRead(me, conv)) {
                    throw new Refusal("Unterhaltung nicht gefunden");
                }
                long before = Js.num(o, "before", 0);
                int max = (int) Math.max(1, Math.min(100, Js.num(o, "limit", Rules.HISTORY_PAGE)));
                ArrayDeque<ChatMessage> q = state.messages.getOrDefault(conv, new ArrayDeque<>());
                ArrayDeque<ChatMessage> page = new ArrayDeque<>();
                Iterator<ChatMessage> it = q.descendingIterator();
                boolean more = false;
                while (it.hasNext()) {
                    ChatMessage m = it.next();
                    if (before > 0 && m.id >= before) {
                        continue;
                    }
                    if (page.size() >= max) {
                        more = true;
                        break;
                    }
                    page.addFirst(m);
                }
                c.conn.send(Js.op("history").put("conv", conv).put("messages", Js.array(new ArrayList<>(page), ChatMessage::json))
                        .put("complete", !more).toString());
                return;
            }
            case "msg.read" -> {
                String conv = Js.str(o, "conv", "");
                if (!canRead(me, conv)) {
                    return;
                }
                ArrayDeque<ChatMessage> q = state.messages.get(conv);
                long newest = q == null || q.isEmpty() ? 0 : q.peekLast().id;
                long id = Math.min(Js.num(o, "id", 0), newest);
                Map<String, Long> mine = state.reads.computeIfAbsent(me, k -> new HashMap<>());
                if (id <= mine.getOrDefault(conv, 0L)) {
                    sendConv(me, conv);
                    return;
                }
                mine.put(conv, id);
                dirty = true;
                for (String p : participants(conv)) {
                    sendConv(p, conv);
                }
                return;
            }
            default -> throw new Refusal("Unbekannte Anfrage: " + op);
        }
        if (rid >= 0) {
            c.conn.send(reply != null ? reply.toString() : Js.op("ok").put("rid", rid).toString());
        }
    }

    private JsonObject sendMessage(Client c, JsonObject o, long rid) {
        String me = c.profile.uuid;
        String conv = Js.str(o, "conv", "");
        String nonce = Js.str(o, "nonce", "");
        if (nonce.length() > 40) {
            nonce = nonce.substring(0, 40);
        }
        if (Conv.isDm(conv)) {
            String other = Conv.other(conv, me);
            if (other == null) {
                throw new Refusal("Unterhaltung nicht gefunden");
            }
            if (!friends(me, other)) {
                throw new Refusal("Nachrichten nur an Freunde.");
            }
        } else if (!canRead(me, conv)) {
            throw new Refusal("Unterhaltung nicht gefunden");
        }
        LinkedHashMap<String, Long> seen = nonces.computeIfAbsent(me, k -> new LinkedHashMap<>());
        Long dup = nonce.isEmpty() ? null : seen.get(nonce);
        if (dup != null) {
            // repeated after a reconnect: confirm the stored message again
            for (ChatMessage m : state.messages.getOrDefault(conv, new ArrayDeque<>())) {
                if (m.id == dup) {
                    c.conn.send(Js.op("msg").put("message", m.json()).put("nonce", nonce).toString());
                }
            }
            return null;
        }
        limit(me + ":msg", messageBurst, 10_000, "Langsamer bitte: höchstens " + messageBurst + " Nachrichten in 10 Sekunden.");
        String text = Rules.clean(Js.str(o, "text", ""), Rules.MESSAGE_MAX, true);
        if (text.isEmpty()) {
            throw new Refusal("Leere Nachricht");
        }
        ChatMessage m = new ChatMessage(state.nextMessageId++, conv, me, text, System.currentTimeMillis());
        ArrayDeque<ChatMessage> q = state.messages.computeIfAbsent(conv, k -> new ArrayDeque<>());
        q.addLast(m);
        while (q.size() > Rules.HISTORY_KEPT) {
            q.removeFirst();
        }
        try {
            store.append(m);
        } catch (IOException e) {
            Log.warn("Writing message failed: " + e);
        }
        if (!nonce.isEmpty()) {
            seen.put(nonce, m.id);
            if (seen.size() > 200) {
                Iterator<String> it = seen.keySet().iterator();
                it.next();
                it.remove();
            }
        }
        state.reads.computeIfAbsent(me, k -> new HashMap<>()).put(conv, m.id);
        dirty = true;
        String withNonce = Js.op("msg").put("message", m.json()).put("nonce", nonce.isEmpty() ? null : nonce).toString();
        String plain = Js.op("msg").put("message", m.json()).toString();
        for (String p : participants(conv)) {
            sendTo(p, p.equals(me) ? withNonce : plain);
            sendConv(p, conv);
        }
        return null;
    }

    // ------------------------------------------------------------------ parties (in memory only)

    /** A party; ends when its last member leaves or the leader is left alone without open invitations. */
    private static final class PartyRec {
        final String id;
        String leader;
        final LinkedHashSet<String> members = new LinkedHashSet<>();
        final LinkedHashMap<String, Long> invited = new LinkedHashMap<>();

        PartyRec(String id, String leader) {
            this.id = id;
            this.leader = leader;
            members.add(leader);
        }
    }

    /** How long a party invitation stays open (shortened by tests). */
    static long partyInviteMs = 300_000;
    private final Map<String, PartyRec> parties = new HashMap<>();
    private final Map<String, String> partyOf = new HashMap<>();

    private JsonObject partyJson(String uuid) {
        PartyRec p = parties.get(partyOf.getOrDefault(uuid, ""));
        return p == null ? null : party(p).json();
    }

    private Party party(PartyRec p) {
        List<Profile> members = new ArrayList<>();
        for (String m : p.members) {
            members.add(profile(m));
        }
        List<Profile> invited = new ArrayList<>();
        for (String m : p.invited.keySet()) {
            invited.add(profile(m));
        }
        return new Party(p.id, p.leader, members, invited);
    }

    private JsonArray invitesJson(String uuid) {
        List<PartyInvite> l = new ArrayList<>();
        for (PartyRec p : parties.values()) {
            Long at = p.invited.get(uuid);
            if (at != null) {
                l.add(new PartyInvite(p.id, profile(p.leader), p.members.size(), at));
            }
        }
        l.sort(Comparator.comparingLong(i -> -i.at));
        return Js.array(l, PartyInvite::json);
    }

    private void sendParty(PartyRec p) {
        String msg = Js.op("party").put("party", party(p).json()).toString();
        for (String m : p.members) {
            sendTo(m, msg);
        }
    }

    private void sendInvites(String uuid) {
        sendTo(uuid, Js.op("party.invites").put("invites", invitesJson(uuid)).toString());
    }

    /** Takes {@code uuid} out of its party; the next member leads if the leader left. False if not in a party. */
    private boolean leaveParty(String uuid) {
        PartyRec p = parties.get(partyOf.getOrDefault(uuid, ""));
        if (p == null) {
            return false;
        }
        partyOf.remove(uuid);
        p.members.remove(uuid);
        sendTo(uuid, Js.op("party").toString());
        if (p.members.isEmpty()) {
            dissolve(p);
            return true;
        }
        if (p.leader.equals(uuid)) {
            p.leader = p.members.iterator().next();
        }
        if (!dissolveIfAlone(p)) {
            sendParty(p);
        }
        return true;
    }

    /** A leader alone without open invitations: the party ends. */
    private boolean dissolveIfAlone(PartyRec p) {
        if (p.members.size() > 1 || !p.invited.isEmpty()) {
            return false;
        }
        for (String m : p.members) {
            partyOf.remove(m);
            sendTo(m, Js.op("party").toString());
        }
        dissolve(p);
        return true;
    }

    private void dissolve(PartyRec p) {
        parties.remove(p.id);
        List<String> invited = new ArrayList<>(p.invited.keySet());
        p.invited.clear();
        for (String i : invited) {
            sendInvites(i);
        }
    }

    /** A player went offline: invitations to them end. */
    private void dropInvites(String uuid) {
        for (PartyRec p : new ArrayList<>(parties.values())) {
            if (p.invited.remove(uuid) != null && !dissolveIfAlone(p)) {
                sendParty(p);
            }
        }
    }

    synchronized void pruneInvites() {
        long now = System.currentTimeMillis();
        for (PartyRec p : new ArrayList<>(parties.values())) {
            List<String> expired = new ArrayList<>();
            p.invited.forEach((u, at) -> {
                if (now - at > partyInviteMs) {
                    expired.add(u);
                }
            });
            if (expired.isEmpty()) {
                continue;
            }
            expired.forEach(p.invited::remove);
            expired.forEach(this::sendInvites);
            if (!dissolveIfAlone(p)) {
                sendParty(p);
            }
        }
    }

    private void befriend(String a, String b) {
        long now = System.currentTimeMillis();
        Map<String, Long> ra = state.requests.get(a);
        if (ra != null) {
            ra.remove(b);
        }
        Map<String, Long> rb = state.requests.get(b);
        if (rb != null) {
            rb.remove(a);
        }
        state.friends.computeIfAbsent(a, k -> new HashMap<>()).put(b, now);
        state.friends.computeIfAbsent(b, k -> new HashMap<>()).put(a, now);
        dirty = true;
        for (String x : List.of(a, b)) {
            sendFriends(x);
            sendRequests(x);
            sendConv(x, Conv.dm(a, b));
        }
    }

    /** New members only count messages after joining as unread. */
    private void markJoined(String uuid, State.GroupRec g) {
        ArrayDeque<ChatMessage> q = state.messages.get(Conv.group(g.id));
        long last = q == null || q.isEmpty() ? 0 : q.peekLast().id;
        state.reads.computeIfAbsent(uuid, k -> new HashMap<>()).put(Conv.group(g.id), last);
    }

    private void removeMember(State.GroupRec g, String uuid) {
        g.members.remove(uuid);
        String conv = Conv.group(g.id);
        Map<String, Long> r = state.reads.get(uuid);
        if (r != null) {
            r.remove(conv);
        }
        Set<String> mu = state.muted.get(uuid);
        if (mu != null) {
            mu.remove(conv);
        }
        sendTo(uuid, Js.op("group.removed").put("id", g.id).toString());
        if (g.members.isEmpty()) {
            deleteGroup(g);
            return;
        }
        if (uuid.equals(g.owner)) {
            // hand over to the longest-serving managing member, else the longest-serving member
            String next = null;
            for (Map.Entry<String, State.MemberRec> m : g.members.entrySet()) {
                Role role = g.roles.get(m.getValue().role);
                if (role != null && role.manage) {
                    next = m.getKey();
                    break;
                }
            }
            if (next == null) {
                next = g.members.keySet().iterator().next();
            }
            g.owner = next;
            g.members.get(next).role = Role.OWNER;
            notice(next, "Du bist jetzt Owner der Gruppe „" + g.name + "“.");
        }
        dirty = true;
        sendGroup(g);
    }

    private void deleteGroup(State.GroupRec g) {
        String conv = Conv.group(g.id);
        state.groups.remove(g.id);
        state.messages.remove(conv);
        for (Map<String, Long> r : state.reads.values()) {
            r.remove(conv);
        }
        for (Set<String> m : state.muted.values()) {
            m.remove(conv);
        }
        try {
            store.deleteConversation(conv);
        } catch (IOException e) {
            Log.warn("Deleting messages of group failed: " + e);
        }
        dirty = true;
        String msg = Js.op("group.removed").put("id", g.id).toString();
        for (String m : g.members.keySet()) {
            sendTo(m, msg);
        }
    }
}
