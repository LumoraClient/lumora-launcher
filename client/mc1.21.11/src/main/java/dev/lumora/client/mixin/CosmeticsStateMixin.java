package dev.lumora.client.mixin;

import dev.lumora.client.render.CosmeticsHolder;
import dev.lumora.core.cosmetics.PlayerFrame;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the Lumora cosmetics frame from extraction to the model pose and the cosmetics layer. */
@Mixin(AvatarRenderState.class)
public abstract class CosmeticsStateMixin implements CosmeticsHolder {
    @Unique
    private PlayerFrame lumora$cosmetics;

    @Override
    public PlayerFrame lumora$cosmetics() {
        return lumora$cosmetics;
    }

    @Override
    public void lumora$cosmetics(PlayerFrame frame) {
        lumora$cosmetics = frame;
    }
}
