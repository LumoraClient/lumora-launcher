package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.DecimalSetting;
import dev.lumora.api.config.IntegerSetting;
import dev.lumora.api.config.SettingsGroup;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Built-in module "Hidden Armor": the armor a player wears is drawn with an opacity per piece (helmet, chestplate,
 * leggings, boots; 0 % = not drawn, 100 % = as in vanilla), for the own player only or for every player. When the
 * player is hit, the armor shows fully again for a few seconds and, optionally, fades back afterwards.
 * Only this client's view changes; what other players see stays the same.
 * <p>
 * The version adapters ask {@link #alpha} once per armor piece and frame, right before Minecraft draws it: 0 skips the
 * piece, 1 draws it as usual, anything in between draws it translucent (the enchantment glint only at 1, it cannot be
 * faded). Cost: a few field reads per piece, nothing while the module is off. Render thread only.
 */
public final class HiddenArmorModule implements LumoraAddon {
    public static final String ID = "lumora-hiddenarmor";
    static final String K = "lumora.hiddenarmor.";
    public static final int HEAD = 0;
    public static final int CHEST = 1;
    public static final int LEGS = 2;
    public static final int FEET = 3;
    /** Opacities this close to 0 or 1 count as hidden or fully visible (no translucent drawing for a rounding rest). */
    static final float EPSILON = 0.004f;

    private static volatile HiddenArmorModule active;

    final IntegerSetting[] opacity = new IntegerSetting[4];
    BooleanSetting sameForAll;
    ChoiceSetting appliesTo;
    BooleanSetting showOnHit;
    DecimalSetting hitSeconds;
    BooleanSetting fade;
    DecimalSetting fadeSeconds;
    /** Last time each player (entity id) was seen hit, System.nanoTime. */
    private final Map<Integer, Long> lastHit = new HashMap<>();
    private boolean linking;
    /** Pieces drawn other than in vanilla so far (diagnostics, smoke test). */
    private int changed;

    /** The running module, {@code null} while switched off. */
    public static HiddenArmorModule active() {
        return active;
    }

    /**
     * Opacity for drawing one armor piece of a player this frame: 0 = skip it, 1 = draw it as usual, in between =
     * translucent. Players other than the own one keep their armor while "Applies to" is "Only me"; entities that are
     * no players are not asked at all.
     *
     * @param slot     {@link #HEAD}, {@link #CHEST}, {@link #LEGS} or {@link #FEET}
     * @param self     the player is this client's own player
     * @param entityId the player's entity id (to remember hits per player)
     * @param hurt     the player shows the red hurt flash right now
     */
    public static float alpha(int slot, boolean self, int entityId, boolean hurt) {
        HiddenArmorModule m = active;
        if (m == null) {
            return 1f;
        }
        float a = m.opacityFor(slot, self, entityId, hurt, System.nanoTime());
        if (a < 1f) {
            m.changed++;
        }
        return a;
    }

    float opacityFor(int slot, boolean self, int entityId, boolean hurt, long now) {
        if (slot < HEAD || slot > FEET || !self && !"everyone".equals(appliesTo.get())) {
            return 1f;
        }
        float target = opacity[slot].get() / 100f;
        if (target >= 1f - EPSILON) {
            return 1f;
        }
        if (showOnHit.get()) {
            if (hurt) {
                remember(entityId, now);
            }
            Long hit = lastHit.get(entityId);
            if (hit != null) {
                double since = (now - hit) / 1e9;
                double hold = hitSeconds.get();
                double fadeTime = fade.get() ? fadeSeconds.get() : 0;
                if (since < hold) {
                    return 1f;
                }
                if (since < hold + fadeTime) {
                    float t = (float) ((since - hold) / fadeTime);
                    return clamp(1f + (target - 1f) * t);
                }
            }
        }
        return clamp(target);
    }

    private static float clamp(float a) {
        return a <= EPSILON ? 0f : a >= 1f - EPSILON ? 1f : a;
    }

    private void remember(int entityId, long now) {
        if (lastHit.size() > 64) {
            // players hit long ago no longer matter
            long keep = (long) ((hitSeconds.get() + fadeSeconds.get() + 1) * 1e9);
            for (Iterator<Long> it = lastHit.values().iterator(); it.hasNext(); ) {
                if (now - it.next() > keep) {
                    it.remove();
                }
            }
        }
        lastHit.put(entityId, now);
    }

    /** Armor pieces drawn hidden or translucent so far (smoke test). */
    public int changedCount() {
        return changed;
    }

    /** Sets the opacity of one piece in percent (smoke test). */
    public void setOpacity(int slot, int percent) {
        opacity[slot].set(percent);
    }

    /** "self" or "everyone" (smoke test). */
    public void setAppliesTo(String value) {
        appliesTo.select(value);
    }

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        String[] keys = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < 4; i++) {
            IntegerSetting o = s.slider(keys[i], 0, 0, 100).unit(" %").name(K + "setting." + keys[i])
                    .description(K + "setting.opacity.description");
            if (i == 0) {
                o.section(K + "section.armor");
            }
            opacity[i] = o;
        }
        sameForAll = s.toggle("same_for_all", false).name(K + "setting.same_for_all")
                .description(K + "setting.same_for_all.description");
        appliesTo = s.dropdown("applies_to", "self", "self", "everyone").name(K + "setting.applies_to")
                .description(K + "setting.applies_to.description");
        showOnHit = s.toggle("show_on_hit", true).name(K + "setting.show_on_hit")
                .description(K + "setting.show_on_hit.description").section(K + "section.hit");
        hitSeconds = s.slider("hit_seconds", 3.0, 0.5, 10.0, 0.5).unit(" s").name(K + "setting.hit_seconds")
                .description(K + "setting.hit_seconds.description").requires(showOnHit);
        fade = s.toggle("fade", true).name(K + "setting.fade").description(K + "setting.fade.description")
                .requires(showOnHit);
        fadeSeconds = s.slider("fade_seconds", 1.0, 0.2, 3.0, 0.1).unit(" s").name(K + "setting.fade_seconds")
                .description(K + "setting.fade_seconds.description").visibleWhen(() -> showOnHit.get() && fade.get());
        // "same for all pieces": moving one slider moves the others
        for (IntegerSetting o : opacity) {
            o.onChange(v -> link(o));
        }
        sameForAll.onChange(on -> {
            if (Boolean.TRUE.equals(on)) {
                link(opacity[0]);
            }
        });
    }

    private void link(IntegerSetting source) {
        if (linking || sameForAll == null || !sameForAll.get()) {
            return;
        }
        linking = true;
        try {
            for (IntegerSetting o : opacity) {
                if (o != source && !o.get().equals(source.get())) {
                    o.set(source.get());
                }
            }
        } finally {
            linking = false;
        }
    }

    @Override
    public void onEnable(AddonContext context) {
        active = this;
    }

    @Override
    public void onDisable(AddonContext context) {
        if (active == this) {
            active = null;
        }
        lastHit.clear();
    }

    /** Switches the module on or off without an addon context (tests). */
    void setActive(boolean on) {
        if (on) {
            active = this;
        } else if (active == this) {
            active = null;
        }
    }
}
