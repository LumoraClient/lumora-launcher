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
 * Every {@link Feature} has its own choice. The options are only the ones the original code really has: where 1.7 and
 * 1.8 behave the same there is one "Legacy (1.7/1.8)" option, where 1.8 already behaves like today's Minecraft there is
 * no separate 1.8 option. {@link Style#DEFAULT} leaves the Minecraft version as it is. Presets set all parts at once;
 * changing a part by hand switches the preset to "Custom".
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
        LEGACY("legacy"),
        /** Today's Minecraft (26.x). */
        MODERN("modern");

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

    /** One part with the styles the original code has for it (DEFAULT is always there). */
    public enum Feature {
        // player
        SNEAK("sneak", "player", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        /** 1.8 hearts blink exactly like today's: no own 1.8 option. */
        HEART("heart", "player", Style.LEGACY_1_7, Style.MODERN),
        BLOCK_BUILD("block_build", "player", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        BACKWARDS_WALKING("backwards_walking", "player", Style.LEGACY, Style.MODERN),
        HEAD_ROTATION("head_rotation", "player", Style.LEGACY, Style.MODERN),
        // items
        SWORD("sword", "items", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        POSTURE("posture", "items", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        BOW("bow", "items", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        FOOD("food", "items", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        FISHING_ROD("fishing_rod", "items", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        ITEM_EQUIP("item_equip", "items", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        POSTURE_THIRD("posture_third", "items", Style.LEGACY_1_7, Style.LEGACY_1_8, Style.MODERN),
        // inventory
        CREATIVE_LAYOUT("creative_layout", "inventory", Style.LEGACY_1_8, Style.MODERN);

        public final String key;
        final String section;
        final List<Style> styles;

        Feature(String key, String section, Style... styles) {
            this.key = key;
            this.section = section;
            List<Style> all = new ArrayList<>();
            all.add(Style.DEFAULT);
            all.addAll(List.of(styles));
            this.styles = List.copyOf(all);
        }

        /** The option keys of the setting, "default" first. */
        String[] options() {
            return styles.stream().map(s -> s.key).toArray(String[]::new);
        }

        /** The style a preset picks: 1.7 / 1.8 if the part has it, else the shared legacy one, else modern. */
        Style forPreset(String preset) {
            return switch (preset) {
                case PRESET_17 -> styles.contains(Style.LEGACY_1_7) ? Style.LEGACY_1_7
                        : styles.contains(Style.LEGACY) ? Style.LEGACY : Style.MODERN;
                case PRESET_18 -> styles.contains(Style.LEGACY_1_8) ? Style.LEGACY_1_8
                        : styles.contains(Style.LEGACY) ? Style.LEGACY : Style.MODERN;
                case PRESET_MODERN -> Style.MODERN;
                default -> null;
            };
        }
    }

    static final String PRESET_17 = "classic_1_7";
    static final String PRESET_18 = "classic_1_8";
    static final String PRESET_MODERN = "modern";
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
    BooleanSetting removeRecipeBook;
    ChoiceSetting swing;
    /** The swing value was taken over from the old client setting once. */
    BooleanSetting swingMigrated;
    /** A preset is being applied: its own changes do not switch to "Custom". */
    private boolean applying;
    /** The parts are built into this Minecraft version (26.x so far); otherwise only the swing style is offered. */
    final boolean partsSupported;
    /** Minecraft versions from which the parts work (the older ones follow). */
    static final String PARTS_FROM = "26.1";

    public AnimationsModule() {
        this(null);
    }

    /** @param minecraftVersion the running version ({@code null}: all parts, tests) */
    public AnimationsModule(String minecraftVersion) {
        this.partsSupported = minecraftVersion == null
                || dev.lumora.core.addon.VersionSupport.compare(minecraftVersion, PARTS_FROM) >= 0;
    }

    /** The running module, {@code null} while switched off. */
    public static AnimationsModule active() {
        return active;
    }

    /** The style of a part right now: {@link Style#DEFAULT} while the module is off. */
    public static Style style(Feature f) {
        return active == null || !active.partsSupported ? Style.DEFAULT : STYLES[f.ordinal()];
    }

    /** Whether the recipe book is taken out of the inventory screens. */
    public static boolean removeRecipeBook() {
        AnimationsModule m = active;
        return m != null && m.partsSupported && m.removeRecipeBook.get();
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
        preset = s.choice("preset", PRESET_CUSTOM, PRESET_17, PRESET_18, PRESET_MODERN, PRESET_CUSTOM)
                .name(K + "preset").description(K + "preset.description").section(K + "section.presets");
        preset.visibleWhen(() -> partsSupported);
        String section = null;
        for (Feature f : Feature.values()) {
            ChoiceSetting c = s.choice(f.key, Style.DEFAULT.key, f.options()).name(K + f.key).description(K + f.key + ".description");
            c.visibleWhen(() -> partsSupported);
            if (!f.section.equals(section)) {
                section = f.section;
                c.section(K + "section." + section);
            }
            if (f == Feature.CREATIVE_LAYOUT) {
                removeRecipeBook = s.toggle("remove_recipe_book", false).name(K + "remove_recipe_book")
                        .description(K + "remove_recipe_book.description");
                removeRecipeBook.visibleWhen(() -> partsSupported);
            }
            features.put(f, c);
            c.onChange(v -> {
                STYLES[f.ordinal()] = Style.of(v);
                if (!applying && !PRESET_CUSTOM.equals(preset.get())) {
                    preset.set(PRESET_CUSTOM);
                }
            });
            STYLES[f.ordinal()] = Style.of(c.get());
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
            for (Feature f : Feature.values()) {
                Style st = f.forPreset(p);
                if (st != null) {
                    features.get(f).set(st.key);
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
