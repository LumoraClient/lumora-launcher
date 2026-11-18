package dev.lumora.api.building;

/**
 * Asked when the player presses the pick-block key (middle mouse button) on a block (client thread).
 *
 * @since 1.10.0
 */
@FunctionalInterface
public interface PickHandler {
    /** @return true if the handler took care of it (Minecraft's own pick is skipped) */
    boolean onPick(BlockHit hit);
}
