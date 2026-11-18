package dev.lumora.core.building;

import dev.lumora.api.building.BlockHit;
import dev.lumora.api.building.BlockInfo;
import dev.lumora.api.building.ItemSpec;

import java.util.List;
import java.util.Optional;

/**
 * World and inventory access of the building tools ({@code AddonContext.building()}), implemented by the version
 * adapters for Minecraft 26.1 and newer. The defaults are "not available": nothing is read, every action does nothing.
 * The adapter enforces creative mode for every action. Client thread only.
 */
public interface BuildingPlatform {
    BuildingPlatform NONE = new BuildingPlatform() {
    };

    default boolean available() {
        return false;
    }

    default boolean creative() {
        return false;
    }

    default Optional<BlockHit> target() {
        return Optional.empty();
    }

    default BlockInfo block(int x, int y, int z) {
        return BlockInfo.AIR;
    }

    default int selectedSlot() {
        return 0;
    }

    default void selectHotbar(int slot) {
    }

    default ItemSpec hotbar(int slot) {
        return ItemSpec.EMPTY;
    }

    default ItemSpec mainHand() {
        return ItemSpec.EMPTY;
    }

    default boolean setCreativeSlot(int slot, ItemSpec item) {
        return false;
    }

    default ItemSpec pick(BlockHit hit, boolean withState) {
        return ItemSpec.EMPTY;
    }

    default boolean breakBlock(BlockHit hit) {
        return false;
    }

    /** Right-click with the selected item at this hit, without asking the use handlers. */
    default boolean place(BlockHit hit) {
        return false;
    }

    /** Ids of all blocks with an item, sorted (API 1.11). */
    default List<String> blockIds() {
        return List.of();
    }

    /** Average texture colour, 0xAARRGGBB with alpha = opaque share; 0 if unknown (API 1.11). */
    default int averageColor(String blockId) {
        return 0;
    }

    /** Creative inventory items matching the search (API 1.11). */
    default List<ItemSpec> creativeItems(String search, boolean blocksOnly) {
        return List.of();
    }
}
