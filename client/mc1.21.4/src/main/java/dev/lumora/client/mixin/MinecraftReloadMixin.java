package dev.lumora.client.mixin;

import dev.lumora.client.api.LumoraRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Source of the API's ResourcesReloadedEvent: posted on the client thread once a pack reload has finished. */
@Mixin(Minecraft.class)
public abstract class MinecraftReloadMixin {
    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private void lumora$reloaded(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        cir.getReturnValue().thenRunAsync(LumoraRuntime::resourcesReloaded, mc);
    }
}
