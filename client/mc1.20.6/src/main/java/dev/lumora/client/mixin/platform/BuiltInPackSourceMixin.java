package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Packs;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** Lumora's resource packs next to the game's own built-in ones (client resources only, not data packs). */
@Mixin(BuiltInPackSource.class)
public abstract class BuiltInPackSourceMixin {
    @Inject(method = "loadPacks", at = @At("RETURN"))
    private void lumora$packs(Consumer<Pack> out, CallbackInfo ci) {
        if ((Object) this instanceof ClientPackSource) {
            Packs.addTo(out);
        }
    }
}
