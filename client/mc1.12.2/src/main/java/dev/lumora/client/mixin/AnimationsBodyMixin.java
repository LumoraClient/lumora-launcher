package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.BodyTurn;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lumora "Animations": backwards walking and head rotation of players as in 1.7/1.8 (see {@link BodyTurn}). Client
 * side only: the body rotation of players is never sent to the server.
 */
@Mixin(LivingEntity.class)
public abstract class AnimationsBodyMixin {
    @Shadow
    public float bodyYaw;
    @Shadow
    public float handSwingProgress;

    @Inject(method = "turnHead", at = @At("HEAD"), cancellable = true)
    private void lumora$bodyTurn(float target, float walked, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity) || !self.world.isClient) {
            return;
        }
        // 1.12 turns the target round when walking backwards, but still lets the head turn 75° like 1.8
        float body = BodyTurn.tick(this.bodyYaw, self.yaw, target, this.handSwingProgress > 0.0F, self.x - self.prevX, self.z - self.prevZ,
                50.0F, true, false);
        if (!Float.isNaN(body)) {
            this.bodyYaw = body;
            cir.setReturnValue(BodyTurn.distance(walked));
        }
    }
}
