package dev.lumora.core.interaction;

import dev.lumora.api.game.WorldAccess;
import dev.lumora.api.interaction.PlayerMenuEntry;
import dev.lumora.api.interaction.PlayerTarget;
import dev.lumora.api.notify.Notification;
import dev.lumora.api.notify.NotificationType;
import dev.lumora.core.LumoraCore;
import dev.lumora.core.Platform;
import dev.lumora.core.social.LumoraSocial;
import dev.lumora.social.Conv;
import dev.lumora.social.SocialClient;
import dev.lumora.social.model.Party;
import dev.lumora.social.model.PlayerCard;
import dev.lumora.social.model.Presence;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The player menu, built into the client: looking at a player and pressing the middle mouse button (the mouse wheel)
 * opens it. Lumora's own entries: friend request, party invitation, client profile, Lumora message, private message
 * and copying the name; addons add theirs through {@link PlayerMenuRegistry}.
 */
public final class PlayerInteraction {
    private final LumoraCore core;
    private final Platform platform;
    private final PlayerMenuRegistry registry;
    private boolean wasDown;
    /** The middle button went down since the last tick (noticed every frame, so a short click is not missed). */
    private boolean pressed;
    private PlayerMenuView shown;

    public PlayerInteraction(LumoraCore core, PlayerMenuRegistry registry) {
        this.core = core;
        this.platform = core.platform();
        this.registry = registry;
        builtins();
    }

    PlayerMenuRegistry registry() {
        return registry;
    }

    // ------------------------------------------------------------------ opening

    /** Every frame while the HUD is drawn. */
    public void frame() {
        boolean down = middleDown();
        if (down && !wasDown) {
            pressed = true;
        }
        wasDown = down;
    }

    /** Every client tick: opens the menu if the middle button went down while looking at a player. */
    public void tick() {
        frame();
        if (!pressed) {
            return;
        }
        pressed = false;
        if (shown != null || !platform.keys().acceptsKeybinds()) {
            return;
        }
        WorldAccess world = platform.world();
        world.crosshairPlayer().ifPresent(p -> open(p.id(), p.name(), middleDown()));
    }

    /** Opens the menu for a player; {@code holding}: the middle button is still down. */
    public void open(UUID id, String name, boolean holding) {
        if (id == null || name == null) {
            return;
        }
        Target t = new Target(id, name, platform.game().inWorld() && platform.game().connection().multiplayer(),
                safePermissionLevel());
        t.owner = this;
        SocialClient c = client();
        if (c != null && c.status() == SocialClient.Status.ONLINE) {
            c.playerCard(t.uuid32).whenComplete((card, e) -> {
                if (card != null) {
                    t.card = card;
                }
            });
        }
        PlayerMenuView v = new PlayerMenuView(this, t, holding);
        shown = v;
        platform.views().show(v);
    }

    /** The menu shown right now (smoke test), or null. */
    public boolean isOpen() {
        return shown != null;
    }

    void close(PlayerMenuView v) {
        platform.views().close(v);
        closed(v);
    }

    void closed(PlayerMenuView v) {
        if (shown == v) {
            shown = null;
        }
    }

