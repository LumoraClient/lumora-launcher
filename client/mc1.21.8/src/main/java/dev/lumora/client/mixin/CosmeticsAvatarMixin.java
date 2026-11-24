package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.CosmeticsHolder;
import dev.lumora.client.render.CosmeticsLayer;
import dev.lumora.client.render.CosmeticsRender;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Cosmetics & Emotes": adds the cosmetics layer, builds each player's frame while its render state is
 * extracted and moves the whole player for emotes like sitting (after the body turn).
 */
@Mixin(PlayerRenderer.class)
public abstract class CosmeticsAvatarMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("TAIL"))
    private void lumora$cosmeticsLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        RenderLayerParent<PlayerRenderState, PlayerModel> parent = (RenderLayerParent<PlayerRenderState, PlayerModel>) (Object) this;
        ((CosmeticsLayerInvoker) (Object) this).lumora$addLayer(new CosmeticsLayer(parent));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("TAIL"))
    private void lumora$cosmeticsFrame(AbstractClientPlayer entity, PlayerRenderState state, float partial, CallbackInfo ci) {
        ((CosmeticsHolder) state).lumora$cosmetics(CosmeticsRender.frame(entity, state, partial));
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void lumora$emoteRoot(PlayerRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
        CosmeticsRender.root(pose, ((CosmeticsHolder) state).lumora$cosmetics());
    }
}
