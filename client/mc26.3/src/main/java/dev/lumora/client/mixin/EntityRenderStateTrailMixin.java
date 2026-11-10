package dev.lumora.client.mixin;

import dev.lumora.client.render.TrailHolder;
import dev.lumora.core.extras.ElytraTrailsModule;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Field for the elytra trails of a player in its render state (see ElytraTrailMixin). */
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateTrailMixin implements TrailHolder {
    @Unique
    private ElytraTrailsModule.Mesh lumora$trail;

    @Override
    public ElytraTrailsModule.Mesh lumora$trail() {
        return lumora$trail;
    }

    @Override
    public void lumora$trail(ElytraTrailsModule.Mesh mesh) {
        lumora$trail = mesh;
    }
}
