package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After a screen built its widgets (first open and every resize); drawing: GameRendererScreenMixin. */
@Mixin(Screen.class)
public abstract class ScreenHooksMixin {
    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
    private void lumora$afterInit(net.minecraft.client.Minecraft mc, int width, int height, CallbackInfo ci) {
        Hooks.fireScreenInit((Screen) (Object) this, width, height);
    }
}
