package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora's HUD layer on top of the vanilla HUD. */
@Mixin(InGameHud.class)
public abstract class LegacyHudMixin {
    @Inject(method = "render(F)V", at = @At("RETURN"))
    private void lumora$hud(float delta, CallbackInfo ci) {
        Hooks.fireHud(MinecraftClient.getInstance(), delta);
    }
}
