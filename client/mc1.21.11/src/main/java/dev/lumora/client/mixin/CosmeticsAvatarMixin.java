package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.lumora.client.render.CosmeticsHolder;
import dev.lumora.client.render.CosmeticsLayer;
import dev.lumora.client.render.CosmeticsRender;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lumora "Cosmetics & Emotes": adds the cosmetics layer, builds each player's frame while its render state is
 * extracted and moves the whole player for emotes like sitting (after the body turn).
 */
@Mixin(AvatarRenderer.class)
public abstract class CosmeticsAvatarMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "<init>", at = @At("TAIL"))
    private void lumora$cosmeticsLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
        ((CosmeticsLayerInvoker) (Object) this).lumora$addLayer(new CosmeticsLayer(parent));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void lumora$cosmeticsFrame(Avatar entity, AvatarRenderState state, float partial, CallbackInfo ci) {
        ((CosmeticsHolder) state).lumora$cosmetics(CosmeticsRender.frame(entity, state, partial));
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void lumora$emoteRoot(AvatarRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
        CosmeticsRender.root(pose, ((CosmeticsHolder) state).lumora$cosmetics());
    }
}
