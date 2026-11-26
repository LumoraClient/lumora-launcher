package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ColorSetting;
import dev.lumora.api.config.SettingsGroup;

/**
 * Built-in module "Name Tags": the own name tag in third person, a text shadow and the background box of every name tag
 * (players, named mobs, armor stands). Only this client's view changes.
 * <p>
 * The version adapters ask the static methods while Minecraft builds a name tag: {@link #showOwn} where Minecraft
 * hides the tag of the camera player, {@link #shadow} and {@link #background} for every text part of the tag. Cost: a
 * field read per call; nothing changes while the module is off. Render thread only.
 */
public final class NameTagsModule implements LumoraAddon {
    public static final String ID = "lumora-nametags";
    static final String K = "lumora.nametags.";
    /** Vanilla's box: black at 25 % (the accessibility option "text background opacity" is replaced while on). */
    static final int DEFAULT_BACKGROUND = 0x40000000;

    private static volatile NameTagsModule active;

    BooleanSetting own;
    BooleanSetting textShadow;
    BooleanSetting background;
    ColorSetting backgroundColor;

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        own = s.toggle("show_own", true).name(K + "setting.show_own").description(K + "setting.show_own.description")
                .section(K + "section.general");
        textShadow = s.toggle("text_shadow", true).name(K + "setting.text_shadow")
                .description(K + "setting.text_shadow.description");
        background = s.toggle("background", true).name(K + "setting.background")
                .description(K + "setting.background.description").section(K + "section.background");
        backgroundColor = s.color("background_color", DEFAULT_BACKGROUND, true).name(K + "setting.background_color")
                .description(K + "setting.background_color.description").requires(background);
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

    /** The running module, {@code null} while switched off. */
    public static NameTagsModule active() {
        return active;
    }

    /** Whether the own name tag shows (third person only; the adapters check the camera). */
    public static boolean showOwn() {
        NameTagsModule m = active;
        return m != null && m.own.get();
    }

    /**
     * Text shadow for a part of a name tag.
     *
     * @param vanilla    what Minecraft would use (no shadow)
     * @param seeThrough the part drawn through walls (never shadowed: the shadow would show through blocks)
     */
    public static boolean shadow(boolean vanilla, boolean seeThrough) {
        NameTagsModule m = active;
        if (m == null) {
            return vanilla;
        }
        return !seeThrough && m.textShadow.get();
    }

    /**
     * Background color (ARGB) for a part of a name tag: parts without a box (0) keep none, the box gets the chosen
     * color or disappears.
     */
    public static int background(int vanilla) {
        NameTagsModule m = active;
        if (m == null || vanilla == 0) {
            return vanilla;
        }
        return m.background.get() ? m.backgroundColor.get() : 0;
    }

    /** The background as ARGB for versions that draw the box themselves (1.8.9, 1.12.2), or {@code vanilla}. */
    public static int backgroundArgb(int vanilla) {
        NameTagsModule m = active;
        if (m == null) {
            return vanilla;
        }
        return m.background.get() ? m.backgroundColor.get() : 0;
    }

    /** Switches the module on or off without an addon context (tests). */
    void setActiveForTest(boolean on) {
        active = on ? this : null;
    }
}
