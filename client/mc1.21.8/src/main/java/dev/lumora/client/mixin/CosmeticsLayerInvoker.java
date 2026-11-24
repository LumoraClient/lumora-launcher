package dev.lumora.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to LivingEntityRenderer.addLayer for the Lumora cosmetics layer. */
@Mixin(LivingEntityRenderer.class)
public interface CosmeticsLayerInvoker {
    @Invoker("addLayer")
    boolean lumora$addLayer(RenderLayer<?, ?> layer);
}
