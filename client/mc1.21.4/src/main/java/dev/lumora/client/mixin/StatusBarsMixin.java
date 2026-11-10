package dev.lumora.client.mixin;

import dev.lumora.core.extras.StatusBarsModule;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Hotbar & Leisten": while the module is on, Minecraft's hotbar, hearts/armor/food/air and experience
 * bar are skipped; the module's HUD widgets draw them where the player put them.
 */
@Mixin(Gui.class)
public abstract class StatusBarsMixin {
    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true)
    private void lumora$hideHotbar_renderItemHotbar(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void lumora$hideHealth_renderPlayerHealth(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void lumora$hideXp_renderExperienceBar(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void lumora$hideXp_renderExperienceLevel(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }
}
