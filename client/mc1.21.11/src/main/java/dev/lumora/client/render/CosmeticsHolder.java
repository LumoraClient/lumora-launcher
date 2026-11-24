package dev.lumora.client.render;

import dev.lumora.core.cosmetics.PlayerFrame;

/** Added to the player render state by a mixin: the Lumora cosmetics frame of the player ({@code null} if none). */
public interface CosmeticsHolder {
    PlayerFrame lumora$cosmetics();

    void lumora$cosmetics(PlayerFrame frame);
}
