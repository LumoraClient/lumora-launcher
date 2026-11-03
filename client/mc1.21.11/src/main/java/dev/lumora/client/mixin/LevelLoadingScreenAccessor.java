package dev.lumora.client.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lumora's world loading screen: the load tracker of Minecraft's loading screen (read only). */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
    @Accessor("loadTracker")
    LevelLoadTracker lumora$loadTracker();
}
