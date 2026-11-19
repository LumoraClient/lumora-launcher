package dev.lumora.client.mixin;

import dev.lumora.client.anim.LegacyArmed;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
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
    /** The legacy style of the state being posed now, {@code null} for Minecraft. */
    @Unique
    private static AnimationsModule.Style lumora$posing;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"))
    private void lumora$state(HumanoidRenderState state, CallbackInfo ci) {
        lumora$posing = state instanceof LegacyArmed legacy ? legacy.lumora$thirdStyle() : null;
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
