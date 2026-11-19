package dev.lumora.core.extras;

import dev.lumora.api.addon.AddonContext;
import dev.lumora.api.addon.LumoraAddon;
import dev.lumora.api.config.BooleanSetting;
import dev.lumora.api.config.ChoiceSetting;
import dev.lumora.api.config.SettingsGroup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in module "Animations": classic Minecraft 1.7 / 1.8 animations or the modern ones, each part on its own.
 * <p>
 * Every {@link Feature} has its own choice with two options: "Default" leaves the Minecraft version as it is,
 * "Legacy" shows the original look of 1.7 (where 1.7 and 1.8 share it, that one; for the creative tabs 1.8). Presets
 * set all parts at once (Legacy, Default); changing a part by hand switches the preset to "Custom".
 * <p>
 * The version adapters ask {@link #style(Feature)} (one array read) at their hook points; nothing is computed per frame
 * here. Everything is cosmetic: only how this client draws things changes, never what is sent to the server, hit
 * boxes or game mechanics. The swing styles (Smooth, Spin, Slap) that used to sit on the Themes page live here too.
 */
public final class AnimationsModule implements LumoraAddon {
    public static final String ID = "lumora-animations";
    /** The animations differ from 1.8 only since 1.9. */
    public static final String MIN_MINECRAFT = "1.9";
    static final String K = "lumora.animations.";

    /** What a part looks like. */
    public enum Style {
        /** The Minecraft version as it is. */
        DEFAULT("default"),
        /** Original Minecraft 1.7.10 behaviour. */
        LEGACY_1_7("legacy_1_7"),
        /** Original Minecraft 1.8.9 behaviour. */
        LEGACY_1_8("legacy_1_8"),
        /** 1.7.10 and 1.8.9 behave the same for this part. */
        LEGACY("legacy");

        public final String key;

        Style(String key) {
            this.key = key;
        }

        static Style of(String key) {
            for (Style s : values()) {
                if (s.key.equals(key)) {
                    return s;
                }
            }
            return DEFAULT;
        }

        /** Whether this is a 1.7 look (also the shared 1.7/1.8 one). */
        public boolean is17() {
            return this == LEGACY_1_7 || this == LEGACY;
        }

        /** Whether this is a 1.8 look (also the shared 1.7/1.8 one). */
        public boolean is18() {
            return this == LEGACY_1_8 || this == LEGACY;
        }

        public boolean legacy() {
            return this == LEGACY_1_7 || this == LEGACY_1_8 || this == LEGACY;
        }
    }

    /**
     * One part. Two options: "default" (the Minecraft version as it is) and "legacy" (the original look of 1.7 where
     * the original code has its own 1.7 behaviour, else the look 1.7 and 1.8 share, for the creative tabs 1.8).
     */
    public enum Feature {
        // player
        SNEAK("sneak", "player", Style.LEGACY_1_7),
        HEART("heart", "player", Style.LEGACY_1_7),
        BLOCK_BUILD("block_build", "player", Style.LEGACY_1_7),
        BACKWARDS_WALKING("backwards_walking", "player", Style.LEGACY),
        HEAD_ROTATION("head_rotation", "player", Style.LEGACY),
        // items
        SWORD("sword", "items", Style.LEGACY_1_7),
        POSTURE("posture", "items", Style.LEGACY_1_7),
        BOW("bow", "items", Style.LEGACY_1_7),
        FOOD("food", "items", Style.LEGACY_1_7),
        FISHING_ROD("fishing_rod", "items", Style.LEGACY_1_7),
        ITEM_EQUIP("item_equip", "items", Style.LEGACY_1_7),
        POSTURE_THIRD("posture_third", "items", Style.LEGACY_1_7),
        // inventory: 1.7 had no recipe book and no other tabs than 1.8
        RECIPE_BOOK("recipe_book", "inventory", Style.LEGACY_1_7),
        CREATIVE_LAYOUT("creative_layout", "inventory", Style.LEGACY_1_8);

        public final String key;
        final String section;
        /** The look of the "legacy" option. */
        public final Style legacy;

        Feature(String key, String section, Style legacy) {
            this.key = key;
            this.section = section;
            this.legacy = legacy;
        }

        /** The option keys of the setting. */
        String[] options() {
            return new String[]{OPTION_DEFAULT, OPTION_LEGACY};
        }

        /** The style of an option key. */
        Style styleOf(String option) {
            return OPTION_LEGACY.equals(option) ? legacy : Style.DEFAULT;
        }
    }

    static final String OPTION_DEFAULT = "default";
    static final String OPTION_LEGACY = "legacy";
    static final String PRESET_LEGACY = "legacy";
    static final String PRESET_DEFAULT = "default";
    static final String PRESET_CUSTOM = "custom";
    /** The swing styles taken over from the Themes page (first-person swing of the main hand). */
    public static final List<String> SWINGS = List.of("vanilla", "smooth", "spin", "slap");

    private static volatile AnimationsModule active;
    /** Current style per feature (ordinal of {@link Style}), read by the version mixins. */
    private static final Style[] STYLES = new Style[Feature.values().length];

    static {
        java.util.Arrays.fill(STYLES, Style.DEFAULT);
    }

    ChoiceSetting preset;
    final Map<Feature, ChoiceSetting> features = new EnumMap<>(Feature.class);
    ChoiceSetting swing;
    /** The swing value was taken over from the old client setting once. */
    BooleanSetting swingMigrated;
    /** A preset is being applied: its own changes do not switch to "Custom". */
    private boolean applying;
    /** The parts this Minecraft version implements ({@code Platform.animations()}); the others stay hidden and unchanged. */
    final java.util.Set<Feature> supported;

    /** All parts (tests). */
    public AnimationsModule() {
        this(java.util.EnumSet.allOf(Feature.class));
    }

    /** @param supported the parts the running version implements */
    public AnimationsModule(java.util.Set<Feature> supported) {
        this.supported = supported.isEmpty() ? java.util.EnumSet.noneOf(Feature.class) : java.util.EnumSet.copyOf(supported);
    }

    /** Whether a part is built into this version. */
    public boolean supports(Feature f) {
        return this.supported.contains(f);
    }

    /** The running module, {@code null} while switched off. */
    public static AnimationsModule active() {
        return active;
    }

    /** The style of a part right now: {@link Style#DEFAULT} while the module is off. */
    public static Style style(Feature f) {
        AnimationsModule m = active;
        return m == null || !m.supported.contains(f) ? Style.DEFAULT : STYLES[f.ordinal()];
    }

    /** Whether the recipe book is taken out of the inventory screens. */
    public static boolean removeRecipeBook() {
        return style(Feature.RECIPE_BOOK).legacy();
    }

    /** The first-person swing style ("vanilla", "smooth", "spin", "slap"); "vanilla" while the module is off. */
    public static String swing() {
        AnimationsModule m = active;
        return m == null ? "vanilla" : m.swing.get();
    }

    /**
     * Takes the swing style over from the old client setting ("swingAnimation" in lumora-client.json) the first time
     * the module runs, so a player keeps the style chosen on the Themes page before.
     */
    public static void migrateSwing(String old) {
        AnimationsModule m = active;
        if (m == null || m.swingMigrated.get()) {
            return;
        }
        String s = old == null ? "" : old.toLowerCase(Locale.ROOT);
        if (SWINGS.contains(s)) {
            m.swing.set(s);
        }
        m.swingMigrated.set(true);
    }

    @Override
    public void onInitialize(AddonContext context) {
        settings(context.configuration().settings());
    }

    void settings(SettingsGroup s) {
        preset = s.choice("preset", PRESET_DEFAULT, PRESET_LEGACY, PRESET_DEFAULT, PRESET_CUSTOM)
                .name(K + "preset").description(K + "preset.description").section(K + "section.presets");
        preset.visibleWhen(() -> !supported.isEmpty());
        String section = null;
        for (Feature f : Feature.values()) {
            ChoiceSetting c = s.choice(f.key, OPTION_DEFAULT, f.options()).name(K + f.key).description(K + f.key + ".description");
            c.visibleWhen(() -> supported.contains(f));
            if (!f.section.equals(section)) {
                section = f.section;
                c.section(K + "section." + section);
            }
            features.put(f, c);
            c.onChange(v -> {
                STYLES[f.ordinal()] = f.styleOf(v);
                if (!applying && !PRESET_CUSTOM.equals(preset.get())) {
                    preset.set(PRESET_CUSTOM);
                }
            });
            STYLES[f.ordinal()] = f.styleOf(c.get());
        }
        swing = s.choice("swing", "smooth", SWINGS.toArray(String[]::new)).name(K + "swing")
                .description(K + "swing.description").section(K + "section.swing");
        swingMigrated = s.toggle("swing_migrated", false).visibleWhen(() -> false);
        preset.onChange(this::applyPreset);
    }

    /** Sets every part to what the preset picks; "Custom" keeps the parts as they are. */
    void applyPreset(String p) {
        applying = true;
        try {
            if (PRESET_LEGACY.equals(p) || PRESET_DEFAULT.equals(p)) {
                String option = PRESET_LEGACY.equals(p) ? OPTION_LEGACY : OPTION_DEFAULT;
                for (Feature f : Feature.values()) {
                    features.get(f).set(option);
                }
            }
        } finally {
            applying = false;
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
