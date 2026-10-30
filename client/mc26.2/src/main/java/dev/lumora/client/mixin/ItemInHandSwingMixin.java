package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.LumoraClient;
import dev.lumora.client.anim.SwingAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla first-person swing transform of the main hand with the selected Lumora swing animation
 * (cosmetic only, same hook point as LiquidBounce's Animations module: ItemInHandRenderer#swingArm).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandSwingMixin {
    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void lumora$swingAnimation(float swing, PoseStack pose, int invert, HumanoidArm arm, CallbackInfo ci) {
        if (LumoraClient.config() == null) {
            return;
        }
        SwingAnimation.Mode mode = SwingAnimation.Mode.parse(LumoraClient.config().swingAnimation);
        LocalPlayer player = Minecraft.getInstance().player;
        if (mode == SwingAnimation.Mode.VANILLA || player == null || arm != player.getMainArm()) {
            return;
        }
        SwingAnimation.apply(mode, pose, swing, arm);
        ci.cancel();
    }
}
