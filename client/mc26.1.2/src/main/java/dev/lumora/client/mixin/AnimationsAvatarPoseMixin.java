package dev.lumora.client.mixin;

import dev.lumora.client.anim.LegacyArmed;
import dev.lumora.client.anim.LegacyHands;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Item posture (third person)": a player who blocks with a shield in the off hand while
 * holding a sword is shown blocking with the sword, as 1.7 and 1.8 showed sword blocking (only while the legacy
 * third-person posture is on). With "Old block animation" the own player holding the use key with a sword is shown
 * blocking too.
 */
@Mixin(AvatarRenderer.class)
public abstract class AnimationsAvatarPoseMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void lumora$swordBlocking(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        if (state instanceof LegacyArmed legacy && legacy.lumora$thirdStyle() != null && legacy.lumora$shieldBlocking()) {
            if (state.mainArm == HumanoidArm.RIGHT) {
                state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                state.leftArmPose = HumanoidModel.ArmPose.ITEM;
            } else {
                state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                state.rightArmPose = HumanoidModel.ArmPose.ITEM;
            }
        } else if (LegacyHands.clickBlocking(entity, entity.getMainHandItem())) {
            if (state.mainArm == HumanoidArm.RIGHT) {
                state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
            } else {
                state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
            }
        }
    }
}
