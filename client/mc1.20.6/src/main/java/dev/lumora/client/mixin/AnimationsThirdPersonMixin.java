package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Animations", "Item posture (third person)": draws a player's main-hand item with the 1.7 / 1.8 placement
 * (see {@link ItemPose#applyThird}), the item drawn without Minecraft's third-person display transform (context NONE).
 */
@Mixin(ItemInHandLayer.class)
public abstract class AnimationsThirdPersonMixin {
    @SuppressWarnings("rawtypes")
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$legacyThird(LivingEntity entity, ItemStack itemStack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (itemStack.isEmpty() || arm != entity.getMainArm()) {
            return;
        }
        AnimationsModule.Style style = LegacyHands.third(entity, itemStack);
        if (style == null) {
            return;
        }
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        boolean blockLight = mc.getItemRenderer().getModel(itemStack, entity.level(), entity, entity.getId() + ItemDisplayContext.NONE.ordinal()).usesBlockLight();
        poseStack.pushPose();
        ((ArmedModel) ((ItemInHandLayer) (Object) this).getParentModel()).translateToHand(arm, poseStack);
        ItemPose.applyThird(style, new LegacyHands.Pose(poseStack), arm == HumanoidArm.RIGHT, LegacyHands.thirdUse(entity, itemStack),
                LegacyHands.kind(itemStack, blockLight));
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(entity, itemStack, ItemDisplayContext.NONE, false, poseStack, buffers, light);
        poseStack.popPose();
    }
}
