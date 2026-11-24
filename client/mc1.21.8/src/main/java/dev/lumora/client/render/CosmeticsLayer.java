package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/** Lumora "Cosmetics & Emotes": draws the worn cosmetics on the player model (added to every PlayerRenderer). */
public final class CosmeticsLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
    public CosmeticsLayer(RenderLayerParent<PlayerRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
        CosmeticsRender.draw(getParentModel(), poseStack, buffers, light, state, ((CosmeticsHolder) state).lumora$cosmetics());
    }
}
