package dev.lumora.client.render;

import dev.lumora.core.extras.ElytraTrailsModule;

/** Carries a player's elytra trails from render state extraction to drawing (EntityRenderStateTrailMixin). */
public interface TrailHolder {
    ElytraTrailsModule.Mesh lumora$trail();

    void lumora$trail(ElytraTrailsModule.Mesh mesh);
}
