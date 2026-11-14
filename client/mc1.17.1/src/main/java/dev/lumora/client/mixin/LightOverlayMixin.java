package dev.lumora.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import dev.lumora.client.render.LightOverlayRender;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Module "Light Block Overlay": its marks are drawn right before the debug renderers, every frame. */
@Mixin(LevelRenderer.class)
public abstract class LightOverlayMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderDebug(Lnet/minecraft/client/Camera;)V"))
    private void lumora$lightOverlay(PoseStack pose, float partialTick, long nanos, boolean outline, Camera camera,
                                     GameRenderer gameRenderer, LightTexture light, Matrix4f projection, CallbackInfo ci) {
        // the GL model-view matrix already holds the camera rotation here (vanilla applied the pose): draw unrotated
        LightOverlayRender.draw(new PoseStack(), camera.getPosition());
    }
}
