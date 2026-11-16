package dev.lumora.client.mixin.perf;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lumora.core.perf.PerformanceEngine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cloud optimization: Minecraft 1.18.2 builds the whole cloud geometry again whenever the cloud colour changed noticeably
 * (all through sunrise, sunset and rain). With the optimization on, the geometry is built white (only the shading of
 * the faces) and the colour is applied as shader colour when drawing, so a colour change alone no longer rebuilds it.
 * The colour follows Minecraft's own steps (it changes exactly when Minecraft would have rebuilt the clouds); the only
 * difference is rounding: Minecraft stores the coloured vertex colours as bytes, so a channel can differ by at most
 * one of 255 steps. Switching the optimization on or off builds the clouds once more in the other form.
 */
@Mixin(LevelRenderer.class)
public abstract class CloudColorMixin {
    @Unique
    private static final Vec3 LUMORA_WHITE = new Vec3(1, 1, 1);
    @Unique
    private static final float[] lumora$saved = new float[4];
    /** The colour Minecraft would have baked into the cloud geometry. */
    @Unique
    private Vec3 lumora$cloudColor = Vec3.ZERO;
    /** The current cloud geometry was built white (colour comes from the shader). */
    @Unique
    private boolean lumora$white;

    @Redirect(method = "renderClouds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"))
    private double lumora$colourChange(Vec3 previous, Vec3 colour) {
        boolean on = PerformanceEngine.clouds;
        if (on != lumora$white) {
            return Double.MAX_VALUE; // geometry in the other form: build it once more
        }
        if (!on) {
            return previous.distanceToSqr(colour);
        }
        if (lumora$cloudColor.distanceToSqr(colour) > 2.0E-4) {
            lumora$cloudColor = colour; // the step Minecraft would have taken (with a rebuild)
        }
        return 0.0;
    }

    @ModifyArg(method = "renderClouds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;buildClouds(Lcom/mojang/blaze3d/vertex/BufferBuilder;DDDLnet/minecraft/world/phys/Vec3;)V"),
            index = 4)
    private Vec3 lumora$whiteGeometry(Vec3 colour) {
        dev.lumora.client.perf.CloudStats.built();
        lumora$cloudColor = colour;
        lumora$white = PerformanceEngine.clouds;
        return lumora$white ? LUMORA_WHITE : colour;
    }

    @Inject(method = "renderClouds", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private void lumora$tint(CallbackInfo ci) {
        if (lumora$white) {
            float[] c = RenderSystem.getShaderColor();
            System.arraycopy(c, 0, lumora$saved, 0, 4);
            Vec3 k = lumora$cloudColor;
            RenderSystem.setShaderColor(c[0] * (float) k.x, c[1] * (float) k.y, c[2] * (float) k.z, c[3]);
        }
    }

    @Inject(method = "renderClouds", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lcom/mojang/math/Matrix4f;Lcom/mojang/math/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V",
            shift = At.Shift.AFTER))
    private void lumora$untint(CallbackInfo ci) {
        if (lumora$white) {
            RenderSystem.setShaderColor(lumora$saved[0], lumora$saved[1], lumora$saved[2], lumora$saved[3]);
        }
    }
}
