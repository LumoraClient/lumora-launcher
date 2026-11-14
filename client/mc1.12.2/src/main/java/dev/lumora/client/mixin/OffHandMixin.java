package dev.lumora.client.mixin;

import dev.lumora.core.extras.OffHandModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module "Off Hand": skips drawing one hand with its item in first person ({@code HeldItemRenderer#method_12329} =
 * renderItemInFirstPerson in Minecraft 1.12.2, called once per hand and frame) when {@link OffHandModule#hide} says so.
 * The method pushes and pops its own GL matrix, so skipping it whole leaves the GL state as it was.
 */
@Mixin(HeldItemRenderer.class)
public abstract class OffHandMixin {
    @Inject(method = "method_12329", at = @At("HEAD"), cancellable = true)
    private void lumora$offHand(AbstractClientPlayerEntity player, float partialTick, float pitch, Hand hand, float swing,
                                ItemStack stack, float equip, CallbackInfo ci) {
        if (OffHandModule.active() != null && OffHandModule.hide(hand == Hand.OFF_HAND,
                stack.getItem() == Items.SHIELD, stack.getItem() == Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }
}
