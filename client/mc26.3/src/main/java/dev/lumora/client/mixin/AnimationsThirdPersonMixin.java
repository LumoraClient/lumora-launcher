package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.anim.LegacyArmed;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
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
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$legacyThird(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack itemStack, HumanoidArm arm,
                                    PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
        if (!(state instanceof LegacyArmed legacy) || arm != state.mainArm || item.isEmpty()) {
            return;
        }
        AnimationsModule.Style style = legacy.lumora$thirdStyle();
        if (style == null) {
            return;
        }
        ci.cancel();
        poseStack.pushPose();
        ((ArmedModel) ((ItemInHandLayer) (Object) this).getParentModel()).translateToHand(state, arm, poseStack);
        ItemPose.applyThird(style, new LegacyHands.Pose(poseStack), arm == HumanoidArm.RIGHT, legacy.lumora$thirdUse(),
                LegacyHands.kind(itemStack, item));
        item.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }
}
