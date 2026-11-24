package dev.lumora.client.mixin;

import dev.lumora.client.render.CosmeticsLayer;
import dev.lumora.client.render.CosmeticsRender;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Cosmetics & Emotes": adds the cosmetics feature, makes the player's frame current while it is drawn and
 * moves the whole player for emotes like sitting (after the body turn, method_5777).
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class CosmeticsAvatarMixin {
    @Inject(method = "<init>(Lnet/minecraft/client/render/entity/EntityRenderDispatcher;Z)V", at = @At("TAIL"))
    private void lumora$cosmeticsLayer(EntityRenderDispatcher dispatcher, boolean slim, CallbackInfo ci) {
        ((CosmeticsLayerInvoker) (Object) this).lumora$addFeature(new CosmeticsLayer((PlayerEntityRenderer) (Object) this));
    }

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;DDDFF)V", at = @At("HEAD"))
    private void lumora$cosmeticsBegin(AbstractClientPlayerEntity player, double x, double y, double z, float yaw, float partial,
                                       CallbackInfo ci) {
        CosmeticsRender.begin(player, partial);
    }

    @Inject(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;DDDFF)V", at = @At("RETURN"))
    private void lumora$cosmeticsEnd(AbstractClientPlayerEntity player, double x, double y, double z, float yaw, float partial,
                                     CallbackInfo ci) {
        CosmeticsRender.end();
    }

    @Inject(method = "method_5777(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFF)V", at = @At("TAIL"))
    private void lumora$emoteRoot(AbstractClientPlayerEntity player, float age, float bodyYaw, float partial, CallbackInfo ci) {
        CosmeticsRender.root();
    }
}
