package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.SettingsGroup;

/**
 * Built-in module "Off Hand": hides the off hand or the main hand in first person, or only a shield or a totem in it.
 * Only this client's own view changes; what other players see stays the same.
 * <p>
 * The version adapters ask {@link #hide} once per hand and frame, right before Minecraft draws that hand with its item
 * (first person only), and skip the hand when it says so. Cost: a few field reads per hand and frame, nothing while
 * the module is off.
 */
public final class OffHandModule implements LumoraAddon {
    public static final String ID = "lumora-offhand";
    /** The off hand exists since Minecraft 1.9. */
    public static final String MIN_MINECRAFT = "1.9";
    static final String K = "lumora.offhand.";

    private static volatile OffHandModule active;

    BooleanSetting offShow;
    BooleanSetting offShield;
    BooleanSetting offTotem;
    BooleanSetting mainShow;
    BooleanSetting mainShield;
    BooleanSetting mainTotem;
    /** Hands skipped so far (diagnostics, smoke test; render thread only). */
    private int hidden;

    /** The running module, {@code null} while switched off. */
    public static OffHandModule active() {
        return active;
    }

    /**
     * Whether the version adapter skips drawing this hand in first person this frame.
     *
     * @param offHand {@code true} for the off hand, {@code false} for the main hand
     * @param shield  the hand holds a shield
     * @param totem   the hand holds a totem of undying
     */
    public static boolean hide(boolean offHand, boolean shield, boolean totem) {
        OffHandModule m = active;
        if (m == null || !m.hides(offHand, shield, totem)) {
            return false;
        }
        m.hidden++;
        return true;
    }

    boolean hides(boolean offHand, boolean shield, boolean totem) {
        BooleanSetting show = offHand ? offShow : mainShow;
        if (!show.get()) {
            return true; // the whole hand is off: the item settings do not matter
        }
        return shield && (offHand ? offShield : mainShield).get() || totem && (offHand ? offTotem : mainTotem).get();
    }

    /** Hands skipped so far (smoke test). */
    public int hiddenCount() {
        return hidden;
    }

    /** Sets one setting by its key (smoke test). */
    public void set(String key, boolean value) {
        BooleanSetting s = switch (key) {
            case "offhand_show" -> offShow;
            case "offhand_shield" -> offShield;
            case "offhand_totem" -> offTotem;
            case "mainhand_show" -> mainShow;
            case "mainhand_shield" -> mainShield;
            case "mainhand_totem" -> mainTotem;
            default -> throw new IllegalArgumentException(key);
        };
        s.set(value);
    }

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        offShow = s.toggle("offhand_show", true).name(K + "setting.offhand_show")
                .description(K + "setting.offhand_show.description").section(K + "section.offhand");
        offShield = s.toggle("offhand_shield", false).name(K + "setting.offhand_shield")
                .description(K + "setting.offhand_shield.description").visibleWhen(() -> offShow.get());
        offTotem = s.toggle("offhand_totem", false).name(K + "setting.offhand_totem")
                .description(K + "setting.offhand_totem.description").visibleWhen(() -> offShow.get());
        mainShow = s.toggle("mainhand_show", true).name(K + "setting.mainhand_show")
                .description(K + "setting.mainhand_show.description").section(K + "section.mainhand");
        mainShield = s.toggle("mainhand_shield", false).name(K + "setting.mainhand_shield")
                .description(K + "setting.mainhand_shield.description").visibleWhen(() -> mainShow.get());
        mainTotem = s.toggle("mainhand_totem", false).name(K + "setting.mainhand_totem")
                .description(K + "setting.mainhand_totem.description").visibleWhen(() -> mainShow.get());
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
