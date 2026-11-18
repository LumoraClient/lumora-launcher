package dev.lumora.api.building;

/**
 * Asked before the player right-clicks a block (client thread). The first handler that does not answer
 * {@link Decision#NORMAL} decides.
 *
 * @since 1.10.0
 */
@FunctionalInterface
public interface UseHandler {
    enum Decision {
        /** Minecraft handles the click as usual. */
        NORMAL,
        /** The click counts as if the player held sneak: the block is not used, the held item is placed next to it. */
        AS_SNEAKING,
        /** The click is dropped (the handler did something else instead). */
        CANCEL
    }

    Decision onUse(BlockHit hit, ItemSpec held);
}
