package dev.lumora.client.mixin.perf;

import dev.lumora.core.perf.Cullable;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Result of the occlusion culler on every entity (written by the culling thread). */
@Mixin(Entity.class)
public abstract class EntityCullableMixin implements Cullable {
    @Unique
    private volatile int lumora$cullState;

    @Override
    public int lumora$cull() {
        return lumora$cullState;
    }

    @Override
    public void lumora$cull(int state) {
        lumora$cullState = state;
    }
}
