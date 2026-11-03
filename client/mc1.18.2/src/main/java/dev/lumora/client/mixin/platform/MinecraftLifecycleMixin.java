package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Game started (main loop about to run), end of every client tick, and shutdown. */
@Mixin(Minecraft.class)
public abstract class MinecraftLifecycleMixin {
    @Inject(method = "run", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;gameThread:Ljava/lang/Thread;"))
    private void lumora$started(CallbackInfo ci) {
        Hooks.fireStarted((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void lumora$endTick(CallbackInfo ci) {
        Hooks.fireEndTick((Minecraft) (Object) this);
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void lumora$stopping(CallbackInfo ci) {
        Hooks.fireStopping((Minecraft) (Object) this);
    }
}
