package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.OffHandModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
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
 * {@link ItemPose}) instead of Minecraft's arm transform, the item drawn without its first-person display transform
 * (context NONE).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class AnimationsHandDrawMixin {
    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                                    MultiBufferSource buffers, int light);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$legacyHand(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
                                   ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, MultiBufferSource buffers,
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
        boolean blockLight = this.itemRenderer.getModel(itemStack, player.level(), player, player.getId() + ItemDisplayContext.NONE.ordinal()).usesBlockLight();
        float useCount = (float) player.getUseItemRemainingTicks() - frameInterp + 1.0F;
        poseStack.pushPose();
        ItemPose.apply(version, new LegacyHands.Pose(poseStack), player.getMainArm() == HumanoidArm.RIGHT, inverseArmHeight, attack,
                ItemPose.frameUse(), useCount, itemStack.getUseDuration(), LegacyHands.kind(itemStack, blockLight));
        this.renderItem(player, itemStack, ItemDisplayContext.NONE, false, poseStack, buffers, lightCoords);
        poseStack.popPose();
    }
}
