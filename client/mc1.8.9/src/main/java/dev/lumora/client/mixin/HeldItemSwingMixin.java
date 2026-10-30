package dev.lumora.client.mixin;

import dev.lumora.client.LumoraClient;
import dev.lumora.client.anim.SwingAnimation;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla first-person swing of held items with the selected Lumora swing animation (cosmetic only).
 * Minecraft 1.8.9, normal items: {@code translateSwingProgress} (sway) then {@code applyEquipAndSwingOffset(equip,
 * swing)}; that is the last of its five calls in {@code renderArmHoldingItem} (the others: maps, eating, blocking,
 * bows - left vanilla). With a Lumora animation the sway is skipped, the vanilla swing rotation gets swing 0 (the equip
 * offset stays) and the Lumora transform follows.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemSwingMixin {
    private static final String RENDER = "renderArmHoldingItem";
    private static final String EQUIP_AND_SWING = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V";

    @Unique
    private float lumora$swing;

    @Unique
    private static SwingAnimation.Mode lumora$mode() {
        return LumoraClient.config() == null ? SwingAnimation.Mode.VANILLA : SwingAnimation.Mode.parse(LumoraClient.config().swingAnimation);
    }

    @Inject(method = "translateSwingProgress", at = @At("HEAD"), cancellable = true)
    private void lumora$noSway(float swing, CallbackInfo ci) {
        if (lumora$mode() != SwingAnimation.Mode.VANILLA) {
            ci.cancel();
        }
    }

    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = EQUIP_AND_SWING, ordinal = 4), index = 1)
    private float lumora$noVanillaSwing(float swing) {
        lumora$swing = swing;
        return lumora$mode() == SwingAnimation.Mode.VANILLA ? swing : 0f;
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = EQUIP_AND_SWING, ordinal = 4, shift = At.Shift.AFTER))
    private void lumora$swing(float tickDelta, CallbackInfo ci) {
        SwingAnimation.Mode mode = lumora$mode();
        if (mode != SwingAnimation.Mode.VANILLA) {
            SwingAnimation.apply(mode, lumora$swing);
        }
    }
}
