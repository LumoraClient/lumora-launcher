package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Packs;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Lumora's theme and font, built into the client: laid over the selected packs when the resources load (Packs#withLayers). */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Inject(method = "openAllSelected", at = @At("RETURN"), cancellable = true)
    private void lumora$layers(CallbackInfoReturnable<List<PackResources>> cir) {
        cir.setReturnValue(Packs.withLayers((PackRepository) (Object) this, cir.getReturnValue()));
    }
}
