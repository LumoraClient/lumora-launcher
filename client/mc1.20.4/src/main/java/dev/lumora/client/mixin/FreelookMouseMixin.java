package dev.lumora.client.mixin;

import dev.lumora.core.camera.Freelook;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Freelook (module "Freelook"): mouse movement turns the free camera instead of the player. */
@Mixin(MouseHandler.class)
public abstract class FreelookMouseMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void lumora$freelookTurn(LocalPlayer player, double dx, double dy) {
        if (!Freelook.turn(dx, dy)) {
            player.turn(dx, dy);
        }
    }
}
