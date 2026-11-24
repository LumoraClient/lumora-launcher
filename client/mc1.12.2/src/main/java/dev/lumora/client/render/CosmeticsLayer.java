package dev.lumora.client.render;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;

/** Lumora "Cosmetics & Emotes": draws the worn cosmetics on the player model (added to every PlayerEntityRenderer). */
public final class CosmeticsLayer implements FeatureRenderer<AbstractClientPlayerEntity> {
    private final PlayerEntityRenderer renderer;

    public CosmeticsLayer(PlayerEntityRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void render(AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float age,
                       float headYaw, float headPitch, float scale) {
        CosmeticsRender.draw(renderer.getModel(), player, tickDelta);
    }

    @Override
    public boolean combineTextures() {
        return false;
    }
}
