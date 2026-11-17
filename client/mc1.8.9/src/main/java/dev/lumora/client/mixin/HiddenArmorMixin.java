package dev.lumora.client.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.lumora.client.render.ArmorAlpha;
import dev.lumora.core.extras.HiddenArmorModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Module "Hidden Armor": asks {@link HiddenArmorModule#alpha} once per armor piece of a player, right before
 * Minecraft draws it. 0 skips the piece, 1 leaves it alone, anything in between draws it translucent (see
 * {@link ArmorAlpha}); the enchantment glint is only drawn at full opacity. Only this client's view changes.
 */
@Mixin(ArmorFeatureRenderer.class)
public abstract class HiddenArmorMixin {
    /** The piece being drawn was made translucent (blending switched on for it). */
    @Unique
    private static boolean lumora$blended;

    @Inject(method = "renderFeature", at = @At("HEAD"), cancellable = true)
    private void lumora$hiddenArmor(LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float age,
                                    float headYaw, float headPitch, float scale, int slot, CallbackInfo ci) {
        ArmorAlpha.current = 1f;
        lumora$blended = false;
        if (HiddenArmorModule.active() == null || !(entity instanceof PlayerEntity)) {
            return;
        }
        float a = HiddenArmorModule.alpha(4 - slot, entity == MinecraftClient.getInstance().player, entity.getEntityId(),
                entity.hurtTime > 0);
        if (a <= 0f) {
            ci.cancel();
        } else if (a < 1f) {
            ArmorAlpha.current = a;
            lumora$blended = true;
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
        }
    }

    @Inject(method = "renderFeature", at = @At("RETURN"))
    private void lumora$hiddenArmorEnd(LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float age,
                                       float headYaw, float headPitch, float scale, int slot, CallbackInfo ci) {
        if (lumora$blended) {
            lumora$blended = false;
            GlStateManager.disableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
        ArmorAlpha.current = 1f;
    }

    @Redirect(method = "renderFeature", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;color(FFFF)V"))
    private void lumora$alpha(float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a * ArmorAlpha.current);
    }

    /** The enchantment glint only at full opacity (outside of an armor piece the opacity is 1). */
    @Inject(method = "renderGlint", at = @At("HEAD"), cancellable = true)
    private void lumora$glintOnlyOpaque(LivingEntity entity, EntityModel model, float limbAngle, float limbDistance, float tickDelta,
                                        float age, float headYaw, float headPitch, float scale, CallbackInfo ci) {
        if (ArmorAlpha.current < 1f) {
            ci.cancel();
        }
    }
}
