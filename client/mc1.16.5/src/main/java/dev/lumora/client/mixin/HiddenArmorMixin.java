package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.ArmorAlpha;
import dev.lumora.core.extras.HiddenArmorModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module "Hidden Armor": asks {@link HiddenArmorModule#alpha} once per armor piece of a player, right before
 * Minecraft draws it. 0 skips the piece, 1 leaves it alone, anything in between draws it translucent (see
 * {@link ArmorAlpha}); the enchantment glint is only drawn at full opacity. Only this client's view changes.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class HiddenArmorMixin {
    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void lumora$hiddenArmor(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot,
                                    int light, HumanoidModel<?> model, CallbackInfo ci) {
        ArmorAlpha.current = 1f;
        if (HiddenArmorModule.active() == null || !(entity instanceof Player)) {
            return;
        }
        float a = HiddenArmorModule.alpha(ArmorAlpha.slot(slot), entity == Minecraft.getInstance().player,
                entity.getId(), entity.hurtTime > 0);
        if (a <= 0f) {
            ci.cancel();
        } else {
            ArmorAlpha.current = a;
        }
    }

    @Inject(method = "renderArmorPiece", at = @At("RETURN"))
    private void lumora$hiddenArmorEnd(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot,
                                       int light, HumanoidModel<?> model, CallbackInfo ci) {
        ArmorAlpha.current = 1f;
    }

    @Redirect(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType lumora$translucentArmor(ResourceLocation texture) {
        return ArmorAlpha.current < 1f ? RenderType.entityTranslucent(texture) : RenderType.armorCutoutNoCull(texture);
    }

    @ModifyArg(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;getArmorFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            index = 3)
    private boolean lumora$glintOnlyOpaque(boolean foil) {
        return foil && ArmorAlpha.current >= 1f;
    }

    @ModifyArg(method = "renderModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/HumanoidModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
            index = 7)
    private float lumora$renderModelAlpha(float alpha) {
        return alpha * ArmorAlpha.current;
    }
}
