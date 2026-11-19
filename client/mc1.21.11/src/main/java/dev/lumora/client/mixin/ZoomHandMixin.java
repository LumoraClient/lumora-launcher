package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.anim.LegacyHands;
import dev.lumora.core.extras.AnimationsModule;
import dev.lumora.core.extras.OffHandModule;
import dev.lumora.core.extras.anim.ItemPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zoom, "Show hand in first person" off: the hand and the held item are not drawn while zoomed
 * ({@link dev.lumora.client.zoom.LumoraZoom#hidesHand}).
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ZoomHandMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void lumora$zoomHand(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand, float attack,
                                   ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector,
                                   int lightCoords, CallbackInfo ci) {
        if (dev.lumora.client.zoom.LumoraZoom.hidesHand()) {
            ci.cancel();
        }
    }
}
