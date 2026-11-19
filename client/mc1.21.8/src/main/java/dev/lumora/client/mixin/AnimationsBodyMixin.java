package dev.lumora.client.mixin;

import dev.lumora.core.extras.anim.BodyTurn;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations": backwards walking and head rotation of players as in 1.7/1.8 or modern (see {@link BodyTurn}).
 * Client side only: the body rotation of players is never sent to the server.
 */
@Mixin(LivingEntity.class)
public abstract class AnimationsBodyMixin {
    @Shadow
    public float yBodyRot;

    @Shadow
    protected abstract float getMaxHeadRotationRelativeToBody();

    @Inject(method = "tickHeadTurn", at = @At("HEAD"), cancellable = true)
    private void lumora$bodyTurn(float yBodyRotT, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player) || !self.level().isClientSide()) {
            return;
        }
        // this version turns the target round when walking backwards and limits the head to 50° itself
        float body = BodyTurn.tick(this.yBodyRot, self.getYRot(), yBodyRotT, self.attackAnim > 0.0F, self.getX() - self.xo, self.getZ() - self.zo,
                this.getMaxHeadRotationRelativeToBody(), true, true);
        if (!Float.isNaN(body)) {
            this.yBodyRot = body;
            ci.cancel();
        }
    }
}
