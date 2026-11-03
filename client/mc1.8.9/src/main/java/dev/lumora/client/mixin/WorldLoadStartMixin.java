package dev.lumora.client.mixin;

import dev.lumora.client.api.McWorldLoad;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.level.LevelInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A singleplayer world starts opening: Lumora's loading screen takes over (a new world comes with its settings). */
@Mixin(MinecraftClient.class)
public abstract class WorldLoadStartMixin {
    @Inject(method = "startIntegratedServer", at = @At("HEAD"))
    private void lumora$start(String fileName, String displayName, LevelInfo info, CallbackInfo ci) {
        McWorldLoad.starting(displayName, info != null);
    }
}