    boolean middleDown() {
        try {
            return platform.extras().mouseMiddleDown();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    private int safePermissionLevel() {
        try {
            return Math.max(0, Math.min(4, platform.extras().permissionLevel()));
        } catch (RuntimeException | LinkageError e) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ the target

    /** The player of an open menu; the card from the Lumora server arrives a moment later. */
    static final class Target implements PlayerTarget {
        private final UUID id;
        private final String name;
        final String uuid32;
        private final boolean multiplayer;
        private final int permissionLevel;
        volatile PlayerCard card;
        PlayerInteraction owner;

        Target(UUID id, String name, boolean multiplayer, int permissionLevel) {
            this.id = id;
            this.name = name;
            this.uuid32 = id.toString().replace("-", "");
            this.multiplayer = multiplayer;
            this.permissionLevel = permissionLevel;
        }

        String uuid32() {
            return uuid32;
        }

        PlayerCard card() {
            return card;
        }

        @Override public UUID id() { return id; }
        @Override public String name() { return name; }
        @Override public boolean multiplayer() { return multiplayer; }
        @Override public int permissionLevel() { return permissionLevel; }

        @Override
        public boolean lumoraUser() {
            PlayerCard c = card;
            return c != null && c.lumora;
        }

        @Override
        public boolean friend() {
            SocialClient c = owner == null ? null : owner.client();
            return c != null && c.friend(uuid32) != null;
        }

        @Override
        public boolean inYourParty() {
            Party p = party();
            return p != null && p.member(uuid32);
        }

        @Override
        public boolean partyLeader() {
            SocialClient c = owner == null ? null : owner.client();
            Party p = party();
            return p != null && c != null && c.self() != null && p.leader.equals(c.self().uuid);
        }

        private Party party() {
            SocialClient c = owner == null ? null : owner.client();
            return c == null ? null : c.party();
        }
    }

    private static Target target(PlayerTarget t) {
        return (Target) t;
    }

    // ------------------------------------------------------------------ Lumora's own entries

    private void builtins() {
        registry.builtin(PlayerMenuEntry.builder("friend", "Freund hinzufügen").icon("✚").order(10)
                .visibleWhen(t -> t instanceof Target && client() != null && !t.friend())
                .label(t -> switch (request(t)) {
                    case INCOMING -> t("Anfrage annehmen", "Accept request");
                    case OUTGOING -> t("Anfrage gesendet", "Request sent");
                    default -> t("Freund hinzufügen", "Add friend") + socialReason(target(t));
                })
                .enabledWhen(t -> socialReason(target(t)).isEmpty() && request(t) != PlayerCard.Request.OUTGOING)
                .onSelect(t -> {
                    SocialClient c = client();
                    if (request(t) == PlayerCard.Request.INCOMING) {
                        report(c.acceptFriend(target(t).uuid32), t.name() + t(" ist jetzt dein Freund", " is your friend now"));
                    } else {
                        report(c.addFriend(t.name()), t("Freundschaftsanfrage an ", "Friend request sent to ") + t.name());
                    }
                }).build());
        registry.builtin(PlayerMenuEntry.builder("party", "In Party einladen").icon("⚑").order(20)
                // always offered once Lumora Social is set up; greyed out with the reason when it cannot work right now
                .visibleWhen(t -> t instanceof Target && client() != null)
                .label(t -> {
                    PlayerCard c = target(t).card;
                    if (c != null && c.partyInvite != null) {
                        return t("Party beitreten", "Join party");
                    }
                    if (c != null && c.sameParty) {
                        return t("Aus Party entfernen", "Remove from party");
                    }
                    if (c != null && c.invited) {
                        return t("Einladung zurückziehen", "Withdraw invitation");
                    }
                    return t("In Party einladen", "Invite to party") + partyReason(target(t));
                })
                .enabledWhen(t -> partyReason(target(t)).isEmpty())
                .onSelect(t -> {
                    SocialClient s = client();
                    PlayerCard c = target(t).card;
                    if (c != null && c.partyInvite != null) {
                        report(s.acceptParty(c.partyInvite), t("Du bist in der Party von ", "You joined the party of ") + t.name());
                    } else if (c != null && (c.sameParty || c.invited)) {
                        report(s.kickFromParty(target(t).uuid32), c.sameParty ? t.name() + t(" ist nicht mehr in deiner Party", " left your party")
                                : t("Einladung zurückgezogen", "Invitation withdrawn"));
                    } else {
                        report(s.inviteToParty(target(t).uuid32), t("Party-Einladung an ", "Party invitation sent to ") + t.name());
                    }
                }).build());
        registry.builtin(PlayerMenuEntry.builder("profile", "Client-Profil").icon("☺").order(30)
                .visibleWhen(t -> t instanceof Target && client() != null)
                .label(t -> shown != null && shown.profileShown() ? t("Profil ausblenden", "Hide profile") : t("Client-Profil öffnen", "Open client profile"))
                .keepMenuOpen()
                .onSelect(t -> {
                    if (shown != null) {
                        shown.toggleProfile();
                    }
                }).build());
        registry.builtin(PlayerMenuEntry.builder("message", "Lumora-Nachricht").icon("✉").order(40)
                .visibleWhen(t -> t instanceof Target && socialOnline() && t.friend())
                .label(t -> t("Lumora-Nachricht", "Lumora message"))
                .onSelect(t -> {
                    LumoraSocial s = core.social();
                    SocialClient c = client();
                    if (s != null && c != null && c.self() != null) {
                        s.openConversation(Conv.dm(c.self().uuid, target(t).uuid32));
                    }
                }).build());
        registry.builtin(PlayerMenuEntry.builder("whisper", "Privat schreiben").icon("✎").order(50)
                .visibleWhen(PlayerTarget::multiplayer)
                .label(t -> t("Privat schreiben", "Private message"))
                .onSelect(t -> platform.extras().openChat("/msg " + t.name() + " ")).build());
        registry.builtin(PlayerMenuEntry.builder("copy", "Name kopieren").icon("⧉").order(60)
                .label(t -> t("Name kopieren", "Copy name"))
                .onSelect(t -> {
                    platform.views().setClipboard(t.name());
                    note(t("Name kopiert", "Name copied"), t.name(), NotificationType.SUCCESS);
                }).build());
    }

    /** Why friend and party entries cannot be used right now (" · reason"), or "" when they can. */
    private String socialReason(Target t) {
        if (!socialOnline()) {
            return t(" · nicht verbunden", " · not connected");
        }
        PlayerCard c = t.card;
        if (c != null && !c.lumora) {
            return t(" · nutzt Lumora nicht", " · no Lumora user");
        }
        return ""; // card still loading or unknown: the server answers the request itself
    }

    private String partyReason(Target t) {
        String r = socialReason(t);
        if (!r.isEmpty()) {
            return r;
        }
        PlayerCard c = t.card;
        if (c != null && c.partyInvite != null) {
            return "";
        }
        SocialClient s = client();
        Party p = s == null ? null : s.party();
        boolean lead = p == null || (s.self() != null && p.leader.equals(s.self().uuid));
        return lead ? "" : t(" · nur der Anführer", " · leader only"); // members of someone else's party cannot invite
    }

    private static PlayerCard.Request request(PlayerTarget t) {
        PlayerCard c = t instanceof Target x ? x.card : null;
        return c == null ? PlayerCard.Request.NONE : c.request;
    }

    // ------------------------------------------------------------------ helpers for the view

    SocialClient client() {
        LumoraSocial s = core.social();
        return s == null ? null : s.client();
    }

    boolean socialOnline() {
        SocialClient c = client();
        return c != null && c.status() == SocialClient.Status.ONLINE;
    }

    /** The second line of the player card above the ring. */
    String status(Target t) {
        if (!socialOnline()) {
            return t.multiplayer() ? t("Spieler", "Player") : t("Spieler in deiner Welt", "Player in your world");
        }
        PlayerCard c = t.card;
        if (c == null) {
            return t("Lädt …", "Loading …");
        }
        if (!c.lumora) {
            return t("Nutzt Lumora nicht", "Does not use Lumora");
        }
        if (c.sameParty) {
            return t("Lumora · in deiner Party", "Lumora · in your party");
        }
        if (c.friend) {
            return t("Lumora · Freund", "Lumora · friend");
        }
        return t("Lumora-Nutzer", "Lumora user");
    }

    String presence(Presence p) {
        return switch (p.state) {
            case OFFLINE -> "Offline";
            case LAUNCHER -> t("Im Launcher", "In the launcher");
            case MENU -> t("Im Hauptmenü", "In the main menu");
            case SINGLEPLAYER -> t("Einzelspieler", "Singleplayer");
            case MULTIPLAYER -> p.detail.isEmpty() ? t("Mehrspieler", "Multiplayer") : p.detail;
        };
    }

    String t(String german, String english) {
        return platform.translations().language().startsWith("de") ? german : english;
    }

    private <T> void report(CompletableFuture<T> f, String success) {
        f.whenComplete((r, e) -> core.taskService().onClientThread(() -> {
            if (e == null) {
                note(success, "", NotificationType.SUCCESS);
            } else {
                Throwable c = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                note(t("Fehlgeschlagen", "Failed"), c.getMessage() == null ? "" : c.getMessage(), NotificationType.ERROR);
            }
        }));
    }

    private void note(String title, String message, NotificationType type) {
        core.notificationCenter().show(Notification.builder(title).message(message).type(type).duration(4000).build());
    }
}
