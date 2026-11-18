package dev.lumora.api.building;

import java.util.Map;

/**
 * An item stack as the building tools see it: registry id, count, the block state it places with ({@code block_state}
 * component, empty for none), whether it is a block item, and its display name (for showing only).
 *
 * @since 1.10.0
 */
public record ItemSpec(String id, int count, Map<String, String> blockState, boolean block, String name) {
    public static final ItemSpec EMPTY = new ItemSpec("minecraft:air", 0, Map.of(), false, "");

    public ItemSpec {
        blockState = Map.copyOf(blockState);
    }

    /** A block item placing this block (one item, no block state). */
    public static ItemSpec ofBlock(String id) {
        return new ItemSpec(id, 1, Map.of(), true, "");
    }

    public boolean isEmpty() {
        return count <= 0 || id.endsWith(":air");
    }

    /** Same item and block state (count and name do not matter). */
    public boolean sameAs(ItemSpec other) {
        return other != null && id.equals(other.id) && blockState.equals(other.blockState);
    }

    public ItemSpec withBlockState(Map<String, String> state) {
        return new ItemSpec(id, count, state, block, name);
    }
}
