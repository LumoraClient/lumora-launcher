package dev.lumora.core.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.lumora.api.game.WorldAccess;
import dev.lumora.social.Rules;
import dev.lumora.social.SocialClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Shows the cosmetics and emotes of other Lumora players through the Lumora Social server (the address set in the
 * launcher under "Friends"): this client publishes what it wears (built-in cosmetics with variant and offset, with
 * the chosen visibility), tells the server which players are near it and gets their loadouts, then only changes;
 * an own emote goes to the players nearby, nothing is stored. Players without Lumora or on another social server
 * simply have no data and are drawn as usual. The local preview never goes through here.
 * <p>
 * Network use: the own loadout when it changes (and once after a login), the list of nearby players when it
 * changes (checked every second, at most every two seconds), one message per emote start or stop.
 */
public final class CosmeticsSync implements Cosmetics.RemoteSource, SocialClient.CosmeticsListener {
    /** Players further away do not get an emote (blocks). */
    static final double EMOTE_RANGE = 48;
    /** Nearest players watched at most. */
    static final int WATCH_LIMIT = 64;
    static final long WATCH_MIN_MS = 2000;

    private final Cosmetics cosmetics;
    private final Supplier<SocialClient> client;
    private final Supplier<WorldAccess> world;
    private final Map<UUID, List<Cosmetics.Worn>> loadouts = new ConcurrentHashMap<>();
    private final Map<UUID, RemoteEmote> emotes = new ConcurrentHashMap<>();
    private SocialClient listening;
    private Set<String> watched;
    private long lastWatch;
    private int ticks;
    private volatile String scope = "all";
    private volatile boolean enabled = true;

    record RemoteEmote(EmoteDefinition emote, double start) {
    }

    public CosmeticsSync(Cosmetics cosmetics, Supplier<SocialClient> client, Supplier<WorldAccess> world) {
        this.cosmetics = cosmetics;
        this.client = client;
        this.world = world;
    }

    /** Who sees the own cosmetics and emotes: "all", "friends" or "none"; {@code enabled} false sends nothing. */
    public void configure(boolean enabled, String scope) {
        this.enabled = enabled;
        this.scope = Rules.statusScope(scope);
        ticks = 19; // publish with the next tick
    }

    // --- RemoteSource (render thread)

    @Override
    public Collection<Cosmetics.Worn> loadout(UUID player) {
        List<Cosmetics.Worn> l = loadouts.get(player);
        return l == null ? List.of() : l;
    }

    @Override
    public EmoteDefinition emote(UUID player) {
        RemoteEmote e = emotes.get(player);
        return e == null ? null : e.emote;
    }

    @Override
    public double emoteStart(UUID player) {
        RemoteEmote e = emotes.get(player);
        return e == null ? 0 : e.start;
    }

    // --- pushes from the server (WebSocket thread)

    @Override
    public void loadout(String uuid, JsonArray loadout) {
        UUID id = uuid(uuid);
        if (id == null) {
            return;
        }
        List<Cosmetics.Worn> l = parse(loadout);
        if (l.isEmpty()) {
            loadouts.remove(id);
        } else {
            loadouts.put(id, l);
        }
    }

    @Override
    public void emote(String uuid, String emoteId) {
        UUID id = uuid(uuid);
        if (id == null) {
            return;
        }
        EmoteDefinition e = emoteId == null || emoteId.isEmpty() ? null : cosmetics.registry().emote(emoteId);
        if (e == null || e.custom()) {
            emotes.remove(id);
        } else {
            emotes.put(id, new RemoteEmote(e, Cosmetics.now()));
        }
    }

    // --- client thread

    /** Every client tick. */
    public void tick() {
        if (++ticks < 20) {
            return;
        }
        ticks = 0;
        SocialClient c = client.get();
        if (c == null || c.status() != SocialClient.Status.ONLINE) {
            watched = null;
            if (!loadouts.isEmpty() || !emotes.isEmpty()) {
                loadouts.clear();
                emotes.clear();
            }
            return;
        }
        if (c != listening) {
            c.setCosmeticsListener(this);
            listening = c;
            watched = null;
        }
        c.setCosmetics(enabled ? ownLoadout() : new JsonArray(), enabled ? scope : "none");
        watchNearby(c);
    }

