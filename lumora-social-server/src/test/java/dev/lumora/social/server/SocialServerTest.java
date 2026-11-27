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
        return connect(p, kind, c -> { });
    }

    SocialClient connect(Profile p, String kind, java.util.function.Consumer<SocialClient> beforeStart) {
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
        beforeStart.accept(c);
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
        checkStorage();
    }

    @Test
    void privacyBlockingStatusAndMessageEditing() throws Exception {
        startServer();
        SocialClient max = connect(MAX, "launcher");
        SocialClient toby = connect(TOBY, "game");
        SocialClient sarah = connect(SARAH, "game");

        // request policy "nobody": refused; "friends_of_friends": only with a friend in common
        get(max.addFriend("Toby"));
        get(toby.acceptFriend(MAX.uuid));
        get(toby.setRequestPolicy("nobody"));
        await(() -> "nobody".equals(toby.requestPolicy()), "policy at toby");
        Exception nobody = assertThrows(SocialClient.SocialException.class, () -> get(sarah.addFriend("Toby")));
        assertTrue(nobody.getMessage().contains("keine Freundschaftsanfragen"), nobody.getMessage());
        get(toby.setRequestPolicy("friends_of_friends"));
        Exception fof = assertThrows(SocialClient.SocialException.class, () -> get(sarah.addFriend("Toby")));
        assertTrue(fof.getMessage().contains("Freunden seiner Freunde"), fof.getMessage());
        get(max.addFriend("JustSarah"));
        get(sarah.acceptFriend(MAX.uuid));
        get(sarah.addFriend("Toby")); // max is a friend of both
        get(toby.acceptFriend(SARAH.uuid));
        await(() -> toby.friends().size() == 2, "toby has max and sarah");

        // status: busy is shown with the activity, invisible looks offline to friends (applied on the server)
        get(toby.setPresenceMode("busy"));
        await(() -> "busy".equals(max.friend(TOBY.uuid).presence.mode), "busy at max");
        assertEquals(Presence.State.MENU, max.friend(TOBY.uuid).presence.state);
        get(toby.setPresenceMode("invisible"));
        await(() -> !max.friend(TOBY.uuid).presence.online(), "invisible = offline at max");
        await(() -> !sarah.friend(TOBY.uuid).presence.online(), "invisible = offline at sarah too"); // pushed to each friend on its own
        get(toby.setPresenceMode("online"));
        await(() -> max.friend(TOBY.uuid).presence.online() && "online".equals(max.friend(TOBY.uuid).presence.mode), "visible again");

        // edit, delete and search messages; the other side sees the change live
        String dm = Conv.dm(MAX.uuid, TOBY.uuid);
        max.setFocused(dm);
        toby.setFocused(dm);
        max.send(dm, "Wir treffen uns um acht");
        await(() -> toby.messages(dm).size() == 1, "message at toby");
        long id = toby.messages(dm).get(0).id;
        assertThrows(SocialClient.SocialException.class, () -> get(toby.editMessage(dm, id, "gefälscht")), "only own messages");
        get(max.editMessage(dm, id, "Wir treffen uns um neun"));
        await(() -> toby.messages(dm).get(0).text.equals("Wir treffen uns um neun") && toby.messages(dm).get(0).editedAt > 0, "edit at toby");
        assertEquals(1, get(toby.searchMessages("um neun", null)).size());
        assertEquals(0, get(sarah.searchMessages("um neun", null)).size(), "not readable for sarah");
        get(max.deleteMessage(dm, id));
        await(() -> toby.messages(dm).get(0).deleted && toby.messages(dm).get(0).text.isEmpty(), "delete at toby");
        assertEquals(0, get(toby.searchMessages("neun", null)).size(), "deleted messages are not found");

        // block: ends the friendship, the blocked player cannot ask again and sees no hint of the block
        get(toby.block(SARAH.uuid));
        await(() -> toby.isBlocked(SARAH.uuid) && toby.friend(SARAH.uuid) == null && sarah.friend(TOBY.uuid) == null, "blocked");
        Exception blocked = assertThrows(SocialClient.SocialException.class, () -> get(sarah.addFriend("Toby")));
        assertTrue(blocked.getMessage().contains("keine Freundschaftsanfragen"), blocked.getMessage());
        Exception ownBlock = assertThrows(SocialClient.SocialException.class, () -> get(toby.addFriend("JustSarah")));
        assertTrue(ownBlock.getMessage().contains("blockiert"), ownBlock.getMessage());

        // restart: settings, block and the changed message are stored
        clients.forEach(SocialClient::close);
        clients.clear();
        server.close();
        startServer();
        SocialClient toby2 = connect(TOBY, "game");
        assertEquals("friends_of_friends", toby2.requestPolicy());
        assertTrue(toby2.isBlocked(SARAH.uuid));
        toby2.setFocused(dm);
        await(() -> toby2.messages(dm).size() == 1 && toby2.messages(dm).get(0).deleted, "deleted after restart");
    }

    /** The data really is on disk (overridden by the PostgreSQL variant). */
    void checkStorage() throws Exception {
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

    static String statusOf(SocialClient asker, Profile p, String key) {
        try {
            return get(asker.sharedStatuses(key, List.of(p.uuid, "not a uuid"))).get(p.uuid);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void sharedStatusLinesFollowTheirScope() throws Exception {
        startServer();
        // set before the login: sent with it
        SocialClient max = connect(MAX, "game", c -> c.setSharedStatus("spotify", "♪ Song §c- Artist", "all"));
        SocialClient toby = connect(TOBY, "game");
        SocialClient sarah = connect(SARAH, "game");
        get(max.addFriend("Toby"));
        await(() -> toby.incomingRequests() == 1, "request");
        get(toby.acceptFriend(MAX.uuid));
        await(() -> max.friend(TOBY.uuid) != null, "friends");

        await(() -> "♪ Song - Artist".equals(statusOf(sarah, MAX, "spotify")), "everyone sees it, without formatting codes");
        assertEquals(null, statusOf(sarah, MAX, "other-addon"), "per addon");
        assertEquals("♪ Song - Artist", statusOf(max, MAX, "spotify"), "the own line");

        max.setSharedStatus("spotify", "♪ Song - Artist", "friends");
        await(() -> statusOf(sarah, MAX, "spotify") == null, "strangers no longer see it");
        assertEquals("♪ Song - Artist", statusOf(toby, MAX, "spotify"), "friends still do");

        max.setSharedStatus("spotify", "", "all");
        await(() -> statusOf(toby, MAX, "spotify") == null, "cleared");

        sarah.setSharedStatus("spotify", "x".repeat(200), "all");
        await(() -> statusOf(max, SARAH, "spotify") != null, "long line");
        assertEquals(dev.lumora.social.Rules.STATUS_MAX, statusOf(max, SARAH, "spotify").length(), "cut to the limit");
        sarah.close();
        await(() -> statusOf(max, SARAH, "spotify") == null, "gone with the connection");

        assertThrows(IllegalArgumentException.class, () -> max.setSharedStatus("Bad Key!", "x", "all"));
    }

    @Test
    void cosmeticsAndEmotesReachWatchingPlayers() throws Exception {
        startServer();
        com.google.gson.JsonArray wings = dev.lumora.social.Js.object(
                "{\"l\":[{\"id\":\"angel_wings\",\"variant\":\"gold\",\"offset\":[0,99,0.26]},{\"id\":\"Bad Id!\"},{\"id\":\"halo\"}]}")
                .getAsJsonArray("l");
        // set before the login: sent with it
        SocialClient max = connect(MAX, "game", c -> c.setCosmetics(wings, "all"));
        SocialClient toby = connect(TOBY, "game");
        SocialClient sarah = connect(SARAH, "launcher");
        java.util.Map<String, com.google.gson.JsonArray> seen = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.List<String> emotes = new java.util.concurrent.CopyOnWriteArrayList<>();
        toby.setCosmeticsListener(new SocialClient.CosmeticsListener() {
            @Override
            public void loadout(String uuid, com.google.gson.JsonArray loadout) {
                seen.put(uuid, loadout);
            }

            @Override
            public void emote(String uuid, String id) {
                emotes.add(uuid + ":" + id);
            }
        });
        java.util.Map<String, com.google.gson.JsonArray> now = get(toby.watchCosmetics(java.util.List.of(MAX.uuid, SARAH.uuid)));
        assertEquals(1, now.size(), "only Max wears something");
        com.google.gson.JsonArray l = now.get(MAX.uuid);
        assertEquals(2, l.size(), "the bad id is dropped");
        assertEquals("gold", l.get(0).getAsJsonObject().get("variant").getAsString());
        assertEquals(8.0, l.get(0).getAsJsonObject().getAsJsonArray("offset").get(1).getAsDouble(), 1e-9, "offset clamped");
        assertEquals(0.5, l.get(0).getAsJsonObject().getAsJsonArray("offset").get(2).getAsDouble(), 1e-9, "half pixels");

        // a change is pushed to the watcher
        max.setCosmetics(dev.lumora.social.Js.object("{\"l\":[{\"id\":\"crown\"}]}").getAsJsonArray("l"), "all");
        await(() -> seen.containsKey(MAX.uuid) && seen.get(MAX.uuid).size() == 1, "push of the new loadout");
        assertEquals("crown", seen.get(MAX.uuid).get(0).getAsJsonObject().get("id").getAsString());

        // emotes go to the targets' game sessions, not to strangers when only friends may see it
        max.sendEmote("wave", java.util.List.of(TOBY.uuid, SARAH.uuid));
        await(() -> emotes.contains(MAX.uuid + ":wave"), "emote arrives");
        max.sendEmote("", java.util.List.of(TOBY.uuid));
        await(() -> emotes.contains(MAX.uuid + ":"), "stop arrives");
        max.setCosmetics(dev.lumora.social.Js.object("{\"l\":[{\"id\":\"crown\"}]}").getAsJsonArray("l"), "friends");
        await(() -> seen.get(MAX.uuid).size() == 0, "strangers no longer see the loadout");
        int before = emotes.size();
        max.sendEmote("clap", java.util.List.of(TOBY.uuid));
        get(max.watchCosmetics(java.util.List.of()));
        Thread.sleep(200);
        assertEquals(before, emotes.size(), "no emote to a stranger with scope friends");

        // gone with the connection
        max.setCosmetics(dev.lumora.social.Js.object("{\"l\":[{\"id\":\"crown\"}]}").getAsJsonArray("l"), "all");
        await(() -> seen.get(MAX.uuid).size() == 1, "visible again");
        max.close();
        await(() -> seen.get(MAX.uuid).size() == 0, "cleared when Max leaves");
        sarah.close();
    }

    @Test
    void lumoraBadgeShowsGamePlayersOnly() throws Exception {
        startServer();
        SocialClient toby = connect(TOBY, "game");
        connect(SARAH, "launcher");
        // nobody near Toby is in the game with Lumora yet (Sarah only has the launcher open)
        get(toby.watchCosmetics(List.of(MAX.uuid, SARAH.uuid)));
        assertFalse(toby.isLumoraPlayer(MAX.uuid));
        assertFalse(toby.isLumoraPlayer(SARAH.uuid), "the launcher alone shows no badge");

        // Max joins with the game: pushed to the watcher, also found by the tab-list lookup
        SocialClient max = connect(MAX, "game");
        await(() -> toby.isLumoraPlayer(MAX.uuid), "badge pushed when Max joins");
        assertEquals(java.util.Set.of(MAX.uuid), get(toby.lookupLumora(List.of(MAX.uuid, SARAH.uuid, TOBY.uuid))));
        assertFalse(get(max.lookupLumora(List.of(MAX.uuid))).contains(MAX.uuid), "not about oneself");

        // hidden by Max's setting, back when switched on again
        get(max.setShowBadge(false));
        await(() -> !max.showBadge() && !toby.isLumoraPlayer(MAX.uuid), "badge hidden");
        assertTrue(get(toby.lookupLumora(List.of(MAX.uuid))).isEmpty());
        get(max.setShowBadge(true));
        await(() -> toby.isLumoraPlayer(MAX.uuid), "badge shown again");

        // a block hides it in both directions
        get(toby.block(MAX.uuid));
        await(() -> !toby.isLumoraPlayer(MAX.uuid), "hidden after the block");
        get(max.watchCosmetics(List.of(TOBY.uuid)));
        assertFalse(max.isLumoraPlayer(TOBY.uuid), "the blocked player does not see it either");
        get(toby.unblock(MAX.uuid));
        await(() -> toby.isLumoraPlayer(MAX.uuid) && max.isLumoraPlayer(TOBY.uuid), "visible after the unblock");

        // gone when Max leaves the game
        max.close();
        await(() -> !toby.isLumoraPlayer(MAX.uuid), "badge gone when Max leaves");

        // the setting is stored
        SocialClient max2 = connect(MAX, "game");
        get(max2.setShowBadge(false));
        clients.forEach(SocialClient::close);
        clients.clear();
        server.close();
        startServer();
        assertFalse(connect(MAX, "game").showBadge(), "kept after a restart");
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
