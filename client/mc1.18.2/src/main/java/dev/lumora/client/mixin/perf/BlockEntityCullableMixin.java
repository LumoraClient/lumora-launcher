package dev.lumora.client.mixin.perf;

import dev.lumora.core.perf.Cullable;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Result of the occlusion culler on every block entity (written by the culling thread). */
@Mixin(BlockEntity.class)
public abstract class BlockEntityCullableMixin implements Cullable {
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
