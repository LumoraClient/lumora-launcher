package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.SneakCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lumora "Animations", "Sneaking": the own player in third person is drawn at the height 1.7 drew it while sneaking
 * (lowered with the camera). Other players keep Minecraft's offset, which 1.7 had as well.
 */
@Mixin(PlayerRenderer.class)
public abstract class AnimationsSneakModelMixin {
    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void lumora$sneakOffset(AbstractClientPlayer player, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (player != Minecraft.getInstance().player) {
            return;
        }
        double y = SneakCamera.ownModelOffset(player.isCrouching(), 1.0F);
        if (!Double.isNaN(y)) {
            // Minecraft returns only its crouch offset while crouching, else the general one
            Vec3 v = player.isCrouching() ? Vec3.ZERO : cir.getReturnValue();
            cir.setReturnValue(new Vec3(v.x, v.y + y, v.z));
        }
    }
}
