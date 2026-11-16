package dev.lumora.client.mixin.perf;

import dev.lumora.client.perf.LegacyClouds;
import dev.lumora.core.perf.PerformanceEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cloud optimization (1.8.9): the fancy clouds from a display list ({@link LegacyClouds}) instead of 128 draws per frame. */
@Mixin(WorldRenderer.class)
public abstract class CloudListMixin {
    @Shadow
    @Final
    private static Identifier CLOUDS;
    @Shadow
    @Final
    private MinecraftClient client;
    @Shadow
    @Final
    private TextureManager textureManager;
    @Shadow
    private ClientWorld world;
    @Shadow
    private int ticks;

    @Inject(method = "renderFancyClouds", at = @At("HEAD"), cancellable = true)
    private void lumora$cachedClouds(float tickDelta, int anaglyph, CallbackInfo ci) {
        if (!PerformanceEngine.clouds) {
            return;
        }
        // the camera position exactly as Minecraft 1.8.9 computes it for the clouds
        Entity cam = client.getCameraEntity();
        double x = cam.prevX + (cam.x - cam.prevX) * (double) tickDelta;
        double y = cam.prevTickY + (cam.y - cam.prevTickY) * (double) tickDelta;
        double z = cam.prevZ + (cam.z - cam.prevZ) * (double) tickDelta;
        LegacyClouds.render(tickDelta, anaglyph, x, y, z, ticks, world.dimension.getCloudHeight(), world.getCloudColor(tickDelta),
                textureManager, CLOUDS);
        ci.cancel();
    }
}
