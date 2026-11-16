package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.Culling;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A section has to be built again (blocks changed, chunk loaded): the culler reads its opaque blocks again. */
@Mixin(LevelRenderer.class)
public abstract class SectionDirtyMixin {
    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void lumora$dirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged, CallbackInfo ci) {
        Culling.invalidate(sectionX, sectionY, sectionZ);
    }
}
