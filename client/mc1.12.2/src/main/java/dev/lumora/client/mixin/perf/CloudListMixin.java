package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.LegacyClouds;
import dev.lumora.core.perf.PerformanceEngine;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cloud optimization (1.12.2): the fancy clouds from a display list ({@link LegacyClouds}) instead of 128 draws per frame. */
@Mixin(WorldRenderer.class)
public abstract class CloudListMixin {
    @Shadow
    @Final
    private static Identifier CLOUDS;
    @Shadow
    @Final
    private TextureManager textureManager;
    @Shadow
    private ClientWorld world;
    @Shadow
    private int ticks;

    /** Fancy clouds; the camera position comes from Minecraft (the same values its own code uses). */
    @Inject(method = "method_13851", at = @At("HEAD"), cancellable = true)
    private void lumora$cachedClouds(float tickDelta, int anaglyph, double camX, double camY, double camZ, CallbackInfo ci) {
        if (!PerformanceEngine.clouds) {
            return;
        }
        LegacyClouds.render(tickDelta, anaglyph, camX, camY, camZ, ticks, world.dimension.getCloudHeight(), world.getCloudColor(tickDelta),
                textureManager, CLOUDS);
        ci.cancel();
    }
}