    private void watchNearby(SocialClient c) {
        WorldAccess w = world.get();
        Set<String> near = new TreeSet<>();
        if (w != null) {
            for (WorldAccess.PlayerInfo p : w.players(WATCH_LIMIT)) {
                String u = Rules.uuid(p.id().toString());
                if (u != null) {
                    near.add(u);
                }
            }
        }
        long now = System.currentTimeMillis();
        if (near.equals(watched) || now - lastWatch < WATCH_MIN_MS) {
            return;
        }
        watched = near;
        lastWatch = now;
        // forget players that left; their emotes too
        loadouts.keySet().removeIf(id -> !near.contains(Rules.uuid(id.toString())));
        emotes.keySet().removeIf(id -> !near.contains(Rules.uuid(id.toString())));
        c.watchCosmetics(near).thenAccept(found -> {
            for (Map.Entry<String, JsonArray> e : found.entrySet()) {
                loadout(e.getKey(), e.getValue());
            }
        }).exceptionally(t -> null);
    }

    /** The own emote started ({@code e}) or stopped ({@code null}): tell the players nearby. */
    public void ownEmote(EmoteDefinition e) {
        SocialClient c = client.get();
        if (!enabled || c == null || c.status() != SocialClient.Status.ONLINE || e != null && e.custom()) {
            return;
        }
        List<String> targets = new ArrayList<>();
        WorldAccess w = world.get();
        if (w != null) {
            WorldAccess.ViewPoint me = w.viewPoint().orElse(null);
            for (WorldAccess.PlayerInfo p : w.players(WATCH_LIMIT)) {
                if (me == null || dist2(me, p) <= EMOTE_RANGE * EMOTE_RANGE) {
                    targets.add(p.id().toString());
                }
            }
        }
        c.sendEmote(e == null ? "" : e.id(), targets);
    }

    private static double dist2(WorldAccess.ViewPoint a, WorldAccess.PlayerInfo b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Leaving a world: forget everything received. */
    public void reset() {
        loadouts.clear();
        emotes.clear();
        watched = null;
    }

    /** Players with data (diagnostics). */
    public int players() {
        return loadouts.size();
    }

    // --- conversion

    /** Built-in cosmetics worn now with their options, as the server expects them. */
    JsonArray ownLoadout() {
        JsonArray a = new JsonArray();
        for (CosmeticDefinition d : cosmetics.equippedDefinitions()) {
            if (d.custom()) {
                continue; // own files: other players do not have them
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", d.id());
            CosmeticsState.Options opt = cosmetics.state().options(d.id());
            CosmeticDefinition.Variant v = d.variant(opt.variant());
            if (v != null && !v.id().equals(d.variants().get(0).id())) {
                o.addProperty("variant", v.id());
            }
            if (opt.hasOffset()) {
                JsonArray off = new JsonArray();
                off.add(new com.google.gson.JsonPrimitive(opt.x()));
                off.add(new com.google.gson.JsonPrimitive(opt.y()));
                off.add(new com.google.gson.JsonPrimitive(opt.z()));
                o.add("offset", off);
            }
            a.add(o);
        }
        return a;
    }

    static List<Cosmetics.Worn> parse(JsonArray loadout) {
        List<Cosmetics.Worn> out = new ArrayList<>();
        for (JsonElement e : Rules.cosmetics(loadout)) {
            JsonObject o = e.getAsJsonObject();
            String variant = o.has("variant") ? o.get("variant").getAsString() : null;
            float[] off = new float[3];
            if (o.has("offset")) {
                JsonArray a = o.getAsJsonArray("offset");
                for (int i = 0; i < 3; i++) {
                    off[i] = a.get(i).getAsFloat();
                }
            }
            out.add(new Cosmetics.Worn(o.get("id").getAsString(),
                    new CosmeticsState.Options(variant, 0, 0, 0).withOffset(off[0], off[1], off[2])));
        }
        return List.copyOf(out);
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
