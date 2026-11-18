package dev.lumora.api.building;

import dev.lumora.api.Registration;

import java.util.List;
import java.util.Optional;

/**
 * Building tools (permission {@code building}). Everything here is a normal game action a player in creative mode
 * could also do with mouse, keyboard and the creative inventory: Lumora only sends the usual packets (break, place,
 * hotbar slot, creative slot, sneak) and never more often or farther than the server allows. No commands.
 * Actions that change the world or the inventory only work in creative mode and return {@code false} otherwise.
 * <p>
 * Only the clients for Minecraft 26.1 and newer implement it; elsewhere {@link #available()} is {@code false} and
 * every action does nothing. Client thread only.
 *
 * @since 1.10.0
 */
public interface Building {
    /** Whether this Minecraft version supports the building tools. */
    boolean available();

    /** The own player is in creative mode. */
    boolean creative();

    /** The block under the crosshair (within the normal reach), empty if the crosshair is not on a block. */
    Optional<BlockHit> target();

    /** The block at this position ({@link BlockInfo#AIR} outside the loaded world). */
    BlockInfo block(int x, int y, int z);

    /** Selected hotbar slot, 0..8. */
    int selectedSlot();

    /** Selects a hotbar slot (0..8), like the number keys. */
    void selectHotbar(int slot);

    /** The item in a hotbar slot (0..8). */
    ItemSpec hotbar(int slot);

    /** The item in the main hand. */
    ItemSpec mainHand();

    /** Puts an item into a hotbar slot (0..8) through the creative inventory. Creative only. */
    boolean setCreativeSlot(int slot, ItemSpec item);

    /** The item Minecraft's pick-block would give for this block; with {@code withState} it keeps the block state. */
    ItemSpec pick(BlockHit hit, boolean withState);

    /** Breaks the block (instant in creative). Creative only. */
    boolean breakBlock(BlockHit hit);

    /**
     * Right-clicks with the selected item at this hit, without asking the {@link UseHandler}s: places next to the block,
     * or into the hit position itself when that block can be replaced (air after {@link #breakBlock}). Creative only.
     */
    boolean place(BlockHit hit);

    /** Ticks between two right-clicks while the key is held (Minecraft: 4); -1 = Minecraft's own. Creative only. */
    void setUseDelay(int ticks);

    /** Ticks between two broken blocks while the key is held (Minecraft: 5); -1 = Minecraft's own. Creative only. */
    void setBreakDelay(int ticks);

    /** Asks the handler before every right-click on a block. Removed when the addon is disabled. */
    Registration onUse(UseHandler handler);

    /** Asks the handler before every pick-block on a block. Removed when the addon is disabled. */
    Registration onPick(PickHandler handler);

    // --- since 1.11.0; the defaults keep 1.10 implementations working

    /** Ids of all blocks that can be held as an item ({@code minecraft:stone} …), sorted. @since 1.11.0 */
    default List<String> blockIds() {
        return List.of();
    }

    /**
     * Average colour of a block as it looks (its main texture), {@code 0xRRGGBB} with the share of opaque pixels as
     * alpha ({@code 0xFF} = fully opaque); 0 if unknown. Tinted textures (grass, leaves) count untinted. @since 1.11.0
     */
    default int averageColor(String blockId) {
        return 0;
    }

    /**
     * Items of the creative inventory whose name or id contains {@code search} (case-insensitive; empty = all),
     * only block items with {@code blocksOnly}. @since 1.11.0
     */
    default List<ItemSpec> creativeItems(String search, boolean blocksOnly) {
        return List.of();
    }

    /** Draws an outline around this block in the world (ARGB colour) until removed or the addon is disabled. @since 1.11.0 */
    default Registration highlight(int x, int y, int z, int argb) {
        throw new UnsupportedOperationException("requires Lumora API 1.11");
    }

    // --- since 1.12.0

    /**
     * What the block is like (full cube, solid, opaque, same texture on all sides); {@link BlockTraits#NONE} if
     * unknown. @since 1.12.0
     */
    default BlockTraits blockTraits(String blockId) {
        return BlockTraits.NONE;
    }

    /**
     * Average colour of an item as it looks in the inventory ({@code 0xAARRGGBB} like {@link #averageColor}); block
     * items give the colour of their block, other items that of their item texture; 0 if unknown. @since 1.12.0
     */
    default int itemColor(String itemId) {
        return 0;
    }

    /**
     * NoClip: while on, the own player flies through blocks in creative mode (only while flying, like spectator).
     * In the own world (singleplayer or as LAN host) Lumora's built-in server lets the player through. On other servers
     * it only works as far as the server accepts the movement; when the server puts the player back from inside a
     * block, NoClip switches itself off ({@link #noClip()} turns {@code false}). Nothing is bypassed. Removed when the
     * addon is disabled. @since 1.12.0
     */
    default void setNoClip(boolean on) {
    }

    /** Whether this addon's NoClip is on; false again after the server refused it. @since 1.12.0 */
    default boolean noClip() {
        return false;
    }
}
