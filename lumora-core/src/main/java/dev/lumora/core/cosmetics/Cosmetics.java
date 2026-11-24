package dev.lumora.core.cosmetics;

import dev.lumora.core.cosmetics.anim.Animation;
import dev.lumora.core.cosmetics.anim.AnimationController;
import dev.lumora.core.cosmetics.anim.Molang;
import dev.lumora.core.cosmetics.anim.PoseBuffer;
import dev.lumora.core.cosmetics.model.CosmeticMesh;
import dev.lumora.core.cosmetics.model.MeshBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The cosmetics and emote runtime ("AnimationManager"): per drawn player it keeps the animation controllers (emote
 * layer over Minecraft's motion, per cosmetic a state controller plus an overlay controller) and builds a
 * {@link PlayerFrame}. Work happens only for players that are actually drawn, in the frame they are drawn; state of
 * players not drawn for a while is dropped.
 * <p>
 * Synchronisation with other players is not part of this version ("local only"): other players come from a
 * {@link RemoteSource}, which is empty, so players without data are drawn exactly as Minecraft draws them. The local
 * preview has its own state and never touches what is equipped.
 * <p>
 * Called from the render thread (frames) and the client thread (commands); both are the same thread in Minecraft.
 */
public final class Cosmetics {
    /** Cross-fade between emotes and from Minecraft's motion into an emote (seconds). */
    static final double EMOTE_FADE_IN = 0.2;
    static final double EMOTE_FADE_OUT = 0.3;
    static final double STATE_FADE = 0.25;
    /** States of players not drawn for this long are dropped (ms). */
    static final long FORGET_MS = 10_000;
    private static final String[] EMOTE_BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root"};

    /** Cosmetics and emotes of other players; the local-only version knows none. */
    public interface RemoteSource {
        RemoteSource NONE = new RemoteSource() {
        };

        default Collection<String> loadout(UUID player) {
            return List.of();
        }

        default EmoteDefinition emote(UUID player) {
            return null;
        }

        default double emoteStart(UUID player) {
            return 0;
        }
    }

    /** Uploads an own cosmetic's texture; returns the texture id or {@code null} (render thread). */
    public interface TextureUploader {
        String upload(String key, int width, int height, int[] argb);
    }

    private static volatile Cosmetics instance;

    private final CosmeticRegistry registry;
    private final CosmeticsState state;
    private RemoteSource remote = RemoteSource.NONE;
    private TextureUploader uploader;
    private final Map<String, String> uploaded = new HashMap<>();
    private final Set<String> failedUploads = new java.util.HashSet<>();
    private final PlayerAnimState self = new PlayerAnimState();
    private final PlayerAnimState preview = new PlayerAnimState();
    private final Map<UUID, PlayerAnimState> others = new HashMap<>();
    private List<CosmeticDefinition> equipped = List.of();
    private final Set<String> previewSet = new LinkedHashSet<>();
    private List<CosmeticDefinition> previewDefs = List.of();
    private boolean showOwn = true;
    private boolean animate = true;
    private boolean stopOnMove = true;
    private Consumer<Animation.Event> events = e -> { };
    private long lastCleanup;
    /** Frames built (diagnostics, smoke test). */
    private volatile int frames;

    public Cosmetics(CosmeticRegistry registry, CosmeticsState state) {
        this.registry = registry;
        this.state = state;
        refreshEquipped();
    }

    /** The running instance, {@code null} while the module is off. */
    public static Cosmetics get() {
        return instance;
    }

    public static void set(Cosmetics c) {
        instance = c;
    }

    public CosmeticRegistry registry() {
        return registry;
    }

    public CosmeticsState state() {
        return state;
    }

    public void remote(RemoteSource source) {
        this.remote = source == null ? RemoteSource.NONE : source;
    }

    public void uploader(TextureUploader u) {
        this.uploader = u;
    }

    public void events(Consumer<Animation.Event> sink) {
        this.events = sink == null ? e -> { } : sink;
    }

    public void options(boolean showOwn, boolean animate, boolean stopOnMove) {
        this.showOwn = showOwn;
        this.animate = animate;
        this.stopOnMove = stopOnMove;
    }

    public int frames() {
        return frames;
    }

    static double now() {
        return System.nanoTime() / 1e9;
    }

    // --- equipping

    public void equip(CosmeticDefinition d) {
        if (d != null && d.unlocked()) {
            state.equip(d, registry);
            refreshEquipped();
        }
    }

    public void unequip(String id) {
        state.unequip(id);
        refreshEquipped();
    }

    public void setEquipped(Collection<String> ids) {
        state.setEquipped(ids);
        refreshEquipped();
    }

    private void refreshEquipped() {
        List<CosmeticDefinition> list = new ArrayList<>();
        for (String id : state.equipped()) {
            CosmeticDefinition d = registry.cosmetic(id);
            if (d != null) {
                list.add(d);
            }
        }
        equipped = List.copyOf(list);
    }

    public List<CosmeticDefinition> equippedDefinitions() {
        return equipped;
    }

    // --- preview (local only)

    /** What the preview shows (starts as what is worn). */
    public Set<String> previewSet() {
        return new LinkedHashSet<>(previewSet);
    }

    public void setPreview(Collection<String> ids) {
        previewSet.clear();
        previewSet.addAll(ids);
        List<CosmeticDefinition> list = new ArrayList<>();
        for (String id : previewSet) {
            CosmeticDefinition d = registry.cosmetic(id);
            if (d != null) {
                list.add(d);
            }
        }
        previewDefs = List.copyOf(list);
    }

    /** Adds or removes one cosmetic in the preview; others of the same slot leave it. */
    public void togglePreview(CosmeticDefinition d) {
        Set<String> next = new LinkedHashSet<>(previewSet);
        if (!next.remove(d.id())) {
            next.removeIf(id -> {
                CosmeticDefinition o = registry.cosmetic(id);
                return o != null && o.slot().equals(d.slot());
            });
            next.add(d.id());
        }
        setPreview(next);
    }

    public void previewEmote(EmoteDefinition e) {
        if (e == null) {
            preview.stopEmote(now());
        } else {
            preview.playEmote(e, now(), true);
        }
    }

    public EmoteDefinition previewEmote() {
        return preview.emoteDef;
    }

    /**
     * {@code true} while the preview emote still plays (not yet at its end). Ended emotes are noticed when the preview
     * is drawn, so the menu can start them again for a looping preview.
     */
    public boolean previewEmoteRunning() {
        if (preview.emote.current() == null) {
            return false;
        }
        preview.emote.update(now(), EMOTE_FADE_OUT, null);
        return preview.emote.current() != null;
    }

    // --- own emotes

    /** Plays an emote on the own player (restarts if it is already playing). */
    public boolean playEmote(EmoteDefinition e) {
        if (e == null || !e.unlocked()) {
            return false;
        }
        self.playEmote(e, now(), true);
        return true;
    }

    public void stopEmote() {
        self.stopEmote(now());
    }

    /** The emote the own player plays, {@code null} if none (also while it fades out). */
    public EmoteDefinition currentEmote() {
        return self.emote.current() == null ? null : self.emoteDef;
    }

    /** {@code true} while an own emote plays or fades out. */
    public boolean emoting() {
        return self.emote.active(now());
    }

    /** Leaving a world: emotes end, other players are forgotten. */
    public void reset() {
        self.emote.reset();
        self.emoteDef = null;
        others.clear();
    }

    // --- frames

    /**
     * The frame for a player about to be drawn, or {@code null} if there is nothing to draw or pose (then the
     * adapter does nothing at all).
     */
    public PlayerFrame frame(PlayerInput in) {
        if (in == null || in.id == null && !in.self) {
            return null;
        }
        long ms = System.currentTimeMillis();
        double now = now();
        PlayerAnimState st;
        Collection<CosmeticDefinition> loadout;
        if (in.preview) {
            st = preview;
            loadout = previewDefs;
        } else if (in.self) {
            st = self;
            loadout = showOwn ? equipped : List.of();
        } else {
            Collection<String> ids = remote.loadout(in.id);
            EmoteDefinition e = remote.emote(in.id);
            PlayerAnimState existing = others.get(in.id);
            if (ids.isEmpty() && e == null && existing == null) {
                return null;
            }
            st = existing != null ? existing : new PlayerAnimState();
            others.put(in.id, st);
            if (e != st.emoteDef) {
                if (e == null) {
                    st.stopEmote(now);
                } else {
                    st.playEmote(e, remote.emoteStart(in.id) > 0 ? remote.emoteStart(in.id) : now, false);
                }
            }
            List<CosmeticDefinition> list = new ArrayList<>();
            for (String id : ids) {
                CosmeticDefinition d = registry.cosmetic(id);
                if (d != null) {
                    list.add(d);
                }
            }
            loadout = list;
        }
        st.lastSeen = ms;
        cleanup(ms);
        st.fillScope(in);
        // emote layer
        st.emote.update(now, EMOTE_FADE_OUT, in.self && !in.preview ? events : null);
        if (in.self && !in.preview && stopOnMove && st.emoteDef != null && st.emoteDef.stopOnMove()
                && st.emote.current() != null && moving(in) && now - st.emote.currentStart() > 0.3) {
            st.stopEmote(now);
        }
        if (st.emote.active(now)) {
            st.emoteBuffer.clear();
            st.emote.sample(now, st.emoteBuffer, 1);
            st.frame.pose.from(st.emoteBuffer);
        } else {
            st.frame.pose.clear();
            if (st.emote.current() == null) {
                st.emoteDef = null;
            }
        }
        // cosmetics
        st.frame.clearItems();
        String playerState = in.state();
        boolean emoting = st.emote.current() != null;
        for (CosmeticDefinition d : loadout) {
            String texture = texture(d);
            if (texture == null) {
                continue;
            }
            CosmeticInstance c = st.cosmetics.get(d.id());
            if (c == null || c.def != d) {
                c = new CosmeticInstance(d, st.scope);
                st.cosmetics.put(d.id(), c);
            }
            c.used = ms;
            CosmeticMesh mesh = c.build(now, playerState, emoting, animate);
            st.frame.add(d.id(), mesh, texture, d.render());
        }
        if (st.cosmetics.size() > loadout.size()) {
            st.cosmetics.values().removeIf(c -> c.used != ms);
        }
        frames++;
        return st.frame.isEmpty() ? null : st.frame;
    }

    private static boolean moving(PlayerInput in) {
        return in.groundSpeed > 0.06f || !in.onGround && in.verticalSpeed > 0.1f || in.swimming || in.gliding;
    }

    private String texture(CosmeticDefinition d) {
        if (d.texture() != null) {
            return d.texture();
        }
        String done = uploaded.get(d.id());
        if (done != null || uploader == null || d.pixels() == null || failedUploads.contains(d.id())) {
            return done;
        }
        String key = d.id().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        String id = uploader.upload(key, d.pixelWidth(), d.pixelHeight(), d.pixels());
        if (id == null) {
            failedUploads.add(d.id());
        } else {
            uploaded.put(d.id(), id);
        }
        return id;
    }

    private void cleanup(long ms) {
        if (ms - lastCleanup < 2000) {
            return;
        }
        lastCleanup = ms;
        Iterator<PlayerAnimState> it = others.values().iterator();
        while (it.hasNext()) {
            if (ms - it.next().lastSeen > FORGET_MS) {
                it.remove();
            }
        }
    }

    /** Number of other players with state (diagnostics; 0 in the local-only version). */
    public int trackedPlayers() {
        return others.size();
    }

    // --- per player

    static final class PlayerAnimState {
        final Molang.Scope scope = new Molang.Scope();
        final AnimationController emote = new AnimationController(scope);
        final PoseBuffer emoteBuffer = new PoseBuffer(EMOTE_BONES);
        final Map<String, CosmeticInstance> cosmetics = new HashMap<>();
        final PlayerFrame frame = new PlayerFrame();
        EmoteDefinition emoteDef;
        long lastSeen;
        private double lastAge = Double.NaN;

        void playEmote(EmoteDefinition e, double start, boolean restart) {
            emoteDef = e;
            emote.play(e.animation(), start, EMOTE_FADE_IN, restart);
        }

        void stopEmote(double now) {
            emote.stop(now, EMOTE_FADE_OUT);
        }

        void fillScope(PlayerInput in) {
            scope.set(Molang.Query.LIFE_TIME, in.age / 20.0);
            scope.set(Molang.Query.DELTA_TIME, Double.isNaN(lastAge) ? 0 : Math.max(0, (in.age - lastAge) / 20.0));
            lastAge = in.age;
            scope.set(Molang.Query.GROUND_SPEED, in.groundSpeed * 20); // blocks per second, as Bedrock
            scope.set(Molang.Query.VERTICAL_SPEED, in.verticalSpeed * 20);
            scope.set(Molang.Query.IS_ON_GROUND, in.onGround ? 1 : 0);
            scope.set(Molang.Query.IS_SNEAKING, in.sneaking ? 1 : 0);
            scope.set(Molang.Query.IS_SPRINTING, in.sprinting ? 1 : 0);
            scope.set(Molang.Query.IS_SWIMMING, in.swimming ? 1 : 0);
            scope.set(Molang.Query.IS_GLIDING, in.gliding ? 1 : 0);
            scope.set(Molang.Query.IS_MOVING, in.groundSpeed > 0.02f || !in.onGround ? 1 : 0);
            scope.set(Molang.Query.HEAD_X_ROTATION, in.headPitch);
            scope.set(Molang.Query.HEAD_Y_ROTATION, in.headYaw);
            scope.set(Molang.Query.BODY_Y_ROTATION, in.bodyYaw);
            scope.set(Molang.Query.LIMB_SWING, in.limbSwing);
            scope.set(Molang.Query.LIMB_SWING_AMOUNT, in.limbAmount);
            scope.set(Molang.Query.IS_FIRST_PERSON, in.firstPerson ? 1 : 0);
        }
    }

    /** One worn cosmetic of one player: mesh builder and its animation controllers. */
    static final class CosmeticInstance {
        final CosmeticDefinition def;
        final MeshBuilder builder;
        final PoseBuffer pose;
        final AnimationController state;
        final AnimationController overlay;
        long used;

        CosmeticInstance(CosmeticDefinition def, Molang.Scope scope) {
            this.def = def;
            this.builder = new MeshBuilder(def.model());
            this.pose = new PoseBuffer(def.model().boneNames());
            this.state = new AnimationController(scope);
            this.overlay = new AnimationController(scope);
        }

        CosmeticMesh build(double now, String playerState, boolean emoting, boolean animate) {
            if (!animate || !def.animated()) {
                return builder.build(null);
            }
            Animation a = emoting ? def.animations().get("emote") : null;
            String s = playerState;
            while (a == null) {
                a = def.animations().get(s);
                if (a != null || s.equals("idle")) {
                    break;
                }
                s = PlayerInput.fallback(s);
            }
            if (a != null) {
                state.play(a, now, STATE_FADE, false);
            } else {
                state.stop(now, STATE_FADE);
            }
            state.update(now, STATE_FADE, null);
            if (def.overlay() != null) {
                overlay.play(def.overlay(), now, 0, false);
                overlay.update(now, 0, null);
            }
            pose.clear();
            state.sample(now, pose, 1);
            overlay.sampleAdditive(now, pose, 1);
            return builder.build(pose);
        }
    }
}
