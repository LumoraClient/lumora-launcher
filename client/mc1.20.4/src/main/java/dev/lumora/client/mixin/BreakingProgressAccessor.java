package dev.lumora.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Block breaking progress (0..1) for the attack indicator module. */
@Mixin(MultiPlayerGameMode.class)
public interface BreakingProgressAccessor {
    @Accessor("destroyProgress")
    float lumora$breakingProgress();
}
