package dev.lumora.client.mixin;

import dev.lumora.client.api.McBuilding;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * NoClip (Lumora API 1.12): inside a block the world is drawn without occlusion culling, as for spectators; otherwise
 * the sections around the camera would disappear.
 */
@Mixin(LevelRenderer.class)
public abstract class NoClipViewMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSpectator()Z"))
    private boolean lumora$noClipView(LocalPlayer player) {
        return player.isSpectator() || McBuilding.noClipView();
    }
}
