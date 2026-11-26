package dev.lumora.core.social;

import dev.lumora.api.Registration;
import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.SettingsGroup;
import dev.lumora.api.event.client.ClientTickEvent;
import dev.lumora.api.event.client.WorldLeaveEvent;
import dev.lumora.api.game.WorldAccess;
import dev.lumora.api.render.NameTags;
import dev.lumora.social.Rules;
import dev.lumora.social.SocialClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Built-in module "Lumora Badge": a small Lumora star next to the names of players who play with Lumora right now,
 * above their heads and in the tab list. Which players that are, only the official Lumora server knows: this client
 * asks it about the players in the tab list and nearby (one lookup at most every {@link #LOOKUP_MIN_MS} when the list
 * changed, else every {@link #REFRESH_MS}); the server answers only yes or no and leaves out players who hide their
 * badge or blocked this player (or were blocked). Players near this one also arrive as pushes through the cosmetics
 * watch. Other clients see nothing of this.
 * <p>
 * Rendering cost: one set lookup per name tag and frame; the set is rebuilt once a second on the client thread.
 */
public final class LumoraBadgeModule implements LumoraAddon {
    public static final String ID = "lumora-badge";
    static final String K = "lumora.badge.";
    /** Font of the badge glyph ({@code assets/lumora/font/badge.json}). */
    static final String FONT = "lumora:badge";
    static final String GLYPH = "";
    /** Drawn on 1.8.9 and 1.12.2 (no custom fonts) in the nearest chat color. */
    static final String FALLBACK = "✦";
    static final int COLOR = 0xFFFFFF;
    static final int FALLBACK_COLOR = 0x7FA8FF;
    static final long LOOKUP_MIN_MS = 5_000;
    static final long REFRESH_MS = 30_000;
    /** Nearby players asked about first (the rest of the lookup is filled from the tab list). */
    static final int NEARBY = 64;

    private final Supplier<SocialClient> client;
    private final Supplier<WorldAccess> world;
    BooleanSetting aboveHead;
    BooleanSetting tabList;
    BooleanSetting own;
    BooleanSetting share;
    private AddonContext context;
    /** The name tag decorator, registered only while there is a badge to draw (no cost for the name tags otherwise). */
    private Registration registration;
    /** Players to draw the badge for (render thread reads, client thread replaces). */
    private volatile Set<UUID> shown = Set.of();
    private volatile UUID self;
    private Set<String> asked = Set.of();
    private long lastLookup;
    private boolean lookupRunning;
    private SocialClient synced;
    private int ticks;

    public LumoraBadgeModule(Supplier<SocialClient> client, Supplier<WorldAccess> world) {
        this.client = client;
        this.world = world;
    }

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        aboveHead = s.toggle("above_head", true).name(K + "setting.above_head")
                .description(K + "setting.above_head.description").section(K + "section.show");
        tabList = s.toggle("tab_list", true).name(K + "setting.tab_list").description(K + "setting.tab_list.description");
        own = s.toggle("own", false).name(K + "setting.own").description(K + "setting.own.description");
        share = s.toggle("share", true).name(K + "setting.share").description(K + "setting.share.description")
                .section(K + "section.privacy");
        share.onChange(v -> synced = null);
    }

    @Override
    public void onEnable(AddonContext context) {
        this.context = context;
        context.events().subscribe(ClientTickEvent.class, e -> tick());
        context.events().subscribe(WorldLeaveEvent.class, e -> reset());
    }

    @Override
    public void onDisable(AddonContext context) {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        this.context = null;
        reset();
    }

    /** Render thread: the badge for {@code player} at {@code place}, if any. */
    Optional<NameTags.Icon> icon(UUID player, NameTags.Place place) {
        if (place == NameTags.Place.WORLD ? !aboveHead.get() : !tabList.get()) {
            return Optional.empty();
        }
        boolean mine = player.equals(self);
        if (mine ? !own.get() : !shown.contains(player)) {
            return Optional.empty();
        }
        return Optional.of(BADGE);
    }

    private static final NameTags.Icon BADGE = new NameTags.Icon(FONT, GLYPH, COLOR, FALLBACK, "");

    /** Once per client tick; the real work once a second. */
    void tick() {
        if (++ticks < 20) {
            return;
        }
        ticks = 0;
        SocialClient c = client.get();
        if (c == null || c.status() != SocialClient.Status.ONLINE) {
            if (!shown.isEmpty()) {
                shown = Set.of();
            }
            asked = Set.of();
            synced = null;
            updateRegistration();
            return;
        }
        self = c.self() == null ? null : uuid(c.self().uuid);
        // the own choice "others see my badge" goes to the server after every login and on change
        if (synced != c) {
            synced = c;
            if (c.showBadge() != share.get()) {
                c.setShowBadge(share.get()).exceptionally(t -> null);
            }
        }
        Set<String> players = players();
        long now = System.currentTimeMillis();
        boolean changed = !players.equals(asked);
        if (!lookupRunning && !players.isEmpty()
                && (changed && now - lastLookup >= LOOKUP_MIN_MS || now - lastLookup >= REFRESH_MS)) {
            asked = players;
            lastLookup = now;
            lookupRunning = true;
            c.lookupLumora(players).whenComplete((r, t) -> lookupRunning = false);
        }
        Set<UUID> next = new HashSet<>();
        for (String u : players) {
            if (c.isLumoraPlayer(u)) {
                next.add(uuid(u));
            }
        }
        shown = next.isEmpty() ? Set.of() : Collections.unmodifiableSet(next);
        updateRegistration();
    }

    /** Registers the decorator while a badge is to be drawn, removes it otherwise. */
    private void updateRegistration() {
        boolean need = !shown.isEmpty() || own.get() && self != null;
        if (need && registration == null && context != null) {
            registration = context.nameTags().register(this::icon);
        } else if (!need && registration != null) {
            registration.unregister();
            registration = null;
        }
    }

    /** Players nearby first, then the tab list; dashless UUIDs, at most {@link Rules#LUMORA_LOOKUP_MAX}. */
    private Set<String> players() {
        WorldAccess w = world.get();
        if (w == null) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        List<UUID> ids = new ArrayList<>();
        for (WorldAccess.PlayerInfo p : w.players(NEARBY)) {
            ids.add(p.id());
        }
        for (WorldAccess.TabEntry e : w.tabList()) {
            ids.add(e.id());
        }
        UUID me = self;
        for (UUID id : ids) {
            if (id == null || id.equals(me) || id.version() != 4) {
                continue; // NPCs of servers use other UUID versions: no real players
            }
            String u = Rules.uuid(id.toString());
            if (u != null) {
                out.add(u);
                if (out.size() >= Rules.LUMORA_LOOKUP_MAX) {
                    break;
                }
            }
        }
        return out;
    }

    void reset() {
        shown = Set.of();
        asked = Set.of();
        lastLookup = 0;
    }

    /** Players shown with the badge now (diagnostics, smoke test). */
    public int shownCount() {
        return shown.size();
    }

    static UUID uuid(String dashless) {
        String u = Rules.uuid(dashless);
        if (u == null) {
            return null;
        }
        return UUID.fromString(u.substring(0, 8) + "-" + u.substring(8, 12) + "-" + u.substring(12, 16) + "-"
                + u.substring(16, 20) + "-" + u.substring(20));
    }
}
