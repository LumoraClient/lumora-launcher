package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.ArmorAlpha;
import dev.lumora.core.extras.HiddenArmorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module "Hidden Armor": asks {@link HiddenArmorModule#alpha} once per armor piece of a player, right before
 * Minecraft draws it. 0 skips the piece, 1 leaves it alone, anything in between draws it translucent (see
 * {@link ArmorAlpha}); the enchantment glint is only drawn at full opacity. Only this client's view changes.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HiddenArmorMixin {
    /** The player whose armor is being drawn (render() hands it to renderArmorPiece). */
    @Unique
    private static HumanoidRenderState lumora$state;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"))
    private void lumora$hiddenArmorState(PoseStack pose, MultiBufferSource buffers, int light, HumanoidRenderState state,
                                         float yRot, float xRot, CallbackInfo ci) {
        lumora$state = state;
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("RETURN"))
    private void lumora$hiddenArmorStateEnd(PoseStack pose, MultiBufferSource buffers, int light, HumanoidRenderState state,
                                            float yRot, float xRot, CallbackInfo ci) {
        lumora$state = null;
    }

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void lumora$hiddenArmor(PoseStack pose, MultiBufferSource buffers, ItemStack stack, EquipmentSlot slot, int light,
                                    HumanoidModel<?> model, CallbackInfo ci) {
        ArmorAlpha.current = 1f;
        HumanoidRenderState state = lumora$state;
        if (HiddenArmorModule.active() == null || !(state instanceof PlayerRenderState p)) {
            return;
        }
        float a = HiddenArmorModule.alpha(ArmorAlpha.slot(slot), Minecraft.getInstance().player != null && p.id == Minecraft.getInstance().player.getId(), p.id, state.hasRedOverlay);
        if (a <= 0f) {
            ci.cancel();
        } else {
            ArmorAlpha.current = a;
        }
    }

    @Inject(method = "renderArmorPiece", at = @At("RETURN"))
    private void lumora$hiddenArmorEnd(PoseStack pose, MultiBufferSource buffers, ItemStack stack, EquipmentSlot slot, int light,
                                       HumanoidModel<?> model, CallbackInfo ci) {
        ArmorAlpha.current = 1f;
    }
}
