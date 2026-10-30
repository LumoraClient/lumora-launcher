package dev.lumora.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Texture-view blit with a tint color (the public overload has no color parameter). */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {
    @Invoker("submitBlit")
    void lumora$submitBlit(RenderPipeline pipeline, GpuTextureView view, int x0, int y0, int x1, int y1,
                          float u0, float u1, float v0, float v1, int color);
}
