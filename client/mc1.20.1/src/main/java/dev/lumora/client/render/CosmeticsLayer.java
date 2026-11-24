package dev.lumora.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Lumora "Cosmetics & Emotes": draws the worn cosmetics on the player model (added to every PlayerRenderer). */
public final class CosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CosmeticsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbAmount, float partial, float age, float headYaw, float headPitch) {
        CosmeticsRender.draw(getParentModel(), poseStack, buffers, light, player);
    }
}
