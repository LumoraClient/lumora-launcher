package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.OffHandModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", first-person item posture: draws the main hand with the 1.7 / 1.8 transform (see
 * {@link ItemPose}) instead of Minecraft's arm transform when {@code AnimationsHandPrepareMixin} decided so.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class AnimationsHandDrawMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$legacyHand(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
                                   InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                                   SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
        AnimationsModule.Style version = ItemPose.frameVersion();
        AvatarRenderState avatar = playerState.avatarRenderState;
        if (version == null || hand != InteractionHand.MAIN_HAND || state.isScoping || avatar == null || itemStack != state.mainHandItem) {
            return;
        }
        ci.cancel();
        if (OffHandModule.hide(false, itemStack.is(Items.SHIELD), itemStack.is(Items.TOTEM_OF_UNDYING))) {
            return;
        }
        float useCount = (float) state.useItemRemainingTicks - partialTicks + 1.0F;
        poseStack.pushPose();
        ItemPose.apply(version, new LegacyHands.Pose(poseStack), avatar.mainArm == HumanoidArm.RIGHT, inverseArmHeight, attack, ItemPose.frameUse(),
                useCount, state.mainHandUseDuration, LegacyHands.kind(itemStack, state.mainHandRenderState));
        state.mainHandRenderState.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

}
