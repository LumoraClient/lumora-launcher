package dev.lumora.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft 1.16.5 keeps the frame rate in a private static field ({@code getFps()} arrived in 1.19). */
@Mixin(Minecraft.class)
public interface MinecraftFpsAccessor {
    @Accessor("fps")
    static int lumora$fps() {
        throw new AssertionError("mixin");
    }
}
