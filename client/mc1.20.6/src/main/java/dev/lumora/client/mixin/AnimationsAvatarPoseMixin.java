package dev.lumora.client.mixin;

import dev.lumora.client.anim.LegacyHands;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Item posture (third person)": a player who blocks with a shield in the off hand while
 * holding a sword is shown blocking with the sword, as 1.7 and 1.8 showed sword blocking (only while the legacy
 * third-person posture is on).
 */
@Mixin(PlayerRenderer.class)
public abstract class AnimationsAvatarPoseMixin {
    @Inject(method = "setModelProperties", at = @At("TAIL"))
    private void lumora$swordBlocking(AbstractClientPlayer player, CallbackInfo ci) {
        ItemStack main = player.getMainHandItem();
        if (LegacyHands.third(player, main) != null && LegacyHands.shieldBlocking(player, main)) {
            HumanoidModel<?> model = ((PlayerRenderer) (Object) this).getModel();
            if (player.getMainArm() == HumanoidArm.RIGHT) {
                model.rightArmPose = HumanoidModel.ArmPose.BLOCK;
                model.leftArmPose = HumanoidModel.ArmPose.ITEM;
            } else {
                model.leftArmPose = HumanoidModel.ArmPose.BLOCK;
                model.rightArmPose = HumanoidModel.ArmPose.ITEM;
            }
        }
    }
}
