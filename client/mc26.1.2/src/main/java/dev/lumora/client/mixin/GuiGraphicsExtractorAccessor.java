package dev.lumora.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Texture-view blit with a tint color (the public overload has no color parameter). */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
    @Invoker("innerBlit")
    void lumora$innerBlit(RenderPipeline pipeline, GpuTextureView view, GpuSampler sampler, int x0, int y0, int x1, int y1,
                          float u0, float u1, float v0, float v1, int color);
}
