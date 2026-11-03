package dev.lumora.client.mixin;

import dev.lumora.client.api.McWorldLoad;
import net.minecraft.client.render.LoadingScreenRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a singleplayer world opens, Minecraft draws its loading text directly (no screen); Lumora draws its loading
 * view instead. Outside a world load (or on any error) Minecraft draws as usual.
 */
@Mixin(LoadingScreenRenderer.class)
public abstract class LoadingScreenMixin {
    @Inject(method = "setProgressPercentage", at = @At("HEAD"), cancellable = true)
    private void lumora$draw(int percent, CallbackInfo ci) {
        if (McWorldLoad.drawLoading()) {
            ci.cancel();
        }
    }
}
