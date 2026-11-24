package dev.lumora.client.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Access to LivingEntityRenderer.addFeature for the Lumora cosmetics feature. */
@Mixin(LivingEntityRenderer.class)
public interface CosmeticsLayerInvoker {
    @Invoker("addFeature")
    boolean lumora$addFeature(FeatureRenderer<?> feature);
}
