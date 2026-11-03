package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import dev.lumora.client.platform.Packs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Game started, end of every tick, shutdown, and Lumora's assets among the default resource packs. */
@Mixin(MinecraftClient.class)
public abstract class LegacyLifecycleMixin {
    @Shadow
    @Final
    private List<ResourcePack> resourcePacks;

    @Inject(method = "initializeGame", at = @At("HEAD"))
    private void lumora$packs(CallbackInfo ci) {
        Packs.addTo(resourcePacks);
    }

    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;initializeGame()V", shift = At.Shift.AFTER))
    private void lumora$started(CallbackInfo ci) {
        Hooks.fireStarted((MinecraftClient) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void lumora$endTick(CallbackInfo ci) {
        Hooks.fireEndTick((MinecraftClient) (Object) this);
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void lumora$stopping(CallbackInfo ci) {
        Hooks.fireStopping((MinecraftClient) (Object) this);
    }
}
