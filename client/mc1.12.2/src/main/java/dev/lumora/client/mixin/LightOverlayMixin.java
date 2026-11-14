package dev.lumora.client.mixin;

import dev.lumora.client.render.LightOverlayRender;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Module "Light Block Overlay": its marks are drawn right before the block breaking cracks, every frame. */
@Mixin(GameRenderer.class)
public abstract class LightOverlayMixin {
    @Inject(method = "renderWorld(IFJ)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;drawBlockDamage(Lnet/minecraft/client/render/Tessellator;Lnet/minecraft/client/render/BufferBuilder;Lnet/minecraft/entity/Entity;F)V"))
    private void lumora$lightOverlay(int pass, float tickDelta, long limit, CallbackInfo ci) {
        LightOverlayRender.draw(tickDelta);
    }
}
