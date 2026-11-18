package dev.lumora.client.mixin;

import dev.lumora.core.extras.ToggleSprintModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Module "Toggle Sprint", fly speed: the horizontal flying speed of the own player ({@code Player#getFlyingSpeed},
 * used by travel) times {@link ToggleSprintModule#flySpeed}. Other players and the integrated server are not touched.
 */
@Mixin(Player.class)
public abstract class FlySpeedMixin {
    @Inject(method = "getFlyingSpeed", at = @At("RETURN"), cancellable = true)
    private void lumora$flySpeed(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer self && self.getAbilities().flying && !self.isPassenger()) {
            float f = ToggleSprintModule.flySpeed();
            if (f != 1f) {
                cir.setReturnValue(cir.getReturnValueF() * f);
            }
        }
    }
}
