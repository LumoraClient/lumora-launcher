package dev.lumora.client.mixin;

import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.server.level.progress.LevelLoadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lumora's world loading screen: which step the integrated server reports (read only). */
@Mixin(LevelLoadTracker.class)
public interface LevelLoadTrackerAccessor {
    @Accessor("serverStage")
    LevelLoadListener.Stage lumora$serverStage();
}
