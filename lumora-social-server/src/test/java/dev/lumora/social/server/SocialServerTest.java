package dev.lumora.social.server;

import dev.lumora.social.Conv;
import dev.lumora.social.SocialClient;
import dev.lumora.social.SocialEvent;
import dev.lumora.social.model.ChatMessage;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.PlayerCard;
import dev.lumora.social.model.Profile;
import dev.lumora.social.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real server (dev auth, random port, temp data) and real clients over WebSocket. */
class SocialServerTest {
    static final Profile MAX = new Profile("11111111111111111111111111111111", "CuzImMax");
    static final Profile TOBY = new Profile("22222222222222222222222222222222", "Toby");
    static final Profile SARAH = new Profile("33333333333333333333333333333333", "JustSarah");

    @TempDir
    Path data;
    Main.Running server;
    final List<SocialClient> clients = new ArrayList<>();

    @BeforeAll
    static void quiet() {
        Log.quiet = true;
        Hub.messageBurst = 1000;
    }

    @AfterEach
    void stop() {
        clients.forEach(SocialClient::close);
        if (server != null) {
            server.close();
        }
    }

    void startServer() throws IOException {
        server = Main.start(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), data, null, true);
    }

    SocialClient connect(Profile p, String kind) {
        SocialClient c = new SocialClient(URI.create("ws://127.0.0.1:" + server.port() + "/"), new SocialClient.Identity() {
            @Override
            public Profile profile() {
                return p;
            }

            @Override
            public void join(String serverId) {
                throw new AssertionError("dev server must not ask for a Mojang join");
            }
        }, kind, "test", new Presence(kind.equals("launcher") ? Presence.State.LAUNCHER : Presence.State.MENU, "", ""));
        clients.add(c);
        c.start();
        await(() -> c.status() == SocialClient.Status.ONLINE, "online " + p.name);
        return c;
    }

    static void await(BooleanSupplier cond, String what) {
        long end = System.currentTimeMillis() + 10_000;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("timeout waiting for " + what);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static <T> T get(CompletableFuture<T> f) throws Exception {
        try {
            return f.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (Exception) e.getCause();
        }
    }

    @Test
    void friendsGroupsRolesChatAndPersistence() throws Exception {
        startServer();
        SocialClient max = connect(MAX, "launcher");
        SocialClient toby = connect(TOBY, "game");
        SocialClient sarah = connect(SARAH, "game");
        List<SocialEvent> tobyEvents = new CopyOnWriteArrayList<>();
        toby.addListener(tobyEvents::add);

        // friend request by name, case-insensitive; unknown players are refused with a clear message
        Exception unknown = assertThrows(SocialClient.SocialException.class, () -> get(max.addFriend("Nobody")));
        assertTrue(unknown.getMessage().contains("noch nie"), unknown.getMessage());
        get(max.addFriend("toby"));
        await(() -> toby.incomingRequests() == 1, "request at toby");
        assertEquals("CuzImMax", toby.requests().get(0).profile.name);
        get(toby.acceptFriend(MAX.uuid));
        await(() -> max.friends().size() == 1 && toby.friends().size() == 1, "friendship");
        assertTrue(max.requests().isEmpty());
        // presence: toby is in game (MENU), max sees it; then toby joins a server
        assertEquals(Presence.State.MENU, max.friend(TOBY.uuid).presence.state);
        toby.setPresence(new Presence(Presence.State.MULTIPLAYER, "mc.example.net", "1.8.9"));
        await(() -> "mc.example.net".equals(max.friend(TOBY.uuid).presence.detail), "presence update");
        // sarah asks max; max asking back accepts instead of creating a second request
        get(sarah.addFriend("CuzImMax"));
        await(() -> max.incomingRequests() == 1, "request at max");
        get(max.addFriend("JustSarah"));
        await(() -> max.friends().size() == 2 && sarah.friends().size() == 1, "mutual request accepted");

        // direct messages with unread counter and read marks
        String dm = Conv.dm(MAX.uuid, TOBY.uuid);
        max.send(dm, "Moin! Bock auf BedWars?");
        await(() -> toby.unread(dm) == 1, "unread at toby");
        assertTrue(tobyEvents.stream().anyMatch(e -> e.kind == SocialEvent.Kind.MESSAGE && e.message.text.startsWith("Moin")));
        max.setFocused(dm);
        await(() -> max.messages(dm).size() == 1 && max.pending(dm).isEmpty(), "own message confirmed");
        assertEquals(0, max.conversation(dm).othersRead);
        toby.setFocused(dm);
        await(() -> toby.unread(dm) == 0 && toby.messages(dm).size() == 1, "toby read");
        await(() -> max.conversation(dm).othersRead >= max.messages(dm).get(0).id, "read mark at max");
        // DMs only between friends
        String strangers = Conv.dm(TOBY.uuid, SARAH.uuid);
        Exception notFriends = assertThrows(SocialClient.SocialException.class, () -> get(sendAndWait(toby, strangers)));
        assertTrue(notFriends.getMessage().contains("Freunde"));

        // group with custom roles
        String gid = get(max.createGroup("BedWars Squad", "Unsere BedWars Truppe!", "flag", 0x8B5CF6));
        assertNotNull(gid);
        await(() -> max.group(gid) != null, "group at max");
        Exception notFriend = assertThrows(SocialClient.SocialException.class, () -> get(max.addMember(gid, "44444444444444444444444444444444")));
        assertTrue(notFriend.getMessage().contains("Freunde"));
        get(max.addMember(gid, TOBY.uuid));
        get(max.addMember(gid, SARAH.uuid));
        await(() -> toby.group(gid) != null && sarah.group(gid) != null, "members see group");
        assertTrue(tobyEvents.stream().anyMatch(e -> e.kind == SocialEvent.Kind.NOTICE && e.text.contains("BedWars Squad")));
        get(max.saveRole(gid, null, "Strateg", "shield", 0x8B5CF6, true));
        get(max.saveRole(gid, null, "Kämpfer", "sword", 0x4F7CFF, false));
        await(() -> max.group(gid).roles.size() == 4, "roles");
        String strateg = roleId(max.group(gid), "Strateg");
        String fighter = roleId(max.group(gid), "Kämpfer");
        get(max.setMemberRole(gid, SARAH.uuid, strateg));
        get(max.setMemberRole(gid, TOBY.uuid, fighter));
        await(() -> fighter.equals(toby.group(gid).member(TOBY.uuid).role), "role at toby");
        // a member without manage permission cannot edit, a managing role can
        assertThrows(SocialClient.SocialException.class, () -> get(toby.updateGroup(gid, "Hacked", "", "flag", 0x8B5CF6)));
        get(sarah.updateGroup(gid, "BedWars Squad", "Hier planen wir Strategien.", "flag", 0x8B5CF6));
        await(() -> toby.group(gid).description.startsWith("Hier planen"), "description");
        // managing role may not kick another managing member, only the owner may; nobody kicks the owner
        assertThrows(SocialClient.SocialException.class, () -> get(sarah.kickMember(gid, MAX.uuid)));

        // group chat: everybody gets the message, unread counts, history pages
        String gc = Conv.group(gid);
        for (int i = 0; i < 60; i++) {
            max.send(gc, "Nachricht " + i);
            Thread.sleep(30); // below the per-connection rate limit (20 messages / s)
        }
        await(() -> toby.unread(gc) == 60, "60 unread at toby (" + toby.unread(gc) + ")");
        toby.setFocused(gc);
        await(() -> toby.messages(gc).size() == 50 && !toby.historyComplete(gc), "first page");
        toby.loadHistory(gc);
        await(() -> toby.messages(gc).size() == 60 && toby.historyComplete(gc), "second page");
        await(() -> toby.unread(gc) == 0, "group read");
        assertEquals("Nachricht 0", toby.messages(gc).get(0).text);
        // mute
        get(sarah.setMuted(gc, true));
        await(() -> sarah.conversation(gc).muted && sarah.unreadTotal() == 0, "muted");

        // leave as owner hands the group to the managing member (Strateg)
        get(max.leaveGroup(gid));
        await(() -> max.group(gid) == null, "max left");
        await(() -> SARAH.uuid.equals(sarah.group(gid).owner), "sarah is owner");
        assertEquals(Role.OWNER, sarah.group(gid).member(SARAH.uuid).role);

        // restart: everything is still there
        long dmCount = max.messages(dm).size();
        clients.forEach(SocialClient::close);
        clients.clear();
        server.close();
        startServer();
        SocialClient toby2 = connect(TOBY, "game");
        assertEquals(1, toby2.friends().size());
        Group g = toby2.group(gid);
        assertNotNull(g);
        assertEquals("Hier planen wir Strategien.", g.description);
        assertEquals("Kämpfer", g.roleOf(TOBY.uuid).name);
        toby2.setFocused(dm);
        await(() -> toby2.messages(dm).size() == dmCount, "dm history after restart");
        ChatMessage first = toby2.messages(dm).get(0);
        assertEquals("Moin! Bock auf BedWars?", first.text);
        assertTrue(Files.exists(data.resolve("state.json")));
    }

    private static CompletableFuture<Void> sendAndWait(SocialClient c, String conv) {
        CompletableFuture<Void> f = new CompletableFuture<>();
        c.addListener(e -> {
            if (e.kind == SocialEvent.Kind.CONVERSATION && conv.equals(e.conv)) {
                for (SocialClient.Pending p : c.pending(conv)) {
                    if (p.error != null) {
                        f.completeExceptionally(new SocialClient.SocialException(p.error));
                    }
                }
            }
        });
        c.send(conv, "Hallo?");
        return f;
    }

    private static String roleId(Group g, String name) {
        for (Role r : g.roles) {
            if (r.name.equals(name)) {
                return r.id;
            }
        }
        throw new AssertionError("role " + name);
    }

    @Test
    void partyInviteAcceptLeaveAndPlayerCard() throws Exception {
        startServer();
        SocialClient max = connect(MAX, "game");
        SocialClient toby = connect(TOBY, "game");
        SocialClient sarah = connect(SARAH, "game");

        // the card: Lumora user, not friends, nothing shared; strangers see no online state
        PlayerCard card = get(max.playerCard(TOBY.uuid));
        assertTrue(card.lumora);
        assertEquals("Toby", card.name);
        assertFalse(card.friend);
        assertEquals(null, card.presence);
        assertFalse(get(max.playerCard("44444444444444444444444444444444")).lumora, "never used Lumora");

        // max invites toby: a party with max as leader and toby invited
        get(max.inviteToParty(TOBY.uuid));
        await(() -> toby.partyInvites().size() == 1 && max.party() != null, "invite");
        assertEquals(MAX.uuid, max.party().leader);
        assertTrue(max.party().isInvited(TOBY.uuid));
        assertEquals("CuzImMax", toby.partyInvites().get(0).from.name);
        assertEquals(get(toby.playerCard(MAX.uuid)).partyInvite, toby.partyInvites().get(0).id, "the card shows the invitation");
        assertTrue(get(max.playerCard(TOBY.uuid)).invited);
        get(toby.acceptParty(toby.partyInvites().get(0).id));
        await(() -> toby.party() != null && max.party().members.size() == 2, "joined");
        assertTrue(toby.partyInvites().isEmpty());
        assertTrue(get(max.playerCard(TOBY.uuid)).sameParty);

        // only the leader invites; sarah declines
        Exception notLeader = assertThrows(SocialClient.SocialException.class, () -> get(toby.inviteToParty(SARAH.uuid)));
        assertTrue(notLeader.getMessage().contains("Anführer"), notLeader.getMessage());
        get(max.inviteToParty(SARAH.uuid));
        await(() -> sarah.partyInvites().size() == 1, "invite sarah");
        get(sarah.declineParty(sarah.partyInvites().get(0).id));
        await(() -> sarah.partyInvites().isEmpty() && max.party().invited.isEmpty(), "declined");

        // the leader leaves: toby leads alone without invitations, so the party ends
        get(max.leaveParty());
        await(() -> max.party() == null && toby.party() == null, "party ended");

        // a new party; when the invited player goes offline the invitation ends
        get(toby.inviteToParty(SARAH.uuid));
        await(() -> sarah.partyInvites().size() == 1, "invite again");
        sarah.close();
        await(() -> toby.party() == null, "invitee offline: party of one ends");
        Exception offline = assertThrows(SocialClient.SocialException.class, () -> get(toby.inviteToParty(SARAH.uuid)));
        assertTrue(offline.getMessage().contains("nicht mit Lumora online"), offline.getMessage());

        // friends see each other's online state on the card
        get(max.addFriend("Toby"));
        await(() -> toby.incomingRequests() == 1, "request");
        assertEquals(PlayerCard.Request.INCOMING, get(toby.playerCard(MAX.uuid)).request);
        get(toby.acceptFriend(MAX.uuid));
        await(() -> max.friend(TOBY.uuid) != null, "friends");
        PlayerCard friend = get(max.playerCard(TOBY.uuid));
        assertTrue(friend.friend);
        assertNotNull(friend.presence);
        assertTrue(friend.presence.online());
    }

    @Test
    void sameAccountInLauncherAndGameShowsTheHigherPresence() throws Exception {
        startServer();
        SocialClient max = connect(MAX, "launcher");
        SocialClient toby = connect(TOBY, "launcher");
        get(max.addFriend("Toby"));
        await(() -> toby.incomingRequests() == 1, "request");
        get(toby.acceptFriend(MAX.uuid));
        await(() -> max.friend(TOBY.uuid) != null, "friends");
        assertEquals(Presence.State.LAUNCHER, max.friend(TOBY.uuid).presence.state);
        SocialClient tobyGame = connect(TOBY, "game");
        tobyGame.setPresence(new Presence(Presence.State.SINGLEPLAYER, "", "26.1.2"));
        await(() -> max.friend(TOBY.uuid).presence.state == Presence.State.SINGLEPLAYER, "game presence wins");
        tobyGame.close();
        await(() -> max.friend(TOBY.uuid).presence.state == Presence.State.LAUNCHER, "back to launcher");
        toby.close();
        await(() -> max.friend(TOBY.uuid).presence.state == Presence.State.OFFLINE, "offline");
        assertFalse(max.friend(TOBY.uuid).presence.online());
    }

    @Test
    void healthEndpointAndGarbageAreHandled() throws Exception {
        startServer();
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            OutputStream out = s.getOutputStream();
            out.write("GET /health HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            InputStream in = s.getInputStream();
            String resp = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(resp.startsWith("HTTP/1.1 200") && resp.endsWith("ok"), resp);
        }
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), server.port())) {
            s.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            String resp = new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(resp.startsWith("HTTP/1.1 426"), resp);
        }
        // the server still works afterwards
        connect(MAX, "game");
        assertEquals(1, server.onlineUsers());
    }

    @Test
    void devAuthIsOnlyAllowedOnLoopback() {
        assertThrows(IllegalArgumentException.class,
                () -> Main.start(new InetSocketAddress("0.0.0.0", 0), data, null, true));
    }
}
