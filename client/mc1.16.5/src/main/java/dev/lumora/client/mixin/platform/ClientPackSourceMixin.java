package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Packs;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Lumora's resource packs next to the client's built-in ones (Minecraft up to 1.18.2). */
@Mixin(ClientPackSource.class)
public abstract class ClientPackSourceMixin {
    @Inject(method = "loadPacks", at = @At("RETURN"))
    private void lumora$packs(Consumer<Pack> out, Pack.PackConstructor constructor, CallbackInfo ci) {
        Packs.addTo(out, constructor);
    }
}
