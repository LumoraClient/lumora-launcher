package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.core.extras.OffHandModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module "Off Hand": skips drawing one hand with its item in first person
 * ({@code ItemInHandRenderer#renderArmWithItem}, called once per hand and frame) when {@link OffHandModule#hide} says so.
 * Only this client's view changes.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class OffHandMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$offHand(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                float swing, ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers,
                                int light, CallbackInfo ci) {
        if (OffHandModule.active() != null && OffHandModule.hide(hand == InteractionHand.OFF_HAND,
                stack.getItem() == Items.SHIELD, stack.getItem() == Items.TOTEM_OF_UNDYING)) {
            ci.cancel();
        }
    }
}
