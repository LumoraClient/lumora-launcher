package dev.lumora.client.mixin;

import dev.lumora.core.extras.StatusBarsModule;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Hotbar & Leisten" (legacy): while the module is on, Minecraft's hotbar, hearts/armor/food/air and experience
 * bar are skipped; the module's HUD widgets draw them where the player put them.
 */
@Mixin(InGameHud.class)
public abstract class StatusBarsMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void lumora$hideHotbar(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void lumora$hideStatusBars(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void lumora$hideExperience(CallbackInfo ci) {
        if (StatusBarsModule.takesOver()) {
            ci.cancel();
        }
    }
}
