package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.anim.LegacyArmed;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Item posture (third person)": draws a player's main-hand item with the 1.7 / 1.8 placement
 * (see {@link ItemPose#applyThird}) when {@code AnimationsArmedStateMixin} decided so.
 */
@Mixin(ItemInHandLayer.class)
public abstract class AnimationsThirdPersonMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$legacyThird(ArmedEntityRenderState state, ItemStackRenderState item, HumanoidArm arm,
                                    PoseStack poseStack, MultiBufferSource buffers, int lightCoords, CallbackInfo ci) {
        if (!(state instanceof LegacyArmed legacy) || arm != state.mainArm || item.isEmpty()) {
            return;
        }
        AnimationsModule.Style style = legacy.lumora$thirdStyle();
        if (style == null) {
            return;
        }
        ci.cancel();
        poseStack.pushPose();
        ((ArmedModel) ((ItemInHandLayer) (Object) this).getParentModel()).translateToHand(arm, poseStack);
        ItemPose.applyThird(style, new LegacyHands.Pose(poseStack), arm == HumanoidArm.RIGHT, legacy.lumora$thirdUse(),
                LegacyHands.kind(legacy.lumora$thirdStack(), item));
        item.render(poseStack, buffers, lightCoords, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
