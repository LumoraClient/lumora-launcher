package dev.lumora.core.extras.anim;

import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.AnimationsModule.Feature;
import dev.lumora.core.extras.AnimationsModule.Style;

/**
 * "Item equip" and "Block building": when the item in the main hand dips in first person. Version independent
 * decisions; the version mixins keep Minecraft's own fields and ask here.
 * <p>
 * Original code:
 * <ul>
 *     <li>1.7.10 {@code ItemRenderer.updateEquippedItem}: the item dips when the hotbar slot or the item changes; the
 *     same item with the same damage is swapped at once (count and NBT do not count). No dip from placing blocks.</li>
 *     <li>1.8.9 {@code ItemRenderer.updateEquippedItem}: the item dips whenever the stack is not equal
 *     ({@code getIsItemStackEqual}: item, count, damage, NBT); so placing a block in survival dips, in creative not.</li>
 *     <li>modern: the height follows the attack cooldown ({@code getItemSwapScale}³), placing a block calls
 *     {@code itemUsed} (height 0 at once), also in creative.</li>
 * </ul>
 * Main hand only: 1.7 and 1.8 have no off hand, so it always behaves like the running version.
 */
public final class HandEquip {
    /** Ways the main hand dips. */
    public enum Mode {
        /** Minecraft's own code runs. */
        VANILLA,
        /** 1.7 comparison: slot, item and damage. */
        LEGACY_1_7,
        /** 1.8 comparison: the whole stack. */
        LEGACY_1_8,
        /** Modern comparison, but a 1.8 build dip is pending. */
        MODERN_WITH_DIP
    }

    /** A block placement asked for a 1.8 style dip (the stack changed) while "Item equip" is not 1.8. */
    private static volatile boolean buildDip;

    private HandEquip() {
    }

    /** How the main hand dips this tick. */
    public static Mode mode() {
        Style e = AnimationsModule.style(Feature.ITEM_EQUIP);
        if (e == Style.LEGACY_1_7) {
            return Mode.LEGACY_1_7;
        }
        if (e == Style.LEGACY_1_8) {
            return Mode.LEGACY_1_8;
        }
        return buildDip ? Mode.MODERN_WITH_DIP : Mode.VANILLA;
    }

    /**
     * Whether a pure change of the count re-equips in the 1.8 comparison. Not while "Block building" is 1.7, which
     * never dips when placing.
     */
    public static boolean countReequips() {
        return AnimationsModule.style(Feature.BLOCK_BUILD) != Style.LEGACY_1_7;
    }

    /** A pending build dip (counts as "re-equip" until the hand is down). */
    public static boolean buildDip() {
        return buildDip;
    }

    /** The hand is down (height below 0.1): a pending build dip is done. */
    public static void handDown() {
        buildDip = false;
    }

    /**
     * Minecraft placed a block and is about to call {@code itemUsed} (the modern dip). Returns whether to let it.
     *
     * @param infiniteMaterials creative: the stack does not get smaller
     */
    public static boolean blockPlaced(boolean infiniteMaterials) {
        Style b = AnimationsModule.style(Feature.BLOCK_BUILD);
        if (b == Style.DEFAULT) {
            return true;
        }
        if (b == Style.LEGACY_1_8 && !infiniteMaterials && AnimationsModule.style(Feature.ITEM_EQUIP) != Style.LEGACY_1_8) {
            // 1.8 dips because the stack got smaller; with 1.8 "Item equip" that comparison does it by itself
            buildDip = true;
        }
        return false;
    }
}
