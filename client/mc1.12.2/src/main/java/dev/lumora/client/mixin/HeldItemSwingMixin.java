package dev.lumora.client.mixin;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.anim.SwingAnimation;
import net.minecraft.client.gui.screen.options.HandOption;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the vanilla first-person swing of the main hand with the selected Lumora swing animation (cosmetic only).
 * Minecraft 1.12.2 ({@code method_12329} = renderItemInFirstPerson, unnamed in Yarn), normal items: a sway
 * {@code GlStateManager.translate} (the 4th translate of the method), {@code method_12335} (equip offset, kept) and
 * {@code method_12332} (swing rotation). With a Lumora animation the sway is skipped and the rotation replaced.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemSwingMixin {
    private static final String RENDER = "method_12329";

    @Shadow
    private void method_12332(HandOption side, float swing) {
        throw new AssertionError("shadowed");
    }

    @Unique
    private static SwingAnimation.Mode lumora$mode(Hand hand) {
        if (LumoraClient.config() == null || hand != Hand.MAIN_HAND) {
            return SwingAnimation.Mode.VANILLA;
        }
        return SwingAnimation.Mode.parse(LumoraClient.config().swingAnimation);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;translate(FFF)V", ordinal = 3))
    private void lumora$sway(float x, float y, float z, AbstractClientPlayerEntity player, float partialTick, float pitch,
                             Hand hand, float swingProgress, ItemStack stack, float equip) {
        if (lumora$mode(hand) == SwingAnimation.Mode.VANILLA) {
            com.mojang.blaze3d.platform.GlStateManager.translate(x, y, z);
        }
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;method_12332(Lnet/minecraft/client/gui/screen/options/HandOption;F)V"))
    private void lumora$swing(HeldItemRenderer self, HandOption side, float swing, AbstractClientPlayerEntity player,
                              float partialTick, float pitch, Hand hand, float swingProgress, ItemStack stack, float equip) {
        SwingAnimation.Mode mode = lumora$mode(hand);
        if (mode == SwingAnimation.Mode.VANILLA) {
            method_12332(side, swing);
        } else {
            SwingAnimation.apply(mode, swing, side == HandOption.RIGHT);
        }
    }
}
