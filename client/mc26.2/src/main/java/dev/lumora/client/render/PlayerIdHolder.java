package dev.lumora.client.render;

import java.util.UUID;

/** Added to EntityRenderState by a mixin: the player's UUID for name tag icons ({@code null} for other entities). */
public interface PlayerIdHolder {
    UUID lumora$playerId();

    void lumora$playerId(UUID id);
}
