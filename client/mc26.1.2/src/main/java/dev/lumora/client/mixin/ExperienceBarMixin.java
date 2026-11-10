package dev.lumora.client.mixin;

import dev.lumora.core.extras.StatusBarsModule;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora "Hotbar & Leisten": Minecraft's experience bar is skipped while the module draws its own. */
@Mixin(ExperienceBarRenderer.class)
public abstract class ExperienceBarMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void lumora$background(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void lumora$bar(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }
}
