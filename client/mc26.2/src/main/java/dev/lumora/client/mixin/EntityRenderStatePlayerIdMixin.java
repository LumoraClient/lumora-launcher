package dev.lumora.client.mixin;

import dev.lumora.client.render.PlayerIdHolder;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.UUID;

/** Carries the player's UUID from extraction to name tag submission (for addon name tag icons). */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStatePlayerIdMixin implements PlayerIdHolder {
    @Unique
    private UUID lumora$playerId;

    @Override
    public UUID lumora$playerId() {
        return lumora$playerId;
    }

    @Override
    public void lumora$playerId(UUID id) {
        lumora$playerId = id;
    }
}
