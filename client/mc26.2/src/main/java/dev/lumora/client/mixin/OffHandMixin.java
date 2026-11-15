package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.core.extras.OffHandModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module "Off Hand" in first person ({@code ItemInHandRenderer#submitArmWithItem}, called once per hand and frame): skips a hand when
 * {@link OffHandModule#hide} says so, and draws the empty off hand as a bare arm (vanilla only draws the empty main
 * hand) on the side opposite the player's main arm. Only this client's view changes.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class OffHandMixin {
    @Shadow
    private void renderPlayerArm(PoseStack pose, SubmitNodeCollector buffers, int light, float equip, float swing,
                                 HumanoidArm arm) {
        throw new AssertionError("shadowed");
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$offHand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector buffers,
                                int light, CallbackInfo ci) {
        if (OffHandModule.active() == null) {
            return;
        }
        boolean offHand = hand == InteractionHand.OFF_HAND;
        if (OffHandModule.hide(offHand, stack.getItem() == Items.SHIELD, stack.getItem() == Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        } else if (offHand && stack.isEmpty() && !player.isInvisible() && !player.isScoping() && OffHandModule.showEmptyOffHand()) {
            pose.pushPose();
            renderPlayerArm(pose, buffers, light, equip, swing, player.getMainArm().getOpposite());
            pose.popPose();
            ci.cancel();
        }
    }
}
