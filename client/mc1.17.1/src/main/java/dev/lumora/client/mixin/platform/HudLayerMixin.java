package dev.lumora.client.mixin.platform;

import dev.lumora.client.platform.Hooks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora's HUD layer on top of the vanilla HUD. */
@Mixin(Gui.class)
public abstract class HudLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("RETURN"))
    private void lumora$hud(PoseStack g, float delta, CallbackInfo ci) {
        Hooks.fireHud(g, delta);
    }
}
