package dev.lumora.social;

import com.google.gson.JsonObject;
import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.ConversationInfo;
import dev.lumora.social.model.Friend;
import dev.lumora.social.model.FriendRequest;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Party;
import dev.lumora.social.model.PartyInvite;
import dev.lumora.social.model.PlayerCard;
import dev.lumora.social.model.Member;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Profile;
import dev.lumora.social.model.Role;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Client of a Lumora Social server (friends, groups, chat), used by the launcher and the companion mod.
 * <p>
 * Connection: WebSocket ({@code java.net.http}, no extra library) → {@code hello} with the profile → the server hands out
 * a random server id → {@link Identity#join} proves the account to Mojang (official session join) → {@code login} → the
 * server checks with Mojang ({@code hasJoined}) and answers {@code welcome} with friends, requests, groups and
 * conversations. No token or password ever reaches the Lumora server. Lost connections are re-established with
 * backoff (2 s … 60 s); a rejected login stops until {@link #reconnect()}.
 * <p>
 * All state getters return copies and may be called from any thread; listeners run on the client's worker thread.
 */
public final class SocialClient implements AutoCloseable {
    public enum Status { STOPPED, CONNECTING, ONLINE, OFFLINE, REJECTED }

    /** Who is connecting and how the account is proven. */
    public interface Identity {
        Profile profile();

        /** Official Mojang session join for {@code serverId} (see {@link MojangSession}). */
        void join(String serverId) throws IOException;
    }

    /** A message typed here and not yet confirmed by the server. */
    public static final class Pending {
        public final String nonce;
        public final String conv;
        public final String text;
        public final long at;
        public final String error;

        Pending(String nonce, String conv, String text, long at, String error) {
            this.nonce = nonce;
            this.conv = conv;
            this.text = text;
            this.at = at;
            this.error = error;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long WATCHDOG_MS = 75_000;

    private final URI uri;
    private final Identity identity;
    private final String clientKind;
    private final String clientVersion;
    private final List<Consumer<SocialEvent>> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lumora-social");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // state, guarded by this
    private Status status = Status.STOPPED;
    private String statusText = "";
    private Profile self;
    private final Map<String, Friend> friends = new LinkedHashMap<>();
    private List<FriendRequest> requests = List.of();
    private Party party;
    private List<PartyInvite> partyInvites = List.of();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, ConversationInfo> convs = new HashMap<>();
    private final Map<String, List<ChatMessage>> messages = new HashMap<>();
    private final Set<String> historyComplete = new HashSet<>();
    private final Set<String> historyLoading = new HashSet<>();
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private Presence presence;
    private String focused;

    // connection
    private volatile WebSocket ws;
    private CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);
    private final AtomicLong rids = new AtomicLong();
    private final Map<Long, CompletableFuture<JsonObject>> calls = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private volatile boolean rejected;
    private volatile long lastSeen;
    private Thread loop;

    /**
     * @param clientKind "launcher" or "game" (shown nowhere, for server statistics and debugging)
     */
    public SocialClient(URI uri, Identity identity, String clientKind, String clientVersion, Presence initial) {
        this.uri = uri;
        this.identity = identity;
        this.clientKind = clientKind;
        this.clientVersion = clientVersion;
        this.presence = initial;
    }

    public URI uri() {
        return uri;
    }

    public void addListener(Consumer<SocialEvent> l) {
        listeners.add(l);
    }

    public void removeListener(Consumer<SocialEvent> l) {
        listeners.remove(l);
    }

    // ------------------------------------------------------------------ lifecycle

    public synchronized void start() {
        if (closed || (loop != null && loop.isAlive())) {
            return;
        }
        rejected = false;
        loop = new Thread(this::run, "lumora-social-connection");
        loop.setDaemon(true);
        loop.start();
    }

    /** Reconnect now (after a rejected login, e.g. once the user signed in again, or to skip the backoff). */
    public synchronized void reconnect() {
        if (closed) {
            return;
        }
        if (loop != null && loop.isAlive()) {
            rejected = false;
            WebSocket w = ws;
            if (w != null) {
                w.abort();
            }
            loop.interrupt();
        } else {
            start();
        }
    }

    @Override
    public void close() {
        Thread t;
        synchronized (this) {
            closed = true;
            t = loop;
        }
        WebSocket w = ws;
        if (w != null) {
            try {
                w.sendClose(WebSocket.NORMAL_CLOSURE, "bye").orTimeout(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // closing anyway
            }
            w.abort();
        }
        if (t != null) {
            t.interrupt();
        }
        setStatus(Status.STOPPED, "");
        worker.shutdown();
    }

    private void run() {
        long backoff = 2000;
        while (!closed) {
            setStatus(Status.CONNECTING, "");
            boolean wasOnline = false;
            String reason;
            CompletableFuture<String> done = new CompletableFuture<>();
            try {
                WebSocket w = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                        .buildAsync(uri, new Receiver(done)).get(20, TimeUnit.SECONDS);
                ws = w;
                synchronized (this) {
                    sendChain = CompletableFuture.completedFuture(w);
                }
                lastSeen = System.currentTimeMillis();
                sendRaw(Js.op("hello").put("v", Rules.PROTOCOL).put("profile", identity.profile().json())
                        .put("client", clientKind).put("version", clientVersion).toString());
                reason = null;
                while (reason == null && !closed) {
                    try {
                        reason = done.get(5, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        if (System.currentTimeMillis() - lastSeen > WATCHDOG_MS) {
                            w.abort();
                            reason = "Server antwortet nicht";
                        }
                    }
                    wasOnline |= status() == Status.ONLINE;
                }
            } catch (InterruptedException e) {
                reason = "neu verbinden";
            } catch (Exception e) {
                reason = describe(e);
            }
            WebSocket w = ws;
            ws = null;
            if (w != null) {
                w.abort();
            }
            failCalls("Verbindung getrennt");
            if (closed) {
                break;
            }
            if (rejected) {
                return; // status REJECTED stays until reconnect()
            }
            setStatus(Status.OFFLINE, reason == null ? "" : reason);
            if (wasOnline) {
                backoff = 2000;
            }
            try {
                Thread.sleep(backoff + RANDOM.nextInt(1000));
            } catch (InterruptedException e) {
                backoff = 1000;
                continue;
            }
            backoff = Math.min(backoff * 2, 60_000);
        }
    }

    private static String describe(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getMessage();
        if (c instanceof java.net.ConnectException || c instanceof java.nio.channels.UnresolvedAddressException) {
            return "Server nicht erreichbar";
        }
        if (c instanceof javax.net.ssl.SSLException) {
            return "TLS-Fehler: " + (m == null ? c.getClass().getSimpleName() : m);
        }
        if (c instanceof java.net.http.WebSocketHandshakeException) {
            return "Kein Lumora-Social-Server unter dieser Adresse";
        }
        return m == null || m.isBlank() ? c.getClass().getSimpleName() : m;
    }

    private final class Receiver implements WebSocket.Listener {
        private final CompletableFuture<String> done;
        private final StringBuilder text = new StringBuilder();

        Receiver(CompletableFuture<String> done) {
            this.done = done;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            lastSeen = System.currentTimeMillis();
            text.append(data);
            if (text.length() > 4_000_000) {
                done.complete("Nachricht zu groß");
                webSocket.abort();
                return null;
            }
            if (last) {
                String msg = text.toString();
                text.setLength(0);
                worker.execute(() -> handle(msg));
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            lastSeen = System.currentTimeMillis();
            webSocket.request(1);
            return null; // the JDK answers with a pong
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            lastSeen = System.currentTimeMillis();
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            done.complete(reason == null || reason.isBlank() ? "Verbindung geschlossen" : reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            done.complete(describe(error));
        }
    }

    // ------------------------------------------------------------------ incoming

    void handle(String raw) {
        JsonObject o;
        try {
            o = Js.object(raw);
        } catch (RuntimeException e) {
            return;
        }
        String op = Js.str(o, "op", "");
        switch (op) {
            case "challenge" -> onChallenge(o);
            case "welcome" -> onWelcome(o);
            case "rejected" -> {
                rejected = true;
                setStatus(Status.REJECTED, Js.str(o, "reason", "Anmeldung abgelehnt"));
                WebSocket w = ws;
                if (w != null) {
                    w.sendClose(WebSocket.NORMAL_CLOSURE, "rejected");
                }
            }
            case "friends" -> {
                List<Friend> list = Js.list(o, "friends", Friend::read);
                synchronized (this) {
                    friends.clear();
                    for (Friend f : list) {
                        friends.put(f.profile.uuid, f);
                    }
                }
                fire(SocialEvent.of(SocialEvent.Kind.FRIENDS));
            }
            case "requests" -> {
                List<FriendRequest> list = Js.list(o, "requests", FriendRequest::read);
                synchronized (this) {
                    requests = List.copyOf(list);
                }
                fire(SocialEvent.of(SocialEvent.Kind.REQUESTS));
            }
            case "presence" -> {
                String uuid = Rules.uuid(Js.str(o, "uuid", null));
                Presence p = Presence.read(Js.obj(o, "presence"));
                synchronized (this) {
                    Friend f = uuid == null ? null : friends.get(uuid);
                    if (f == null) {
                        return;
                    }
                    friends.put(uuid, f.withPresence(p));
                }
                fire(SocialEvent.of(SocialEvent.Kind.FRIENDS));
            }
            case "group" -> {
                Group g = Group.read(Js.obj(o, "group"));
                if (g != null) {
                    synchronized (this) {
                        groups.put(g.id, g);
                    }
                    fire(SocialEvent.of(SocialEvent.Kind.GROUPS));
                }
            }
            case "group.removed" -> {
                String id = Js.str(o, "id", "");
                synchronized (this) {
                    groups.remove(id);
                    String conv = Conv.group(id);
                    convs.remove(conv);
                    messages.remove(conv);
                    historyComplete.remove(conv);
                    pending.values().removeIf(p -> p.conv.equals(conv));
                }
                fire(SocialEvent.of(SocialEvent.Kind.GROUPS));
            }
            case "conv" -> {
                ConversationInfo c = ConversationInfo.read(Js.obj(o, "info"));
                if (c != null) {
                    synchronized (this) {
                        convs.put(c.conv, c);
                    }
                    fire(new SocialEvent(SocialEvent.Kind.CONVERSATION, c.conv, null, null));
                }
            }
            case "party" -> {
                Party p = Party.read(Js.obj(o, "party"));
                synchronized (this) {
                    party = p;
                }
                fire(SocialEvent.of(SocialEvent.Kind.PARTY));
            }
            case "party.invites" -> {
                List<PartyInvite> list = Js.list(o, "invites", PartyInvite::read);
                synchronized (this) {
                    partyInvites = List.copyOf(list);
                }
                fire(SocialEvent.of(SocialEvent.Kind.PARTY));
            }
            case "msg" -> onMessage(o);
            case "history" -> onHistory(o);
            case "notice" -> fire(new SocialEvent(SocialEvent.Kind.NOTICE, null, null, Rules.clean(Js.str(o, "text", ""), 200, false)));
            case "ok", "error" -> {
                CompletableFuture<JsonObject> call = calls.remove(Js.num(o, "rid", -1));
                if (call == null) {
                    if (op.equals("error")) {
                        fire(new SocialEvent(SocialEvent.Kind.ERROR, null, null, Rules.clean(Js.str(o, "message", "Fehler"), 200, false)));
                    }
                } else if (op.equals("ok")) {
                    call.complete(o);
                } else {
                    call.completeExceptionally(new SocialException(Rules.clean(Js.str(o, "message", "Fehler"), 200, false)));
                }
            }
            default -> {
                // unknown op from a newer server: ignore
            }
        }
    }

    private void onChallenge(JsonObject o) {
        String serverId = Js.str(o, "serverId", "");
        if (!serverId.matches("[0-9a-f]{16,64}")) {
            return;
        }
        if (!Js.bool(o, "dev", false)) {
            try {
                identity.join(serverId);
            } catch (IOException e) {
                rejected = true;
                setStatus(Status.REJECTED, e.getMessage());
                WebSocket w = ws;
                if (w != null) {
                    w.sendClose(WebSocket.NORMAL_CLOSURE, "no session");
                }
                return;
            }
        }
        sendRaw(Js.op("login").toString());
    }

    private void onWelcome(JsonObject o) {
        Profile me = Profile.read(Js.obj(o, "self"));
        List<Friend> fl = Js.list(o, "friends", Friend::read);
        List<FriendRequest> rl = Js.list(o, "requests", FriendRequest::read);
        List<Group> gl = Js.list(o, "groups", Group::read);
        List<ConversationInfo> cl = Js.list(o, "conversations", ConversationInfo::read);
        Party pa = Party.read(Js.obj(o, "party"));
        List<PartyInvite> pi = Js.list(o, "partyInvites", PartyInvite::read);
        List<Pending> resend;
        Presence p;
        String focus;
        synchronized (this) {
            self = me;
            friends.clear();
            for (Friend f : fl) {
                friends.put(f.profile.uuid, f);
            }
            requests = List.copyOf(rl);
            party = pa;
            partyInvites = List.copyOf(pi);
            groups.clear();
            for (Group g : gl) {
                groups.put(g.id, g);
            }
            convs.clear();
            for (ConversationInfo c : cl) {
                convs.put(c.conv, c);
            }
            // history may have changed while offline: reloaded when a conversation is shown
            messages.clear();
            historyComplete.clear();
            historyLoading.clear();
            resend = new ArrayList<>(pending.values());
            p = presence;
            focus = focused;
        }
        setStatus(Status.ONLINE, "");
        sendRaw(Js.op("presence").put("presence", p.json()).toString());
        for (Pending m : resend) {
            sendPending(m);
        }
        fire(SocialEvent.of(SocialEvent.Kind.FRIENDS));
        fire(SocialEvent.of(SocialEvent.Kind.REQUESTS));
        fire(SocialEvent.of(SocialEvent.Kind.GROUPS));
        fire(SocialEvent.of(SocialEvent.Kind.PARTY));
        if (focus != null) {
            loadHistory(focus);
        }
    }

    private void onMessage(JsonObject o) {
        ChatMessage m = ChatMessage.read(Js.obj(o, "message"));
        if (m == null) {
            return;
        }
        String nonce = Js.str(o, "nonce", null);
        boolean own;
        boolean focus;
        synchronized (this) {
            own = self != null && m.sender.equals(self.uuid);
            if (nonce != null) {
                pending.remove(nonce);
            }
            List<ChatMessage> list = messages.get(m.conv);
            if (list != null && list.stream().noneMatch(x -> x.id == m.id)) {
                list.add(m);
                list.sort(Comparator.comparingLong(x -> x.id));
            }
            focus = m.conv.equals(focused);
        }
        fire(new SocialEvent(SocialEvent.Kind.CONVERSATION, m.conv, m, null));
        if (!own) {
            if (focus) {
                markRead(m.conv);
            }
            fire(new SocialEvent(SocialEvent.Kind.MESSAGE, m.conv, m, null));
        }
    }

    private void onHistory(JsonObject o) {
        String conv = Js.str(o, "conv", "");
        List<ChatMessage> got = Js.list(o, "messages", ChatMessage::read);
        boolean complete = Js.bool(o, "complete", false);
        synchronized (this) {
            historyLoading.remove(conv);
            List<ChatMessage> list = messages.computeIfAbsent(conv, k -> new ArrayList<>());
            Set<Long> have = new HashSet<>();
            for (ChatMessage m : list) {
                have.add(m.id);
            }
            for (ChatMessage m : got) {
                if (m.conv.equals(conv) && have.add(m.id)) {
                    list.add(m);
                }
            }
            list.sort(Comparator.comparingLong(x -> x.id));
            if (complete) {
                historyComplete.add(conv);
            }
        }
        fire(new SocialEvent(SocialEvent.Kind.HISTORY, conv, null, null));
        String focus;
        synchronized (this) {
            focus = focused;
        }
        if (conv.equals(focus)) {
            markRead(conv);
        }
    }

    // ------------------------------------------------------------------ outgoing

    private void sendRaw(String text) {
        synchronized (this) {
            sendChain = sendChain.thenCompose(w -> w == null ? CompletableFuture.completedFuture(null) : w.sendText(text, true))
                    .exceptionally(e -> null);
        }
    }

    private CompletableFuture<JsonObject> call(Js.Obj body) {
        if (ws == null || status() != Status.ONLINE) {
            return CompletableFuture.failedFuture(new SocialException("Nicht mit dem Lumora-Social-Server verbunden."));
        }
        long rid = rids.incrementAndGet();
        CompletableFuture<JsonObject> f = new CompletableFuture<>();
        calls.put(rid, f);
        sendRaw(body.put("rid", rid).toString());
        return f.orTimeout(15, TimeUnit.SECONDS).whenComplete((r, e) -> calls.remove(rid));
    }

    private CompletableFuture<Void> action(Js.Obj body) {
        return call(body).thenApply(r -> null);
    }

    private void failCalls(String reason) {
        for (Long rid : new ArrayList<>(calls.keySet())) {
            CompletableFuture<JsonObject> f = calls.remove(rid);
            if (f != null) {
                f.completeExceptionally(new SocialException(reason));
            }
        }
    }

    /** Shown to friends; sent now if connected, else after the next login. */
    public void setPresence(Presence p) {
        synchronized (this) {
            if (p.equals(presence)) {
                return;
            }
            presence = p;
        }
        if (status() == Status.ONLINE) {
            sendRaw(Js.op("presence").put("presence", p.json()).toString());
        }
    }

    public CompletableFuture<Void> addFriend(String name) {
        String n = name == null ? "" : name.strip();
        if (!Rules.validName(n)) {
            return CompletableFuture.failedFuture(new SocialException("Ungültiger Spielername."));
        }
        return action(Js.op("friend.request").put("name", n));
    }

    public CompletableFuture<Void> acceptFriend(String uuid) {
        return action(Js.op("friend.accept").put("uuid", uuid));
    }

    /** Declines an incoming or withdraws an outgoing request. */
    public CompletableFuture<Void> declineFriend(String uuid) {
        return action(Js.op("friend.decline").put("uuid", uuid));
    }

    public CompletableFuture<Void> removeFriend(String uuid) {
        return action(Js.op("friend.remove").put("uuid", uuid));
    }

    /** Invites a player (UUID) into your party; starts a party if you have none. Only the leader invites. */
    public CompletableFuture<Void> inviteToParty(String uuid) {
        return action(Js.op("party.invite").put("uuid", uuid));
    }

    public CompletableFuture<Void> acceptParty(String partyId) {
        return action(Js.op("party.accept").put("id", partyId));
    }

    public CompletableFuture<Void> declineParty(String partyId) {
        return action(Js.op("party.decline").put("id", partyId));
    }

    public CompletableFuture<Void> leaveParty() {
        return action(Js.op("party.leave"));
    }

    /** Removes a member or withdraws an invitation (leader only). */
    public CompletableFuture<Void> kickFromParty(String uuid) {
        return action(Js.op("party.kick").put("uuid", uuid));
    }

    /** What the server tells about a player (UUID): Lumora user, friendship, shared groups, party. */
    public CompletableFuture<PlayerCard> playerCard(String uuid) {
        return call(Js.op("player.card").put("uuid", uuid)).thenApply(r -> PlayerCard.read(Js.obj(r, "card")));
    }

    /** Creates a group; completes with its id. */
    public CompletableFuture<String> createGroup(String name, String description, String icon, int color) {
        return call(Js.op("group.create").put("name", Rules.clean(name, Rules.GROUP_NAME_MAX, false))
                .put("description", Rules.clean(description, Rules.GROUP_DESCRIPTION_MAX, true))
                .put("icon", Rules.icon(icon)).put("color", Rules.color(color)))
                .thenApply(r -> Js.str(r, "id", null));
    }

    public CompletableFuture<Void> updateGroup(String id, String name, String description, String icon, int color) {
        return action(Js.op("group.update").put("id", id).put("name", Rules.clean(name, Rules.GROUP_NAME_MAX, false))
                .put("description", Rules.clean(description, Rules.GROUP_DESCRIPTION_MAX, true))
                .put("icon", Rules.icon(icon)).put("color", Rules.color(color)));
    }

    public CompletableFuture<Void> deleteGroup(String id) {
        return action(Js.op("group.delete").put("id", id));
    }

    /** Adds a friend to a group (needs a managing role). */
    public CompletableFuture<Void> addMember(String groupId, String uuid) {
        return action(Js.op("group.add").put("id", groupId).put("uuid", uuid));
    }

    public CompletableFuture<Void> kickMember(String groupId, String uuid) {
        return action(Js.op("group.kick").put("id", groupId).put("uuid", uuid));
    }

    public CompletableFuture<Void> leaveGroup(String groupId) {
        return action(Js.op("group.leave").put("id", groupId));
    }

    /** Creates ({@code role.id} null) or changes a role (owner only). */
    public CompletableFuture<Void> saveRole(String groupId, String roleId, String name, String icon, int color, boolean manage) {
        Js.Obj r = Js.obj().put("name", Rules.clean(name, Rules.ROLE_NAME_MAX, false)).put("icon", Rules.icon(icon))
                .put("color", Rules.color(color)).put("manage", manage);
        if (roleId != null) {
            r.put("id", roleId);
        }
        return action(Js.op("group.role.save").put("id", groupId).put("role", r.json()));
    }

    public CompletableFuture<Void> deleteRole(String groupId, String roleId) {
        return action(Js.op("group.role.delete").put("id", groupId).put("roleId", roleId));
    }

    public CompletableFuture<Void> setMemberRole(String groupId, String uuid, String roleId) {
        return action(Js.op("group.member.role").put("id", groupId).put("uuid", uuid).put("roleId", roleId));
    }

    public CompletableFuture<Void> setMuted(String conv, boolean muted) {
        return action(Js.op("conv.mute").put("conv", conv).put("muted", muted));
    }

    /** Sends a message; it shows as pending until the server confirms it (also across a reconnect). */
    public void send(String conv, String text) {
        String t = Rules.clean(text, Rules.MESSAGE_MAX, true);
        if (t.isEmpty()) {
            return;
        }
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        StringBuilder nonce = new StringBuilder();
        for (byte x : b) {
            nonce.append(String.format(Locale.ROOT, "%02x", x));
        }
        Pending p = new Pending(nonce.toString(), conv, t, System.currentTimeMillis(), null);
        synchronized (this) {
            pending.put(p.nonce, p);
        }
        fire(new SocialEvent(SocialEvent.Kind.CONVERSATION, conv, null, null));
        if (status() == Status.ONLINE) {
            sendPending(p);
        }
    }

    private void sendPending(Pending p) {
        call(Js.op("msg.send").put("conv", p.conv).put("text", p.text).put("nonce", p.nonce)).whenComplete((r, e) -> {
            if (e != null) {
                synchronized (this) {
                    if (pending.containsKey(p.nonce)) {
                        Throwable c = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
                        pending.put(p.nonce, new Pending(p.nonce, p.conv, p.text, p.at, c instanceof SocialException
                                ? c.getMessage() : "Nicht gesendet"));
                    }
                }
                fire(new SocialEvent(SocialEvent.Kind.CONVERSATION, p.conv, null, null));
            }
        });
    }

    /** Removes a failed pending message (or retries it with {@code retry}). */
    public void discardPending(String nonce, boolean retry) {
        Pending p;
        synchronized (this) {
            p = pending.remove(nonce);
        }
        if (p != null && retry) {
            send(p.conv, p.text);
        } else if (p != null) {
            fire(new SocialEvent(SocialEvent.Kind.CONVERSATION, p.conv, null, null));
        }
    }

    /** Loads the newest page of a conversation if nothing is loaded yet. */
    public void ensureHistory(String conv) {
        boolean need;
        synchronized (this) {
            need = !messages.containsKey(conv);
        }
        if (need) {
            loadHistory(conv);
        }
    }

    /** Loads the page before the oldest loaded message. */
    public void loadHistory(String conv) {
        long before;
        synchronized (this) {
            if (status != Status.ONLINE || historyLoading.contains(conv) || historyComplete.contains(conv)) {
                return;
            }
            historyLoading.add(conv);
            List<ChatMessage> list = messages.get(conv);
            before = list == null || list.isEmpty() ? 0 : list.get(0).id;
        }
        sendRaw(Js.op("msg.history").put("conv", conv).put("before", before).put("limit", Rules.HISTORY_PAGE).toString());
    }

    /** The conversation on screen; its new messages are marked read right away. Null when none is shown. */
    public void setFocused(String conv) {
        synchronized (this) {
            focused = conv;
        }
        if (conv != null) {
            ensureHistory(conv);
            markRead(conv);
        }
    }

    public void markRead(String conv) {
        long id;
        synchronized (this) {
            ConversationInfo c = convs.get(conv);
            List<ChatMessage> list = messages.get(conv);
            long newest = list != null && !list.isEmpty() ? list.get(list.size() - 1).id : c != null && c.last != null ? c.last.id : 0;
            if (c == null || newest <= c.lastRead && c.unread == 0) {
                return;
            }
            id = newest;
            convs.put(conv, c.read(id));
        }
        if (status() == Status.ONLINE) {
            sendRaw(Js.op("msg.read").put("conv", conv).put("id", id).toString());
        }
        fire(new SocialEvent(SocialEvent.Kind.CONVERSATION, conv, null, null));
    }

    // ------------------------------------------------------------------ state

    private void setStatus(Status s, String text) {
        synchronized (this) {
            if (status == s && statusText.equals(text)) {
                return;
            }
            status = s;
            statusText = text == null ? "" : text;
        }
        fire(SocialEvent.of(SocialEvent.Kind.STATUS));
    }

    private void fire(SocialEvent e) {
        for (Consumer<SocialEvent> l : listeners) {
            try {
                l.accept(e);
            } catch (RuntimeException ex) {
                // a broken listener must not stop the client
            }
        }
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized String statusText() {
        return statusText;
    }

    /** The own profile as confirmed by the server, else the local identity. */
    public synchronized Profile self() {
        return self != null ? self : identity.profile();
    }

    public synchronized Presence presence() {
        return presence;
    }

    /** Friends: online first (in game before launcher), then by name. */
    public synchronized List<Friend> friends() {
        List<Friend> l = new ArrayList<>(friends.values());
        l.sort(Comparator.<Friend>comparingInt(f -> -f.presence.state.rank())
                .thenComparing(f -> f.profile.name.toLowerCase(Locale.ROOT)));
        return l;
    }

    public synchronized Friend friend(String uuid) {
        return friends.get(uuid);
    }

    public synchronized List<FriendRequest> requests() {
        return requests;
    }

    /** Your party, or null. */
    public synchronized Party party() {
        return party;
    }

    /** Open invitations into other parties, newest first. */
    public synchronized List<PartyInvite> partyInvites() {
        return partyInvites;
    }

    /** Groups, most recent activity first. */
    public synchronized List<Group> groups() {
        List<Group> l = new ArrayList<>(groups.values());
        l.sort(Comparator.<Group>comparingLong(g -> {
            ConversationInfo c = convs.get(g.conv());
            return -(c != null && c.last != null ? c.last.at : g.created);
        }).thenComparing(g -> g.name.toLowerCase(Locale.ROOT)));
        return l;
    }

    public synchronized Group group(String id) {
        return groups.get(id);
    }

    public synchronized ConversationInfo conversation(String conv) {
        return convs.get(conv);
    }

    public synchronized List<ChatMessage> messages(String conv) {
        List<ChatMessage> l = messages.get(conv);
        return l == null ? List.of() : List.copyOf(l);
    }

    public synchronized boolean historyComplete(String conv) {
        return historyComplete.contains(conv);
    }

    public synchronized boolean historyLoaded(String conv) {
        return messages.containsKey(conv);
    }

    public synchronized List<Pending> pending(String conv) {
        List<Pending> l = new ArrayList<>();
        for (Pending p : pending.values()) {
            if (p.conv.equals(conv)) {
                l.add(p);
            }
        }
        return l;
    }

    /** Unread messages of all conversations that are not muted. */
    public synchronized int unreadTotal() {
        int n = 0;
        for (ConversationInfo c : convs.values()) {
            if (!c.muted) {
                n += c.unread;
            }
        }
        return n;
    }

    public synchronized int unread(String conv) {
        ConversationInfo c = convs.get(conv);
        return c == null ? 0 : c.unread;
    }

    public synchronized int incomingRequests() {
        int n = 0;
        for (FriendRequest r : requests) {
            if (r.incoming) {
                n++;
            }
        }
        return n;
    }

    /** Name of a player known from friends, requests, groups or the own profile; else a short UUID. */
    public synchronized String nameOf(String uuid) {
        Profile me = self();
        if (me != null && me.uuid.equals(uuid)) {
            return me.name;
        }
        Friend f = friends.get(uuid);
        if (f != null) {
            return f.profile.name;
        }
        for (FriendRequest r : requests) {
            if (r.profile.uuid.equals(uuid)) {
                return r.profile.name;
            }
        }
        for (Group g : groups.values()) {
            Member m = g.member(uuid);
            if (m != null) {
                return m.profile.name;
            }
        }
        return uuid == null ? "?" : uuid.substring(0, Math.min(8, uuid.length()));
    }

    /** Presence of a friend, or OFFLINE for anyone else (presence is only shared between friends). */
    public synchronized Presence presenceOf(String uuid) {
        Profile me = self();
        if (me != null && me.uuid.equals(uuid)) {
            return presence;
        }
        Friend f = friends.get(uuid);
        return f == null ? Presence.OFFLINE : f.presence;
    }

    /** Failure reported to the UI; the message is German and meant for the user. */
    public static final class SocialException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SocialException(String message) {
            super(message, null, false, false);
        }
    }
}
