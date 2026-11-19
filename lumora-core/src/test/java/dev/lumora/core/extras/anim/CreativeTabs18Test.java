package dev.lumora.core.extras.anim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 1.8.9 creative tab table: read from the resource, original order and contents, the place of newer items. */
class CreativeTabs18Test {
    @Test
    void theTableHoldsThe18TabsInTheirOrder() {
        List<CreativeTabs18.Entry> all = CreativeTabs18.entries();
        assertEquals(624, all.size());
        List<CreativeTabs18.Entry> blocks = CreativeTabs18.entries(CreativeTabs18.Tab.BUILDING_BLOCKS);
        assertEquals(158, blocks.size());
        assertEquals(List.of("minecraft:stone", "minecraft:granite", "minecraft:polished_granite"),
                blocks.subList(0, 3).stream().map(CreativeTabs18.Entry::id).toList(), "stone:0, stone:1, stone:2 of 1.8");
        CreativeTabs18.Entry regeneration = CreativeTabs18.entries(CreativeTabs18.Tab.BREWING).stream()
                .filter(e -> "minecraft:regeneration".equals(e.potion())).findFirst().orElseThrow();
        assertEquals("minecraft:potion", regeneration.id());
        CreativeTabs18.Entry book = CreativeTabs18.entries(CreativeTabs18.Tab.TOOLS).stream()
                .filter(e -> e.enchantment() != null).findFirst().orElseThrow();
        assertEquals("minecraft:efficiency", book.enchantment());
        assertEquals(5, book.level());
        assertEquals(CreativeTabs18.Tab.DECORATIONS, CreativeTabs18.SLOTS.get("colored_blocks"));
        assertTrue(CreativeTabs18.HIDDEN.contains("op_blocks"));
    }

    @Test
    void newerItemsGoToTheMatching18Tab() {
        assertEquals(CreativeTabs18.Tab.BUILDING_BLOCKS, CreativeTabs18.fallback("building_blocks", "minecraft:deepslate_bricks"));
        assertEquals(CreativeTabs18.Tab.DECORATIONS, CreativeTabs18.fallback("colored_blocks", "minecraft:cyan_candle"));
        assertEquals(CreativeTabs18.Tab.BUILDING_BLOCKS, CreativeTabs18.fallback("colored_blocks", "minecraft:cyan_concrete"));
        assertEquals(CreativeTabs18.Tab.TRANSPORTATION, CreativeTabs18.fallback("tools_and_utilities", "minecraft:oak_chest_boat"));
        assertEquals(CreativeTabs18.Tab.MISC, CreativeTabs18.fallback("tools_and_utilities", "minecraft:axolotl_bucket"));
        assertEquals(CreativeTabs18.Tab.BREWING, CreativeTabs18.fallback("food_and_drinks", "minecraft:lingering_potion"));
        assertEquals(CreativeTabs18.Tab.MISC, CreativeTabs18.fallback("spawn_eggs", "minecraft:allay_spawn_egg"));
        assertNull(CreativeTabs18.fallback("op_blocks", "minecraft:command_block"), "operator items had no tab in 1.8");
    }
}
