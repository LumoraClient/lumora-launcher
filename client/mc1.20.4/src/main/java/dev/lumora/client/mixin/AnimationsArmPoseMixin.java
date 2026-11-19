package dev.lumora.client.mixin;

import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Item posture (third person)": the blocking arm of a player as in 1.7 (not turned) or 1.8
 * (turned by 30°), without the modern head follow (see {@link ItemPose#blockingArm}). Render thread only.
 */
@Mixin(HumanoidModel.class)
public abstract class AnimationsArmPoseMixin {
    /** The legacy style of the entity being posed now, {@code null} for Minecraft. */
    @Unique
    private static AnimationsModule.Style lumora$posing;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void lumora$entity(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
                               CallbackInfo ci) {
        lumora$posing = LegacyHands.third(entity, entity.getMainHandItem());
    }

    @Inject(method = "poseBlockingArm", at = @At("HEAD"), cancellable = true)
    private void lumora$blockingArm(ModelPart arm, boolean right, CallbackInfo ci) {
        AnimationsModule.Style style = lumora$posing;
        if (style != null) {
            float[] r = ItemPose.blockingArm(style, arm.xRot, right);
            arm.xRot = r[0];
            arm.yRot = r[1];
            ci.cancel();
        }
    }
}
