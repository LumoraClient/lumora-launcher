package dev.lumora.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lumora's world loading screen: whether the game is still running (Minecraft stops its loading drawing otherwise). */
@Mixin(MinecraftClient.class)
public interface MinecraftClientRunningAccessor {
    @Accessor("running")
    boolean lumora$running();
}
