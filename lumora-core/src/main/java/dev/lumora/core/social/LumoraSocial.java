package dev.lumora.core.social;

import dev.lumora.api.game.ConnectionInfo;
import dev.lumora.api.game.GameContext;
import dev.lumora.api.notify.Notification;
import dev.lumora.api.notify.NotificationType;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.Platform;
import dev.lumora.social.Conv;
import dev.lumora.social.Endpoint;
import dev.lumora.social.MojangSession;
import dev.lumora.social.Rules;
import dev.lumora.social.SocialClient;
import dev.lumora.social.SocialEvent;
import dev.lumora.social.model.ConversationInfo;
import dev.lumora.social.model.Friend;
import dev.lumora.social.model.FriendRequest;
import dev.lumora.social.model.Group;
import dev.lumora.social.model.Presence;
import dev.lumora.social.model.Profile;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Lumora Social in the game: connects to the server the launcher passes ({@code -Dlumora.social.server}, the address
 * only), proves the account with the game's own session (official Mojang join), keeps the presence up to date
 * (menu / singleplayer / server, the server address only if {@code -Dlumora.social.shareServer} allows it) and shows
 * Lumora notifications for new messages, friend requests and friends coming online. Events from the network thread
 * are handled on the client thread in {@link #tick()}.
 */
public final class LumoraSocial {
    private final LumoraCore core;
    private final Platform platform;
    private final SocialClient client;
    private final String disabledReason;
    private final boolean shareServer;
    private final boolean german;
    private final ConcurrentLinkedQueue<SocialEvent> events = new ConcurrentLinkedQueue<>();
    private final Map<String, Presence.State> knownStates = new HashMap<>();
    private final Set<String> knownRequests = new HashSet<>();
    private boolean primed;
    private long ticks;
    private String viewConv;

    private LumoraSocial(LumoraCore core, Platform platform, SocialClient client, String disabledReason, boolean shareServer) {
        this.core = core;
        this.platform = platform;
        this.client = client;
        this.disabledReason = disabledReason;
        this.shareServer = shareServer;
        this.german = platform.translations().language().startsWith("de");
    }

    /** Reads the launcher's settings and connects; a disabled instance (with reason) when that is not possible. */
    public static LumoraSocial start(LumoraCore core, Platform platform) {
        boolean share = !"false".equals(System.getProperty("lumora.social.shareServer"));
        String server = System.getProperty("lumora.social.server");
        boolean de = platform.translations().language().startsWith("de");
        if (server == null || server.isBlank()) {
            return new LumoraSocial(core, platform, null, de
                    ? "Lumora Social ist nicht eingerichtet. Im Launcher unter „Freunde“ einen Server eintragen und das Spiel neu starten."
                    : "Lumora Social is not set up. Enter a server in the launcher under \"Freunde\" and restart the game.", share);
        }
        URI uri;
        try {
            uri = Endpoint.parse(server);
        } catch (IllegalArgumentException e) {
            return new LumoraSocial(core, platform, null, e.getMessage(), share);
        }
        Optional<GameSession> s = platform.session();
        String uuid = s.map(x -> Rules.uuid(x.uuid())).orElse(null);
        if (s.isEmpty() || uuid == null || !Rules.validName(s.get().name())) {
            return new LumoraSocial(core, platform, null, de ? "Keine Minecraft-Sitzung im Spiel." : "No Minecraft session in the game.", share);
        }
        Profile me = new Profile(uuid, s.get().name());
        GameSession session = s.get();
        SocialClient c = new SocialClient(uri, new SocialClient.Identity() {
            @Override
            public Profile profile() {
                return me;
            }

            @Override
            public void join(String serverId) throws IOException {
                MojangSession.join(session.accessToken().get(), me.uuid, serverId);
            }
        }, "game", platform.minecraftVersion(), new Presence(Presence.State.MENU, "", platform.minecraftVersion()));
        LumoraSocial social = new LumoraSocial(core, platform, c, null, share);
        c.addListener(social.events::add);
        c.start();
        core.logger().info("Lumora Social: connecting to " + Endpoint.display(uri) + " as " + me.name);
        return social;
    }

    /** The client, or null when disabled. */
    public SocialClient client() {
        return client;
    }

    /** Why there is no client (for the screen), else null. */
    public String disabledReason() {
        return disabledReason;
    }

    public boolean german() {
        return german;
    }

    /** Unread messages and incoming requests (badge on the Lumora buttons). */
    public int badge() {
        return client == null ? 0 : client.unreadTotal() + client.incomingRequests();
    }

    /** The social screen shows {@code conv} (null: closed); its messages count as read and do not notify. */
    public void viewing(String conv) {
        viewConv = conv;
        if (client != null) {
            client.setFocused(conv);
        }
    }

    /** Client thread, every tick. */
    public void tick() {
        if (client == null) {
            return;
        }
        if (++ticks % 20 == 0) {
            client.setPresence(presenceNow());
        }
        SocialEvent e;
        while ((e = events.poll()) != null) {
            handle(e);
        }
    }

    private Presence presenceNow() {
        GameContext game = platform.game();
        String v = platform.minecraftVersion();
        if (!game.inWorld()) {
            return new Presence(Presence.State.MENU, "", v);
        }
        ConnectionInfo c = game.connection();
        if (c.multiplayer()) {
            return new Presence(Presence.State.MULTIPLAYER, shareServer ? Rules.clean(c.address(), Rules.PRESENCE_DETAIL_MAX, false) : "", v);
        }
        return new Presence(Presence.State.SINGLEPLAYER, "", v);
    }

    private void handle(SocialEvent e) {
        switch (e.kind) {
            case STATUS -> {
                if (client.status() != SocialClient.Status.ONLINE) {
                    primed = false;
                }
            }
            case FRIENDS -> {
                boolean notify = primed;
                for (Friend f : client.friends()) {
                    Presence.State before = knownStates.put(f.profile.uuid, f.presence.state);
                    if (notify && before == Presence.State.OFFLINE && f.presence.online()) {
                        show(f.profile.name, german ? "ist jetzt online" : "is online now", NotificationType.INFO);
                    }
                }
                primed = client.status() == SocialClient.Status.ONLINE;
            }
            case REQUESTS -> {
                Set<String> now = new HashSet<>();
                for (FriendRequest r : client.requests()) {
                    if (r.incoming) {
                        now.add(r.profile.uuid);
                        if (!knownRequests.contains(r.profile.uuid) && client.status() == SocialClient.Status.ONLINE) {
                            show(german ? "Freundschaftsanfrage" : "Friend request", r.profile.name + (german
                                    ? " möchte dein Freund sein (" + keyHint() + ")" : " wants to be your friend (" + keyHint() + ")"), NotificationType.INFO);
                        }
                    }
                }
                knownRequests.clear();
                knownRequests.addAll(now);
            }
            case MESSAGE -> {
                if (e.message == null || e.conv.equals(viewConv)) {
                    return;
                }
                ConversationInfo ci = client.conversation(e.conv);
                if (ci != null && ci.muted) {
                    return;
                }
                String sender = client.nameOf(e.message.sender);
                Group g = Conv.isGroup(e.conv) ? client.group(Conv.groupId(e.conv)) : null;
                String title = g == null ? sender : sender + " · " + g.name;
                String text = e.message.text.length() > 90 ? e.message.text.substring(0, 88) + "…" : e.message.text;
                show(title, text.replace('\n', ' '), NotificationType.INFO);
            }
            case NOTICE -> show("Lumora Social", e.text, NotificationType.INFO);
            case ERROR -> show("Lumora Social", e.text, NotificationType.WARNING);
            default -> {
            }
        }
    }

    private String keyHint() {
        for (var k : core.keybindManager().of("lumora")) {
            if (k.id().endsWith("social") && k.key() >= 0) {
                return german ? "Taste " + core.keybindManager().keyName(k.key()) : "key " + core.keybindManager().keyName(k.key());
            }
        }
        return german ? "Lumora-Hub → Freunde" : "Lumora hub → Friends";
    }

    private void show(String title, String message, NotificationType type) {
        core.notificationCenter().show(Notification.builder(title).message(message).type(type).duration(5000).build());
    }

    public void stop() {
        if (client != null) {
            client.close();
        }
    }
}
