package dev.lumora.client.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block breaking progress (0..1) for the attack indicator module. */
@Mixin(ClientPlayerInteractionManager.class)
public interface BreakingProgressAccessor {
    @Accessor("currentBreakingProgress")
    float lumora$breakingProgress();
}
