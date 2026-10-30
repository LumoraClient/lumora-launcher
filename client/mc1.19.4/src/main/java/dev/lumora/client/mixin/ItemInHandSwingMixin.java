package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.anim.SwingAnimation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the vanilla first-person swing transform of the main hand with the selected Lumora swing animation
 * (cosmetic only). Up to Minecraft 1.21.1 (here 1.19.4) the swing is inlined in {@code renderArmWithItem} (1.21.2+ has
 * {@code swingArm}): a sway {@code translate}, then {@code applyItemArmTransform} (equip offset, kept) and
 * {@code applyItemArmAttackTransform} (the swing rotation). Both swing sites - crossbow and every other item - use
 * that order; with a Lumora animation the sway is skipped and the attack transform is replaced.
 * The translate ordinals (3 = crossbow, 12 = default) are verified against the 1.19.4 bytecode.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandSwingMixin {
    private static final String RENDER_ARM = "renderArmWithItem";

    @Shadow
    private void applyItemArmAttackTransform(PoseStack pose, HumanoidArm arm, float swing) {
        throw new AssertionError("shadowed");
    }

    /** The selected animation for this hand, or VANILLA (off-hand, no config). */
    @Unique
    private static SwingAnimation.Mode lumora$mode(InteractionHand hand) {
        if (LumoraClient.config() == null || hand != InteractionHand.MAIN_HAND) {
            return SwingAnimation.Mode.VANILLA;
        }
        return SwingAnimation.Mode.parse(LumoraClient.config().swingAnimation);
    }

    @Redirect(method = RENDER_ARM, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 3))
    private void lumora$crossbowSway(PoseStack pose, float x, float y, float z, AbstractClientPlayer player, float partialTick,
                                     float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                                     PoseStack pose2, MultiBufferSource buffers, int light) {
        if (lumora$mode(hand) == SwingAnimation.Mode.VANILLA) {
            pose.translate(x, y, z);
        }
    }

    @Redirect(method = RENDER_ARM, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 12))
    private void lumora$itemSway(PoseStack pose, float x, float y, float z, AbstractClientPlayer player, float partialTick,
                                 float pitch, InteractionHand hand, float swing, ItemStack stack, float equip,
                                 PoseStack pose2, MultiBufferSource buffers, int light) {
        if (lumora$mode(hand) == SwingAnimation.Mode.VANILLA) {
            pose.translate(x, y, z);
        }
    }

    @Redirect(method = RENDER_ARM, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
    private void lumora$swing(ItemInHandRenderer self, PoseStack pose, HumanoidArm arm, float swing, AbstractClientPlayer player,
                              float partialTick, float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
                              float equip, PoseStack pose2, MultiBufferSource buffers, int light) {
        SwingAnimation.Mode mode = lumora$mode(hand);
        if (mode == SwingAnimation.Mode.VANILLA) {
            applyItemArmAttackTransform(pose, arm, swing);
        } else {
            SwingAnimation.apply(mode, pose, swing, arm);
        }
    }
}
