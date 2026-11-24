package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.CosmeticsLayer;
import dev.lumora.client.render.CosmeticsRender;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Cosmetics & Emotes": adds the cosmetics layer, makes the player's frame current while it is drawn and moves
 * the whole player for emotes like sitting (after the body turn).
 */
@Mixin(PlayerRenderer.class)
public abstract class CosmeticsAvatarMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void lumora$cosmeticsLayer(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent =
                (RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) this;
        ((CosmeticsLayerInvoker) (Object) this).lumora$addLayer(new CosmeticsLayer(parent));
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void lumora$cosmeticsBegin(AbstractClientPlayer player, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                                       int light, CallbackInfo ci) {
        CosmeticsRender.begin(player, partial);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void lumora$cosmeticsEnd(AbstractClientPlayer player, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                                     int light, CallbackInfo ci) {
        CosmeticsRender.end();
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("TAIL"))
    private void lumora$emoteRoot(AbstractClientPlayer player, PoseStack pose, float age, float bodyRot, float partial, CallbackInfo ci) {
        CosmeticsRender.root(pose);
    }
}
