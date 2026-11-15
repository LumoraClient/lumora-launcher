package dev.lumora.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.core.extras.OffHandModule;
import net.minecraft.client.gui.screen.options.HandOption;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module "Off Hand" in first person ({@code HeldItemRenderer#method_12329} = renderItemInFirstPerson in Minecraft
 * 1.12.2, called once per hand and frame): skips a hand when {@link OffHandModule#hide} says so, and draws the empty
 * off hand as a bare arm ({@code method_12325}, vanilla only draws the empty main hand) on the side opposite the
 * player's main arm ({@code getDurability()} is the main arm in these Yarn names, {@code method_13037} the opposite).
 * The method pushes and pops its own GL matrix, so skipping it whole leaves the GL state as it was.
 */
@Mixin(HeldItemRenderer.class)
public abstract class OffHandMixin {
    @Shadow
    private void method_12325(float equip, float swing, HandOption side) {
        throw new AssertionError("shadowed");
    }

    @Inject(method = "method_12329", at = @At("HEAD"), cancellable = true)
    private void lumora$offHand(AbstractClientPlayerEntity player, float partialTick, float pitch, Hand hand, float swing,
                                ItemStack stack, float equip, CallbackInfo ci) {
        if (OffHandModule.active() == null) {
            return;
        }
        boolean offHand = hand == Hand.OFF_HAND;
        if (OffHandModule.hide(offHand, stack.getItem() == Items.SHIELD, stack.getItem() == Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        } else if (offHand && stack.isEmpty() && !player.isInvisible() && OffHandModule.showEmptyOffHand()) {
            GlStateManager.pushMatrix();
            method_12325(equip, swing, player.getDurability().method_13037());
            GlStateManager.popMatrix();
            ci.cancel();
        }
    }
}
