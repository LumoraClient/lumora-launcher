package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Lumora "Cosmetics & Emotes": draws the worn cosmetics on the player model (added to every AvatarRenderer). */
public final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        CosmeticsRender.submit(getParentModel(), poseStack, collector, light, state, ((CosmeticsHolder) state).lumora$cosmetics());
    }
}
