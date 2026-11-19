package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.OffHandModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", first-person item posture: draws the main hand with the 1.7 / 1.8 transform (see
 * {@link ItemPose}) instead of Minecraft's arm transform, the item prepared without its first-person display
 * transform ({@link ItemDisplayContext#NONE}).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class AnimationsHandDrawMixin {
    @Shadow
    @Final
    private ItemModelResolver itemModelResolver;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$legacyHand(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
                                   ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
                                   int lightCoords, CallbackInfo ci) {
        if (dev.lumora.client.zoom.LumoraZoom.hidesHand()) {
            return; // ZoomHandMixin hides the hand
        }
        if (hand != InteractionHand.MAIN_HAND || player.isScoping()) {
            return;
        }
        LegacyHands.decide(player, itemStack);
        AnimationsModule.Style version = ItemPose.frameVersion();
        if (version == null) {
            return;
        }
        ci.cancel();
        if (OffHandModule.hide(false, itemStack.is(Items.SHIELD), itemStack.is(Items.TOTEM_OF_UNDYING))) {
            return;
        }
        ItemStackRenderState render = new ItemStackRenderState();
        this.itemModelResolver.updateForTopItem(render, itemStack, ItemDisplayContext.NONE, player.level(), player,
                player.getId() + ItemDisplayContext.NONE.ordinal());
        float useCount = (float) player.getUseItemRemainingTicks() - frameInterp + 1.0F;
        poseStack.pushPose();
        ItemPose.apply(version, new LegacyHands.Pose(poseStack), player.getMainArm() == HumanoidArm.RIGHT, inverseArmHeight, attack,
                ItemPose.frameUse(), useCount, itemStack.getUseDuration(player), LegacyHands.kind(itemStack, render));
        render.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
