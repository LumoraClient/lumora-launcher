package dev.lumora.client.mixin;

import dev.lumora.client.api.McWorldLoad;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.LevelSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Remembers which world is being opened, so Lumora's loading screen can name it before the server is up. */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenStartMixin {
    @Inject(method = "openWorld", at = @At("HEAD"))
    private void lumora$open(String levelId, Runnable onCancel, CallbackInfo ci) {
        McWorldLoad.starting(levelId, false);
    }

    @Inject(method = "createFreshLevel", at = @At("HEAD"))
    private void lumora$create(String levelId, LevelSettings settings, net.minecraft.world.level.levelgen.WorldOptions options,
                               java.util.function.Function<net.minecraft.core.HolderLookup.Provider, net.minecraft.world.level.levelgen.WorldDimensions> dimensions, net.minecraft.client.gui.screens.Screen parent, CallbackInfo ci) {
        McWorldLoad.starting(settings == null ? levelId : settings.levelName(), true);
    }
}
