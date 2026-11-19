package dev.lumora.client.anim;

import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;

/**
 * Lumora "Animations": what the third-person drawing of an armed entity's main-hand item needs to know, kept on the
 * render state ({@code ArmedEntityRenderState}, filled when Minecraft prepares it).
 */
public interface LegacyArmed {
    /** The legacy style of the main-hand item, {@code null} for Minecraft's drawing. */
    AnimationsModule.Style lumora$thirdStyle();

    ItemPose.Use lumora$thirdUse();

    /** Blocking with a shield in the off hand while holding a sword. */
    boolean lumora$shieldBlocking();

    void lumora$setThird(AnimationsModule.Style style, ItemPose.Use use, boolean shieldBlocking);
}
