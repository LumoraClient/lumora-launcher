package dev.lumora.client.mixin;

import dev.lumora.client.render.CosmeticsHolder;
import dev.lumora.client.render.CosmeticsRender;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lumora "Cosmetics & Emotes": the emote moves the model parts after Minecraft's own animation. */
@Mixin(PlayerModel.class)
public abstract class CosmeticsModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void lumora$emotePose(PlayerRenderState state, CallbackInfo ci) {
        CosmeticsRender.pose((PlayerModel) (Object) this, ((CosmeticsHolder) state).lumora$cosmetics());
    }
}
