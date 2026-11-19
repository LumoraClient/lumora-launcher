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
 * Lumora "Animations", "Item posture (third person)": the blocking arm of a player as in 1.7 (not turned). This
 * version's blocking arm does not follow the head yet, so its x and the 1.8 turn by 30° are already the legacy ones
 * (see {@link ItemPose#blockingArm}).
 */
@Mixin(HumanoidModel.class)
public abstract class AnimationsArmPoseMixin {
    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void lumora$right(LivingEntity entity, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (model.rightArmPose == HumanoidModel.ArmPose.BLOCK) {
            lumora$block(entity, model.rightArm, true);
        }
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void lumora$left(LivingEntity entity, CallbackInfo ci) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        if (model.leftArmPose == HumanoidModel.ArmPose.BLOCK) {
            lumora$block(entity, model.leftArm, false);
        }
    }

    @Unique
    private static void lumora$block(LivingEntity entity, ModelPart arm, boolean right) {
        AnimationsModule.Style style = LegacyHands.third(entity, entity.getMainHandItem());
        if (style != null) {
            arm.yRot = ItemPose.blockingArm(style, arm.xRot, right)[1];
        }
    }
}
