package dev.lumora.core.extras.anim;

import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * "Creative layout", Legacy (1.8): the ten creative tabs of Minecraft 1.8.9 with their original names, icons, places
 * and contents. Version independent; the version mixins show these instead of Minecraft's own tabs (the tab slots
 * Minecraft has are reused: the top row 1.8 had on top, the bottom row below; hotbar and operator tabs did not exist
 * in 1.8 and are hidden).
 * <p>
 * The contents ({@code lumora/animations/creative_1_8.tsv}) were read from 1.8.9 itself
 * ({@code CreativeTabs.displayAllReleventItems}) and turned into today's item ids with Minecraft's own DataFixer.
 * Items that came after 1.8 go to the 1.8 tab that matches their tab today ({@link #fallback}), after the 1.8 items.
 */
public final class CreativeTabs18 {
    /** The tabs of 1.8.9 in their order (top row, then bottom row; search and inventory stay Minecraft's). */
    public enum Tab {
        BUILDING_BLOCKS("building_blocks", true, 0, "minecraft:bricks"),
        DECORATIONS("decorations", true, 1, "minecraft:peony"),
        REDSTONE("redstone", true, 2, "minecraft:redstone"),
        TRANSPORTATION("transportation", true, 3, "minecraft:powered_rail"),
        MISC("misc", true, 4, "minecraft:lava_bucket"),
        FOOD("food", false, 0, "minecraft:apple"),
        TOOLS("tools", false, 1, "minecraft:iron_axe"),
        COMBAT("combat", false, 2, "minecraft:golden_sword"),
        BREWING("brewing", false, 3, "minecraft:potion"),
        MATERIALS("materials", false, 4, "minecraft:stick");

        public final String key;
        public final boolean top;
        public final int column;
        /** The icon item of 1.8 (decorations: the peony, brewing: a water bottle). */
        public final String icon;

        Tab(String key, boolean top, int column, String icon) {
            this.key = key;
            this.top = top;
            this.column = column;
            this.icon = icon;
        }

        /** Lang key of the tab name. */
        public String nameKey() {
            return "lumora.animations.creative." + key;
        }

        static Tab of(String key) {
            for (Tab t : values()) {
                if (t.key.equals(key)) {
                    return t;
                }
            }
            return null;
        }
    }

    /** One 1.8 entry: tab, today's item id, and the potion or stored enchantment it carried ({@code null} if none). */
    public record Entry(Tab tab, String id, String potion, String enchantment, int level) {
    }

    /**
     * The 1.8 tab that stands in each of Minecraft's tab slots, by the slot's registry id path ({@code null}: the slot
     * is hidden). Same places as in 1.8: five on top, five below.
     */
    public static final Map<String, Tab> SLOTS = Map.of(
            "building_blocks", Tab.BUILDING_BLOCKS, "colored_blocks", Tab.DECORATIONS, "natural_blocks", Tab.REDSTONE,
            "functional_blocks", Tab.TRANSPORTATION, "redstone_blocks", Tab.MISC, "tools_and_utilities", Tab.FOOD,
            "combat", Tab.TOOLS, "food_and_drinks", Tab.COMBAT, "ingredients", Tab.BREWING, "spawn_eggs", Tab.MATERIALS);
    /** Minecraft's tab slots with no 1.8 counterpart. */
    public static final List<String> HIDDEN = List.of("hotbar", "op_blocks");

    private static volatile List<Entry> entries;

    private CreativeTabs18() {
    }

    /** Whether the 1.8 tabs are shown. */
    public static boolean active() {
        return AnimationsModule.style(Feature.CREATIVE_LAYOUT) == Style.LEGACY_1_8;
    }

    /** The 1.8 entries in their original order (read once). */
    public static List<Entry> entries() {
        List<Entry> e = entries;
        if (e == null) {
            e = read();
            entries = e;
        }
        return e;
    }

    /** The 1.8 entries of one tab, in order. */
    public static List<Entry> entries(Tab tab) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries()) {
            if (e.tab() == tab) {
                out.add(e);
            }
        }
        return out;
    }

    static List<Entry> read() {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = CreativeTabs18.class.getResourceAsStream("/lumora/animations/creative_1_8.tsv")) {
            if (in == null) {
                return List.of();
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                Tab tab = Tab.of(f[0]);
                if (tab == null || f.length < 2) {
                    continue;
                }
                String extra = f.length > 2 ? f[2] : "";
                String potion = extra.startsWith("potion=") ? extra.substring(7) : null;
                String ench = null;
                int level = 0;
                if (extra.startsWith("ench=")) {
                    int colon = extra.lastIndexOf(':');
                    ench = extra.substring(5, colon);
                    level = Integer.parseInt(extra.substring(colon + 1));
                }
                out.add(new Entry(tab, f[1], potion, ench, level));
            }
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
        return List.copyOf(out);
    }

    /**
     * The 1.8 tab for an item that came after 1.8, from its tab today ({@code modernTab}: the registry id path of
     * Minecraft's tab) and its id. {@code null}: not shown (operator items, which 1.8 had in no tab).
     */
    public static Tab fallback(String modernTab, String id) {
        String path = id.indexOf(':') >= 0 ? id.substring(id.indexOf(':') + 1) : id;
        return switch (modernTab) {
            case "building_blocks" -> Tab.BUILDING_BLOCKS;
            case "colored_blocks" -> endsWith(path, "_carpet", "_bed", "_banner", "_candle", "_glass_pane", "_shulker_box")
                    ? Tab.DECORATIONS : Tab.BUILDING_BLOCKS;
            case "natural_blocks" -> contains(path, "_log", "_wood", "_stem", "_hyphae", "_ore", "stone", "dirt", "sand", "gravel", "clay",
                    "terracotta", "ice", "snow_block", "netherrack", "obsidian", "basalt", "deepslate", "tuff", "calcite", "mud", "moss_block",
                    "nylium", "_block") ? Tab.BUILDING_BLOCKS : Tab.DECORATIONS;
            case "functional_blocks" -> Tab.DECORATIONS;
            case "redstone_blocks" -> Tab.REDSTONE;
            case "tools_and_utilities" -> contains(path, "minecart", "boat", "raft", "saddle", "elytra", "rail") ? Tab.TRANSPORTATION
                    : endsWith(path, "_bucket") || path.equals("bucket") || path.startsWith("music_disc") ? Tab.MISC : Tab.TOOLS;
            case "combat" -> Tab.COMBAT;
            case "food_and_drinks" -> path.endsWith("potion") || path.equals("tipped_arrow") ? Tab.BREWING : Tab.FOOD;
            case "ingredients" -> Tab.MATERIALS;
            case "spawn_eggs" -> Tab.MISC;
            default -> null;
        };
    }

    private static boolean endsWith(String s, String... ends) {
        for (String e : ends) {
            if (s.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String s, String... parts) {
        for (String p : parts) {
            if (s.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /** Tabs by key, for the version mixins. */
    public static Map<Tab, List<Entry>> byTab() {
        Map<Tab, List<Entry>> out = new EnumMap<>(Tab.class);
        for (Tab t : Tab.values()) {
            out.put(t, entries(t));
        }
        return out;
    }
}
