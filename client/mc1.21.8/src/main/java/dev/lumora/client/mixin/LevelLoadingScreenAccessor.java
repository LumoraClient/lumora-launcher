package dev.lumora.client.mixin;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lumora's world loading screen: the chunk progress of Minecraft's spawn area screen (read only). */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {
    @Accessor("progressListener")
    StoringChunkProgressListener lumora$progressListener();
}
